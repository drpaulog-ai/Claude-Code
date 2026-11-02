package com.letrista.app.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Erro apresentável ao utilizador. */
class LyricsException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Gera letras com a API gratuita do Google Gemini (chave grátis em aistudio.google.com),
 * devolvendo o texto em streaming.
 */
class LyricsGenerator(
    private val apiKey: String,
    model: String = DEFAULT_MODEL,
    private val baseUrl: String = BASE_URL,
) {
    private val model = model.trim().ifEmpty { DEFAULT_MODEL }

    private val http = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()

    /**
     * Emite pedaços de texto à medida que chegam. Lança [LyricsException] em caso de erro
     * ou de bloqueio do conteúdo (nesse caso o texto parcial deve ser descartado).
     */
    fun generate(request: SongRequest): Flow<String> = flow {
        val body = JSONObject()
            .put("systemInstruction", JSONObject().put("parts", textParts(PromptBuilder.systemPrompt)))
            .put(
                "contents",
                JSONArray().put(
                    JSONObject().put("role", "user").put("parts", textParts(PromptBuilder.userPrompt(request))),
                ),
            )
            .put("generationConfig", JSONObject().put("temperature", 1.0))

        val response = openStream(body.toString())

        var finishReason = ""
        var receivedText = false
        response.use { resp ->
            val source = resp.body?.source() ?: throw LyricsException("O Gemini não devolveu resposta.")
            try {
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val line = source.readUtf8Line() ?: break
                    if (!line.startsWith("data:")) continue
                    val chunk = JSONObject(line.removePrefix("data:").trim())

                    chunk.optJSONObject("promptFeedback")?.optString("blockReason").orEmpty().let {
                        if (it.isNotEmpty()) throw blocked()
                    }
                    val candidate = chunk.optJSONArray("candidates")?.optJSONObject(0) ?: continue
                    val parts = candidate.optJSONObject("content")?.optJSONArray("parts")
                    if (parts != null) {
                        for (i in 0 until parts.length()) {
                            val part = parts.optJSONObject(i) ?: continue
                            if (part.optBoolean("thought")) continue
                            val text = part.optString("text")
                            if (text.isNotEmpty()) {
                                receivedText = true
                                emit(text)
                            }
                        }
                    }
                    candidate.optString("finishReason").takeIf { it.isNotEmpty() }?.let { finishReason = it }
                }
            } catch (e: IOException) {
                throw LyricsException("A ligação foi interrompida. Tenta novamente.", e)
            }
        }

        when (finishReason) {
            "SAFETY", "PROHIBITED_CONTENT", "BLOCKLIST", "SPII", "RECITATION" -> throw blocked()
            "MAX_TOKENS" -> emit("\n\n[A letra foi cortada por exceder o tamanho máximo.]")
        }
        if (!receivedText) throw LyricsException("O Gemini não devolveu texto. Tenta novamente.")
    }.flowOn(Dispatchers.IO)

    private fun blocked() =
        LyricsException("O Gemini não pôde criar este conteúdo. Experimenta reformular os temas ou as notas.")

    /**
     * Abre o streaming. Se o modelo estiver sobrecarregado (503), sem quota (429) ou não existir,
     * tenta automaticamente os outros modelos gratuitos, com pequenas pausas entre tentativas.
     */
    private suspend fun openStream(payload: String): Response {
        val models = (listOf(model) + FALLBACK_MODELS).distinct()
        var busy = false
        var rateLimited = false
        var chosenModelMissing = false

        repeat(ROUNDS) { round ->
            if (round > 0) delay(ROUND_PAUSE_MS)
            for (m in models) {
                currentCoroutineContext().ensureActive()
                val request = Request.Builder()
                    .url("$baseUrl/models/$m:streamGenerateContent?alt=sse")
                    .header("x-goog-api-key", apiKey)
                    .post(payload.toRequestBody(JSON))
                    .build()
                val resp = try {
                    http.newCall(request).execute()
                } catch (e: IOException) {
                    throw LyricsException("Sem ligação à internet ou ao Gemini.", e)
                }
                if (resp.isSuccessful) return resp

                val code = resp.code
                val message = resp.use { errorMessage(it.body?.string().orEmpty()) }
                val wrongModel = code == 404 ||
                    (code == 400 && message.contains("model", true) && !message.contains("api key", true))
                when {
                    code == 503 || code == 500 || code == 504 -> { busy = true; delay(RETRY_PAUSE_MS) }
                    code == 429 -> { rateLimited = true; delay(RETRY_PAUSE_MS) }
                    wrongModel -> if (m == model) chosenModelMissing = true
                    code == 400 || code == 401 || code == 403 ->
                        throw LyricsException("Chave do Gemini inválida ou sem permissão. Verifica-a nas definições.")
                    else -> throw LyricsException("Erro do Gemini ($code): $message")
                }
            }
        }
        throw when {
            busy -> LyricsException(
                "Os servidores gratuitos do Gemini estão sobrecarregados neste momento. " +
                    "Tenta novamente daqui a um ou dois minutos.",
            )
            rateLimited -> LyricsException(
                "Limite gratuito do Gemini atingido por agora. Espera um minuto e tenta de novo.",
            )
            chosenModelMissing -> LyricsException(
                "O modelo \"$model\" não está disponível para esta chave. " +
                    "Muda o modelo nas definições para um destes: ${availableModels()}",
            )
            else -> LyricsException("Não foi possível contactar o Gemini. Tenta novamente.")
        }
    }

    private fun errorMessage(body: String): String = runCatching {
        JSONObject(body).optJSONObject("error")?.optString("message").orEmpty()
    }.getOrDefault("")

    /** Lista modelos de texto disponíveis para a chave (para ajudar a escolher outro). */
    private fun availableModels(): String = try {
        val req = Request.Builder()
            .url("$baseUrl/models?pageSize=200")
            .header("x-goog-api-key", apiKey)
            .build()
        http.newCall(req).execute().use { resp ->
            val models = JSONObject(resp.body?.string().orEmpty()).optJSONArray("models") ?: JSONArray()
            val names = (0 until models.length()).mapNotNull { i ->
                val m = models.optJSONObject(i) ?: return@mapNotNull null
                val methods = m.optJSONArray("supportedGenerationMethods") ?: return@mapNotNull null
                val canGenerate = (0 until methods.length()).any { methods.optString(it) == "generateContent" }
                m.optString("name").removePrefix("models/").takeIf { canGenerate }
            }.filter { it.startsWith("gemini") && !Regex("embedding|tts|image|audio|live").containsMatchIn(it) }
                .sortedWith(compareByDescending<String> { "flash" in it }.thenByDescending { it })
            if (names.isEmpty()) "(nenhum encontrado)" else names.take(8).joinToString(", ")
        }
    } catch (e: Exception) {
        "(não foi possível obter a lista)"
    }

    private fun textParts(text: String) = JSONArray().put(JSONObject().put("text", text))

    companion object {
        const val DEFAULT_MODEL = "gemini-flash-latest"
        const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta"
        private val JSON = "application/json; charset=utf-8".toMediaType()

        /** Modelos gratuitos tentados (por ordem) quando o escolhido falha. */
        val FALLBACK_MODELS = listOf(
            "gemini-flash-latest",
            "gemini-2.5-flash",
            "gemini-flash-lite-latest",
            "gemini-2.5-flash-lite",
        )
        private const val ROUNDS = 2
        private const val RETRY_PAUSE_MS = 1_000L
        private const val ROUND_PAUSE_MS = 4_000L
    }
}
