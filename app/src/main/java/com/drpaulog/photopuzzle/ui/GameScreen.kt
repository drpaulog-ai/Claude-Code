package com.drpaulog.photopuzzle.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

@Composable
fun GameScreen(vm: PuzzleViewModel, game: GameUi) {
    val n = game.gridSize
    var showOriginal by remember { mutableStateOf(false) }

    Surface(Modifier.fillMaxSize()) {
        BoxWithConstraints(Modifier.safeDrawingPadding()) {
            // O tabuleiro ocupa 85% da altura do ecrã; o resto fica para a barra de cima.
            val boardHeight = maxHeight * 0.85f
            Column(Modifier.fillMaxSize()) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(maxHeight - boardHeight)
                        .padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = vm::exitGame) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
                    }
                    Column(Modifier.weight(1f)) {
                        val locked = game.cells.indices.count { game.cells[it] == it }
                        Text(
                            (game.levelIndex?.let { "Nível ${it + 1}" } ?: "Foto escolhida") + " · ${n}×$n",
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                        )
                        Text(
                            "Jogadas: ${game.moves}   No sítio: $locked/${n * n}",
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                        )
                    }
                    IconButton(onClick = { showOriginal = !showOriginal }, enabled = game.image != null) {
                        Icon(
                            Icons.Filled.Search,
                            contentDescription = if (showOriginal) "Voltar ao puzzle" else "Ver original",
                            tint = if (showOriginal) MaterialTheme.colorScheme.primary else LocalContentColor.current,
                        )
                    }
                    IconButton(onClick = vm::restart, enabled = game.image != null) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Baralhar")
                    }
                }

                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(boardHeight)
                        .padding(horizontal = 4.dp, vertical = 4.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    val image = game.image
                    when {
                        image != null && showOriginal -> Image(
                            bitmap = image,
                            contentDescription = "Imagem original",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxSize()
                                .clickable { showOriginal = false },
                        )
                        image != null -> PuzzleCanvas(vm, game, image)
                        game.loadError -> Text("Não foi possível abrir esta foto.")
                        else -> CircularProgressIndicator()
                    }
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
                    val col = (offset.x * n / size.width).toInt().coerceIn(0, n - 1)
                    val row = (offset.y * n / size.height).toInt().coerceIn(0, n - 1)
                    when (vm.tap(row * n + col)) {
                        TapResult.Rejected -> haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        TapResult.Solved -> haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        else -> {}
                    }
                }
            }
    ) {
        drawRect(Color.Black)
        val cellW = size.width / n
        val cellH = size.height / n
        val half = gapPx / 2
        // Recorte central da foto com o mesmo formato do tabuleiro (como ContentScale.Crop).
        val boardAspect = size.width / size.height
        val cropW: Int
        val cropH: Int
        if (image.width.toFloat() / image.height > boardAspect) {
            cropH = image.height
            cropW = (image.height * boardAspect).toInt().coerceAtMost(image.width)
        } else {
            cropW = image.width
            cropH = (image.width / boardAspect).toInt().coerceAtMost(image.height)
        }
        val cropX = (image.width - cropW) / 2
        val cropY = (image.height - cropH) / 2

        for (cell in 0 until n * n) {
            val piece = game.cells[cell]
            val pos = positions[piece].value
            // Só há separação nos lados em que a peça não encaixa com a vizinha.
            val left = if (board.joinedLeft(cell)) 0f else half
            val top = if (board.joinedUp(cell)) 0f else half
            val right = if (board.joinedRight(cell)) 0f else half
            val bottom = if (board.joinedDown(cell)) 0f else half
            val x = pos.x * cellW
            val y = pos.y * cellH
            val srcX0 = cropX + (piece % n) * cropW / n
            val srcY0 = cropY + (piece / n) * cropH / n
            val srcX1 = cropX + (piece % n + 1) * cropW / n
            val srcY1 = cropY + (piece / n + 1) * cropH / n
            val dstX = (x + left).roundToInt()
            val dstY = (y + top).roundToInt()
            drawImage(
                image = image,
                srcOffset = IntOffset(srcX0, srcY0),
                srcSize = IntSize(srcX1 - srcX0, srcY1 - srcY0),
                dstOffset = IntOffset(dstX, dstY),
                dstSize = IntSize(
                    (x + cellW - right).roundToInt() - dstX,
                    (y + cellH - bottom).roundToInt() - dstY,
                ),
            )
        }

        if (game.selected.isNotEmpty()) {
            for (cell in game.selected) {
                val pos = positions[game.cells[cell]].value
                drawSelection(cell, pos, Size(cellW, cellH), n, game.selected, highlight)
            }
        }
    }
}

/** Realça uma peça do grupo escolhido e desenha o contorno exterior do grupo. */
private fun DrawScope.drawSelection(
    cell: Int,
    pos: Offset,
    cellSize: Size,
    n: Int,
    selected: Set<Int>,
    color: Color,
) {
    val topLeft = Offset(pos.x * cellSize.width, pos.y * cellSize.height)
    drawRect(Color.White.copy(alpha = 0.3f), topLeft, cellSize)
    val stroke = 3.dp.toPx()
    val x0 = topLeft.x
    val y0 = topLeft.y
    val x1 = x0 + cellSize.width
    val y1 = y0 + cellSize.height
    val col = cell % n
    if (col == 0 || (cell - 1) !in selected) drawLine(color, Offset(x0, y0), Offset(x0, y1), stroke)
    if (col == n - 1 || (cell + 1) !in selected) drawLine(color, Offset(x1, y0), Offset(x1, y1), stroke)
    if (cell < n || (cell - n) !in selected) drawLine(color, Offset(x0, y0), Offset(x1, y0), stroke)
    if (cell >= n * (n - 1) || (cell + n) !in selected) drawLine(color, Offset(x0, y1), Offset(x1, y1), stroke)
}
