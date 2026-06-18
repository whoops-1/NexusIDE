package com.nexus.ide.features.editor.actions

import com.nexus.ide.features.editor.EditorSession

/**
 * High-level editor actions invoked by toolbar buttons, menu items, and
 * keybindings. Each action reads/writes the [EditorSession] atomically.
 */
object EditorCommands {

    fun toggleComment(session: EditorSession) {
        val sel = session.selection.value
        val (startLine, endLine) = if (sel != null) {
            val s = sel.start; val e = sel.end
            Pair(minOf(s.line, e.line), maxOf(s.line, e.line))
        } else {
            val c = session.cursor.value
            Pair(c.line, c.line)
        }
        val commentToken = commentTokenFor(session.languageId)
        if (commentToken == null) return
        val allCommented = (startLine..endLine).all {
            session.buffer.getLine(it).trimStart().startsWith(commentToken)
        }
        for (line in startLine..endLine) {
            val text = session.buffer.getLine(line)
            if (allCommented) {
                val idx = text.indexOf(commentToken)
                if (idx >= 0) {
                    val newText = text.removeRange(idx, idx + commentToken.length)
                    session.buffer.replaceLineRange(line, 0, line, newText.length, newText)
                }
            } else {
                val indent = text.takeWhile { it == ' ' || it == '\t' }
                val newText = indent + commentToken + text.removePrefix(indent)
                session.buffer.replaceLineRange(line, 0, line, newText.length, newText)
            }
        }
    }

    fun duplicateLine(session: EditorSession) {
        val c = session.cursor.value
        val line = session.buffer.getLine(c.line)
        session.buffer.insert(c.line, line.length, "\n" + line)
    }

    fun deleteLine(session: EditorSession) {
        val c = session.cursor.value
        session.buffer.delete(c.line, 0, c.line + 1, 0)
    }

    fun moveLineUp(session: EditorSession) {
        val c = session.cursor.value
        if (c.line == 0) return
        val cur = session.buffer.getLine(c.line)
        val prev = session.buffer.getLine(c.line - 1)
        session.buffer.replaceLineRange(c.line - 1, 0, c.line, prev.length, prev)
        session.buffer.replaceLineRange(c.line - 1, 0, c.line - 1, cur.length, cur)
    }

    fun moveLineDown(session: EditorSession) {
        val c = session.cursor.value
        if (c.line >= session.buffer.lineCount() - 1) return
        val cur = session.buffer.getLine(c.line)
        val next = session.buffer.getLine(c.line + 1)
        session.buffer.replaceLineRange(c.line, 0, c.line + 1, next.length, next)
        session.buffer.replaceLineRange(c.line, 0, c.line, cur.length, cur)
    }

    fun indent(session: EditorSession) {
        val sel = session.selection.value
        val (startLine, endLine) = if (sel != null) {
            val s = sel.start; val e = sel.end
            Pair(minOf(s.line, e.line), maxOf(s.line, e.line))
        } else {
            val c = session.cursor.value
            Pair(c.line, c.line)
        }
        for (i in startLine..endLine) {
            session.buffer.insert(i, 0, "    ")
        }
    }

    fun outdent(session: EditorSession) {
        val sel = session.selection.value
        val (startLine, endLine) = if (sel != null) {
            val s = sel.start; val e = sel.end
            Pair(minOf(s.line, e.line), maxOf(s.line, e.line))
        } else {
            val c = session.cursor.value
            Pair(c.line, c.line)
        }
        for (i in startLine..endLine) {
            val line = session.buffer.getLine(i)
            val trim = when {
                line.startsWith("    ") -> 4
                line.startsWith("\t") -> 1
                line.startsWith("  ") -> 2
                else -> 0
            }
            if (trim > 0) session.buffer.delete(i, 0, i, trim)
        }
    }

    fun goToLine(session: EditorSession, line: Int) {
        val target = line.coerceIn(0, session.buffer.lineCount() - 1)
        session.setCursor(target, 0)
    }

    fun findAll(session: EditorSession, needle: String): List<Pair<Int, Int>> {
        if (needle.isEmpty()) return emptyList()
        val out = ArrayList<Pair<Int, Int>>()
        val n = session.buffer.lineCount()
        for (i in 0 until n) {
            val line = session.buffer.getLine(i)
            var idx = 0
            while (true) {
                val found = line.indexOf(needle, idx)
                if (found < 0) break
                out.add(i to found)
                idx = found + needle.length
            }
        }
        return out
    }

    private fun commentTokenFor(id: String): String? = when (id) {
        "python", "yaml", "sh", "bash", "ruby", "toml", "ini", "dockerfile", "makefile" -> "#"
        "html", "xml" -> "<!--"
        "css", "scss" -> "/*"
        else -> "//"
    }
}
