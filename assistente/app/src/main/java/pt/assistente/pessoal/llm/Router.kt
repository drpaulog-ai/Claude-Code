package pt.assistente.pessoal.llm

import kotlinx.coroutines.CancellationException
import pt.assistente.pessoal.data.ChatMessage
import pt.assistente.pessoal.data.Mode
import pt.assistente.pessoal.data.Role
import pt.assistente.pessoal.data.SettingsStore
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

data class RouterResult(val text: String, val source: String)

internal data class LocalParse(val answer: String, val confidence: Int?, val escalate: Boolean)

/**
 * Decide quem responde:
 *  1. O modelo local tenta primeiro e indica a sua confiança (0–10).
 *  2. Confiança baixa ou pergunta sobre informação atual → a nuvem responde.
 *  3. Confiança média → a nuvem valida (e corrige, se for preciso) a resposta local.
 *  4. Confiança alta → fica a resposta local.
 * Nuvem por ordem de prioridade: Claude → Gemini → ChatGPT (salta os que não têm chave ou falham).
 */
class Router(private val local: LocalLlm, private val settings: SettingsStore) {

    private val providers: List<CloudProvider> = listOf(
        ClaudeProvider(settings),
        GeminiProvider(settings),
        OpenAiProvider(settings),
    )

    private fun configured() = providers.filter { it.isConfigured() }

    suspend fun answer(history: List<ChatMessage>, onStatus: (String) -> Unit): RouterResult {
        val mode = settings.mode
        if (mode == Mode.CLOUD) return cloudAnswer(history, null, onStatus)

        if (!local.isLoaded) {
            if (mode == Mode.LOCAL) {
                return RouterResult("O modelo local não está carregado. Abre Definições → Modelo local.", "Sistema")
            }
            return cloudAnswer(history, null, onStatus)
        }

        onStatus("A pensar localmente…")
        val raw = try {
            local.generate(LOCAL_SYSTEM + dateLine(), localTurns(history))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (mode == Mode.LOCAL) return RouterResult("Erro no modelo local: ${e.message}", "Sistema")
            return cloudAnswer(history, null, onStatus)
        }
        val parsed = parseLocal(raw)

        if (mode == Mode.LOCAL) {
            return RouterResult(
                parsed.answer.ifBlank { "O modelo local acha que esta pergunta precisa de informação atual ou de um modelo maior." },
                localLabel(parsed),
            )
        }

        if (configured().isEmpty()) {
            return RouterResult(
                parsed.answer.ifBlank { "Não consigo responder localmente e não há chaves de nuvem configuradas." },
                localLabel(parsed) + " · sem nuvem configurada",
            )
        }

        val confidence = parsed.confidence ?: 5
        return when {
            parsed.escalate || parsed.answer.isBlank() -> cloudAnswer(history, null, onStatus)
            confidence < settings.escalateBelow -> cloudAnswer(history, parsed.answer, onStatus)
            confidence < settings.acceptFrom -> validate(history, parsed, onStatus)
            else -> RouterResult(parsed.answer, localLabel(parsed))
        }
    }

    private suspend fun cloudAnswer(
        history: List<ChatMessage>,
        draft: String?,
        onStatus: (String) -> Unit,
    ): RouterResult {
        val system = buildString {
            append(CLOUD_SYSTEM)
            append(dateLine())
            if (draft != null) {
                append("\n\nUm modelo local mais pequeno propôs este rascunho, com pouca confiança. ")
                append("Aproveita-o só no que estiver correto e corrige ou substitui o resto:\n\"\"\"\n")
                append(draft)
                append("\n\"\"\"")
            }
        }
        val errors = mutableListOf<String>()
        for (p in configured()) {
            onStatus("A consultar ${p.name}…")
            try {
                val text = p.complete(system, history.takeLast(MAX_CLOUD_HISTORY), settings.webSearch)
                return RouterResult(text, if (draft != null) "${p.name} · substituiu rascunho local" else p.name)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                errors += "${p.name}: ${e.message}"
            }
        }
        val note = if (errors.isEmpty()) "Não há chaves de nuvem configuradas." else errors.joinToString("\n")
        return if (draft != null) {
            RouterResult("$draft\n\n⚠️ Nuvem indisponível:\n$note", "Local · não validado")
        } else {
            RouterResult("⚠️ Nenhum serviço respondeu.\n$note", "Sistema")
        }
    }

