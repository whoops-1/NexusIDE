package com.nexus.ide.features.ai

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexus.ide.data.local.prefs.SecureStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

/**
 * Holds the conversation with the AI assistant and exposes a stream
 * of assistant messages to the UI.
 */
class AiChatViewModel(private val engine: AiEngine, private val store: SecureStore) : ViewModel() {

    data class Turn(val role: String, val text: String, val streaming: Boolean = false)

    private val _messages = MutableStateFlow<List<Turn>>(emptyList())
    val messages: StateFlow<List<Turn>> = _messages.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    fun send(userText: String) {
        if (_busy.value || userText.isBlank()) return
        val history = _messages.value + Turn("user", userText)
        _messages.value = history
        _busy.value = true
        val model = store.getAiModel() ?: "gpt-4o-mini"
        val base = store.getAiBaseUrl() ?: "https://api.openai.com/v1"
        viewModelScope.launch {
            val messages = (listOf(AiEngine.Message("system", AiPrompts.SYSTEM_ASSISTANT)) +
                history.map { AiEngine.Message(it.role, it.text) })
            val updatedHistory = history + Turn("assistant", "", streaming = true)
            _messages.value = updatedHistory
            val sb = StringBuilder()
            engine.stream(messages, AiEngine.Options(model = model, baseUrl = base))
                .catch { e ->
                    sb.append("[error] ${e.message}")
                }
                .collect { piece ->
                    sb.append(piece)
                    _messages.value = updatedHistory.mapIndexed { i, t ->
                        if (i == updatedHistory.lastIndex) t.copy(text = sb.toString(), streaming = true) else t
                    }
                }
            _messages.value = updatedHistory.mapIndexed { i, t ->
                if (i == updatedHistory.lastIndex) t.copy(text = sb.toString(), streaming = false) else t
            }
            _busy.value = false
        }
    }
}
