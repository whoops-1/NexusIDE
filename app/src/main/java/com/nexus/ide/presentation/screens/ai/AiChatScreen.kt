package com.nexus.ide.presentation.screens.ai

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nexus.ide.core.di.ServiceLocator
import com.nexus.ide.features.ai.AiChatViewModel
import com.nexus.ide.features.ai.AiEngine
import com.nexus.ide.presentation.components.ChatRichText
import com.nexus.ide.presentation.viewmodels.ProjectViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiChatScreen(projectVm: ProjectViewModel) {
    val store = remember { ServiceLocator.secureStore }
    val engine = remember { AiEngine(store) }
    val session = remember { ServiceLocator.chatSession }
    val vm: AiChatViewModel = viewModel(factory = remember {
        object : androidx.lifecycle.ViewModelProvider.Factory {
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return AiChatViewModel(engine, store, session) as T
            }
        }
    })

    val messages by vm.messages.collectAsState()
    val busy by vm.busy.collectAsState()
    val conversations by vm.conversations.collectAsState()
    val currentConvId by vm.conversationId.collectAsState()
    val searchQuery by vm.searchQuery.collectAsState()
    var input by remember { mutableStateOf("") }
    var showHistory by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Top bar
            Surface(tonalElevation = 1.dp) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { showHistory = !showHistory }) {
                        Icon(Icons.Default.History, contentDescription = "Chat history",
                            tint = if (showHistory) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                    }
                    Text(
                        "AI Chat",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f).padding(horizontal = 4.dp)
                    )
                    IconButton(onClick = { vm.newConversation() }) {
                        Icon(Icons.Default.Add, contentDescription = "New conversation")
                    }
                }
            }
            HorizontalDivider(thickness = 0.5.dp)

            // Quick-action chips
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf("Explain" to "Explain this code in detail.", "Tests" to "Write unit tests.", "Refactor" to "Suggest a refactor.", "Docs" to "Add doc comments.").forEach { (label, prompt) ->
                    AssistChip(onClick = { if (!busy) vm.send(prompt) }, label = { Text(label, fontSize = 11.sp) }, enabled = !busy)
                }
            }
            HorizontalDivider(thickness = 0.5.dp)

            // Messages
            if (messages.isEmpty()) {
                EmptyChatState(onSend = { vm.send(it) })
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(messages) { msg -> ChatBubble(msg) }
                }
            }

            // Composer
            ChatComposer(
                value = input,
                onValueChange = { input = it },
                isStreaming = busy,
                onSend = { if (input.isNotBlank()) { vm.send(input); input = "" } },
                onStop = {}
            )
        }

        // History drawer overlay
        AnimatedVisibility(
            visible = showHistory,
            enter = slideInHorizontally { -it },
            exit = slideOutHorizontally { -it },
            modifier = Modifier.fillMaxHeight().fillMaxWidth(0.82f).align(Alignment.CenterStart)
        ) {
            Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 8.dp) {
                Column(modifier = Modifier.fillMaxSize()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("History", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        IconButton(onClick = { showHistory = false }) {
                            Icon(Icons.Default.Close, contentDescription = "Close")
                        }
                    }
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { vm.setSearchQuery(it) },
                        placeholder = { Text("Search conversations…") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)
                    )
                    HorizontalDivider(modifier = Modifier.padding(top = 8.dp))
                    if (conversations.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("No saved conversations", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                        }
                    } else {
                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                            val filtered = if (searchQuery.isBlank()) conversations
                                else conversations.filter { it.contains(searchQuery, ignoreCase = true) }
                            items(filtered) { convId ->
                                ConversationRow(
                                    convId = convId,
                                    isActive = convId == currentConvId,
                                    onSelect = { vm.switchToConversation(convId); showHistory = false },
                                    onDelete = { vm.deleteConversation(convId) }
                                )
                            }
                        }
                    }
                }
            }
        }
        // Scrim behind drawer
        if (showHistory) {
            Box(modifier = Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.3f)).clickable { showHistory = false })
        }
    }
}

@Composable
private fun ConversationRow(convId: String, isActive: Boolean, onSelect: () -> Unit, onDelete: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (isActive) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f) else androidx.compose.ui.graphics.Color.Transparent)
            .clickable(onClick = onSelect)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Default.Chat, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(10.dp))
        Text(
            convId.take(18) + "…",
            modifier = Modifier.weight(1f),
            fontSize = 13.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal
        )
        IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Default.Delete, contentDescription = "Delete", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    HorizontalDivider(thickness = 0.3.dp)
}

@Composable
private fun EmptyChatState(onSend: (String) -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier.size(56.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.SmartToy, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(28.dp))
        }
        Spacer(Modifier.height(16.dp))
        Text("Ask Nexus AI", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(4.dp))
        Text("Explain code, generate tests, refactor, or ask anything about your project.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        Spacer(Modifier.height(20.dp))
        listOf("Explain how coroutines work in Kotlin", "Write a unit test for my ViewModel", "What's the best way to handle state in Compose?").forEach { suggestion ->
            SuggestionChip(
                onClick = { onSend(suggestion) },
                label = { Text(suggestion, fontSize = 12.sp) },
                modifier = Modifier.padding(vertical = 3.dp)
            )
        }
    }
}

@Composable
private fun ChatBubble(msg: AiChatViewModel.Turn) {
    val isUser = msg.role == "user"
    val bubbleColor = if (isUser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
    val textColor = if (isUser) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Bottom
    ) {
        if (!isUser) {
            Box(modifier = Modifier.size(28.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.SmartToy, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(16.dp))
            }
            Spacer(Modifier.width(8.dp))
        }
        Card(
            modifier = Modifier.widthIn(max = 320.dp),
            colors = CardDefaults.cardColors(containerColor = bubbleColor),
            shape = RoundedCornerShape(
                topStart = 16.dp, topEnd = 16.dp,
                bottomStart = if (isUser) 16.dp else 4.dp,
                bottomEnd = if (isUser) 4.dp else 16.dp
            )
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                if (msg.text.isBlank() && msg.streaming) {
                    TypingIndicator()
                } else {
                    ChatRichText(text = msg.text, textColor = textColor)
                }
            }
        }
        if (isUser) Spacer(Modifier.width(8.dp))
    }
}

@Composable
private fun TypingIndicator() {
    val infiniteTransition = androidx.compose.animation.core.rememberInfiniteTransition(label = "typing")
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(4.dp)) {
        repeat(3) { i ->
            val alpha by infiniteTransition.animateFloat(
                initialValue = 0.3f, targetValue = 1f, label = "dot$i",
                animationSpec = androidx.compose.animation.core.infiniteRepeatable(
                    animation = androidx.compose.animation.core.tween(600, delayMillis = i * 150),
                    repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
                )
            )
            Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha)))
        }
    }
}

@Composable
private fun ChatComposer(value: String, onValueChange: (String) -> Unit, isStreaming: Boolean, onSend: () -> Unit, onStop: () -> Unit) {
    Surface(tonalElevation = 2.dp) {
        Row(
            modifier = Modifier.fillMaxWidth().imePadding().navigationBarsPadding().padding(8.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.weight(1f),
                placeholder = { Text("Ask Nexus…", fontSize = 14.sp) },
                maxLines = 6,
                shape = RoundedCornerShape(20.dp)
            )
            FilledIconButton(
                onClick = if (isStreaming) onStop else onSend,
                modifier = Modifier.size(44.dp)
            ) {
                Icon(
                    if (isStreaming) Icons.Default.Stop else Icons.Default.Send,
                    contentDescription = if (isStreaming) "Stop" else "Send",
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
