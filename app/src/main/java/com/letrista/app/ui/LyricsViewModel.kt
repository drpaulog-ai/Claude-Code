package com.letrista.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.letrista.app.core.LyricsException
import com.letrista.app.core.LyricsGenerator
import com.letrista.app.core.MusicCatalog
import com.letrista.app.core.SongRequest
import com.letrista.app.data.SettingsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class Step(val title: String) {
    STYLE("Estilo musical"),
    THEMES("Tema(s)"),
    ARTIST("Intérprete de referência"),
    OPTIONS("Últimos detalhes"),
    RESULT("A tua letra"),
}

data class UiState(
    val step: Step = Step.STYLE,
    val styleQuery: String = "",
    val style: String? = null,
    val themes: Set<String> = emptySet(),
    val customThemes: List<String> = emptyList(),
    val wantsArtist: Boolean? = null,
    val artist: String = "",
    val language: String = MusicCatalog.languages.first(),
    val mood: String = MusicCatalog.moods.first(),
    val includeChorus: Boolean = true,
    val notes: String = "",
    val lyrics: String = "",
    val isGenerating: Boolean = false,
    val error: String? = null,
    val apiKey: String = "",
    val apiKeyLoaded: Boolean = false,
    val showSettings: Boolean = false,
) {
    val canContinue: Boolean
        get() = when (step) {
            Step.STYLE -> style != null
            Step.THEMES -> themes.isNotEmpty()
            Step.ARTIST -> wantsArtist == false || (wantsArtist == true && artist.isNotBlank())
            Step.OPTIONS -> true
            Step.RESULT -> false
        }

    fun toRequest() = SongRequest(
        style = style.orEmpty(),
        themes = themes.toList(),
        referenceArtist = if (wantsArtist == true) artist.trim() else null,
        language = language,
        mood = mood,
        extraNotes = notes,
        includeChorus = includeChorus,
    )
}

class LyricsViewModel(app: Application) : AndroidViewModel(app) {

    private val settings = SettingsRepository(app)
    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private var generator: LyricsGenerator? = null
    private var generatorKey: String? = null
    private var job: Job? = null

    init {
        viewModelScope.launch {
            settings.apiKey.collect { key ->
                _state.update {
                    it.copy(
                        apiKey = key,
                        // Na primeira utilização, pede logo a chave.
                        showSettings = if (!it.apiKeyLoaded) key.isBlank() else it.showSettings,
                        apiKeyLoaded = true,
                    )
                }
            }
        }
    }

    // ---- Navegação ----

    fun next() {
        val s = _state.value
        if (!s.canContinue) return
        when (s.step) {
            Step.STYLE -> _state.update { it.copy(step = Step.THEMES) }
            Step.THEMES -> _state.update { it.copy(step = Step.ARTIST) }
            Step.ARTIST -> _state.update { it.copy(step = Step.OPTIONS) }
            Step.OPTIONS -> generate()
            Step.RESULT -> Unit
        }
    }

    /** Devolve false quando já está no primeiro passo (o sistema deve fechar a app). */
    fun back(): Boolean {
        val s = _state.value
        val previous = when (s.step) {
            Step.STYLE -> return false
            Step.THEMES -> Step.STYLE
            Step.ARTIST -> Step.THEMES
            Step.OPTIONS -> Step.ARTIST
            Step.RESULT -> {
                job?.cancel()
                Step.OPTIONS
            }
        }
        _state.update { it.copy(step = previous, isGenerating = false) }
        return true
    }

    fun startOver() {
        job?.cancel()
        _state.update { UiState(apiKey = it.apiKey, apiKeyLoaded = true) }
    }

    // ---- Passo 1: estilo ----

    fun setStyleQuery(q: String) = _state.update { it.copy(styleQuery = q) }
    fun selectStyle(style: String) = _state.update { it.copy(style = style.trim()) }

    // ---- Passo 2: temas ----

    fun toggleTheme(theme: String) = _state.update {
        it.copy(themes = if (theme in it.themes) it.themes - theme else it.themes + theme)
    }

    fun addCustomTheme(theme: String) {
        val t = theme.trim()
        if (t.isEmpty()) return
        _state.update {
            val known = t in MusicCatalog.themes || t in it.customThemes
            it.copy(
                customThemes = if (known) it.customThemes else it.customThemes + t,
                themes = it.themes + t,
            )
        }
    }

    // ---- Passo 3: intérprete ----

    fun setWantsArtist(value: Boolean) = _state.update { it.copy(wantsArtist = value) }
    fun setArtist(value: String) = _state.update { it.copy(artist = value) }

    // ---- Passo 4: opções ----

    fun setLanguage(value: String) = _state.update { it.copy(language = value) }
    fun setMood(value: String) = _state.update { it.copy(mood = value) }
    fun setIncludeChorus(value: Boolean) = _state.update { it.copy(includeChorus = value) }
    fun setNotes(value: String) = _state.update { it.copy(notes = value) }

    // ---- Definições ----

    fun openSettings() = _state.update { it.copy(showSettings = true) }
    fun closeSettings() = _state.update { it.copy(showSettings = false) }

    fun saveApiKey(key: String) {
        viewModelScope.launch { settings.setApiKey(key) }
        _state.update { it.copy(showSettings = false, apiKey = key.trim(), error = null) }
    }

    // ---- Geração ----

    fun generate() {
        val s = _state.value
        if (s.apiKey.isBlank()) {
            _state.update { it.copy(showSettings = true) }
            return
        }
        job?.cancel()
        _state.update { it.copy(step = Step.RESULT, lyrics = "", error = null, isGenerating = true) }

        val gen = generator.takeIf { generatorKey == s.apiKey }
            ?: LyricsGenerator(s.apiKey).also { generator = it; generatorKey = s.apiKey }

        job = viewModelScope.launch {
            try {
                gen.generate(s.toRequest()).collect { chunk ->
                    _state.update { it.copy(lyrics = it.lyrics + chunk) }
                }
                _state.update { it.copy(isGenerating = false) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: LyricsException) {
                // Em caso de recusa ou erro, descarta o texto parcial.
                _state.update { it.copy(isGenerating = false, lyrics = "", error = e.message) }
            } catch (e: Exception) {
                _state.update {
                    it.copy(isGenerating = false, lyrics = "", error = "Erro inesperado: ${e.message}")
                }
            }
        }
    }
}
