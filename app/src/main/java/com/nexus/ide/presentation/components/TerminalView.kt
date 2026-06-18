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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nexus.ide.features.terminal.TerminalHost
import com.nexus.ide.features.terminal.TerminalSession
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Tabbed terminal view. Each tab is a [TerminalSession]; the toolbar shows
 * tabs with a "+" to spawn a new shell. Lines stream in as the host
 * process produces them.
 *
 * Uses MaterialTheme.colorScheme rather than the editor's syntax-color
 * palette - this is generic terminal chrome, not the code surface.
 */
@Composable
fun TerminalView(
    host: TerminalHost,
    modifier: Modifier = Modifier
) {
    val sessions = remember { mutableStateListOf<TerminalSession>() }
    val activeId by host.active.collectAsState()
    var input by remember { mutableStateOf(TextFieldValue("")) }
    val scope = rememberCoroutineScope()
    val scroll = rememberScrollState()

    LaunchedEffect(Unit) {
        if (sessions.isEmpty()) {
            val first = host.newLocal(java.io.File(System.getProperty("user.home") ?: "/"), "sh")
            sessions.add(first)
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
                        modifier = Modifier.padding(end = 2.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                s.title,
                                color = if (sel) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                                fontSize = 12.sp
                            )
                            IconButton(onClick = { host.close(s.id); sessions.remove(s) }, modifier = Modifier.height(24.dp)) {
                                Icon(Icons.Filled.Close, contentDescription = "Close", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
                IconButton(onClick = {
                    val next = host.newLocal(java.io.File(System.getProperty("user.home") ?: "/"), "sh")
                    sessions.add(next)
                    host.setActive(next.id)
                }) {
                    Icon(Icons.Filled.Add, contentDescription = "New", tint = MaterialTheme.colorScheme.onSurface)
                }
            }
        }
        // Output
        Box(modifier = Modifier.weight(1f).fillMaxWidth().padding(8.dp).verticalScroll(scroll)) {
            if (active != null) {
                var buffered by remember(active.id) { mutableStateOf(StringBuilder()) }
                LaunchedEffect(active.id) {
                    active.output.collectLatest { chunk ->
                        buffered.append(chunk)
                        scroll.scrollTo(scroll.maxValue)
                    }
                }
                Text(
                    text = buildAnnotatedString {
                        val text = buffered.toString()
                        withStyle(SpanStyle(color = MaterialTheme.colorScheme.onSurface)) { append(text) }
                    },
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    style = TextStyle(color = MaterialTheme.colorScheme.onSurface)
                )
            }
        }
        // Input row
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("$", color = MaterialTheme.colorScheme.primary, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
            BasicTextField(
                value = input,
                onValueChange = { input = it },
                textStyle = LocalTextStyle.current.copy(color = MaterialTheme.colorScheme.onSurface, fontFamily = FontFamily.Monospace, fontSize = 12.sp),
                modifier = Modifier.weight(1f),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary)
            )
            // Pressing enter submits the line
            LaunchedEffect(input) {
                if (input.text.endsWith("\n")) {
                    val line = input.text.dropLast(1)
                    if (active != null) scope.launch { active.write(line + "\n") }
                    input = TextFieldValue("")
                }
            }
        }
    }
}
