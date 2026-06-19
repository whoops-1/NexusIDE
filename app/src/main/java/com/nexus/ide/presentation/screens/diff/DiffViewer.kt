package com.nexus.ide.presentation.screens.diff

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private enum class DiffMode { Inline, SideBySide }
private enum class DiffLineType { Added, Removed, Context }

private data class DiffLine(
    val type: DiffLineType,
    val content: String,
    val oldLineNo: Int?,
    val newLineNo: Int?,
    val sign: String
)

private data class DiffHunk(val header: String, val lines: List<DiffLine>)

private val HUNK_HEADER_RE = Regex("""^@@ -(\d+)(?:,\d+)? \+(\d+)(?:,\d+)? @@(.*)""")

private fun parseDiff(raw: String): List<DiffHunk> {
    val hunks = mutableListOf<DiffHunk>()
    var currentHeader = ""
    var currentLines = mutableListOf<DiffLine>()
    var oldLine = 0
    var newLine = 0
    raw.lines().forEach { line ->
        val m = HUNK_HEADER_RE.find(line)
        if (m != null) {
            if (currentLines.isNotEmpty()) hunks.add(DiffHunk(currentHeader, currentLines.toList()))
            currentHeader = line.removePrefix("@@ ").trim()
            currentLines = mutableListOf()
            oldLine = m.groupValues[1].toIntOrNull() ?: 0
            newLine = m.groupValues[2].toIntOrNull() ?: 0
        } else when {
            line.startsWith("+") && !line.startsWith("+++") ->
                currentLines.add(DiffLine(DiffLineType.Added, line.drop(1), null, newLine++, "+"))
            line.startsWith("-") && !line.startsWith("---") ->
                currentLines.add(DiffLine(DiffLineType.Removed, line.drop(1), oldLine++, null, "-"))
            line.startsWith(" ") ->
                currentLines.add(DiffLine(DiffLineType.Context, line.drop(1), oldLine++, newLine++, " "))
        }
    }
    if (currentLines.isNotEmpty()) hunks.add(DiffHunk(currentHeader, currentLines.toList()))
    return hunks
}

