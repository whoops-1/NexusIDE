package com.nexus.ide.presentation.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nexus.ide.presentation.components.EditorView
import com.nexus.ide.presentation.components.FileExplorer
import com.nexus.ide.presentation.components.TerminalView
import com.nexus.ide.presentation.viewmodels.ProjectViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(vm: ProjectViewModel) {
    val openFiles by vm.openFiles.collectAsState()
    val activeIndex by vm.activeIndex.collectAsState()
    val activeFile by vm.activeFile.collectAsState()
    var showExplorer by remember { mutableStateOf(true) }
    var showTerminal by remember { mutableStateOf(true) }
    var explorerWidth by remember { mutableStateOf(0.3f) }
    var terminalHeight by remember { mutableStateOf(0.3f) }

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
                    FileExplorer(onFileOpened = { vm.openFile(it) })
                }
                VerticalDivider()
            }
            Column(modifier = Modifier.weight(1f)) {
                Box(modifier = Modifier.fillMaxWidth().weight(1f - terminalHeight)) {
                    if (activeFile != null) {
                        val session = vm.sessionFor(activeFile!!.file)
                        EditorView(session = session, modifier = Modifier.fillMaxSize())
                    } else {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("Open a file to start editing", style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
                if (showTerminal) {
                    HorizontalDivider()
                    Box(modifier = Modifier.fillMaxWidth().fillMaxHeight(terminalHeight)) {
                        TerminalView(modifier = Modifier.fillMaxSize())
                    }
                }
            }
        }
    }
}
