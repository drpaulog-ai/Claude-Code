package pt.assistente.pessoal.llm

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import pt.assistente.pessoal.data.ChatMessage
import pt.assistente.pessoal.data.Role
import pt.assistente.pessoal.data.SettingsStore
import java.util.concurrent.TimeUnit

class CloudException(message: String, val code: Int = 0) : Exception(message)

interface CloudProvider {
    val name: String
    fun isConfigured(): Boolean
    suspend fun complete(system: String, history: List<ChatMessage>, webSearch: Boolean): String
}

private val JSON_TYPE = "application/json; charset=utf-8".toMediaType()

private val http: OkHttpClient = OkHttpClient.Builder()
    .connectTimeout(30, TimeUnit.SECONDS)
    .readTimeout(180, TimeUnit.SECONDS)
    .writeTimeout(60, TimeUnit.SECONDS)
    .build()

private fun postJson(url: String, headers: Map<String, String>, body: JSONObject): JSONObject {
    val builder = Request.Builder().url(url).post(body.toString().toRequestBody(JSON_TYPE))
    headers.forEach { (k, v) -> builder.header(k, v) }
    http.newCall(builder.build()).execute().use { resp ->
        val text = resp.body?.string().orEmpty()
        if (!resp.isSuccessful) throw CloudException("HTTP ${resp.code}: ${text.take(300)}", resp.code)
        return JSONObject(text)
    }
}

private fun getJson(url: String, headers: Map<String, String>): JSONObject {
    val builder = Request.Builder().url(url).get()
    headers.forEach { (k, v) -> builder.header(k, v) }
    http.newCall(builder.build()).execute().use { resp ->
        val text = resp.body?.string().orEmpty()
        if (!resp.isSuccessful) throw CloudException("HTTP ${resp.code}: ${text.take(300)}", resp.code)
        return JSONObject(text)
    }
}

/** Junta mensagens seguidas do mesmo papel e garante que a conversa começa pelo utilizador. */
internal fun normalize(history: List<ChatMessage>): List<ChatMessage> {
    val out = mutableListOf<ChatMessage>()
    for (m in history) {
        if (m.text.isBlank()) continue
        if (out.isEmpty() && m.role != Role.USER) continue
        val last = out.lastOrNull()
        if (last != null && last.role == m.role) {
            out[out.size - 1] = last.copy(text = last.text + "\n\n" + m.text)
        } else {
            out.add(m)
        }
    }
    return out
}

/**
 * Tenta primeiro com pesquisa web; se o fornecedor recusar a ferramenta (HTTP 400)
 * ou devolver texto vazio, repete sem ela.
 */
private fun withToolFallback(webSearch: Boolean, call: (Boolean) -> String): String {
    if (!webSearch) return requireText(call(false))
    return try {
        requireText(call(true))
    } catch (e: CloudException) {
        if (e.code == 400 || e.code == EMPTY) requireText(call(false)) else throw e
    }
}

private const val EMPTY = -1

private fun requireText(text: String): String {
    if (text.isBlank()) throw CloudException("Resposta vazia", EMPTY)
    return text
}

class ClaudeProvider(private val s: SettingsStore) : CloudProvider {
    override val name = "Claude"
    override fun isConfigured() = s.claudeKey.isNotBlank()

    override suspend fun complete(system: String, history: List<ChatMessage>, webSearch: Boolean): String =
        withContext(Dispatchers.IO) {
            withToolFallback(webSearch) { withTools ->
                val messages = JSONArray()
                normalize(history).forEach {
                    messages.put(
                        JSONObject()
                            .put("role", if (it.role == Role.USER) "user" else "assistant")
                            .put("content", it.text)
                    )
                }
                val body = JSONObject()
                    .put("model", s.claudeModel)
                    .put("max_tokens", 4096)
                    .put("system", system)
                    .put("messages", messages)
                if (withTools) {
                    body.put(
                        "tools", JSONArray().put(
                            JSONObject()
                                .put("type", "web_search_20250305")
                                .put("name", "web_search")
                                .put("max_uses", 3)
                        )
                    )
                }
                val json = postJson(
                    "https://api.anthropic.com/v1/messages",
                    mapOf("x-api-key" to s.claudeKey, "anthropic-version" to "2023-06-01"),
                    body,
                )
                val content = json.optJSONArray("content") ?: JSONArray()
                buildString {
                    for (i in 0 until content.length()) {
                        val block = content.getJSONObject(i)
                        if (block.optString("type") == "text") append(block.optString("text"))
                    }
                }.trim()
            }
        }
}

