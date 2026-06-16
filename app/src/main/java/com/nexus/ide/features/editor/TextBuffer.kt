package com.nexus.ide.features.editor

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

/**
 * Thread-safe text buffer optimized for editor use.
 *
 * Design:
 *  - Stores the document as a list of mutable CharArray chunks. Each
 *    chunk holds one or more whole lines. A line index allows O(log n)
 *    random access by line number.
 *  - Mutating operations (insert/delete) lock for write, query operations
 *    use a read lock. The lock is *not* a coroutine-aware mutex; the UI
 *    dispatcher is responsible for confining edits to the main thread.
 *  - Edits publish a [Change] via [changes] which downstream views use to
 *    update their caches incrementally.
 */
class TextBuffer(initial: String = "") {

    /** One or more lines, separated by \n, as a single mutable CharArray. */
    private val lines: ArrayList<CharArray> = ArrayList(64)

    private val lock = ReentrantReadWriteLock()

    private val _changes = MutableStateFlow<Change>(Change.Init(initial))
    val changes: StateFlow<Change> = _changes.asStateFlow()

    private val _version = MutableStateFlow(0L)
    val version: StateFlow<Long> = _version.asStateFlow()

    init {
        if (initial.isEmpty()) lines.add(CharArray(0)) else splitLinesInto(initial)
    }

    fun lineCount(): Int = lock.read { lines.size }

    fun length(): Int = lock.read { lines.sumOf { it.size + 1 } - 1 }

    fun getLine(index: Int): String = lock.read {
        if (index < 0 || index >= lines.size) throw IndexOutOfBoundsException("line $index, size ${lines.size}")
        String(lines[index])
    }

    fun getText(): String = lock.read {
        if (lines.size == 1) return String(lines[0])
        val sb = StringBuilder(lines.sumOf { it.size + 1 })
        for ((i, a) in lines.withIndex()) {
            if (i > 0) sb.append('\n')
            sb.append(a, 0, a.size)
        }
        sb.toString()
    }

    fun insert(line: Int, col: Int, text: String) = lock.write {
        require(line in 0..lines.size)
        if (text.isEmpty()) return@write
        val target = lines[line].copyOf(lines[line].size + text.length)
        val bytes = text.toCharArray()
        // Copy col..end then bytes
        System.arraycopy(lines[line], col, target, col + bytes.size, lines[line].size - col)
        System.arraycopy(bytes, 0, target, col, bytes.size)
        lines[line] = target
        // Re-split if we inserted newlines
        if ('\n' in text) {
            val pieces = String(target).split('\n')
            lines.removeAt(line)
            for ((i, p) in pieces.withIndex()) {
                lines.add(line + i, p.toCharArray())
            }
        }
        bumpVersion()
        _changes.value = Change.Insert(line, col, text)
    }

    fun delete(startLine: Int, startCol: Int, endLine: Int, endCol: Int) = lock.write {
        require(startLine in 0..lines.size)
        require(endLine in startLine..lines.size)
        if (startLine == endLine) {
            val arr = lines[startLine]
            val newLen = arr.size - (endCol - startCol)
            val target = arr.copyOf(newLen)
            System.arraycopy(arr, endCol, target, startCol, arr.size - endCol)
            lines[startLine] = target
        } else {
            // Stitch first and last pieces together
            val first = lines[startLine]
            val last = lines[endLine]
            val newLen = first.size - startCol + endCol
            val target = CharArray(newLen)
            System.arraycopy(first, 0, target, 0, first.size - startCol)
            System.arraycopy(last, endCol, target, first.size - startCol, last.size - endCol)
            for (i in endLine downTo startLine) lines.removeAt(i)
            lines.add(startLine, target)
        }
        bumpVersion()
        _changes.value = Change.Delete(startLine, startCol, endLine, endCol)
    }

    fun replaceAll(text: String) = lock.write {
        lines.clear()
        if (text.isEmpty()) lines.add(CharArray(0)) else splitLinesInto(text)
        bumpVersion()
        _changes.value = Change.Init(text)
    }

    private fun splitLinesInto(text: String) {
        var start = 0
        var i = 0
        val n = text.length
        while (i < n) {
            if (text[i] == '\n') {
                lines.add(text.substring(start, i).toCharArray())
                start = i + 1
            }
            i++
        }
        lines.add(text.substring(start, n).toCharArray())
    }

    private fun bumpVersion() {
        _version.value = _version.value + 1
    }

    sealed class Change {
        data class Init(val text: String) : Change()
        data class Insert(val line: Int, val col: Int, val text: String) : Change()
        data class Delete(val startLine: Int, val startCol: Int, val endLine: Int, val endCol: Int) : Change()
    }

    /**
     * Replace the text between (startLine,startCol) and (endLine,endCol) with [text].
     * If the range spans multiple lines, the replacement text is inserted as
     * additional lines. This is a convenience over insert+delete for whole
     * line replacements and region overwrites.
     */
    fun replaceLineRange(startLine: Int, startCol: Int, endLine: Int, endCol: Int, text: String) = lock.write {
        delete(startLine, startCol, endLine, endCol)
        if (text.isNotEmpty()) insert(startLine, startCol, text)
    }

}
