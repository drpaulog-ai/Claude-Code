package com.letrista.app.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings")

/** Definições da IA, guardadas localmente no dispositivo (armazenamento privado da app). */
data class AiSettings(val apiKey: String = "", val model: String = "")

class SettingsRepository(private val context: Context) {

    private val apiKeyPref = stringPreferencesKey("gemini_api_key")
    private val modelPref = stringPreferencesKey("gemini_model")

    val settings: Flow<AiSettings> = context.dataStore.data.map {
        AiSettings(apiKey = it[apiKeyPref].orEmpty(), model = it[modelPref].orEmpty())
    }

    suspend fun save(value: AiSettings) {
        context.dataStore.edit {
            it[apiKeyPref] = value.apiKey.trim()
            it[modelPref] = value.model.trim()
        }
    }
}
