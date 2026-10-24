package com.drpaulog.photopuzzle.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.drpaulog.photopuzzle.GameUi
import com.drpaulog.photopuzzle.PuzzleBoard
import com.drpaulog.photopuzzle.PuzzleViewModel
import com.drpaulog.photopuzzle.TapResult
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameScreen(vm: PuzzleViewModel, game: GameUi) {
    val n = game.gridSize
    var showOriginal by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        (game.levelIndex?.let { "Nível ${it + 1}" } ?: "Foto escolhida") + " · ${n}×$n"
                    )
                },
                navigationIcon = {
                    IconButton(onClick = vm::exitGame) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val locked = game.cells.indices.count { game.cells[it] == it }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Jogadas: ${game.moves}", style = MaterialTheme.typography.titleMedium)
                Text("No sítio: $locked/${n * n}", style = MaterialTheme.typography.titleMedium)
            }

            Box(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f),
                contentAlignment = Alignment.Center,
            ) {
                val image = game.image
                when {
                    image != null && showOriginal -> Image(
                        bitmap = image,
                        contentDescription = "Imagem original",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize(),
                    )
                    image != null -> PuzzleCanvas(vm, game, image)
                    game.loadError -> Text("Não foi possível abrir esta foto.")
                    else -> CircularProgressIndicator()
                }
            }

            Text(
                "Toca numa peça e depois no sítio para onde a queres trocar. " +
                    "Peças que encaixam ficam unidas e movem-se juntas; " +
                    "as que estão no sítio certo ficam bloqueadas.",
                style = MaterialTheme.typography.bodySmall,
            )

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FilledTonalButton(
                    onClick = { showOriginal = !showOriginal },
                    enabled = game.image != null,
                ) {
                    Icon(Icons.Filled.Search, contentDescription = null)
                    Text(if (showOriginal) "  Voltar ao puzzle" else "  Ver original")
                }
                OutlinedButton(onClick = vm::restart, enabled = game.image != null) {
                    Icon(Icons.Filled.Refresh, contentDescription = null)
                    Text("  Baralhar")
                }
            }
        }
    }

    if (game.solved) {
        val hasNext = game.levelIndex != null && game.levelIndex + 1 < vm.photos.size
        val best = game.photoId?.let { vm.bestMoves(it) }
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Parabéns! 🎉") },
            text = {
                Text(
                    "Completaste o puzzle em ${game.moves} jogadas." +
                        (best?.let { "\nMelhor resultado: $it jogadas." } ?: "")
                )
            },
            confirmButton = {
                TextButton(onClick = vm::nextLevel) {
                    Text(if (hasNext) "Próximo nível" else "Voltar aos níveis")
                }
            },
            dismissButton = {
                TextButton(onClick = vm::restart) { Text("Jogar de novo") }
            },
        )
    }
}

@Composable
private fun PuzzleCanvas(vm: PuzzleViewModel, game: GameUi, image: ImageBitmap) {
    val n = game.gridSize
    val haptics = LocalHapticFeedback.current
    val board = remember(game.cells) { PuzzleBoard(n, game.cells) }
    val highlight = MaterialTheme.colorScheme.primary
    val gapPx = with(LocalDensity.current) { 1.5.dp.toPx() }

    // Posição animada (em unidades de célula: coluna, linha) de cada peça.
    val positions = remember(n) {
        val cellOf = IntArray(n * n).also { inv -> game.cells.forEachIndexed { cell, piece -> inv[piece] = cell } }
        Array(n * n) { piece ->
            val cell = cellOf[piece]
            Animatable(Offset((cell % n).toFloat(), (cell / n).toFloat()), Offset.VectorConverter)
        }
    }
    LaunchedEffect(game.cells) {
        game.cells.forEachIndexed { cell, piece ->
            val target = Offset((cell % n).toFloat(), (cell / n).toFloat())
            if (positions[piece].targetValue != target) {
                launch { positions[piece].animateTo(target, tween(durationMillis = 220)) }
            }
        }
    }

    Canvas(
        Modifier
            .fillMaxSize()
            .pointerInput(n) {
                detectTapGestures { offset ->
                    val cellSize = size.width.toFloat() / n
                    val col = (offset.x / cellSize).toInt().coerceIn(0, n - 1)
                    val row = (offset.y / cellSize).toInt().coerceIn(0, n - 1)
                    when (vm.tap(row * n + col)) {
                        TapResult.Rejected -> haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        TapResult.Solved -> haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        else -> {}
                    }
                }
            }
    ) {
        drawRect(Color.Black)
        val cellSize = size.width / n
        val tile = image.width / n
        val half = gapPx / 2

        for (cell in 0 until n * n) {
            val piece = game.cells[cell]
            val pos = positions[piece].value
            // Só há separação nos lados em que a peça não encaixa com a vizinha.
            val left = if (board.joinedLeft(cell)) 0f else half
            val top = if (board.joinedUp(cell)) 0f else half
            val right = if (board.joinedRight(cell)) 0f else half
            val bottom = if (board.joinedDown(cell)) 0f else half
            val x = pos.x * cellSize
            val y = pos.y * cellSize
            val dstX = (x + left).roundToInt()
            val dstY = (y + top).roundToInt()
            drawImage(
                image = image,
                srcOffset = IntOffset((piece % n) * tile, (piece / n) * tile),
                srcSize = IntSize(tile, tile),
                dstOffset = IntOffset(dstX, dstY),
                dstSize = IntSize(
                    (x + cellSize - right).roundToInt() - dstX,
                    (y + cellSize - bottom).roundToInt() - dstY,
                ),
            )
        }

        if (game.selected.isNotEmpty()) {
            for (cell in game.selected) {
                val pos = positions[game.cells[cell]].value
                drawSelection(cell, pos, cellSize, n, game.selected, highlight)
            }
        }
    }
}

/** Realça uma peça do grupo escolhido e desenha o contorno exterior do grupo. */
private fun DrawScope.drawSelection(
    cell: Int,
    pos: Offset,
    cellSize: Float,
    n: Int,
    selected: Set<Int>,
    color: Color,
) {
    val topLeft = Offset(pos.x * cellSize, pos.y * cellSize)
    drawRect(Color.White.copy(alpha = 0.3f), topLeft, Size(cellSize, cellSize))
    val stroke = 3.dp.toPx()
    val x0 = topLeft.x
    val y0 = topLeft.y
    val x1 = x0 + cellSize
    val y1 = y0 + cellSize
    val col = cell % n
    if (col == 0 || (cell - 1) !in selected) drawLine(color, Offset(x0, y0), Offset(x0, y1), stroke)
    if (col == n - 1 || (cell + 1) !in selected) drawLine(color, Offset(x1, y0), Offset(x1, y1), stroke)
    if (cell < n || (cell - n) !in selected) drawLine(color, Offset(x0, y0), Offset(x1, y0), stroke)
    if (cell >= n * (n - 1) || (cell + n) !in selected) drawLine(color, Offset(x0, y1), Offset(x1, y1), stroke)
}
