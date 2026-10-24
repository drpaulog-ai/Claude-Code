package com.drpaulog.photopuzzle

import android.app.Application
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import coil.imageLoader
import coil.request.ImageRequest
import coil.size.Scale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

val GRID_SIZES = listOf(4, 5, 6)

data class GameUi(
    val photoId: Long?,
    val levelIndex: Int?,
    val gridSize: Int,
    val image: ImageBitmap?,
    val cells: IntArray,
    val selected: Set<Int> = emptySet(),
    /** Célula tocada primeiro: é esta peça que vai para a célula do segundo toque. */
    val anchor: Int = -1,
    val moves: Int = 0,
    val solved: Boolean = false,
    val loadError: Boolean = false,
)

class PuzzleViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = PhotoRepository(app)

    /** Todas as fotos do dispositivo a que a app tem acesso. */
    var allPhotos by mutableStateOf<List<Photo>>(emptyList())
        private set
    var photosLoaded by mutableStateOf(false)
        private set
    /** Fotos escolhidas pelo utilizador para níveis; null enquanto ainda não escolheu. */
    var levelIds by mutableStateOf(repo.levelPhotoIds())
        private set
    /** Fotos que são níveis, pela ordem do dispositivo (mais antiga primeiro). */
    val photos by derivedStateOf {
        val ids = levelIds ?: emptySet()
        allPhotos.filter { it.id in ids }
    }
    var editingPhotos by mutableStateOf(false)
        private set
    /** Fotos marcadas no ecrã de escolha; fica no ViewModel para não se perder ao rodar o ecrã. */
    val draftSelection = mutableStateListOf<Long>()
    var hasAccess by mutableStateOf(repo.hasPhotoAccess())
        private set
    var gridSize by mutableStateOf(repo.gridSize)
        private set
    var completed by mutableStateOf(repo.completedLevels(gridSize))
        private set
    var game by mutableStateOf<GameUi?>(null)
        private set

    private var board: PuzzleBoard? = null
    private var gameToken = 0

    fun refreshPhotos() {
        hasAccess = repo.hasPhotoAccess()
        viewModelScope.launch {
            allPhotos = withContext(Dispatchers.IO) { repo.loadPhotos() }
            photosLoaded = true
        }
    }

    /** Depois de dar acesso às fotos, o utilizador escolhe quais quer usar como níveis. */
    val choosingPhotos: Boolean
        get() = hasAccess && (editingPhotos || levelIds == null)

    fun editLevelPhotos() {
        draftSelection.clear()
        draftSelection.addAll(levelIds.orEmpty())
        editingPhotos = true
    }

    fun cancelEditLevelPhotos() {
        editingPhotos = false
    }

    fun saveLevelPhotos() {
        val ids = draftSelection.toSet()
        repo.saveLevelPhotoIds(ids)
        levelIds = ids
        editingPhotos = false
    }

    fun selectGridSize(size: Int) {
        gridSize = size
        repo.gridSize = size
        completed = repo.completedLevels(size)
    }

    fun bestMoves(photoId: Long): Int? = repo.bestMoves(gridSize, photoId)

    fun startLevel(index: Int) {
        val photo = photos.getOrNull(index) ?: return
        start(photo.uri, photo.id, index)
    }

    fun startCustom(uri: Uri) = start(uri, null, null)

    fun nextLevel() {
        val current = game?.levelIndex
        if (current != null && current + 1 < photos.size) startLevel(current + 1) else exitGame()
    }

    fun restart() {
        val g = game ?: return
        if (g.image == null) return
        val fresh = PuzzleBoard.shuffled(g.gridSize)
        board = fresh
        game = g.copy(cells = fresh.cells(), selected = emptySet(), anchor = -1, moves = 0, solved = false)
    }

    fun exitGame() {
        gameToken++
        game = null
        board = null
    }

    private fun start(uri: Uri, photoId: Long?, levelIndex: Int?) {
        val size = gridSize
        val fresh = PuzzleBoard.shuffled(size)
        board = fresh
        game = GameUi(photoId, levelIndex, size, image = null, cells = fresh.cells())
        val token = ++gameToken
        viewModelScope.launch {
            val image = loadImage(uri)
            val g = game ?: return@launch
            if (token != gameToken) return@launch
            game = if (image != null) g.copy(image = image) else g.copy(loadError = true)
        }
    }

    /** Toque numa célula: o primeiro escolhe a peça/grupo, o segundo indica para onde vai. */
    fun tap(cell: Int): TapResult {
        val g = game ?: return TapResult.Ignored
        val b = board ?: return TapResult.Ignored
        if (g.solved || g.image == null) return TapResult.Ignored

        if (g.selected.isEmpty()) {
            if (b.isLocked(cell)) return TapResult.Rejected
            game = g.copy(selected = b.groupOf(cell), anchor = cell)
            return TapResult.Selected
        }
        if (cell in g.selected) {
            game = g.copy(selected = emptySet(), anchor = -1)
            return TapResult.Ignored
        }
        if (!b.move(g.anchor, cell)) {
            game = g.copy(selected = emptySet(), anchor = -1)
            return TapResult.Rejected
        }
        val solved = b.isSolved()
        game = g.copy(cells = b.cells(), selected = emptySet(), anchor = -1, moves = g.moves + 1, solved = solved)
        if (solved && g.photoId != null) {
            repo.markCompleted(g.gridSize, g.photoId, g.moves + 1)
            completed = repo.completedLevels(gridSize)
        }
        return if (solved) TapResult.Solved else TapResult.Moved
    }

    private suspend fun loadImage(uri: Uri): ImageBitmap? {
        val app = getApplication<Application>()
        // O recorte para o formato do tabuleiro é feito ao desenhar, conforme o espaço no ecrã.
        val request = ImageRequest.Builder(app)
            .data(uri)
            .size(1600)
            .scale(Scale.FILL)
            .allowHardware(false)
            .build()
        val drawable = app.imageLoader.execute(request).drawable as? BitmapDrawable ?: return null
        return drawable.bitmap.asImageBitmap()
    }
}

enum class TapResult { Ignored, Selected, Moved, Rejected, Solved }
