package com.nexus.ide.presentation.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.weight
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.nexus.ide.core.di.ServiceLocator
import com.nexus.ide.features.terminal.TerminalHost
import com.nexus.ide.presentation.components.EditorView
import com.nexus.ide.presentation.components.FileExplorer
import com.nexus.ide.presentation.components.TerminalView
import com.nexus.ide.presentation.theme.LocalEditorTheme
import com.nexus.ide.presentation.viewmodels.ProjectViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(vm: ProjectViewModel) {
    val openFiles by vm.openFiles.collectAsState()
    val activeIndex by vm.activeIndex.collectAsState()
    val activeFile by vm.activeFile.collectAsState()
    val editorTheme = LocalEditorTheme.current
    var showExplorer by remember { mutableStateOf(true) }
    var showTerminal by remember { mutableStateOf(true) }
    var explorerWidth by remember { mutableStateOf(0.3f) }
    var terminalHeight by remember { mutableStateOf(0.3f) }
    val terminalHost = remember { TerminalHost(termux = ServiceLocator.termux, onSessionClosed = {}) }

    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        TopAppBar(
            title = { Text(activeFile?.displayName ?: "NexusIDE") },
            actions = {
                IconButton(onClick = { showExplorer = !showExplorer }) {
                    Icon(Icons.Default.Folder, contentDescription = "Explorer")
                }
                IconButton(onClick = { vm.saveActive() }) {
                    Icon(Icons.Default.Save, contentDescription = "Save")
                }
                IconButton(onClick = { showTerminal = !showTerminal }) {
                    Icon(Icons.Default.Terminal, contentDescription = "Terminal")
                }
            }
        )
        if (openFiles.isNotEmpty()) {
            ScrollableTabRow(selectedTabIndex = activeIndex.coerceAtLeast(0)) {
                openFiles.forEachIndexed { idx, file ->
                    Tab(
                        selected = idx == activeIndex,
                        onClick = { vm.setActive(idx) },
                        text = { Text(file.displayName) }
                    )
                }
            }
        }
        Row(modifier = Modifier.fillMaxSize().weight(1f)) {
            if (showExplorer) {
                Box(modifier = Modifier.fillMaxWidth(explorerWidth).fillMaxHeight()) {
                    FileExplorer(workspace = ServiceLocator.workspace, onOpenFile = { vm.openFile(it) })
                }
                VerticalDivider()
            }
            Column(modifier = Modifier.weight(1f)) {
                Box(modifier = Modifier.fillMaxWidth().weight(1f - terminalHeight)) {
                    if (activeFile != null) {
                        val session = vm.sessionFor(activeFile!!.file)
                        EditorView(session = session, theme = editorTheme, modifier = Modifier.fillMaxSize())
                    } else {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("Open a file to start editing", style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
                if (showTerminal) {
                    HorizontalDivider()
                    Box(modifier = Modifier.fillMaxWidth().fillMaxHeight(terminalHeight)) {
                        TerminalView(host = terminalHost, modifier = Modifier.fillMaxSize())
                    }
                }
            }
        }
    }
}
