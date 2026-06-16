package com.nexus.ide.presentation.screens.git

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CallMerge
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nexus.ide.core.di.ServiceLocator
import com.nexus.ide.features.git.GitRepository
import com.nexus.ide.features.git.GitService
import com.nexus.ide.presentation.components.NexusTopBar
import com.nexus.ide.presentation.viewmodels.ProjectViewModel

/**
 * Git integration screen — status, commit, push, pull.
 *
 * GitRepository is constructed from the workspace root. Operations are
 * synchronous shell-outs; for a mobile IDE this is acceptable on a
 * background coroutine (TODO: move to IO dispatcher).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GitScreen(vm: ProjectViewModel) {
    val workspaceRoot = remember { ServiceLocator.workspace.workspaceRoot }
    val repo = remember { GitRepository(workspaceRoot) }

    val status by produceState<List<GitRepository.StatusEntry>>(emptyList()) {
        value = repo.parseStatus()
    }
    val branch by produceState<String?>(null) {
        value = repo.currentBranch()
    }

    var subject by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }
    var refreshKey by remember { mutableStateOf(0) }

    Scaffold(
        topBar = {
            NexusTopBar(
                title = "Source Control",
                subtitle = branch ?: "(no git repo)",
                actions = {
                    IconButton(onClick = { refreshKey++ }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Refresh")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Toolbar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilledTonalButton(onClick = { repo.service.addAll() }) { Text("Stage all") }
                FilledTonalButton(onClick = { /* git reset HEAD -- unstage all */ }) { Text("Unstage") }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = { repo.service.fetch() }) {
                    Icon(Icons.Filled.History, contentDescription = "Fetch")
                }
                IconButton(onClick = { repo.service.pull() }) {
                    Icon(Icons.Filled.CallMerge, contentDescription = "Pull")
                }
                IconButton(onClick = { repo.service.push() }) {
                    Icon(Icons.Filled.CloudUpload, contentDescription = "Push")
                }
            }
            HorizontalDivider()

            // Changed files
            if (status.isEmpty()) {
                Text(
                    "Working tree clean",
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(16.dp)
                )
            } else {
                LazyColumn {
                    items(status) { entry ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "${entry.indexStatus}${entry.workTreeStatus}",
                                color = MaterialTheme.colorScheme.primary,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.width(28.dp)
                            )
                            Text(
                                text = entry.path,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }

            HorizontalDivider()

            // Commit box
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                OutlinedTextField(
                    value = subject,
                    onValueChange = { subject = it },
                    label = { Text("Subject") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = body,
                    onValueChange = { body = it },
                    label = { Text("Description (optional)") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    maxLines = 6
                )
                Spacer(Modifier.height(8.dp))
                FilledTonalButton(
                    onClick = {
                        val msg = if (body.isBlank()) subject else "$subject\n\n$body"
                        repo.service.commit(msg)
                        subject = ""; body = ""
                        refreshKey++
                    },
                    enabled = subject.isNotBlank() && status.any { it.isStaged }
                ) {
                    Text("Commit")
                }
            }
        }
    }
}
