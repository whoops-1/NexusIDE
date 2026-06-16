package com.nexus.ide.presentation.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.nexus.ide.features.editor.highlighter.TokenKind

/**
 * All theme tokens in one place. The editor reads from [LocalEditorTheme]
 * for its own colors; the rest of the app uses Material 3.
 */
data class EditorTheme(
    val id: String,
    val name: String,
    val isDark: Boolean,
    val background: Color,
    val foreground: Color,
    val gutter: Color,
    val lineNumber: Color,
    val cursorLineNumber: Color,
    val cursorLineBg: Color,
    val selection: Color,
    val caret: Color,
    val keyword: Color,
    val string: Color,
    val number: Color,
    val comment: Color,
    val function: Color,
    val type: Color,
    val variable: Color,
    val builtin: Color,
    val operator: Color,
    val punctuation: Color,
    val property: Color,
    val tag: Color,
    val attribute: Color,
    val annotation: Color,
    val preprocessor: Color,
) {
    companion object {
        fun githubDark() = EditorTheme(
            id = "github-dark",
            name = "GitHub Dark",
            isDark = true,
            background = Color(0xFF0d1117),
            foreground = Color(0xFFc9d1d9),
            gutter = Color(0xFF0d1117),
            lineNumber = Color(0xFF484f58),
            cursorLineNumber = Color(0xFFc9d1d9),
            cursorLineBg = Color(0xFF161b22),
            selection = Color(0xFF264f78),
            caret = Color(0xFF58a6ff),
            keyword = Color(0xFFff7b72),
            string = Color(0xFFa5d6ff),
            number = Color(0xFF79c0ff),
            comment = Color(0xFF8b949e),
            function = Color(0xFFd2a8ff),
            type = Color(0xFFffa657),
            variable = Color(0xFFc9d1d9),
            builtin = Color(0xFFffa657),
            operator = Color(0xFFff7b72),
            punctuation = Color(0xFFc9d1d9),
            property = Color(0xFF79c0ff),
            tag = Color(0xFF7ee787),
            attribute = Color(0xFF79c0ff),
            annotation = Color(0xFFd2a8ff),
            preprocessor = Color(0xFFff7b72),
        )

        fun monokai() = EditorTheme(
            id = "monokai",
            name = "Monokai",
            isDark = true,
            background = Color(0xFF272822),
            foreground = Color(0xFFF8F8F2),
            gutter = Color(0xFF272822),
            lineNumber = Color(0xFF75715E),
            cursorLineNumber = Color(0xFFF8F8F2),
            cursorLineBg = Color(0xFF3E3D32),
            selection = Color(0xFF49483E),
            caret = Color(0xFFF8F8F2),
            keyword = Color(0xFFF92672),
            string = Color(0xFFE6DB74),
            number = Color(0xFFAE81FF),
            comment = Color(0xFF75715E),
            function = Color(0xFFA6E22E),
            type = Color(0xFF66D9EF),
            variable = Color(0xFFF8F8F2),
            builtin = Color(0xFF66D9EF),
            operator = Color(0xFFF92672),
            punctuation = Color(0xFFF8F8F2),
            property = Color(0xFF66D9EF),
            tag = Color(0xFFF92672),
            attribute = Color(0xFFA6E22E),
            annotation = Color(0xFFA6E22E),
            preprocessor = Color(0xFFF92672),
        )

        fun solarizedLight() = EditorTheme(
            id = "solarized-light",
            name = "Solarized Light",
            isDark = false,
            background = Color(0xFFFDF6E3),
            foreground = Color(0xFF586E75),
            gutter = Color(0xFFEEE8D5),
            lineNumber = Color(0xFF93A1A1),
            cursorLineNumber = Color(0xFF586E75),
            cursorLineBg = Color(0xFFEEE8D5),
            selection = Color(0xFFEEE8D5),
            caret = Color(0xFF268BD2),
            keyword = Color(0xFF859900),
            string = Color(0xFF2AA198),
            number = Color(0xFFD33682),
            comment = Color(0xFF93A1A1),
            function = Color(0xFF268BD2),
            type = Color(0xFFB58900),
            variable = Color(0xFF586E75),
            builtin = Color(0xFF268BD2),
            operator = Color(0xFF859900),
            punctuation = Color(0xFF586E75),
            property = Color(0xFF268BD2),
            tag = Color(0xFF859900),
            attribute = Color(0xFF268BD2),
            annotation = Color(0xFFB58900),
            preprocessor = Color(0xFFCB4B16),
        )
    }
}

val BUILT_IN_THEMES: List<EditorTheme> = listOf(
    EditorTheme.githubDark(),
    EditorTheme.monokai(),
    EditorTheme.solarizedLight(),
)

fun findTheme(id: String): EditorTheme =
    BUILT_IN_THEMES.firstOrNull { it.id == id } ?: BUILT_IN_THEMES.first()

val LocalEditorTheme = staticCompositionLocalOf<EditorTheme> { BUILT_IN_THEMES.first() }

private fun editorToMaterial3(theme: EditorTheme) = if (theme.isDark) {
    darkColorScheme(
        primary = theme.keyword,
        onPrimary = theme.background,
        secondary = theme.function,
        background = theme.background,
        onBackground = theme.foreground,
        surface = theme.background,
        onSurface = theme.foreground,
        surfaceVariant = theme.cursorLineBg,
        onSurfaceVariant = theme.foreground,
        outline = theme.lineNumber,
        error = Color(0xFFF85149),
        onError = theme.foreground,
    )
} else {
    lightColorScheme(
        primary = theme.keyword,
        onPrimary = theme.background,
        secondary = theme.function,
        background = theme.background,
        onBackground = theme.foreground,
        surface = theme.background,
        onSurface = theme.foreground,
        surfaceVariant = theme.cursorLineBg,
        onSurfaceVariant = theme.foreground,
        outline = theme.lineNumber,
    )
}

@Composable
fun NexusTheme(
    themeId: String = "github-dark",
    content: @Composable () -> Unit,
) {
    val theme = findTheme(themeId)
    MaterialTheme(
        colorScheme = editorToMaterial3(theme),
        content = {
            CompositionLocalProvider(LocalEditorTheme provides theme) {
                content()
            }
        }
    )
}
