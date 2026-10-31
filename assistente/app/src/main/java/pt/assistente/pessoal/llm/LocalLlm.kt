package pt.assistente.pessoal.llm

import android.content.Context
import android.net.Uri
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/** Modelo de linguagem que corre no próprio telemóvel (MediaPipe LLM Inference). */
class LocalLlm(private val context: Context) {

    private var engine: LlmInference? = null
    private val mutex = Mutex()

    val modelFile: File get() = File(context.filesDir, "models/modelo.task")
    fun hasModelFile() = modelFile.exists() && modelFile.length() > 0
    val isLoaded: Boolean get() = engine != null

    /** Copia o ficheiro .task escolhido pelo utilizador para o armazenamento privado da app. */
    suspend fun importModel(uri: Uri, onProgress: (Long) -> Unit) = withContext(Dispatchers.IO) {
        close()
        val dir = modelFile.parentFile!!
        dir.mkdirs()
        val tmp = File(dir, "modelo.task.tmp")
        val input = context.contentResolver.openInputStream(uri)
            ?: error("Não foi possível abrir o ficheiro")
        input.use { src ->
            FileOutputStream(tmp).use { dst ->
                val buf = ByteArray(1 shl 20)
                var total = 0L
                var lastReport = 0L
                while (true) {
                    val n = src.read(buf)
                    if (n < 0) break
                    dst.write(buf, 0, n)
                    total += n
                    if (total - lastReport > 50L * 1024 * 1024) {
                        lastReport = total
                        onProgress(total)
                    }
                }
            }
        }
        modelFile.delete()
        if (!tmp.renameTo(modelFile)) error("Falha ao guardar o modelo")
    }

    /**
     * Verifica o início do ficheiro: um .task do MediaPipe é um arquivo zip ("PK").
     * Devolve uma explicação em português se o ficheiro não for utilizável, ou null se parecer válido.
     */
    fun diagnoseModelFile(): String? {
        if (!hasModelFile()) return "Nenhum modelo importado."
        val header = ByteArray(16)
        val n = modelFile.inputStream().use { it.read(header) }
        val mb = modelFile.length() / (1024 * 1024)
        val text = String(header, 0, maxOf(n, 0), Charsets.ISO_8859_1)
        return when {
            n >= 2 && header[0] == 'P'.code.toByte() && header[1] == 'K'.code.toByte() -> null
            text.startsWith("LITERTLM") ->
                "O ficheiro importado ($mb MB) é do formato .litertlm, que esta versão da app ainda não suporta. " +
                    "No Hugging Face escolhe o ficheiro que termina em .task."
            text.trimStart().startsWith("<") || text.trimStart().startsWith("{") ->
                "O ficheiro importado ($mb MB) é uma página web/mensagem de erro, não um modelo. " +
                    "Normalmente acontece quando o download exige sessão iniciada no Hugging Face e aceitar a licença do Gemma. " +
                    "Inicia sessão, aceita a licença na página do modelo e descarrega de novo o ficheiro .task."
            else ->
                "O ficheiro importado ($mb MB) não é um modelo .task válido (talvez seja de outro formato, " +
                    "como .gguf ou .tflite, ou o download ficou incompleto). Descarrega de novo um ficheiro .task."
        }
    }

    suspend fun load() = withContext(Dispatchers.Default) {
        mutex.withLock {
            engine?.close()
            engine = null
            diagnoseModelFile()?.let { throw IllegalStateException(it) }
            val options = LlmInference.LlmInferenceOptions.builder()
                .setModelPath(modelFile.absolutePath)
                .setMaxTokens(4096)
                .build()
            engine = LlmInference.createFromOptions(context, options)
        }
    }

    suspend fun generate(prompt: String): String = withContext(Dispatchers.Default) {
        mutex.withLock {
            val e = engine ?: error("Modelo local não carregado")
            e.generateResponse(prompt)
        }
    }

    fun close() {
        engine?.close()
        engine = null
    }
}
