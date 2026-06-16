package com.nexus.ide.domain.models

import java.io.File

/**
 * Domain models — the shapes the UI and feature layers operate on.
 * Repositories convert to/from these and never leak Room entities
 * outside the data layer.
 */

data class Project(
    val id: Long,
    val name: String,
    val root: File,
    val template: String?,
    val lastOpened: Long,
    val pinned: Boolean,
    val iconColor: Int
) {
    val isTermuxManaged: Boolean
        get() = root.absolutePath.startsWith("/data/data/com.termux/files/home") ||
                root.absolutePath.startsWith("/storage/emulated/0/termux") ||
                root.absolutePath.startsWith("/sdcard/termux")
}

data class FsEntry(
    val file: File,
    val name: String,
    val isDir: Boolean,
    val size: Long,
    val lastModified: Long,
    val children: Int = 0
) {
    val extension: String get() = file.extension.lowercase()
}

data class OpenBuffer(
    val id: String,        // stable id (path hash)
    val file: File,
    val dirty: Boolean = false,
    val readonly: Boolean = false,
    val encoding: String = "UTF-8",
    val lineCount: Int = 0,
    val language: String = "plaintext",
    val cursorLine: Int = 0,
    val cursorCol: Int = 0,
    val pinned: Boolean = false
)

data class EditorTab(
    val bufferId: String,
    val title: String,
    val language: String,
    val dirty: Boolean,
    val pinned: Boolean,
    val active: Boolean
)

data class Symbol(
    val name: String,
    val kind: String,        // function, class, variable...
    val file: File,
    val line: Int,
    val col: Int = 0
)

data class Diagnostic(
    val line: Int,
    val col: Int,
    val message: String,
    val severity: Severity,
    val source: String = ""
) {
    enum class Severity { Error, Warning, Info, Hint }
}

data class CompletionItem(
    val label: String,
    val kind: String,        // keyword, function, variable...
    val detail: String? = null,
    val insertText: String = label,
    val insertTextFormat: InsertFormat = InsertFormat.PlainText
) {
    enum class InsertFormat { PlainText, Snippet }
}

data class ThemeColors(
    val background: Long,
    val foreground: Long,
    val gutterBg: Long,
    val gutterFg: Long,
    val lineHighlight: Long,
    val selection: Long,
    val caret: Long,
    val cursorLine: Long,
    val find: Long,
    val keyword: Long,
    val string: Long,
    val number: Long,
    val comment: Long,
    val function: Long,
    val type: Long,
    val variable: Long,
    val operator: Long,
    val punctuation: Long,
    val accent: Long,
    val error: Long,
    val warning: Long,
    val info: Long
) {
    fun toLongs(): LongArray = longArrayOf(
        background, foreground, gutterBg, gutterFg, lineHighlight, selection, caret, cursorLine,
        find, keyword, string, number, comment, function, type, variable, operator, punctuation,
        accent, error, warning, info
    )
}
