package pt.assistente.pessoal.data

enum class Role { USER, ASSISTANT }

data class ChatMessage(
    val role: Role,
    val text: String,
    /** De onde veio a resposta, ex.: "Local · confiança 8/10" ou "Claude". */
    val source: String = "",
    val time: Long = System.currentTimeMillis(),
)

enum class Mode(val label: String) {
    AUTO("Automático"),
    LOCAL("Só local"),
    CLOUD("Só nuvem"),
}
