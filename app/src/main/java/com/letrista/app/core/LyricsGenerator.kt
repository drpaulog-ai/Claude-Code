package com.letrista.app.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
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

        val httpRequest = Request.Builder()
            .url("$baseUrl/models/$model:streamGenerateContent?alt=sse")
            .header("x-goog-api-key", apiKey)
            .post(body.toString().toRequestBody(JSON))
            .build()

        val response = try {
            http.newCall(httpRequest).execute()
        } catch (e: IOException) {
            throw LyricsException("Sem ligação à internet ou ao Gemini.", e)
        }

        var finishReason = ""
        var receivedText = false
        response.use { resp ->
            if (!resp.isSuccessful) throw httpError(resp.code, resp.body?.string().orEmpty())
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

    private fun httpError(code: Int, body: String): LyricsException {
        val message = runCatching {
            JSONObject(body).optJSONObject("error")?.optString("message").orEmpty()
        }.getOrDefault("")
        val modelProblem = code == 404 ||
            (code == 400 && message.contains("model", true) && !message.contains("api key", true))
        return when {
            modelProblem -> LyricsException(
                "O modelo \"$model\" não está disponível para esta chave. " +
                    "Muda o modelo nas definições para um destes: ${availableModels()}",
            )
            code == 400 || code == 401 || code == 403 ->
                LyricsException("Chave do Gemini inválida ou sem permissão. Verifica-a nas definições.")
            code == 429 ->
                LyricsException("Limite gratuito do Gemini atingido por agora. Espera um minuto e tenta de novo.")
            code >= 500 -> LyricsException("O Gemini está com problemas ($code). Tenta novamente daqui a pouco.")
            else -> LyricsException("Erro do Gemini ($code): $message")
        }
    }

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
    }
}