    private suspend fun validate(
        history: List<ChatMessage>,
        parsed: LocalParse,
        onStatus: (String) -> Unit,
    ): RouterResult {
        val question = history.lastOrNull { it.role == Role.USER }?.text.orEmpty()
        val check = ChatMessage(
            Role.USER,
            "Pergunta do utilizador:\n$question\n\nResposta proposta pelo modelo local:\n${parsed.answer}",
        )
        val context = history.dropLast(1).takeLast(MAX_CLOUD_HISTORY - 1) + check
        for (p in configured()) {
            onStatus("A validar com ${p.name}…")
            try {
                val text = p.complete(VALIDATE_SYSTEM + dateLine(), context, settings.webSearch)
                return if (text.trim().uppercase(Locale.ROOT).startsWith("VALIDADO")) {
                    RouterResult(parsed.answer, "Local · validado por ${p.name}")
                } else {
                    RouterResult(text, "${p.name} · corrigiu a resposta local")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // tenta o próximo fornecedor
            }
        }
        return RouterResult(parsed.answer, localLabel(parsed) + " · não validado (nuvem indisponível)")
    }

    /** O modelo local só vê as últimas mensagens (e cada uma cortada), para ser rápido. */
    private fun localTurns(history: List<ChatMessage>): List<ChatMessage> =
        normalize(history.takeLast(MAX_LOCAL_HISTORY)).map { it.copy(text = it.text.take(1500)) }

    private fun localLabel(p: LocalParse) =
        if (p.confidence != null) "Local · confiança ${p.confidence}/10" else "Local"

    private fun dateLine(): String {
        val today = LocalDate.now().format(DateTimeFormatter.ofPattern("d 'de' MMMM 'de' yyyy", Locale("pt", "PT")))
        return "\nData de hoje: $today."
    }

    companion object {
        private const val MAX_CLOUD_HISTORY = 20
        private const val MAX_LOCAL_HISTORY = 6

        private const val LOCAL_SYSTEM =
            "És um assistente pessoal. Responde em português de Portugal, de forma clara e concisa.\n" +
                "Regras obrigatórias:\n" +
                "- Se a pergunta exigir informação atual ou em tempo real (notícias, preços, meteorologia, " +
                "resultados, horários, acontecimentos recentes) ou se não souberes responder com segurança, " +
                "responde apenas com a palavra ESCALAR.\n" +
                "- Caso contrário, responde e termina com uma linha no formato 'CONFIANÇA: N', " +
                "em que N vai de 0 (nada confiante) a 10 (totalmente confiante). Sê honesto e rigoroso nessa nota."

        private const val CLOUD_SYSTEM =
            "És o assistente pessoal do utilizador, a funcionar numa app no telemóvel dele. " +
                "Responde em português de Portugal, de forma clara e direta. " +
                "Se a pergunta exigir informação atual, usa a pesquisa na web quando disponível."

        private const val VALIDATE_SYSTEM =
            "És um verificador de respostas. Recebes uma pergunta e a resposta proposta por um pequeno modelo local. " +
                "Se a resposta estiver correta e suficientemente completa, responde apenas com a palavra VALIDADO. " +
                "Se tiver erros, omissões importantes ou informação desatualizada, responde diretamente ao utilizador " +
                "com a resposta corrigida e completa, em português de Portugal, sem mencionar o modelo local nem esta verificação."
    }
}

private val CONFIDENCE_REGEX = Regex("""[Cc][Oo][Nn][Ff][Ii][Aa][Nn][ÇçCc][Aa]\s*\**\s*[:=]?\s*\**\s*(\d{1,2})""")

internal fun parseLocal(raw: String): LocalParse {
    var text = raw.replace("<end_of_turn>", "").trim()
    val escalate = text.uppercase(Locale.ROOT).trimStart('*', ' ').startsWith("ESCALAR")
    val match = CONFIDENCE_REGEX.findAll(text).lastOrNull()
    val confidence = match?.groupValues?.get(1)?.toIntOrNull()?.coerceIn(0, 10)
    if (match != null) text = text.removeRange(match.range).trim().trimEnd('*', '-', '(', ' ').trim()
    return LocalParse(if (escalate) "" else text, confidence, escalate)
}
