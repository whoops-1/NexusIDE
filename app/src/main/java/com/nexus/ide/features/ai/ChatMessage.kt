package com.nexus.ide.features.ai

/**
 * Represents a single turn in the AI conversation.
 *
 * [role] maps to the OpenAI chat role conventions used by [AiEngine].
 */
enum class ChatRole(val apiValue: String) {
    User("user"),
    Assistant("assistant"),
    System("system")
}

data class ChatMessage(
    val role: ChatRole,
    val content: String,
    val isStreaming: Boolean = false
)
