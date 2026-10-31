package pt.assistente.pessoal.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Guarda a conversa num ficheiro privado da app (nunca sai do telemóvel). */
class ChatStore(context: Context) {
    private val file = File(context.filesDir, "conversa.json")

    fun load(): List<ChatMessage> = runCatching {
        val arr = JSONArray(file.readText())
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            ChatMessage(
                role = Role.valueOf(o.getString("role")),
                text = o.getString("text"),
                source = o.optString("source"),
                time = o.optLong("time"),
            )
        }
    }.getOrDefault(emptyList())

    fun save(messages: List<ChatMessage>) {
        runCatching {
            val arr = JSONArray()
            messages.forEach {
                arr.put(
                    JSONObject()
                        .put("role", it.role.name)
                        .put("text", it.text)
                        .put("source", it.source)
                        .put("time", it.time)
                )
            }
            file.writeText(arr.toString())
        }
    }
}
