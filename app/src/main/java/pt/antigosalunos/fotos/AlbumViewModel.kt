package pt.antigosalunos.fotos

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AlbumUiState(
    val loading: Boolean = true,
    val album: Album? = null,
    val error: String? = null,
)

class AlbumViewModel : ViewModel() {
    private val _state = MutableStateFlow(AlbumUiState())
    val state: StateFlow<AlbumUiState> = _state

    init {
        refresh()
    }

    fun refresh() {
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            try {
                val album = AlbumRepository.loadAlbum(BuildConfig.ALBUM_URL)
                _state.value = AlbumUiState(loading = false, album = album)
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, error = e.message ?: e.toString()) }
            }
        }
    }
}
