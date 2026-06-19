package com.nexus.ide.presentation.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.NoteAdd
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nexus.ide.features.filesystem.WorkspaceService
import kotlinx.coroutines.launch
import java.io.File

/**
 * Single-pane file explorer with inline create / rename / delete, and
 * a breadcrumb bar so the user always knows where they are.
 *
 * [onOpenFile] is called when the user taps a file entry to open it in the editor.
 * [onOpenFolder] — if non-null — shows an "Open device folder" button at the top
 * that invokes the SAF folder picker (caller hosts the ActivityResultLauncher).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FileExplorer(
    workspace: WorkspaceService,
    onOpenFile: (File) -> Unit,
    onOpenFolder: (() -> Unit)? = null
) {
    val scope = rememberCoroutineScope()
    var currentDir by remember { mutableStateOf(workspace.workspaceRoot) }
    var entries by remember(currentDir) { mutableStateOf(workspace.listDirectory(currentDir)) }
    val expanded = remember { mutableStateMapOf<String, Boolean>() }

    // Dialog state — one enum-based slot is cleaner than 5 nullable booleans
    var dialog by remember { mutableStateOf<ExplorerDialog>(ExplorerDialog.None) }
    var selectedEntry by remember { mutableStateOf<File?>(null) }
    var menuTarget by remember { mutableStateOf<File?>(null) }

    fun refresh() { entries = workspace.listDirectory(currentDir) }

    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {

        // ── Toolbar ──────────────────────────────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Up one level
            IconButton(
                onClick = {
                    val parent = currentDir.parentFile
                    if (parent != null && currentDir != workspace.workspaceRoot) {
                        currentDir = parent
                    }
                },
                enabled = currentDir != workspace.workspaceRoot
            ) {
                Icon(Icons.Filled.ArrowUpward, contentDescription = "Up", modifier = Modifier.size(18.dp))
            }
            // Breadcrumb
            Text(
                text = currentDir.relativeTo(workspace.workspaceRoot).path.ifEmpty { "/" },
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(horizontal = 4.dp)
            )
            // New file
            IconButton(onClick = { dialog = ExplorerDialog.NewFile }) {
                Icon(Icons.Filled.NoteAdd, contentDescription = "New file", modifier = Modifier.size(18.dp))
            }
            // New folder
            IconButton(onClick = { dialog = ExplorerDialog.NewFolder }) {
                Icon(Icons.Filled.CreateNewFolder, contentDescription = "New folder", modifier = Modifier.size(18.dp))
            }
            // Open device folder (SAF)
            if (onOpenFolder != null) {
                IconButton(onClick = onOpenFolder) {
                    Icon(Icons.Filled.OpenInNew, contentDescription = "Open device folder", modifier = Modifier.size(18.dp))
                }
            }
        }
        HorizontalDivider(thickness = 0.5.dp)

        // ── File list ─────────────────────────────────────────────────────────
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(entries, key = { it.file.absolutePath }) { entry ->
                val isDir = entry.file.isDirectory
                val isSelected = selectedEntry == entry.file
                var showMenu by remember { mutableStateOf(false) }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                            else Color.Transparent
                        )
                        .combinedClickable(
                            onClick = {
                                selectedEntry = entry.file
                                if (isDir) {
                                    currentDir = entry.file
                                } else {
                                    onOpenFile(entry.file)
                                }
                            },
                            onLongClick = {
                                selectedEntry = entry.file
                                showMenu = true
                            }
                        )
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = when {
                            isDir && expanded[entry.file.absolutePath] == true -> Icons.Filled.FolderOpen
                            isDir -> Icons.Filled.Folder
                            entry.file.extension in codeExtensions -> Icons.Filled.Code
                            else -> Icons.Filled.Description
                        },
                        contentDescription = null,
                        tint = if (isDir) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            entry.file.name,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            fontWeight = if (isDir) FontWeight.Medium else FontWeight.Normal
                        )
                        if (!isDir) {
                            Text(
                                "${entry.sizeLabel}  ${entry.modifiedLabel}",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    // Context menu icon
                    Box {
                        IconButton(onClick = { selectedEntry = entry.file; showMenu = true }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Filled.MoreVert, contentDescription = "Options", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                            if (!isDir) {
                                DropdownMenuItem(
                                    text = { Text("Open") },
                                    leadingIcon = { Icon(Icons.Filled.Code, contentDescription = null, modifier = Modifier.size(16.dp)) },
                                    onClick = { showMenu = false; onOpenFile(entry.file) }
                                )
                            }
                            DropdownMenuItem(
                                text = { Text("Rename") },
                                leadingIcon = { Icon(Icons.Filled.DriveFileRenameOutline, contentDescription = null, modifier = Modifier.size(16.dp)) },
                                onClick = { showMenu = false; menuTarget = entry.file; dialog = ExplorerDialog.Rename }
                            )
                            DropdownMenuItem(
                                text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                                leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.error) },
                                onClick = { showMenu = false; menuTarget = entry.file; dialog = ExplorerDialog.ConfirmDelete }
                            )
                        }
                    }
                }
                HorizontalDivider(thickness = 0.3.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            }
        }
    }

    // ── Dialogs ───────────────────────────────────────────────────────────────
    when (val d = dialog) {
        ExplorerDialog.NewFile -> InputDialog(
            title = "New file",
            label = "File name",
            placeholder = "main.kt",
            onConfirm = { name ->
                if (name.isNotBlank()) {
                    scope.launch {
                        val f = File(currentDir, name)
                        f.parentFile?.mkdirs()
                        if (!f.exists()) f.createNewFile()
                        refresh()
                    }
                }
                dialog = ExplorerDialog.None
            },
            onDismiss = { dialog = ExplorerDialog.None }
        )

        ExplorerDialog.NewFolder -> InputDialog(
            title = "New folder",
            label = "Folder name",
            placeholder = "src",
            onConfirm = { name ->
                if (name.isNotBlank()) {
                    scope.launch {
                        File(currentDir, name).mkdirs()
                        refresh()
                    }
                }
                dialog = ExplorerDialog.None
            },
            onDismiss = { dialog = ExplorerDialog.None }
        )

        ExplorerDialog.Rename -> InputDialog(
            title = "Rename",
            label = "New name",
            initial = menuTarget?.name ?: "",
            onConfirm = { newName ->
                if (newName.isNotBlank()) {
                    scope.launch {
                        menuTarget?.let { f ->
                            workspace.rename(f, newName)
                            refresh()
                        }
                    }
                }
                dialog = ExplorerDialog.None
            },
            onDismiss = { dialog = ExplorerDialog.None }
        )

        ExplorerDialog.ConfirmDelete -> {
            val target = menuTarget
            AlertDialog(
                onDismissRequest = { dialog = ExplorerDialog.None },
                title = { Text("Delete ${target?.name}?") },
                text = {
                    Text(
                        if (target?.isDirectory == true)
                            "This will permanently delete the folder and all its contents."
                        else
                            "This file will be permanently deleted."
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        scope.launch {
                            target?.let { workspace.delete(it) }
                            refresh()
                        }
                        dialog = ExplorerDialog.None
                    }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
                },
                dismissButton = {
                    TextButton(onClick = { dialog = ExplorerDialog.None }) { Text("Cancel") }
                }
            )
        }

        ExplorerDialog.None -> Unit
    }
}

private sealed interface ExplorerDialog {
    data object None : ExplorerDialog
    data object NewFile : ExplorerDialog
    data object NewFolder : ExplorerDialog
    data object Rename : ExplorerDialog
    data object ConfirmDelete : ExplorerDialog
}

private val codeExtensions = setOf(
    "kt", "java", "py", "js", "ts", "jsx", "tsx", "rs", "go", "cpp", "c", "h",
    "cs", "rb", "php", "swift", "sh", "bash", "zsh", "html", "css", "scss",
    "json", "yaml", "yml", "toml", "xml", "md", "sql", "gradle", "kts"
)

@Composable
private fun InputDialog(
    title: String,
    label: String,
    placeholder: String = "",
    initial: String = "",
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text(label) },
                placeholder = { Text(placeholder) },
                singleLine = true
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(text.trim()) }, enabled = text.isNotBlank()) {
                Text("OK")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