class GeminiProvider(private val s: SettingsStore) : CloudProvider {
    override val name = "Gemini"
    override fun isConfigured() = s.geminiKey.isNotBlank()

    override suspend fun complete(system: String, history: List<ChatMessage>, webSearch: Boolean): String =
        withContext(Dispatchers.IO) {
            try {
                request(system, history, webSearch)
            } catch (e: CloudException) {
                if ((e.code == 400 || e.code == 404) && e.message.orEmpty().contains("model", ignoreCase = true)) {
                    throw CloudException(
                        "O modelo \"${s.geminiModel}\" não existe ou não está disponível para esta chave. " +
                            "Copia um destes para Definições → Modelo Gemini: ${availableModels()}",
                        e.code,
                    )
                }
                throw e
            }
        }

    /** Lista os modelos de texto disponíveis para a chave (os "flash" primeiro). */
    private fun availableModels(): String = try {
        val models = getJson(
            "https://generativelanguage.googleapis.com/v1beta/models?pageSize=200",
            mapOf("x-goog-api-key" to s.geminiKey),
        ).optJSONArray("models") ?: JSONArray()
        val names = (0 until models.length()).map { models.getJSONObject(it) }
            .filter { m ->
                val methods = m.optJSONArray("supportedGenerationMethods") ?: JSONArray()
                (0 until methods.length()).any { methods.getString(it) == "generateContent" }
            }
            .map { it.optString("name").removePrefix("models/") }
            .filter { it.startsWith("gemini") && !it.contains("embedding") && !it.contains("tts") && !it.contains("image") }
            .sortedWith(compareByDescending<String> { it.contains("flash") }.thenByDescending { it })
        if (names.isEmpty()) "(nenhum encontrado)" else names.take(8).joinToString(", ")
    } catch (e: Exception) {
        "(não foi possível obter a lista: ${e.message})"
    }

    private fun request(system: String, history: List<ChatMessage>, webSearch: Boolean): String =
            withToolFallback(webSearch) { withTools ->
                val contents = JSONArray()
                normalize(history).forEach {
                    contents.put(
                        JSONObject()
                            .put("role", if (it.role == Role.USER) "user" else "model")
                            .put("parts", JSONArray().put(JSONObject().put("text", it.text)))
                    )
                }
                val body = JSONObject()
                    .put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", system))))
                    .put("contents", contents)
                if (withTools) {
                    body.put("tools", JSONArray().put(JSONObject().put("google_search", JSONObject())))
                }
                val json = postJson(
                    "https://generativelanguage.googleapis.com/v1beta/models/${s.geminiModel}:generateContent",
                    mapOf("x-goog-api-key" to s.geminiKey),
                    body,
                )
                val parts = json.optJSONArray("candidates")?.optJSONObject(0)
                    ?.optJSONObject("content")?.optJSONArray("parts") ?: JSONArray()
                buildString {
                    for (i in 0 until parts.length()) append(parts.getJSONObject(i).optString("text"))
                }.trim()
            }
}

class OpenAiProvider(private val s: SettingsStore) : CloudProvider {
    override val name = "ChatGPT"
    override fun isConfigured() = s.openaiKey.isNotBlank()

    override suspend fun complete(system: String, history: List<ChatMessage>, webSearch: Boolean): String =
        withContext(Dispatchers.IO) {
            withToolFallback(webSearch) { withTools ->
                val input = JSONArray()
                normalize(history).forEach {
                    input.put(
                        JSONObject()
                            .put("role", if (it.role == Role.USER) "user" else "assistant")
                            .put("content", it.text)
                    )
                }
                val body = JSONObject()
                    .put("model", s.openaiModel)
                    .put("instructions", system)
                    .put("input", input)
                if (withTools) {
                    body.put("tools", JSONArray().put(JSONObject().put("type", "web_search")))
                }
                val json = postJson(
                    "https://api.openai.com/v1/responses",
                    mapOf("Authorization" to "Bearer ${s.openaiKey}"),
                    body,
                )
                val output = json.optJSONArray("output") ?: JSONArray()
                buildString {
                    for (i in 0 until output.length()) {
                        val item = output.getJSONObject(i)
                        if (item.optString("type") != "message") continue
                        val content = item.optJSONArray("content") ?: continue
                        for (j in 0 until content.length()) {
                            val c = content.getJSONObject(j)
                            if (c.optString("type") == "output_text") append(c.optString("text"))
                        }
                    }
                }.trim()
            }
        }
}
