package pt.assistente.pessoal.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/** Definições e chaves de API, guardadas cifradas apenas neste telemóvel. */
class SettingsStore(context: Context) {

    private val prefs: SharedPreferences = try {
        create(context)
    } catch (e: Exception) {
        // Se a chave do Keystore se perdeu (ex.: restauro), recomeça do zero.
        context.deleteSharedPreferences(FILE)
        create(context)
    }

    private fun create(context: Context): SharedPreferences {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        return EncryptedSharedPreferences.create(
            context,
            FILE,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    private fun str(key: String, default: String) = prefs.getString(key, default) ?: default
    private fun put(key: String, value: String) = prefs.edit().putString(key, value.trim()).apply()

    var claudeKey: String
        get() = str("claude_key", "")
        set(v) = put("claude_key", v)
    var geminiKey: String
        get() = str("gemini_key", "")
        set(v) = put("gemini_key", v)
    var openaiKey: String
        get() = str("openai_key", "")
        set(v) = put("openai_key", v)

    var claudeModel: String
        get() = str("claude_model", DEFAULT_CLAUDE_MODEL).ifBlank { DEFAULT_CLAUDE_MODEL }
        set(v) = put("claude_model", v)
    var geminiModel: String
        get() = str("gemini_model", DEFAULT_GEMINI_MODEL).ifBlank { DEFAULT_GEMINI_MODEL }
        set(v) = put("gemini_model", v)
    var openaiModel: String
        get() = str("openai_model", DEFAULT_OPENAI_MODEL).ifBlank { DEFAULT_OPENAI_MODEL }
        set(v) = put("openai_model", v)

    var mode: Mode
        get() = runCatching { Mode.valueOf(str("mode", Mode.AUTO.name)) }.getOrDefault(Mode.AUTO)
        set(v) = put("mode", v.name)

    /** Confiança local abaixo deste valor → a nuvem responde. */
    var escalateBelow: Int
        get() = prefs.getInt("escalate_below", 5)
        set(v) = prefs.edit().putInt("escalate_below", v).apply()

    /** Confiança local a partir deste valor → aceita a resposta local sem validar. */
    var acceptFrom: Int
        get() = prefs.getInt("accept_from", 8)
        set(v) = prefs.edit().putInt("accept_from", v).apply()

    var webSearch: Boolean
        get() = prefs.getBoolean("web_search", true)
        set(v) = prefs.edit().putBoolean("web_search", v).apply()

    companion object {
        private const val FILE = "definicoes"
        const val DEFAULT_CLAUDE_MODEL = "claude-sonnet-5-5"
        const val DEFAULT_GEMINI_MODEL = "gemini-2.5-flash"
        const val DEFAULT_OPENAI_MODEL = "gpt-5"
    }
}
