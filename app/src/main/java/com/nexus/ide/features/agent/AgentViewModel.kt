package com.nexus.ide.features.agent

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexus.ide.core.di.ServiceLocator
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

/**
 * Owns the agent conversation, approval gates, and message list.
 *
 * Approval flow:
 *   1. Engine emits [AgentEvent.ToolCall] for a destructive tool.
 *   2. ViewModel sets [pendingApproval] so the UI shows a confirmation card.
 *   3. User taps Allow or Deny → [resolveApproval] is called.
 *   4. The suspended [approvalChannel] receive() unblocks and the engine continues.
 */
class AgentViewModel : ViewModel() {

    // UI message model — what the screen renders
    sealed class AgentMessage {
        data class User(val text: String) : AgentMessage()
        data class Assistant(val text: String, val streaming: Boolean = false) : AgentMessage()
        data class ToolUse(
            val callId: String,
            val toolName: String,
            val args: Map<String, String>,
            val result: String? = null,
            val rejected: Boolean = false,
            val pending: Boolean = false   // awaiting approval
        ) : AgentMessage()
        data class Error(val message: String) : AgentMessage()
    }

    private val engine: AgentEngine = AgentEngine(
        secureStore = ServiceLocator.secureStore,
        settings = ServiceLocator.settings,
        workspace = ServiceLocator.workspace,
        termux = ServiceLocator.termux
    )

    private val _messages = MutableStateFlow<List<AgentMessage>>(emptyList())
    val messages: StateFlow<List<AgentMessage>> = _messages.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    /** Non-null when an action is waiting for user approval. */
    private val _pendingApproval = MutableStateFlow<AgentEvent.ToolCall?>(null)
    val pendingApproval: StateFlow<AgentEvent.ToolCall?> = _pendingApproval.asStateFlow()

    // Conversation history fed to the engine (separate from display messages)
    private val history = mutableListOf<AgentEngine.Message>()

    // Channel bridges the coroutine collecting engine events and the UI approval callback
    private var approvalChannel: Channel<Boolean>? = null

    fun send(userText: String) {
        if (_busy.value || userText.isBlank()) return
        _busy.value = true

        // Add to display + engine history
        appendMessage(AgentMessage.User(userText))
        history.add(AgentEngine.Message(role = "user", content = userText))

        // Placeholder for assistant response
        val assistantIndex = _messages.value.size
        appendMessage(AgentMessage.Assistant("", streaming = true))

        viewModelScope.launch {
            var assistantBuffer = StringBuilder()
            val pendingToolCalls = mutableMapOf<String, AgentEngine.RawToolCall>()

            engine.run(
                history = history.toList(),
                onApprovalNeeded = { callEvent ->
                    val ch = Channel<Boolean>(1)
                    approvalChannel = ch
                    _pendingApproval.value = callEvent
                    val approved = ch.receive()
                    _pendingApproval.value = null
                    approved
                }
            )
            .catch { e -> emit(AgentEvent.Error(e.message ?: "Unknown error")) }
            .collect { event ->
                when (event) {
                    is AgentEvent.TextDelta -> {
                        assistantBuffer.append(event.chunk)
                        updateMessageAt(assistantIndex, AgentMessage.Assistant(assistantBuffer.toString(), streaming = true))
                    }

                    is AgentEvent.TextComplete -> {
                        assistantBuffer = StringBuilder(event.text)
                        updateMessageAt(assistantIndex, AgentMessage.Assistant(event.text, streaming = false))
                        if (event.text.isNotBlank()) {
                            history.add(AgentEngine.Message(role = "assistant", content = event.text))
                        }
                    }

                    is AgentEvent.ToolCall -> {
                        pendingToolCalls[event.callId] = AgentEngine.RawToolCall(
                            id = event.callId,
                            name = event.tool.name,
                            arguments = argsToJson(event.args)
                        )
                        appendMessage(AgentMessage.ToolUse(
                            callId = event.callId,
                            toolName = event.tool.name,
                            args = event.args,
                            pending = event.tool.requiresApproval
                        ))
                    }

                    is AgentEvent.ToolResult -> {
                        updateToolMessage(event.callId) { it.copy(result = event.result, pending = false) }
                        history.add(AgentEngine.Message(
                            role = "tool",
                            content = event.result,
                            toolCallId = event.callId
                        ))
                        // Flush assistant tool_calls into history once we have results
                        val calls = pendingToolCalls.values.toList()
                        if (calls.isNotEmpty() && history.none { it.role == "assistant" && !it.toolCalls.isNullOrEmpty() }) {
                            history.add(AgentEngine.Message(
                                role = "assistant",
                                content = assistantBuffer.toString(),
                                toolCalls = calls
                            ))
                        }
                    }

                    is AgentEvent.ToolRejected -> {
                        updateToolMessage(event.callId) { it.copy(rejected = true, pending = false) }
                    }

                    is AgentEvent.Error -> {
                        appendMessage(AgentMessage.Error(event.message))
                    }

                    is AgentEvent.Done -> {
                        _busy.value = false
                    }
                }
            }
        }
    }

    /** Called by the UI when the user taps Allow or Deny on the approval card. */
    fun resolveApproval(approved: Boolean) {
        viewModelScope.launch {
            approvalChannel?.send(approved)
        }
    }

    fun clearHistory() {
        history.clear()
        _messages.value = emptyList()
    }

    // -------------------------------------------------------------------------
    // Message list helpers
    // -------------------------------------------------------------------------

    private fun appendMessage(msg: AgentMessage) {
        _messages.value = _messages.value + msg
    }

    private fun updateMessageAt(index: Int, msg: AgentMessage) {
        val current = _messages.value.toMutableList()
        if (index in current.indices) {
            current[index] = msg
            _messages.value = current
        }
    }

    private fun updateToolMessage(callId: String, update: (AgentMessage.ToolUse) -> AgentMessage.ToolUse) {
        _messages.value = _messages.value.map { msg ->
            if (msg is AgentMessage.ToolUse && msg.callId == callId) update(msg) else msg
        }
    }

    private fun argsToJson(args: Map<String, String>): String {
        val obj = org.json.JSONObject()
        args.forEach { (k, v) -> obj.put(k, v) }
        return obj.toString()
    }
}
