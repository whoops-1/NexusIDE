package com.nexus.ide.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nexus.ide.features.terminal.TerminalHost
import com.nexus.ide.features.terminal.TerminalSession
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.io.File

/** Cap scrollback so a noisy or runaway process can't grow output unbounded. */
private const val SCROLLBACK_CHAR_CAP = 200_000

/**
 * Tabbed terminal view. Each tab is a [TerminalSession]; the toolbar shows
 * tabs with a "+" to spawn a new shell. Lines stream in as the host
 * process produces them.
 *
 * [workingDir] must be a directory this process can actually chdir into —
 * pass [com.nexus.ide.features.filesystem.WorkspaceService.workspaceRoot],
 * not a generic "home" path. Android's sandbox has no access to most of
 * the filesystem, including its own root, even though `File("/")` reports
 * `isDirectory == true`.
 *
 * Uses MaterialTheme.colorScheme rather than the editor's syntax-color
 * palette - this is generic terminal chrome, not the code surface.
 */
@Composable
fun TerminalView(
    host: TerminalHost,
    workingDir: File,
    modifier: Modifier = Modifier
) {
    val sessions = remember { mutableStateListOf<TerminalSession>().apply { addAll(host.all()) } }
    val activeId by host.active.collectAsState()
    var input by remember { mutableStateOf(TextFieldValue("")) }
    var launchError by remember { mutableStateOf<String?>(null) }
    var launchAttempt by remember { mutableStateOf(0) }
    val scope = rememberCoroutineScope()
    val scroll = rememberScrollState()

    LaunchedEffect(launchAttempt) {
        if (sessions.isEmpty()) {
            host.newLocal(workingDir, "sh")
                .onSuccess { session -> launchError = null; sessions.add(session) }
                .onFailure { e -> launchError = e.message ?: "Failed to start shell" }
        }
    }

    val active = sessions.firstOrNull { it.id == activeId } ?: sessions.firstOrNull()

    Column(modifier = modifier.background(MaterialTheme.colorScheme.background)) {
        // Tab bar
        Surface(color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth().height(40.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 4.dp)) {
                sessions.forEach { s ->
                    val sel = s.id == activeId
                    Surface(
                        color = if (sel) MaterialTheme.colorScheme.background else MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp),
                        modifier = Modifier.padding(end = 2.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                s.title,
                                color = if (sel) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                                fontSize = 12.sp,
                                fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Normal
                            )
                            IconButton(onClick = { host.close(s.id); sessions.remove(s) }, modifier = Modifier.size(24.dp)) {
                                Icon(Icons.Filled.Close, contentDescription = "Close ${s.title}", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(14.dp))
                            }
                        }
                    }
                }
                IconButton(onClick = {
                    host.newLocal(workingDir, "sh")
                        .onSuccess { session -> launchError = null; sessions.add(session); host.setActive(session.id) }
                        .onFailure { e -> launchError = e.message ?: "Failed to start shell" }
                }) {
                    Icon(Icons.Filled.Add, contentDescription = "New session", tint = MaterialTheme.colorScheme.onSurface)
                }
            }
        }
        // Output
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when {
                launchError != null -> TerminalErrorState(
                    message = launchError!!,
                    onRetry = { launchError = null; launchAttempt++ }
                )
                active != null -> {
                    var buffered by remember(active.id) { mutableStateOf("") }
                    LaunchedEffect(active.id) {
                        active.output.collectLatest { chunk ->
                            val next = buffered + chunk
                            buffered = if (next.length > SCROLLBACK_CHAR_CAP) next.takeLast(SCROLLBACK_CHAR_CAP) else next
                            scroll.scrollTo(scroll.maxValue)
                        }
                    }
                    Text(
                        text = buffered,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(8.dp)
                            .verticalScroll(scroll)
                    )
                }
                else -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Starting shell…", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                    }
                }
            }
        }
        // Input row
        if (active != null && launchError == null) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("$", color = MaterialTheme.colorScheme.primary, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                BasicTextField(
                    value = input,
                    onValueChange = { value ->
                        input = value
                        if (value.text.endsWith("\n")) {
                            val line = value.text.dropLast(1)
                            scope.launch { active.write(line + "\n") }
                            input = TextFieldValue("")
                        }
                    },
                    textStyle = LocalTextStyle.current.copy(color = MaterialTheme.colorScheme.onSurface, fontFamily = FontFamily.Monospace, fontSize = 12.sp),
                    modifier = Modifier.weight(1f),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary)
                )
            }
        }
    }
}

@Composable
private fun TerminalErrorState(message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier.size(48.dp).clip(CircleShape).background(MaterialTheme.colorScheme.errorContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Filled.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer)
        }
        Text(
            "Couldn't start a shell",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = 12.dp)
        )
        Text(
            message,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
        )
        Button(onClick = onRetry) { Text("Retry") }
    }
}