private fun lineColors(type: DiffLineType): Pair<Color, Color> = when (type) {
    DiffLineType.Added   -> Color(0xFF0d2818) to Color(0xFF3fb950)
    DiffLineType.Removed -> Color(0xFF2d0f0f) to Color(0xFFf85149)
    DiffLineType.Context -> Color.Transparent to Color(0xFFc9d1d9)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiffViewer(
    diff: String,
    filename: String = "",
    onAccept: ((hunkIndex: Int) -> Unit)? = null,
    onReject: ((hunkIndex: Int) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val hunks = remember(diff) { parseDiff(diff) }
    var mode by remember { mutableStateOf(DiffMode.Inline) }
    var acceptedHunks by remember { mutableStateOf(setOf<Int>()) }
    var rejectedHunks by remember { mutableStateOf(setOf<Int>()) }

    Column(modifier = modifier) {
        Surface(color = MaterialTheme.colorScheme.surfaceVariant) {
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Code, null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(6.dp))
                Text(filename.ifBlank { "diff" }, fontSize = 11.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                SingleChoiceSegmentedButtonRow(modifier = Modifier.height(28.dp)) {
                    SegmentedButton(selected = mode == DiffMode.Inline, onClick = { mode = DiffMode.Inline }, shape = SegmentedButtonDefaults.itemShape(0, 2), modifier = Modifier.height(28.dp)) { Text("Inline", fontSize = 10.sp) }
                    SegmentedButton(selected = mode == DiffMode.SideBySide, onClick = { mode = DiffMode.SideBySide }, shape = SegmentedButtonDefaults.itemShape(1, 2), modifier = Modifier.height(28.dp)) { Text("Split", fontSize = 10.sp) }
                }
            }
        }
        HorizontalDivider(thickness = 0.5.dp)

        if (hunks.isEmpty()) {
            Box(Modifier.fillMaxSize(), Alignment.Center) { Text("No changes", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                itemsIndexed(hunks) { idx, hunk ->
                    val accepted = idx in acceptedHunks
                    val rejected = idx in rejectedHunks
                    Column(Modifier.fillMaxWidth()) {
                        // Hunk header with accept/reject
                        Row(
                            Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant.copy(0.5f)).padding(horizontal = 8.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Hunk ${idx+1}/${hunks.size}  ${hunk.header}", fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                            when {
                                accepted -> Surface(shape = RoundedCornerShape(4.dp), color = Color(0xFF1a472a)) { Text("Accepted", fontSize = 10.sp, color = Color(0xFF3fb950), modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)) }
                                rejected -> Surface(shape = RoundedCornerShape(4.dp), color = Color(0xFF3d1a1a)) { Text("Rejected", fontSize = 10.sp, color = Color(0xFFf85149), modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)) }
                                else -> {
                                    if (onAccept != null) TextButton(onClick = { acceptedHunks = acceptedHunks + idx; onAccept(idx) }, contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp), modifier = Modifier.height(26.dp)) {
                                        Icon(Icons.Default.Check, null, Modifier.size(12.dp)); Spacer(Modifier.width(2.dp)); Text("Accept", fontSize = 10.sp)
                                    }
                                    if (onReject != null) TextButton(onClick = { rejectedHunks = rejectedHunks + idx; onReject(idx) }, contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp), modifier = Modifier.height(26.dp), colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
                                        Icon(Icons.Default.Close, null, Modifier.size(12.dp)); Spacer(Modifier.width(2.dp)); Text("Reject", fontSize = 10.sp)
                                    }
                                }
                            }
                        }
                        if (mode == DiffMode.Inline) {
                            Column(Modifier.horizontalScroll(rememberScrollState())) {
                                hunk.lines.forEach { dl ->
                                    val (bg, fg) = lineColors(dl.type)
                                    Row(Modifier.background(if (accepted && dl.type == DiffLineType.Added) Color(0xFF0d2818) else if (rejected && dl.type == DiffLineType.Removed) Color(0xFF2d0f0f) else bg)) {
                                        Text(dl.oldLineNo?.toString() ?: "", fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.4f), modifier = Modifier.width(30.dp).padding(horizontal = 3.dp))
                                        Text(dl.newLineNo?.toString() ?: "", fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.4f), modifier = Modifier.width(30.dp).padding(horizontal = 3.dp))
                                        Text(dl.sign, fontSize = 12.sp, fontFamily = FontFamily.Monospace, color = fg, fontWeight = FontWeight.Bold, modifier = Modifier.width(12.dp))
                                        Text(dl.content, fontSize = 12.sp, fontFamily = FontFamily.Monospace, color = fg)
                                    }
                                }
                            }
                        } else {
                            // Side-by-side
                            val oldLines = hunk.lines.filter { it.type != DiffLineType.Added }
                            val newLines = hunk.lines.filter { it.type != DiffLineType.Removed }
                            val rows = maxOf(oldLines.size, newLines.size)
                            Row(Modifier.horizontalScroll(rememberScrollState())) {
                                Column(Modifier.weight(1f)) {
                                    repeat(rows) { i ->
                                        val dl = oldLines.getOrNull(i)
                                        if (dl != null) {
                                            val (bg, fg) = lineColors(dl.type)
                                            Row(Modifier.background(bg).padding(horizontal = 4.dp)) {
                                                Text(dl.oldLineNo?.toString() ?: "", fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.4f), modifier = Modifier.width(28.dp))
                                                Text(dl.sign + dl.content, fontSize = 12.sp, fontFamily = FontFamily.Monospace, color = fg)
                                            }
                                        } else Box(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant.copy(0.15f)).padding(vertical = 1.dp)) { Spacer(Modifier.height(19.dp)) }
                                    }
                                }
                                VerticalDivider(thickness = 0.5.dp)
                                Column(Modifier.weight(1f)) {
                                    repeat(rows) { i ->
                                        val dl = newLines.getOrNull(i)
                                        if (dl != null) {
                                            val (bg, fg) = lineColors(dl.type)
                                            Row(Modifier.background(bg).padding(horizontal = 4.dp)) {
                                                Text(dl.newLineNo?.toString() ?: "", fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.4f), modifier = Modifier.width(28.dp))
                                                Text(dl.sign + dl.content, fontSize = 12.sp, fontFamily = FontFamily.Monospace, color = fg)
                                            }
                                        } else Box(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant.copy(0.15f)).padding(vertical = 1.dp)) { Spacer(Modifier.height(19.dp)) }
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                    }
                }
            }
        }
    }
}
