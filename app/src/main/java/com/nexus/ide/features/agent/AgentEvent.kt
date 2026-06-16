package com.nexus.ide.features.agent

/**
 * Every event the agent loop can emit. The ViewModel collects these and
 * maps them into [AgentMessage] items for the UI.
 *
 * Keeping events separate from UI models lets the engine stay pure —
 * it never imports Compose or knows about approval flows.
 */
sealed class AgentEvent {

    /** The model is thinking / streaming a text response. */
    data class TextDelta(val chunk: String) : AgentEvent()

    /** The model finished streaming a full text response. */
    data class TextComplete(val text: String) : AgentEvent()

    /** The model wants to call a tool — awaiting user approval if needed. */
    data class ToolCall(
        val callId: String,
        val tool: AgentTool,
        val args: Map<String, String>
    ) : AgentEvent()

    /** A tool finished executing successfully. */
    data class ToolResult(
        val callId: String,
        val tool: AgentTool,
        val result: String
    ) : AgentEvent()

    /** A tool was rejected by the user. */
    data class ToolRejected(
        val callId: String,
        val tool: AgentTool
    ) : AgentEvent()

    /** A tool or network call failed. */
    data class Error(val message: String) : AgentEvent()

    /** The full agent turn is complete. */
    object Done : AgentEvent()
}
