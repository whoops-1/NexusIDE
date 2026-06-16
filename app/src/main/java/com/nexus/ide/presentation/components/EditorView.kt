package com.nexus.ide.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nexus.ide.features.editor.EditorSession
import com.nexus.ide.features.editor.highlighter.Token
import com.nexus.ide.features.editor.highlighter.TokenKind
import com.nexus.ide.presentation.theme.EditorTheme

/**
 * Mobile-first code editor surface.
 *
 * Strategy:
 *  - We do NOT use Compose's `BasicTextField` for the visible text. The
 *    source of truth is `EditorSession.buffer`; Compose merely renders a
 *    window over it. This keeps edits O(1) regardless of file size and
 *    avoids the Compose TextField's notoriously poor performance on
 *    documents over ~10k chars.
 *  - Only the visible lines are tokenized (the [EditorSession.cache] is
 *    an LRU so cold lines are still re-highlighted on scroll).
 *  - The IME / hardware keyboard feed into the session via a hidden
 *    `TextField` zero-size shim that owns the soft keyboard.
 */
@Composable
fun EditorView(
    session: EditorSession,
    theme: EditorTheme,
    modifier: Modifier = Modifier
) {
    val buffer = session.buffer
    val scroll = rememberScrollState()
    val hScroll = rememberScrollState()
    val density = LocalDensity.current
    val fontSize by session.fontSize.collectAsState()
    val lineHeightDp = (fontSize * 1.45f).dp
    val lineHeightPx = with(density) { lineHeightDp.toPx() }
    val version by buffer.version.collectAsState()
    val cursor by session.cursor.collectAsState()
    val totalLines = buffer.lineCount()

    BoxWithConstraints(modifier = modifier.background(theme.background)) {
        val viewportPx = with(density) { maxHeight.toPx() }
        val first = (scroll.value / lineHeightPx).toInt().coerceAtLeast(0)
        val last = ((scroll.value + viewportPx) / lineHeightPx).toInt().coerceAtMost(totalLines - 1) + 2

        Row(
            modifier = Modifier
                .fillMaxSize()
                .horizontalScroll(hScroll)
        ) {
            EditorGutter(
                totalLines = totalLines,
                firstVisibleLine = first,
                lastVisibleLine = last,
                cursorLine = cursor.line,
                theme = theme,
                fontSize = fontSize
            )

            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .widthIn(max = 2000.dp)
                    .pointerInput(version, fontSize) {
                        detectTapGestures { offset ->
                            val y = offset.y
                            val lineInWindow = (y / lineHeightPx).toInt()
                            val line = (first + lineInWindow).coerceIn(0, totalLines - 1)
                            val x = (offset.x - with(density) { 48.dp.toPx() }).coerceAtLeast(0f)
                            val col = estimateColumn(buffer.getLine(line), x, fontSize)
                            session.moveCursor(line, col)
                        }
                    }
                    .pointerInput(Unit) {
                        detectTransformGestures { _, _, zoom, _ ->
                            if (zoom != 1f) {
                                val newSize = (fontSize * zoom).coerceIn(9f, 32f)
                                session.setFontSize(newSize)
                            }
                        }
                    }
                    .verticalScroll(scroll)
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    for (i in first until minOf(last, totalLines)) {
                        val tokens = session.highlight.tokensFor(i, buffer.getLine(i))
                        CodeLine(
                            lineIndex = i,
                            text = buffer.getLine(i),
                            tokens = tokens,
                            theme = theme,
                            fontSize = fontSize,
                            isCursorLine = i == cursor.line
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EditorGutter(
    totalLines: Int,
    firstVisibleLine: Int,
    lastVisibleLine: Int,
    cursorLine: Int,
    theme: EditorTheme,
    fontSize: Float
) {
    Column(
        modifier = Modifier
            .width(48.dp)
            .fillMaxHeight()
            .background(theme.gutter)
            .padding(vertical = 2.dp),
        horizontalAlignment = Alignment.End
    ) {
        for (i in firstVisibleLine..minOf(lastVisibleLine, totalLines - 1)) {
            Text(
                text = (i + 1).toString(),
                color = if (i == cursorLine) theme.cursorLineNumber else theme.lineNumber,
                fontSize = (fontSize * 0.85f).sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(end = 6.dp, top = 1.dp, bottom = 1.dp),
                textAlign = TextAlign.End,
                maxLines = 1,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

@Composable
private fun CodeLine(
    lineIndex: Int,
    text: String,
    tokens: List<Token>,
    theme: EditorTheme,
    fontSize: Float,
    isCursorLine: Boolean
) {
    val annotated = remember(text, tokens, fontSize) { highlightToAnnotated(text, tokens, theme) }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (isCursorLine) theme.cursorLineBg else Color.Transparent)
            .padding(start = 8.dp, end = 8.dp, top = 1.dp, bottom = 1.dp)
    ) {
        Text(
            text = annotated,
            style = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = fontSize.sp,
                color = theme.foreground,
                lineHeight = (fontSize * 1.45f).sp
            ),
            overflow = TextOverflow.Visible,
            softWrap = false,
            maxLines = 1
        )
    }
}

private fun highlightToAnnotated(
    src: String,
    tokens: List<Token>,
    theme: EditorTheme
): AnnotatedString = buildAnnotatedString {
    append(src)
    for (t in tokens) {
        if (t.start < 0 || t.end > src.length || t.start >= t.end) continue
        val color = when (t.kind) {
            TokenKind.Keyword -> theme.keyword
            TokenKind.String -> theme.string
            TokenKind.Number -> theme.number
            TokenKind.Comment -> theme.comment
            TokenKind.Function -> theme.function
            TokenKind.Type -> theme.type
            TokenKind.Variable -> theme.variable
            TokenKind.Builtin -> theme.builtin
            TokenKind.Operator -> theme.operator
            TokenKind.Punctuation -> theme.punctuation
            TokenKind.Property -> theme.property
            TokenKind.Tag -> theme.tag
            TokenKind.Attribute -> theme.attribute
            TokenKind.Annotation -> theme.annotation
            TokenKind.Preprocessor -> theme.preprocessor
            TokenKind.Plain -> theme.foreground
            TokenKind.Whitespace -> Color.Transparent
        }
        addStyle(SpanStyle(color = color), t.start, t.end)
    }
}

/**
 * Cheap column-from-x estimator: counts how many chars of the line fit
 * in [x] pixels at the editor's font size. Good enough for tap-to-position.
 * For higher accuracy we'd cache per-line TextLayoutResult, but this is
 * far faster and avoids per-frame layout work.
 */
private fun estimateColumn(line: String, xPx: Float, fontSizeSp: Float): Int {
    if (xPx <= 0f) return 0
    val charWidth = fontSizeSp * 0.6f
    val col = (xPx / charWidth).toInt()
    return col.coerceIn(0, line.length)
}
