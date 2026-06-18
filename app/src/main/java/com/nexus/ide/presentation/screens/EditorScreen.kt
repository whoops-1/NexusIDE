package com.nexus.ide.presentation.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nexus.ide.core.di.ServiceLocator
import com.nexus.ide.features.terminal.TerminalHost
import com.nexus.ide.presentation.components.EditorView
import com.nexus.ide.presentation.components.FileExplorer
import com.nexus.ide.presentation.components.TerminalView
import com.nexus.ide.presentation.screens.ai.AiHubScreen
import com.nexus.ide.presentation.screens.settings.SettingsScreen
import com.nexus.ide.presentation.theme.LocalEditorTheme
import com.nexus.ide.presentation.viewmodels.ProjectViewModel

/** Which docked/overlay panel is currently open. Only one overlay panel at a time on phone width. */
private enum class OverlayPanel { Explorer, Ai, Settings }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(vm: ProjectViewModel) {
    val openFiles by vm.openFiles.collectAsState()
    val activeIndex by vm.activeIndex.collectAsState()
    val activeFile by vm.activeFile.collectAsState()
    val editorTheme = LocalEditorTheme.current

    // Mobile-first default: every panel starts CLOSED so the code surface
    // gets the full screen. Each is opened explicitly and independently.
    var overlayPanel by rememberSaveable { mutableStateOf<OverlayPanel?>(null) }
    var showTerminal by rememberSaveable { mutableStateOf(false) }
    var aiFullscreen by rememberSaveable { mutableStateOf(false) }
    var terminalHeightFraction by remember { mutableStateOf(0.42f) }

