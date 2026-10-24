package com.drpaulog.photopuzzle

import kotlin.random.Random

/**
 * Estado de um puzzle de troca de peças num tabuleiro [size] x [size].
 *
 * Cada célula guarda o id da peça que lá está; o id de uma peça é o índice
 * da célula onde ela pertence (a sua "casa"). Peças vizinhas no tabuleiro que
 * também são vizinhas na imagem original, na mesma posição relativa, ficam
 * unidas num grupo e movem-se juntas. Uma peça na sua casa fica bloqueada.
 */
class PuzzleBoard(val size: Int, initialCells: IntArray) {

    private var cells: IntArray = initialCells.copyOf()

    init {
        require(size >= 2) { "size must be at least 2" }
        require(cells.size == size * size) { "expected ${size * size} cells" }
        require(cells.sorted() == cells.indices.toList()) { "cells must be a permutation" }
    }

    val cellCount: Int get() = cells.size

    fun cells(): IntArray = cells.copyOf()

    fun pieceAt(cell: Int): Int = cells[cell]

    fun isLocked(cell: Int): Boolean = cells[cell] == cell

    fun lockedCount(): Int = cells.indices.count { cells[it] == it }

    fun isSolved(): Boolean = cells.indices.all { cells[it] == it }

    /** True se a peça na célula à direita de [cell] encaixa com a peça em [cell]. */
    fun joinedRight(cell: Int): Boolean {
        if (cell % size == size - 1) return false
        val a = cells[cell]
        val b = cells[cell + 1]
        return a % size != size - 1 && b == a + 1
    }

    /** True se a peça na célula abaixo de [cell] encaixa com a peça em [cell]. */
    fun joinedDown(cell: Int): Boolean {
        if (cell / size == size - 1) return false
        return cells[cell + size] == cells[cell] + size
    }

    fun joinedLeft(cell: Int): Boolean = cell % size != 0 && joinedRight(cell - 1)

    fun joinedUp(cell: Int): Boolean = cell >= size && joinedDown(cell - size)

    /** Todas as células do grupo que contém [cell] (peças unidas entre si). */
    fun groupOf(cell: Int): Set<Int> {
        val group = HashSet<Int>()
        val stack = ArrayDeque<Int>()
        stack.addLast(cell)
        while (stack.isNotEmpty()) {
            val c = stack.removeLast()
            if (!group.add(c)) continue
            if (joinedRight(c)) stack.addLast(c + 1)
            if (joinedLeft(c)) stack.addLast(c - 1)
            if (joinedDown(c)) stack.addLast(c + size)
            if (joinedUp(c)) stack.addLast(c - size)
        }
        return group
    }

    /**
     * Move o grupo que contém [from] de modo a que a peça em [from] vá para [to].
     * As peças que estavam no destino ocupam as células libertadas pelo grupo,
     * mantendo a sua disposição relativa. Para um grupo de uma peça isto é uma
     * simples troca.
     *
     * Devolve false (sem alterar nada) se o grupo estiver bloqueado, se sair do
     * tabuleiro ou se fosse por cima de uma peça bloqueada.
     */
    fun move(from: Int, to: Int): Boolean {
        if (from == to || isLocked(from) || isLocked(to)) return false
        val dr = to / size - from / size
        val dc = to % size - from % size
        val group = groupOf(from)

        val shift = HashMap<Int, Int>(group.size)
        for (c in group) {
            val r = c / size + dr
            val col = c % size + dc
            if (r !in 0 until size || col !in 0 until size) return false
            val target = r * size + col
            if (isLocked(target)) return false
            shift[c] = target
        }
        val targets = shift.values.toHashSet()

        val next = cells.copyOf()
        for ((src, dst) in shift) next[dst] = cells[src]
        // Cada peça deslocada recua pelo vetor do movimento até sair da área de
        // destino; essa célula é necessariamente uma das que o grupo libertou.
        val back = -(dr * size + dc)
        for (t in targets) {
            if (t in group) continue
            var c = t
            do {
                c += back
            } while (c in targets)
            next[c] = cells[t]
        }
        cells = next
        return true
    }

    companion object {
        /**
         * Cria um tabuleiro baralhado em que nenhuma peça começa no sítio certo
         * e, sempre que possível, nenhuma começa já unida a outra.
         */
        fun shuffled(size: Int, random: Random = Random.Default): PuzzleBoard {
            val n = size * size
            var best: IntArray? = null
            repeat(500) {
                val perm = (0 until n).shuffled(random).toIntArray()
                if (perm.indices.any { perm[it] == it }) return@repeat
                val board = PuzzleBoard(size, perm)
                if (best == null) best = perm
                val anyJoined = (0 until n).any { board.joinedRight(it) || board.joinedDown(it) }
                if (!anyJoined) return board
            }
            // Recurso: aceita uniões iniciais, mas nunca peças já no sítio.
            return PuzzleBoard(size, best ?: derangement(n, random))
        }

        private fun derangement(n: Int, random: Random): IntArray {
            // Rotação cíclica de uma ordem aleatória: nunca tem pontos fixos.
            val order = (0 until n).shuffled(random)
            val result = IntArray(n)
            for (i in 0 until n) result[order[i]] = order[(i + 1) % n]
            return result
        }
    }
}
