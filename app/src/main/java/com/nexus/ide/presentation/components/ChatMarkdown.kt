package com.nexus.ide.presentation.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.jeziellago.compose.markdowntext.MarkdownText
import kotlinx.coroutines.delay

/**
 * Renders chat message text the way ChatGPT/Claude/Cursor do: prose goes
 * through real markdown (bold, lists, inline code, links), fenced code
 * blocks get their own fixed-style surface with a language label and a
 * copy button rather than being flattened into plain paragraph text.
 *
 * Code blocks intentionally ignore the active editor theme and use a
 * fixed dark "console" surface — this matches how every reference product
 * named in the brief renders code, and keeps code legible regardless of
 * whether the surrounding bubble is using a light theme.
 */
private val CODE_FENCE_REGEX = Regex("```([a-zA-Z0-9_+\\-]*)\\n?([\\s\\S]*?)```")

private sealed class ChatSegment {
    data class Prose(val markdown: String) : ChatSegment()
    data class Code(val language: String, val code: String) : ChatSegment()
}

private fun parseChatSegments(text: String): List<ChatSegment> {
    val segments = mutableListOf<ChatSegment>()
    var lastEnd = 0
    for (match in CODE_FENCE_REGEX.findAll(text)) {
        if (match.range.first > lastEnd) {
            val prose = text.substring(lastEnd, match.range.first)
            if (prose.isNotBlank()) segments.add(ChatSegment.Prose(prose))
        }
        val lang = match.groupValues[1].ifBlank { "text" }
        val code = match.groupValues[2].trimEnd('\n')
        if (code.isNotBlank()) segments.add(ChatSegment.Code(lang, code))
        lastEnd = match.range.last + 1
    }
    if (lastEnd < text.length) {
        val rest = text.substring(lastEnd)
        if (rest.isNotBlank()) segments.add(ChatSegment.Prose(rest))
    }
    if (segments.isEmpty() && text.isNotBlank()) segments.add(ChatSegment.Prose(text))
    return segments
}

@Composable
fun ChatRichText(
    text: String,
    textColor: Color = MaterialTheme.colorScheme.onSurface,
    modifier: Modifier = Modifier
) {
    val segments = remember(text) { parseChatSegments(text) }
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        segments.forEach { segment ->
            when (segment) {
                is ChatSegment.Prose -> MarkdownText(
                    markdown = segment.markdown.trim(),
                    style = LocalTextStyle.current.copy(color = textColor),
                    modifier = Modifier.fillMaxWidth()
                )
                is ChatSegment.Code -> CodeBlock(language = segment.language, code = segment.code)
            }
        }
    }
}

@Composable
private fun CodeBlock(language: String, code: String) {
    val clipboard = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = Color(0xFF1E1E1E),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(language, color = Color(0xFF9DA5B4), fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                TextButton(onClick = {
                    clipboard.setText(AnnotatedString(code))
                    copied = true
                }) {
                    Icon(
                        if (copied) Icons.Filled.Check else Icons.Filled.ContentCopy,
                        contentDescription = "Copy code",
                        tint = Color(0xFF9DA5B4),
                        modifier = Modifier.padding(end = 4.dp)
                    )
                    Text(if (copied) "Copied" else "Copy", color = Color(0xFF9DA5B4), fontSize = 11.sp)
                }
            }
            HorizontalDivider(color = Color(0xFF3A3A3A), thickness = 0.5.dp)
            Box(modifier = Modifier.horizontalScroll(rememberScrollState()).padding(12.dp)) {
                Text(
                    text = code,
                    color = Color(0xFFD4D4D4),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.5.sp
                )
            }
        }
    }

    LaunchedEffect(copied) {
        if (copied) {
            delay(1500)
            copied = false
        }
    }
}
