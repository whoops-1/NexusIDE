package com.nexus.ide.features.ai

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexus.ide.core.di.ServiceLocator
import com.nexus.ide.data.local.prefs.SecureStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AiChatViewModel(
    private val engine: AiEngine,
    private val store: SecureStore,
    private val chatSession: ChatSession
) : ViewModel() {

    data class Turn(val role: String, val text: String, val streaming: Boolean = false)

    /** Currently displayed messages in the active conversation */
    private val _messages = MutableStateFlow<List<Turn>>(emptyList())
    val messages: StateFlow<List<Turn>> = _messages.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    /** Active conversation ID — changes when user starts a new session */
    private val _conversationId = MutableStateFlow(chatSession.newConversationId())
    val conversationId: StateFlow<String> = _conversationId.asStateFlow()

    /** All past conversation IDs from DB for the history panel */
    val conversations: StateFlow<List<String>> = chatSession.observeConversations()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Conversation search query */
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    init {
        // Load most recent conversation if any exists
        viewModelScope.launch {
            val convs = chatSession.observeConversations()
            convs.collect { list ->
                if (list.isNotEmpty() && _messages.value.isEmpty()) {
                    switchToConversation(list.first())
                    return@collect
                }
            }
        }
    }

    fun setSearchQuery(q: String) { _searchQuery.value = q }

    fun newConversation() {
        _conversationId.value = chatSession.newConversationId()
        _messages.value = emptyList()
    }

    fun switchToConversation(id: String) {
        _conversationId.value = id
        viewModelScope.launch {
            val msgs = chatSession.loadMessages(id)
            _messages.value = msgs.map { Turn(it.role, it.content) }
        }
    }

    fun deleteConversation(id: String) {
        viewModelScope.launch {
            chatSession.deleteConversation(id)
            if (id == _conversationId.value) newConversation()
        }
    }

    fun send(userText: String) {
        if (_busy.value || userText.isBlank()) return

        val cid = _conversationId.value
        val model = store.getAiModel() ?: ServiceLocator.settings.aiModel
        val base = store.getAiBaseUrl() ?: "https://api.openai.com/v1"

        val history = _messages.value + Turn("user", userText)
        _messages.value = history
        _busy.value = true

        viewModelScope.launch {
            // Persist user message
            chatSession.insert(cid, "user", userText, model)

            val apiMessages = (listOf(AiEngine.Message("system", AiPrompts.SYSTEM_ASSISTANT)) +
                history.map { AiEngine.Message(it.role, it.text) })

            val updatedHistory = history + Turn("assistant", "", streaming = true)
            _messages.value = updatedHistory
            val sb = StringBuilder()

            engine.stream(apiMessages, AiEngine.Options(model = model, baseUrl = base))
                .catch { e -> sb.append("[error] ${e.message}") }
                .collect { piece ->
                    sb.append(piece)
                    _messages.value = updatedHistory.mapIndexed { i, t ->
                        if (i == updatedHistory.lastIndex) t.copy(text = sb.toString(), streaming = true) else t
                    }
                }

            val finalText = sb.toString()
            _messages.value = updatedHistory.mapIndexed { i, t ->
                if (i == updatedHistory.lastIndex) t.copy(text = finalText, streaming = false) else t
            }

            // Persist assistant message
            chatSession.insert(cid, "assistant", finalText, model)
            _busy.value = false
        }
    }
}
