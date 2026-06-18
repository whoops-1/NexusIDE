package com.nexus.ide.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nexus.ide.features.filesystem.WorkspaceService
import java.io.File

/**
 * Two-pane file explorer: directory tree on the left, file metadata on
 * the right. Tapping a file dispatches an "open in editor" event the
 * shell handles.
 *
 * Uses MaterialTheme.colorScheme rather than the editor's syntax-color
 * palette - this is generic app chrome, not the code surface, and
 * stays visually consistent with every other screen in the app.
 */
@Composable
fun FileExplorer(workspace: WorkspaceService, onOpenFile: (File) -> Unit) {
    var currentPath by remember { mutableStateOf(workspace.workspaceRoot) }
    var entries by remember { mutableStateOf(workspace.listDirectory(currentPath)) }
    var selected by remember { mutableStateOf<File?>(null) }

    Row(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant)) {
        // Tree pane
        Column(modifier = Modifier.weight(0.45f).fillMaxHeight().padding(8.dp)) {
            Text(
                "WORKSPACE",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(8.dp)
            )
            TreeNode(workspace, workspace.workspaceRoot, 0, currentPath, onClick = {
                currentPath = it
                entries = workspace.listDirectory(it)
            }, onOpen = onOpenFile)
        }
        HorizontalDivider(
            modifier = Modifier.width(1.dp).fillMaxHeight(),
            color = MaterialTheme.colorScheme.outlineVariant
        )
        // Listing pane
        Column(modifier = Modifier.weight(0.55f).fillMaxHeight().padding(8.dp)) {
            Text(
                currentPath.absolutePath,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 10.sp,
                modifier = Modifier.padding(8.dp)
            )
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(entries) { entry ->
                    FileRow(entry, isSelected = selected == entry.file, onClick = {
                        selected = entry.file
                        if (entry.file.isDirectory) {
                            currentPath = entry.file
                            entries = workspace.listDirectory(entry.file)
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
    var expanded by remember { mutableStateOf(depth < 1) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick(dir); expanded = !expanded }
            .padding(start = (8 + depth * 12).dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (expanded) Icons.Filled.KeyboardArrowDown else Icons.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp)
        )
        Icon(
            imageVector = if (dir == current) Icons.Filled.FolderOpen else Icons.Filled.Folder,
            contentDescription = null,
            tint = if (dir == current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(16.dp).padding(start = 2.dp)
        )
        Spacer(Modifier.width(4.dp))
        Text(dir.name.ifEmpty { dir.absolutePath }, color = MaterialTheme.colorScheme.onSurface, fontSize = 12.sp)
    }
    if (expanded && dir.isDirectory) {
        workspace.listDirectory(dir).filter { it.file.isDirectory }.forEach { child ->
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
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (isSelected) MaterialTheme.colorScheme.surface else Color.Transparent)
            .clickable { onClick() }
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (entry.file.isDirectory) Icons.Filled.Folder else Icons.Filled.Description,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(16.dp)
        )
        Spacer(Modifier.width(6.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(entry.file.name, color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp)
            Text(
                "${entry.sizeLabel} • ${entry.modifiedLabel}",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 10.sp
            )
        }
        IconButton(onClick = onOpen) {
            Icon(
                Icons.Filled.Edit,
                contentDescription = "Open",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}
