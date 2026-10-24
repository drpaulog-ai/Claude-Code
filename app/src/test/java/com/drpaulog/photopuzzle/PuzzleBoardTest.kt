package com.drpaulog.photopuzzle

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class PuzzleBoardTest {

    private fun solved(size: Int) = IntArray(size * size) { it }

    private fun swapped(size: Int, a: Int, b: Int) = solved(size).also {
        val t = it[a]; it[a] = it[b]; it[b] = t
    }

    @Test
    fun singlePieceSwap() {
        val board = PuzzleBoard(3, intArrayOf(1, 0, 2, 3, 4, 5, 6, 7, 8))
        // 0 e 1 estão trocadas: cada uma é um grupo isolado (2..8 estão bloqueadas).
        assertTrue(board.move(0, 1))
        assertTrue(board.isSolved())
    }

    @Test
    fun lockedPiecesCannotMoveOrBeDisplaced() {
        val board = PuzzleBoard(3, swapped(3, 0, 8))
        assertFalse(board.move(4, 0)) // peça 4 bloqueada
        assertFalse(board.move(0, 4)) // destino bloqueado
        assertTrue(board.move(0, 8))
        assertTrue(board.isSolved())
    }

    @Test
    fun joinedPiecesFormGroups() {
        // Peças 0 e 1 (vizinhas na imagem) estão juntas nas células 4 e 5.
        val cells = intArrayOf(4, 5, 2, 3, 0, 1, 6, 7, 8)
        val board = PuzzleBoard(3, cells)
        assertEquals(setOf(4, 5), board.groupOf(4))
        assertEquals(setOf(0, 1), board.groupOf(0))
    }

    @Test
    fun groupMovesTogetherAndDisplacedPiecesKeepLayout() {
        val cells = intArrayOf(4, 5, 2, 3, 0, 1, 6, 7, 8)
        val board = PuzzleBoard(3, cells)
        // Arrasta o grupo {0,1} (células 4,5) para que a peça 0 vá para a célula 0.
        assertTrue(board.move(4, 0))
        assertTrue(board.isSolved())
    }

    @Test
    fun overlappingGroupMoveShiftsDisplacedPieces() {
        // Linha de 4: grupo [A B] nas células 1,2; mover uma casa para a esquerda.
        val cells = intArrayOf(9, 0, 1, 3, 4, 5, 6, 7, 8, 2, 10, 11, 12, 13, 14, 15)
        val board = PuzzleBoard(4, cells)
        assertEquals(setOf(1, 2), board.groupOf(1))
        assertTrue(board.move(1, 0))
        // A peça 9 que estava na célula 0 passa para a célula 2, libertada pelo grupo.
        assertArrayEquals(intArrayOf(0, 1, 9, 3, 4, 5, 6, 7, 8, 2, 10, 11, 12, 13, 14, 15), board.cells())
    }

    @Test
    fun moveOutOfBoundsIsRejected() {
        val cells = intArrayOf(4, 5, 2, 3, 0, 1, 6, 7, 8)
        val board = PuzzleBoard(3, cells)
        // Deslocar o grupo {0,1} uma casa para a direita empurraria a peça 1 para fora.
        assertFalse(board.move(4, 5))
        assertArrayEquals(cells, board.cells())
    }

    @Test
    fun rowWrapIsNotAJoin() {
        // Peça 2 (fim da linha 0) seguida da peça 3 (início da linha 1) não estão unidas.
        val board = PuzzleBoard(3, intArrayOf(2, 3, 0, 1, 4, 5, 6, 7, 8))
        assertFalse(board.joinedRight(0))
    }

    @Test
    fun shuffledBoardsArePermutationsWithoutLockedPieces() {
        val random = Random(42)
        for (size in listOf(6, 8, 10)) {
            repeat(20) {
                val board = PuzzleBoard.shuffled(size, random)
                assertEquals(size * size, board.cellCount)
                assertEquals(0, board.lockedCount())
            }
        }
    }

    @Test
    fun randomMovesAlwaysKeepAPermutation() {
        val random = Random(7)
        for (size in listOf(6, 8, 10)) {
            val board = PuzzleBoard.shuffled(size, random)
            repeat(2000) {
                val from = random.nextInt(board.cellCount)
                val to = random.nextInt(board.cellCount)
                val lockedBefore = (0 until board.cellCount).filter { board.isLocked(it) }
                board.move(from, to)
                assertEquals((0 until board.cellCount).toList(), board.cells().sorted())
                // Uma peça bloqueada nunca sai do sítio.
                lockedBefore.forEach { assertTrue(board.isLocked(it)) }
            }
        }
    }

    @Test
    fun greedySolverFinishesEveryGrid() {
        // Coloca cada peça na sua casa com toques sucessivos: o puzzle tem sempre solução.
        val random = Random(3)
        for (size in listOf(6, 8, 10)) {
            val board = PuzzleBoard.shuffled(size, random)
            var guard = 0
            while (!board.isSolved() && guard++ < 10_000) {
                val home = (0 until board.cellCount).first { !board.isLocked(it) }
                val from = board.cells().indexOf(home)
                if (!board.move(from, home)) {
                    // Grupo não cabe: troca apenas a peça que ocupa a casa com outra fora do grupo.
                    val blocker = (0 until board.cellCount).first {
                        !board.isLocked(it) && it !in board.groupOf(from) && board.move(it, home)
                    }
                    check(blocker >= 0)
                }
            }
            assertTrue("size $size not solved", board.isSolved())
        }
    }
}
