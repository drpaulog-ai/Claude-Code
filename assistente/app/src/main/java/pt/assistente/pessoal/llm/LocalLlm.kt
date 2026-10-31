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

    suspend fun load() = withContext(Dispatchers.Default) {
        mutex.withLock {
            engine?.close()
            engine = null
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
