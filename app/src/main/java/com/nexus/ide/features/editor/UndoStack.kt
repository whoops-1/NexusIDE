package com.nexus.ide.features.editor

import java.util.ArrayDeque

/**
 * Operation-based undo/redo. Each [Op] records the inverse that, when
 * applied, restores the previous state.
 *
 * Memory:
 *  - We keep the last [maxDepth] operations. The first operation dropped
 *    is the oldest.
 *  - Coalescing: consecutive character inserts at the cursor are merged
 *    into a single op (with a 750ms idle window) so typing a word
 *    produces one undo step, not one per character.
 */
class UndoStack(private val maxDepth: Int = 500) {

    data class Op(val inverse: Inverse, val timestamp: Long = System.currentTimeMillis()) {
        sealed class Inverse {
            data class Replace(val line: Int, val startCol: Int, val endLine: Int, val endCol: Int, val text: String) : Inverse()
        }
    }

    private val undo = ArrayDeque<Op>()
    private val redo = ArrayDeque<Op>()
    private val coalesceWindowMs = 750L

    val canUndo: Boolean get() = undo.isNotEmpty()
    val canRedo: Boolean get() = redo.isNotEmpty()

    fun record(inverse: Op.Inverse) {
        val now = System.currentTimeMillis()
        val last = undo.peekLast()
        if (last != null && now - last.timestamp < coalesceWindowMs) {
            // Merge with previous if it represents an adjacent range
            val prev = last.inverse as? Op.Inverse.Replace ?: run { push(Op(inverse, now)); return }
            val curr = inverse as? Op.Inverse.Replace ?: run { push(Op(inverse, now)); return }
            if (prev.endLine == curr.line && prev.endCol == curr.startCol) {
                val merged = Op.Inverse.Replace(
                    line = prev.line, startCol = prev.startCol,
                    endLine = curr.endLine, endCol = curr.endCol,
                    text = prev.text + curr.text
                )
                undo.removeLast()
                undo.addLast(Op(merged, now))
                redo.clear()
                return
            }
        }
        push(Op(inverse, now))
    }

    private fun push(op: Op) {
        undo.addLast(op)
        if (undo.size > maxDepth) undo.removeFirst()
        redo.clear()
    }

    fun popUndo(): Op? = if (undo.isEmpty()) null else undo.removeLast()
    fun popRedo(): Op? = if (redo.isEmpty()) null else redo.removeLast()
    fun pushRedo(op: Op) { redo.addLast(op) }
    fun clear() { undo.clear(); redo.clear() }
}
