package pt.assistente.pessoal.llm

import android.content.Context
import android.net.Uri
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.Message
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import pt.assistente.pessoal.data.ChatMessage
import pt.assistente.pessoal.data.Role
import java.io.File
import java.io.FileOutputStream

/** Modelo de linguagem que corre no próprio telemóvel (Google LiteRT-LM). */
class LocalLlm(private val context: Context) {

    private var engine: Engine? = null
    private val mutex = Mutex()

    /** "GPU" ou "CPU", conforme o que conseguiu arrancar. */
    var backendName: String = ""
        private set

    private val dir: File get() = File(context.filesDir, "models")
    private val candidates: List<File> get() = listOf(File(dir, "modelo.litertlm"), File(dir, "modelo.task"))

    val modelFile: File get() = candidates.firstOrNull { it.exists() && it.length() > 0 } ?: candidates[0]
    fun hasModelFile() = modelFile.exists() && modelFile.length() > 0
    val isLoaded: Boolean get() = engine != null

    /** Copia o ficheiro escolhido pelo utilizador para o armazenamento privado da app. */
    suspend fun importModel(uri: Uri, onProgress: (Long) -> Unit) = withContext(Dispatchers.IO) {
        close()
        dir.mkdirs()
        val tmp = File(dir, "importar.tmp")
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
        // O motor reconhece o formato pela extensão: dá o nome certo conforme o conteúdo.
        val target = if (readHeader(tmp).startsWith("PK")) File(dir, "modelo.task") else File(dir, "modelo.litertlm")
        candidates.forEach { it.delete() }
        if (!tmp.renameTo(target)) error("Falha ao guardar o modelo")
    }

    private fun readHeader(f: File): String {
        val header = ByteArray(16)
        val n = f.inputStream().use { it.read(header) }
        return String(header, 0, maxOf(n, 0), Charsets.ISO_8859_1)
    }

    /** Explicação em português se o ficheiro não for um modelo utilizável; null se parecer válido. */
    fun diagnoseModelFile(): String? {
        if (!hasModelFile()) return "Nenhum modelo importado."
        val text = readHeader(modelFile)
        val mb = modelFile.length() / (1024 * 1024)
        return when {
            text.startsWith("LITERTLM") || text.startsWith("PK") -> null
            text.trimStart().startsWith("<") || text.trimStart().startsWith("{") ->
                "O ficheiro importado ($mb MB) é uma página web/mensagem de erro, não um modelo. " +
                    "Normalmente acontece quando o download exige sessão iniciada no Hugging Face e aceitar a licença do Gemma. " +
                    "Inicia sessão, aceita a licença na página do modelo e descarrega de novo."
            else ->
                "O ficheiro importado ($mb MB) não é um modelo .litertlm ou .task válido (talvez seja .gguf/.tflite, " +
                    "ou o download ficou incompleto). Descarrega de novo."
        }
    }

    suspend fun load() = withContext(Dispatchers.Default) {
        mutex.withLock {
            engine?.close()
            engine = null
            diagnoseModelFile()?.let { throw IllegalStateException(it) }
            val base = EngineConfig(
                modelPath = modelFile.absolutePath,
                backend = Backend.GPU(),
                maxNumTokens = 4096,
                cacheDir = context.cacheDir.path,
            )
            // Tenta a GPU (mais rápida); se falhar, usa o CPU.
            engine = try {
                start(base).also { backendName = "GPU" }
            } catch (t: Throwable) {
                start(base.copy(backend = Backend.CPU())).also { backendName = "CPU" }
            }
        }
    }

    private fun start(config: EngineConfig): Engine {
        val e = Engine(config)
        try {
            e.initialize()
        } catch (t: Throwable) {
            runCatching { e.close() }
            throw t
        }
        return e
    }

    /** Gera a resposta à última mensagem do utilizador, com as anteriores como contexto. */
    suspend fun generate(system: String, turns: List<ChatMessage>): String = withContext(Dispatchers.Default) {
        mutex.withLock {
            val e = engine ?: error("Modelo local não carregado")
            require(turns.isNotEmpty() && turns.last().role == Role.USER)
            val prior = turns.dropLast(1).map {
                if (it.role == Role.USER) Message.user(it.text) else Message.model(it.text)
            }
            val config = ConversationConfig(
                systemInstruction = Contents.of(system),
                initialMessages = prior,
            )
            e.createConversation(config).use { conversation ->
                conversation.sendMessage(turns.last().text).toString()
            }
        }
    }

    fun close() {
        runCatching { engine?.close() }
        engine = null
    }
}
