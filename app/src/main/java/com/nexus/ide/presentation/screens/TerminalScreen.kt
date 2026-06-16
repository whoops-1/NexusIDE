package com.nexus.ide.presentation.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.nexus.ide.features.terminal.TerminalSession
import com.nexus.ide.features.terminal.TermuxBridge
import com.nexus.ide.presentation.components.TerminalView
import com.nexus.ide.presentation.viewmodels.ProjectViewModel

/**
 * Top-level terminal screen.
 *
 * - Detects Termux and prefers it when present.
 * - Otherwise spawns a local session backed by the in-app runtime.
 * - Supports multiple tabs.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TerminalScreen(vm: ProjectViewModel) {
    val termuxAvailable = remember { TermuxBridge.isAvailable() }
    val sessions = remember { mutableStateOf(listOf<TerminalSession>(TerminalSession.spawnLocal())) }
    var active by remember { mutableStateOf(0) }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        TopAppBar(
            title = { Text("Terminal · ${if (termuxAvailable) "Termux" else "Local"}") },
            actions = {
                IconButton(onClick = {
                    sessions.value = sessions.value + TerminalSession.spawnLocal()
                    active = sessions.value.lastIndex
                }) { Icon(Icons.Default.Add, contentDescription = "New tab") }
            }
        )

        if (sessions.value.size > 1) {
            TabRow(selectedTabIndex = active) {
                sessions.value.forEachIndexed { i, s ->
                    Tab(
                        selected = i == active,
                        onClick = { active = i; sessions.value = sessions.value.toMutableList() },
                        text = { Text("tab ${i + 1}") }
                    )
                }
            }
        }

        Box(Modifier.fillMaxSize()) {
            val s = sessions.value.getOrNull(active) ?: return@Box
            TerminalView(session = s, modifier = Modifier.fillMaxSize())
        }
    }
}
