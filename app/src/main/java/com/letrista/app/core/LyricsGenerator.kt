package com.letrista.app.core

import com.anthropic.client.AnthropicClient
import com.anthropic.client.okhttp.AnthropicOkHttpClient
import com.anthropic.core.JsonValue
import com.anthropic.errors.AnthropicIoException
import com.anthropic.errors.AnthropicServiceException
import com.anthropic.errors.PermissionDeniedException
import com.anthropic.errors.RateLimitException
import com.anthropic.errors.UnauthorizedException
import com.anthropic.models.beta.messages.BetaOutputConfig
import com.anthropic.models.beta.messages.BetaStopReason
import com.anthropic.models.beta.messages.MessageCreateParams
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlin.jvm.optionals.getOrNull

/** Erro apresentável ao utilizador. */
class LyricsException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** Gera letras com a API do Claude, devolvendo o texto em streaming. */
class LyricsGenerator(apiKey: String) {

    private val client: AnthropicClient = AnthropicOkHttpClient.builder()
        .apiKey(apiKey)
        .build()

    /**
     * Emite pedaços de texto à medida que chegam. Lança [LyricsException] em caso de erro
     * ou de recusa do modelo (nesse caso o texto parcial deve ser descartado).
     */
    fun generate(request: SongRequest): Flow<String> = flow {
        val params = MessageCreateParams.builder()
            .model(MODEL)
            .maxTokens(16000L)
            .system(PromptBuilder.systemPrompt)
            .addUserMessage(PromptBuilder.userPrompt(request))
            .outputConfig(BetaOutputConfig.builder().effort(BetaOutputConfig.Effort.MEDIUM).build())
            // Se o modelo recusar o pedido, a API tenta automaticamente outro modelo recomendado.
            .addBeta("server-side-fallback-2026-07-01")
            .putAdditionalBodyProperty("fallbacks", JsonValue.from("default"))
            .build()

        var stopReason: BetaStopReason? = null
        try {
            client.beta().messages().createStreaming(params).use { response ->
                val events = response.stream().iterator()
                while (events.hasNext()) {
                    currentCoroutineContext().ensureActive()
                    val event = events.next()
                    event.contentBlockDelta().getOrNull()?.delta()?.text()?.getOrNull()?.let {
                        emit(it.text())
                    }
                    event.messageDelta().getOrNull()?.delta()?.stopReason()?.getOrNull()?.let {
                        stopReason = it
                    }
                }
            }
        } catch (e: UnauthorizedException) {
            throw LyricsException("Chave da API inválida. Verifica-a nas definições.", e)
        } catch (e: PermissionDeniedException) {
            throw LyricsException("A chave da API não tem permissão para usar este modelo.", e)
        } catch (e: RateLimitException) {
            throw LyricsException("Demasiados pedidos. Espera um pouco e tenta de novo.", e)
        } catch (e: AnthropicServiceException) {
            throw LyricsException("O serviço devolveu um erro (${e.statusCode()}). Tenta novamente.", e)
        } catch (e: AnthropicIoException) {
            throw LyricsException("Sem ligação à internet ou ligação interrompida.", e)
        }

        when (stopReason) {
            BetaStopReason.REFUSAL ->
                throw LyricsException("O pedido foi recusado. Experimenta reformular os temas ou as notas.")
            BetaStopReason.MAX_TOKENS ->
                emit("\n\n[A letra foi cortada por exceder o tamanho máximo.]")
            else -> Unit
        }
    }.flowOn(Dispatchers.IO)

    companion object {
        const val MODEL = "claude-opus-5-5"
    }
}
