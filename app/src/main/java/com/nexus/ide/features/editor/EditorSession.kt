package com.nexus.ide.features.editor

import com.nexus.ide.features.editor.highlighter.HighlightCache
import com.nexus.ide.features.editor.highlighter.TokenizerRegistry
import com.nexus.ide.features.editor.language.LanguageRegistry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.nio.charset.StandardCharsets

/**
 * State for one open file. Owns its [TextBuffer], [HighlightCache], and
 * [UndoStack]. Exposes flows the UI binds to.
 */
class EditorSession(
    val file: File?,
    val languageId: String = LanguageRegistry.detect(file).id,
    initialText: String = "",
) {

    val buffer: TextBuffer = TextBuffer(initialText)
    val highlight: HighlightCache = HighlightCache(TokenizerRegistry.forLanguage(languageId))
    val undo: UndoStack = UndoStack()

    private val _dirty = MutableStateFlow(false)
    val dirty: StateFlow<Boolean> = _dirty.asStateFlow()

    private val _cursor = MutableStateFlow(Cursor(0, 0))
    val cursor: StateFlow<Cursor> = _cursor.asStateFlow()

    private val _fontSize = MutableStateFlow(14f)
    val fontSize: StateFlow<Float> = _fontSize.asStateFlow()

    fun setFontSize(sp: Float) { _fontSize.value = sp.coerceIn(9f, 32f) }

    private val _selection = MutableStateFlow<Selection?>(null)
    val selection: StateFlow<Selection?> = _selection.asStateFlow()

    data class Cursor(val line: Int, val col: Int)
    data class Selection(val anchor: Cursor, val caret: Cursor) {
        val start: Cursor get() = if (anchor <= caret) anchor else caret
        val end: Cursor get() = if (anchor <= caret) caret else anchor
        val isEmpty: Boolean get() = anchor == caret
    }

    fun setCursor(line: Int, col: Int) { _cursor.value = Cursor(line, col); _selection.value = null }
    fun setSelection(anchor: Cursor, caret: Cursor) {
        _cursor.value = caret
        _selection.value = if (anchor == caret) null else Selection(anchor, caret)
    }

    fun type(text: String) {
        if (text.isEmpty()) return
        val sel = _selection.value
        if (sel != null) deleteSelection(sel)
        val (l, c) = _cursor.value
        buffer.insert(l, c, text)
        undo.record(UndoStack.Op.Inverse.Replace(l, c, l, c + text.length, ""))
        moveCursor(l, c + text.length)
        markDirty()
    }

    fun backspace() {
        val sel = _selection.value
        if (sel != null) { deleteSelection(sel); return }
        val (l, c) = _cursor.value
        if (l == 0 && c == 0) return
        if (c == 0) {
            val prev = l - 1
            val prevLen = buffer.getLine(prev).length
            buffer.delete(prev, prevLen, l, 0)
            undo.record(UndoStack.Op.Inverse.Replace(prev, prevLen, l, 0, ""))
            setCursor(prev, prevLen)
        } else {
            buffer.delete(l, c - 1, l, c)
            undo.record(UndoStack.Op.Inverse.Replace(l, c - 1, l, c, ""))
            setCursor(l, c - 1)
        }
        markDirty()
    }

    fun deleteForward() {
        val sel = _selection.value
        if (sel != null) { deleteSelection(sel); return }
        val (l, c) = _cursor.value
        val lineLen = buffer.getLine(l).length
        if (c >= lineLen) {
            if (l + 1 >= buffer.lineCount()) return
            buffer.delete(l, c, l + 1, 0)
            undo.record(UndoStack.Op.Inverse.Replace(l, c, l + 1, 0, ""))
        } else {
            buffer.delete(l, c, l, c + 1)
            undo.record(UndoStack.Op.Inverse.Replace(l, c, l, c + 1, ""))
        }
        markDirty()
    }

    fun enter() {
        val sel = _selection.value
        if (sel != null) deleteSelection(sel)
        val (l, c) = _cursor.value
        buffer.insert(l, c, "\n")
        undo.record(UndoStack.Op.Inverse.Replace(l, c, l + 1, 0, ""))
        setCursor(l + 1, 0)
        markDirty()
    }

    fun undoLast(): Boolean {
        val op = undo.popUndo() ?: return false
        val inv = op.inverse as UndoStack.Op.Inverse.Replace
        val current = collectTextInRange(inv.line, inv.startCol, inv.endLine, inv.endCol)
        buffer.delete(inv.line, inv.startCol, inv.endLine, inv.endCol)
        if (inv.text.isNotEmpty()) buffer.insert(inv.line, inv.startCol, inv.text)
        undo.pushRedo(UndoStack.Op.Inverse.Replace(inv.line, inv.startCol, inv.endLine, inv.endCol, current))
        moveCursor(inv.line, inv.startCol)
        markDirty()
        return true
    }

    fun redoLast(): Boolean {
        val op = undo.popRedo() ?: return false
        val inv = op.inverse as UndoStack.Op.Inverse.Replace
        val current = collectTextInRange(inv.line, inv.startCol, inv.endLine, inv.endCol)
        buffer.delete(inv.line, inv.startCol, inv.endLine, inv.endCol)
        if (inv.text.isNotEmpty()) buffer.insert(inv.line, inv.startCol, inv.text)
        undo.record(UndoStack.Op.Inverse.Replace(inv.line, inv.startCol, inv.endLine, inv.endCol, current))
        moveCursor(inv.line, inv.startCol)
        markDirty()
        return true
    }

    private fun deleteSelection(sel: Selection) {
        val s = sel.start; val e = sel.end
        val removed = collectTextInRange(s.line, s.col, e.line, e.col)
        buffer.delete(s.line, s.col, e.line, e.col)
        undo.record(UndoStack.Op.Inverse.Replace(s.line, s.col, e.line, e.col, removed))
        setCursor(s.line, s.col)
        markDirty()
    }

    private fun collectTextInRange(sl: Int, sc: Int, el: Int, ec: Int): String {
        if (sl == el) return buffer.getLine(sl).substring(sc, ec)
        val sb = StringBuilder()
        sb.append(buffer.getLine(sl).substring(sc)); sb.append('\n')
        for (i in sl + 1 until el) { sb.append(buffer.getLine(i)); sb.append('\n') }
        sb.append(buffer.getLine(el).substring(0, ec))
        return sb.toString()
    }

    fun moveCursor(line: Int, col: Int) {
        val lineCount = buffer.lineCount()
        val l = line.coerceIn(0, lineCount - 1)
        val c = col.coerceAtMost(buffer.getLine(l).length)
        setCursor(l, c)
    }

    fun loadFromDisk() {
        if (file == null || !file.exists()) return
        val text = runCatching { file.readText(StandardCharsets.UTF_8) }.getOrDefault("")
        buffer.replaceAll(text)
        undo.clear()
        _dirty.value = false
    }

    fun saveToDisk(): Boolean {
        if (file == null) return false
        return runCatching {
            file.parentFile?.mkdirs()
            file.writeText(buffer.getText(), StandardCharsets.UTF_8)
            true
        }.getOrDefault(false).also { ok -> if (ok) _dirty.value = false }
    }

    /** Mark the buffer as not-dirty (used when the VM has written the file). */
    fun markSaved() { _dirty.value = false }

    private fun markDirty() { _dirty.value = true }
}
