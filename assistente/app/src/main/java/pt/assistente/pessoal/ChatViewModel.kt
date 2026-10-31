package pt.assistente.pessoal

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import pt.assistente.pessoal.data.ChatMessage
import pt.assistente.pessoal.data.ChatStore
import pt.assistente.pessoal.data.Mode
import pt.assistente.pessoal.data.Role
import pt.assistente.pessoal.data.SettingsStore
import pt.assistente.pessoal.llm.LocalLlm
import pt.assistente.pessoal.llm.Router
import pt.assistente.pessoal.llm.RouterResult

class ChatViewModel(app: Application) : AndroidViewModel(app) {

    val settings = SettingsStore(app)
    private val local = LocalLlm(app)
    private val router = Router(local, settings)
    private val store = ChatStore(app)

    val messages = mutableStateListOf<ChatMessage>().apply { addAll(store.load()) }

    var status by mutableStateOf<String?>(null)
        private set
    var busy by mutableStateOf(false)
        private set
    var mode by mutableStateOf(settings.mode)
        private set
    var localStatus by mutableStateOf("")
        private set
    var localBusy by mutableStateOf(false)
        private set

    init {
        if (local.hasModelFile()) loadLocal() else localStatus = "Nenhum modelo local importado."
    }

    fun changeMode(m: Mode) {
        mode = m
        settings.mode = m
    }

    fun send(text: String) {
        val t = text.trim()
        if (t.isEmpty() || busy) return
        messages.add(ChatMessage(Role.USER, t))
        persist()
        busy = true
        viewModelScope.launch {
            val result = try {
                router.answer(messages.toList()) { s -> status = s }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                RouterResult("Erro: ${e.message}", "Sistema")
            }
            messages.add(ChatMessage(Role.ASSISTANT, result.text, result.source))
            persist()
            status = null
            busy = false
        }
    }

    fun clear() {
        if (busy) return
        messages.clear()
        persist()
    }

    fun importModel(uri: Uri) {
        if (localBusy) return
        localBusy = true
        viewModelScope.launch {
            try {
                localStatus = "A importar modelo…"
                local.importModel(uri) { bytes -> localStatus = "A importar modelo… ${bytes / (1024 * 1024)} MB" }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                localStatus = "Erro ao importar: ${e.message}"
                localBusy = false
                return@launch
            }
            localBusy = false
            loadLocal()
        }
    }

    fun loadLocal() {
        if (localBusy) return
        if (!local.hasModelFile()) {
            localStatus = "Nenhum modelo local importado."
            return
        }
        localBusy = true
        viewModelScope.launch {
            localStatus = "A carregar modelo local…"
            localStatus = try {
                local.load()
                val mb = local.modelFile.length() / (1024 * 1024)
                "Modelo local pronto ($mb MB)."
            } catch (e: CancellationException) {
                throw e
            } catch (e: IllegalStateException) {
                e.message ?: "Erro ao carregar o modelo."
            } catch (e: Throwable) {
                "Erro ao carregar o modelo: ${e.message?.substringBefore("===")?.trim()}"
            }
            localBusy = false
        }
    }

    private fun persist() = store.save(messages.toList())

    override fun onCleared() {
        local.close()
    }
}
