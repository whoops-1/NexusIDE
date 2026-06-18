package com.nexus.ide.presentation.screens.ai

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nexus.ide.core.di.ServiceLocator
import com.nexus.ide.features.ai.AiChatViewModel
import com.nexus.ide.features.ai.AiEngine
import com.nexus.ide.features.ai.ChatMessage
import com.nexus.ide.features.ai.ChatRole
import com.nexus.ide.presentation.components.NexusTopBar
import com.nexus.ide.presentation.viewmodels.ProjectViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiChatScreen(projectVm: ProjectViewModel) {
    val store = remember { ServiceLocator.secureStore }
    val engine = remember { AiEngine(store) }
    val vm: AiChatViewModel = viewModel(factory = remember {
        object : androidx.lifecycle.ViewModelProvider.Factory {
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return AiChatViewModel(engine, store) as T
            }
        }
    })

    val messages by vm.messages.collectAsState()
    val busy by vm.busy.collectAsState()
    var input by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }

    Scaffold(
        topBar = {
            NexusTopBar(
                title = "AI Assistant",
                subtitle = ServiceLocator.settings.aiModel
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
        ) {
            QuickActions(
                onAction = { prompt -> vm.send(prompt) },
                enabled = !busy
            )
            HorizontalDivider()
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(messages) { msg -> ChatBubble(msg) }
            }
            Composer(
                value = input,
                onValueChange = { input = it },
                isStreaming = busy,
                onSend = { vm.send(input); input = "" },
                onStop = { /* AiChatViewModel.stop not yet implemented */ }
            )
        }
    }
}

@Composable
private fun QuickActions(onAction: (String) -> Unit, enabled: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        listOf(
            "Explain" to "Explain what this code does, line by line.",
            "Tests" to "Write unit tests for the selected code.",
            "Refactor" to "Suggest a refactor of the selected code.",
            "Document" to "Add doc comments to the selected code."
        ).forEach { (label, prompt) ->
            TextButton(onClick = { onAction(prompt) }, enabled = enabled) {
                Text(label)
            }
        }
    }
}

@Composable
private fun ChatBubble(msg: AiChatViewModel.Turn) {
    val isUser = msg.role == "user"
    val bubbleColor = if (isUser) MaterialTheme.colorScheme.primary
                      else MaterialTheme.colorScheme.surfaceVariant
    val textColor = if (isUser) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.onSurfaceVariant

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        if (!isUser) {
            Icon(
                Icons.Filled.SmartToy,
                contentDescription = null,
                modifier = Modifier
                    .size(28.dp)
                    .padding(top = 4.dp, end = 8.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        }
        Card(
            modifier = Modifier.widthIn(max = if (isUser) 280.dp else 420.dp),
            colors = CardDefaults.cardColors(containerColor = bubbleColor),
            shape = RoundedCornerShape(
                topStart = 12.dp, topEnd = 12.dp,
                bottomStart = if (isUser) 12.dp else 2.dp,
                bottomEnd = if (isUser) 2.dp else 12.dp
            )
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                com.nexus.ide.presentation.components.ChatRichText(text = msg.text, textColor = textColor)
                if (!isUser && msg.text.isBlank() && msg.streaming) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp
                    )
                }
            }
        }
        if (isUser) Spacer(Modifier.width(36.dp))
    }
}

@Composable
private fun Composer(
    value: String,
    onValueChange: (String) -> Unit,
    isStreaming: Boolean,
    onSend: () -> Unit,
    onStop: () -> Unit
) {
    Surface(tonalElevation = 2.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.weight(1f),
                placeholder = { Text("Ask Nexus…") },
                maxLines = 5
            )
            Spacer(Modifier.width(8.dp))
            FilledIconButton(onClick = if (isStreaming) onStop else onSend) {
                if (isStreaming) {
                    Icon(Icons.Filled.Stop, contentDescription = "Stop")
                } else {
                    Icon(Icons.Filled.Send, contentDescription = "Send")
                }
            }
        }
    }
}
