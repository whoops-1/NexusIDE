package com.nexus.ide.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nexus.ide.features.filesystem.WorkspaceService
import com.nexus.ide.presentation.navigation.LocalTabs
import com.nexus.ide.presentation.theme.NexusLocal
import java.io.File

/**
 * Two-pane file explorer: directory tree on the left, file metadata on
 * the right. Tapping a file dispatches an "open in editor" event the
 * shell handles.
 */
@Composable
fun FileExplorer(workspace: WorkspaceService, onOpenFile: (File) -> Unit) {
    val theme = NexusLocal.nexusTheme.current
    val tabs = LocalTabs.current
    var currentPath by remember { mutableStateOf(workspace.workspaceRoot) }
    var entries by remember { mutableStateOf(workspace.listDirectory(currentPath)) }
    var selected by remember { mutableStateOf<File?>(null) }

    Row(modifier = Modifier.fillMaxSize().background(theme.gutter.bg)) {
        // Tree pane
        Column(modifier = Modifier.weight(0.45f).fillMaxHeight().padding(8.dp)) {
            Text("WORKSPACE", color = theme.editor.muted, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(8.dp))
            TreeNode(workspace, workspace.workspaceRoot, 0, currentPath, onClick = {
                currentPath = it
                entries = workspace.list(it)
            }, onOpen = onOpenFile)
        }
        Divider(color = theme.gutter.line, modifier = Modifier.width(1.dp).fillMaxHeight())
        // Listing pane
        Column(modifier = Modifier.weight(0.55f).fillMaxHeight().padding(8.dp)) {
            Text(currentPath.absolutePath, color = theme.editor.muted, fontSize = 10.sp, modifier = Modifier.padding(8.dp))
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(entries) { entry ->
                    FileRow(entry, isSelected = selected == entry.file, onClick = {
                        selected = entry.file
                        if (entry.file.isDirectory) {
                            currentPath = entry.file
                            entries = workspace.list(entry.file)
                        }
                    }, onOpen = { onOpenFile(entry.file) })
                }
            }
        }
    }
}

@Composable
private fun TreeNode(
    workspace: WorkspaceService,
    dir: File,
    depth: Int,
    current: File,
    onClick: (File) -> Unit,
    onOpen: (File) -> Unit
) {
    val theme = NexusLocal.nexusTheme.current
    var expanded by remember { mutableStateOf(depth < 1) }
    Row(
        modifier = Modifier.fillMaxWidth().clickable { onClick(dir); expanded = !expanded }.padding(start = (8 + depth * 12).dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (expanded) Icons.Filled.KeyboardArrowDown else Icons.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = theme.editor.muted,
            modifier = Modifier.size(16.dp)
        )
        Icon(
            imageVector = if (dir == current) Icons.Filled.FolderOpen else Icons.Filled.Folder,
            contentDescription = null,
            tint = if (dir == current) theme.editor.accent else theme.editor.fg,
            modifier = Modifier.size(16.dp).padding(start = 2.dp)
        )
        Spacer(Modifier.width(4.dp))
        Text(dir.name.ifEmpty { dir.absolutePath }, color = theme.editor.fg, fontSize = 12.sp)
    }
    if (expanded && dir.isDirectory) {
        workspace.listDirectory(dir).filter { it.file.isDirectory && !it.file.isHidden }.forEach { child ->
            TreeNode(workspace, child.file, depth + 1, current, onClick, onOpen)
        }
    }
}

@Composable
private fun FileRow(
    entry: WorkspaceService.Entry,
    isSelected: Boolean,
    onClick: () -> Unit,
    onOpen: () -> Unit
) {
    val theme = NexusLocal.nexusTheme.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (isSelected) theme.gutter.line else androidx.compose.ui.graphics.Color.Transparent)
            .clickable { onClick() }
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (entry.file.isDirectory) Icons.Filled.Folder else Icons.Filled.Description,
            contentDescription = null,
            tint = theme.editor.fg,
            modifier = Modifier.size(16.dp)
        )
        Spacer(Modifier.width(6.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(entry.file.name, color = theme.editor.fg, fontSize = 13.sp)
            Text("${entry.sizeLabel} • ${entry.modifiedLabel}", color = theme.editor.muted, fontSize = 10.sp)
        }
        IconButton(onClick = onOpen) {
            Icon(Icons.Filled.Edit, contentDescription = "Open", tint = theme.editor.accent, modifier = Modifier.size(16.dp))
        }
    }
}