    val terminalHost = remember { TerminalHost(termux = ServiceLocator.termux, onSessionClosed = {}) }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            TopAppBar(
                title = { Text(activeFile?.displayName ?: "NexusIDE", style = MaterialTheme.typography.titleMedium) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
                actions = {
                    IconButton(onClick = { overlayPanel = if (overlayPanel == OverlayPanel.Explorer) null else OverlayPanel.Explorer }) {
                        Icon(Icons.Default.Folder, contentDescription = "Toggle file explorer", tint = if (overlayPanel == OverlayPanel.Explorer) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                    }
                    IconButton(onClick = { showTerminal = !showTerminal }) {
                        Icon(Icons.Default.Terminal, contentDescription = "Toggle terminal", tint = if (showTerminal) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                    }
                    IconButton(onClick = { overlayPanel = if (overlayPanel == OverlayPanel.Ai) null else OverlayPanel.Ai }) {
                        Icon(Icons.Default.SmartToy, contentDescription = "Toggle AI assistant", tint = if (overlayPanel == OverlayPanel.Ai) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                    }
                    IconButton(onClick = { overlayPanel = if (overlayPanel == OverlayPanel.Settings) null else OverlayPanel.Settings }) {
                        Icon(Icons.Default.Settings, contentDescription = "Toggle settings", tint = if (overlayPanel == OverlayPanel.Settings) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                    }
                    IconButton(onClick = { vm.saveActive() }, enabled = activeFile != null) {
                        Icon(Icons.Default.Save, contentDescription = "Save")
                    }
                }
            )

            if (openFiles.isNotEmpty()) {
                ScrollableTabRow(
                    selectedTabIndex = activeIndex.coerceAtLeast(0),
                    edgePadding = 0.dp,
                    containerColor = MaterialTheme.colorScheme.surface
                ) {
                    openFiles.forEachIndexed { idx, file ->
                        Tab(
                            selected = idx == activeIndex,
                            onClick = { vm.setActive(idx) },
                            text = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(file.displayName, maxLines = 1)
                                    if (file.isDirty) {
                                        Box(
                                            Modifier
                                                .padding(start = 4.dp)
                                                .size(6.dp)
                                                .background(MaterialTheme.colorScheme.primary, shape = androidx.compose.foundation.shape.CircleShape)
                                        )
                                    }
                                    IconButton(onClick = { vm.closeFile(idx) }, modifier = Modifier.size(20.dp).padding(start = 6.dp)) {
                                        Icon(Icons.Default.Close, contentDescription = "Close ${file.displayName}", modifier = Modifier.size(14.dp))
                                    }
                                }
                            }
                        )
                    }
                }
                HorizontalDivider(thickness = 0.5.dp)
            }

            Column(modifier = Modifier.fillMaxSize().weight(1f)) {
                Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                    if (activeFile != null) {
                        val session = vm.sessionFor(activeFile!!.file)
                        EditorView(session = session, theme = editorTheme, modifier = Modifier.fillMaxSize())
                    } else {
                        EmptyEditorState(onOpenExplorer = { overlayPanel = OverlayPanel.Explorer })
                    }
                }
                if (showTerminal) {
                    HorizontalDivider(thickness = 0.5.dp)
                    Box(modifier = Modifier.fillMaxWidth().fillMaxHeight(terminalHeightFraction)) {
                        TerminalView(
                            host = terminalHost,
                            workingDir = ServiceLocator.workspace.workspaceRoot,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }

        // --- Overlay: File Explorer (slide in from the left) ---
        AnimatedVisibility(
            visible = overlayPanel == OverlayPanel.Explorer,
            enter = fadeIn(tween(150)),
            exit = fadeOut(tween(150)),
            modifier = Modifier.fillMaxSize()
        ) {
            Scrim(onDismiss = { overlayPanel = null })
        }
        AnimatedVisibility(
            visible = overlayPanel == OverlayPanel.Explorer,
            enter = slideInHorizontally(tween(200)) { -it },
            exit = slideOutHorizontally(tween(200)) { -it },
            modifier = Modifier.fillMaxHeight().fillMaxWidth(0.86f).align(Alignment.CenterStart)
        ) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 8.dp
            ) {
                Column {
                    PanelHeader(title = "Explorer", onClose = { overlayPanel = null })
                    FileExplorer(
                        workspace = ServiceLocator.workspace,
                        onOpenFile = { file -> vm.openFile(file); overlayPanel = null }
                    )
                }
            }
        }

        // --- Overlay: AI Assistant (slide in from the right, expandable to fullscreen) ---
        AnimatedVisibility(
            visible = overlayPanel == OverlayPanel.Ai,
            enter = fadeIn(tween(150)),
            exit = fadeOut(tween(150)),
            modifier = Modifier.fillMaxSize()
        ) {
            Scrim(onDismiss = { overlayPanel = null; aiFullscreen = false })
        }
        AnimatedVisibility(
            visible = overlayPanel == OverlayPanel.Ai,
            enter = slideInHorizontally(tween(200)) { it },
            exit = slideOutHorizontally(tween(200)) { it },
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(if (aiFullscreen) 1f else 0.92f)
                .align(Alignment.CenterEnd)
        ) {
            Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface, shadowElevation = 8.dp) {
                Column {
                    PanelHeader(
                        title = "AI Assistant",
                        onClose = { overlayPanel = null; aiFullscreen = false },
                        onToggleFullscreen = { aiFullscreen = !aiFullscreen },
                        isFullscreen = aiFullscreen
                    )
                    AiHubScreen(vm)
                }
            }
        }

        // --- Overlay: Settings (full screen, since it's content-dense) ---
        AnimatedVisibility(
            visible = overlayPanel == OverlayPanel.Settings,
            enter = fadeIn(tween(150)) + slideInHorizontally(tween(200)) { it },
            exit = fadeOut(tween(150)) + slideOutHorizontally(tween(200)) { it },
            modifier = Modifier.fillMaxSize()
        ) {
            Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
                Column {
                    PanelHeader(title = "Settings", onClose = { overlayPanel = null })
                    SettingsScreen(vm)
                }
            }
        }
    }
}

@Composable
private fun Scrim(onDismiss: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.4f))
            .clickable(onClick = onDismiss)
    )
}

@Composable
private fun PanelHeader(
    title: String,
    onClose: () -> Unit,
    onToggleFullscreen: (() -> Unit)? = null,
    isFullscreen: Boolean = false
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        if (onToggleFullscreen != null) {
            IconButton(onClick = onToggleFullscreen) {
                Icon(
                    if (isFullscreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                    contentDescription = if (isFullscreen) "Exit fullscreen" else "Fullscreen"
                )
            }
        }
        IconButton(onClick = onClose) {
            Icon(Icons.Default.Close, contentDescription = "Close $title")
        }
    }
    HorizontalDivider(thickness = 0.5.dp)
}

@Composable
private fun EmptyEditorState(onOpenExplorer: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Default.Folder,
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                "Open a file to start editing",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(top = 12.dp, bottom = 12.dp)
            )
            Text(
                "Tap the folder icon above to browse the workspace",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.clickable(onClick = onOpenExplorer)
            )
        }
    }
}
