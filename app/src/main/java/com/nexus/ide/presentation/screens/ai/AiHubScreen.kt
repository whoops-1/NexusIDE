package com.nexus.ide.presentation.screens.ai

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nexus.ide.presentation.screens.agent.AgentScreen
import com.nexus.ide.presentation.viewmodels.ProjectViewModel

/**
 * Hosts both AI surfaces under a single bottom-nav tab.
 *
 * Rationale: the bottom nav already carries 7 destinations, which is at
 * the edge of what Material guidance considers usable on a phone width.
 * Chat (quick Q&A, no tool access) and Agent (autonomous, tool-calling)
 * are different depths of the same feature, not different features —
 * this mirrors how Cursor and similar tools switch modes within one
 * panel rather than adding a destination per mode.
 */
private enum class AiMode { Chat, Agent }

@Composable
fun AiHubScreen(vm: ProjectViewModel) {
    var mode by rememberSaveable { mutableStateOf(AiMode.Agent) }

    Column(modifier = Modifier.fillMaxSize()) {
        ModeSwitcher(
            mode = mode,
            onModeChange = { mode = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        )
        Column(modifier = Modifier.fillMaxSize()) {
            when (mode) {
                AiMode.Chat -> AiChatScreen(vm)
                AiMode.Agent -> AgentScreen(vm)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModeSwitcher(
    mode: AiMode,
    onModeChange: (AiMode) -> Unit,
    modifier: Modifier = Modifier
) {
    SingleChoiceSegmentedButtonRow(modifier = modifier) {
        SegmentedButton(
            selected = mode == AiMode.Chat,
            onClick = { onModeChange(AiMode.Chat) },
            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
            icon = { Icon(Icons.Filled.Chat, contentDescription = null) }
        ) {
            Text("Chat")
        }
        SegmentedButton(
            selected = mode == AiMode.Agent,
            onClick = { onModeChange(AiMode.Agent) },
            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
            icon = { Icon(Icons.Filled.AutoAwesome, contentDescription = null) }
        ) {
            Text("Agent")
        }
    }
}
