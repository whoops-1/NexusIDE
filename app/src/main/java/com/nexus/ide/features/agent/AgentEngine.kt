package com.nexus.ide.features.agent

import com.nexus.ide.core.utils.Logger
import com.nexus.ide.data.local.prefs.SecureStore
import com.nexus.ide.data.local.prefs.SettingsStore
import com.nexus.ide.features.ai.formatApiError
import com.nexus.ide.features.ai.resolveChatCompletionsUrl
import com.nexus.ide.features.filesystem.WorkspaceService
import com.nexus.ide.features.terminal.TermuxBridge
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * The core agentic loop.
 *
 * Architecture:
 *   1. Caller provides a user message and the full conversation history.
 *   2. Engine sends the message to the LLM with tool definitions.
 *   3. LLM either streams a text response (done) or requests a tool call.
 *   4. For tool calls:
 *      a. Engine emits [AgentEvent.ToolCall] and suspends.
 *      b. Caller (ViewModel) gates approval, then calls [executeTool].
 *      c. Result is fed back into the conversation and we go to step 2.
 *   5. Loop ends when LLM responds with text and no more tool calls,
 *      or when [maxTurns] is exceeded.
 *
 * The engine is stateless — conversation history is owned by the ViewModel.
 */
class AgentEngine(
    private val secureStore: SecureStore,
    private val settings: SettingsStore,
    private val workspace: WorkspaceService,
    private val termux: TermuxBridge
) {
    companion object {
        private const val TAG = "AgentEngine"
        private const val MAX_TURNS = 20
        const val SYSTEM_PROMPT = """You are Nexus Agent, an expert software engineer embedded inside NexusIDE — a mobile Android IDE.

You have access to tools that let you read, write, search, and run code in the user's workspace.

Rules you must follow:
- Always read a file before modifying it so you understand the existing structure.
- Write complete file contents, never partial diffs.
- Run commands only when necessary (builds, tests, linters).
- Explain your plan in plain language before starting a series of tool calls.
- When you finish a task, summarize exactly what you changed and why.
- If you're uncertain about scope, ask a clarifying question instead of guessing.
- Never delete files without explicitly confirming the user asked for it."""
    }

    data class Message(
        val role: String,       // "user" | "assistant" | "tool"
        val content: String,
        val toolCallId: String? = null,
        val toolCalls: List<RawToolCall>? = null
    )

    data class RawToolCall(
        val id: String,
        val name: String,
        val arguments: String   // raw JSON string from the API
    )

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(180, TimeUnit.SECONDS)
        .build()

    /**
     * Run one user turn. Emits [AgentEvent]s as things happen.
     * The flow completes when the agent is done or hits an error.
     *
     * @param history Full conversation history including the new user message.
     * @param onApprovalNeeded Suspend callback; return true to allow, false to deny.
     */
    fun run(
        history: List<Message>,
        onApprovalNeeded: suspend (AgentEvent.ToolCall) -> Boolean
    ): Flow<AgentEvent> = callbackFlow {
        var messages = history.toMutableList()
        var turns = 0

        while (turns < MAX_TURNS) {
            turns++
            val apiMessages = buildApiMessages(messages)
            val result = callApi(apiMessages)

            when (result) {
                is ApiResult.Text -> {
                    // Emit the text in chunks so the UI streams progressively
                    // rather than showing nothing until the full response lands.
                    // The API call is non-streaming (tool-calling requires full
                    // JSON back), so we simulate streaming here at the display layer.
                    val text = result.text
                    val chunkSize = 8
                    var offset = 0
                    while (offset < text.length) {
                        val end = minOf(offset + chunkSize, text.length)
                        trySend(AgentEvent.TextDelta(text.substring(offset, end)))
                        offset = end
                    }
                    trySend(AgentEvent.TextComplete(result.text))
                    trySend(AgentEvent.Done)
                    break
                }
                is ApiResult.ToolCalls -> {
                    // Append the assistant message with tool_calls to history
                    messages.add(
                        Message(
                            role = "assistant",
                            content = result.reasoning,
                            toolCalls = result.calls
                        )
                    )

                    // Process each tool call sequentially
                    var anyRejected = false
                    for (rawCall in result.calls) {
                        val tool = AgentTool.byName(rawCall.name)
                        if (tool == null) {
                            trySend(AgentEvent.Error("Unknown tool: ${rawCall.name}"))
                            continue
                        }

                        val args = parseArgs(rawCall.arguments)
                        val callEvent = AgentEvent.ToolCall(rawCall.id, tool, args)
                        trySend(callEvent)

                        // Gate approval for destructive tools
                        val approved = if (tool.requiresApproval) {
                            onApprovalNeeded(callEvent)
                        } else {
                            true
                        }

                        if (!approved) {
                            trySend(AgentEvent.ToolRejected(rawCall.id, tool))
                            messages.add(Message(
                                role = "tool",
                                content = "[User rejected this action]",
                                toolCallId = rawCall.id
                            ))
                            anyRejected = true
                            continue
                        }

                        val toolResult = withContext(Dispatchers.IO) {
                            executeTool(tool, args)
                        }
                        trySend(AgentEvent.ToolResult(rawCall.id, tool, toolResult))
                        messages.add(Message(
                            role = "tool",
                            content = toolResult,
                            toolCallId = rawCall.id
                        ))
                    }

                    if (anyRejected) {
                        // Let the model know some actions were denied and ask what to do next
                        messages.add(Message(
                            role = "user",
                            content = "Some of your requested actions were rejected by the user. Please continue with what was approved, or ask the user how to proceed."
                        ))
                    }
                }
                is ApiResult.Error -> {
                    trySend(AgentEvent.Error(result.message))
                    trySend(AgentEvent.Done)
                    break
                }
            }
        }

        if (turns >= MAX_TURNS) {
            trySend(AgentEvent.Error("Reached maximum turn limit ($MAX_TURNS). Start a new session."))
            trySend(AgentEvent.Done)
        }

        close()
        awaitClose()
    }.flowOn(Dispatchers.IO)

    // -------------------------------------------------------------------------
    // API call
    // -------------------------------------------------------------------------

    private sealed class ApiResult {
        data class Text(val text: String) : ApiResult()
        data class ToolCalls(val reasoning: String, val calls: List<RawToolCall>) : ApiResult()
        data class Error(val message: String) : ApiResult()
    }

    private fun callApi(messages: JSONArray): ApiResult {
        val key = secureStore.getApiKey() ?: return ApiResult.Error("No API key configured. Add one in Settings.")
        val model = settings.aiModel
        val baseUrl = secureStore.getAiBaseUrl() ?: "https://api.openai.com/v1"

        // Models that don't support the OpenAI function-calling tool schema.
        // When matched, we omit "tools" from the request body and rely on
        // the system prompt asking the model to emit JSON tool calls in-text.
        val noToolsSupport = model.startsWith("nvidia/") ||
            model.startsWith("meta-llama/") ||
            model.contains("nemotron") ||
            model.contains("mistral") ||
            model.contains("minimax") ||
            model.contains(":free")

        val body = JSONObject().apply {
            put("model", model)
            put("temperature", 0.2)
            put("max_tokens", 4096)
            put("messages", messages)
            if (!noToolsSupport) {
                put("tools", AgentTool.toApiJson())
                put("tool_choice", "auto")
            }
        }

        val req = Request.Builder()
            .url(resolveChatCompletionsUrl(baseUrl))
            .addHeader("Authorization", "Bearer $key")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()

        return try {
            client.newCall(req).execute().use { resp ->
                val raw = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) return ApiResult.Error(formatApiError(resp.code, raw))

                val obj = JSONObject(raw)
                val choice = obj.getJSONArray("choices").getJSONObject(0)
                val message = choice.getJSONObject("message")
                val finishReason = choice.optString("finish_reason", "stop")

                if (finishReason == "tool_calls") {
                    val toolCallsArr = message.optJSONArray("tool_calls") ?: return ApiResult.Error("Expected tool_calls but got none")
                    val calls = (0 until toolCallsArr.length()).map { i ->
                        val tc = toolCallsArr.getJSONObject(i)
                        RawToolCall(
                            id = tc.getString("id"),
                            name = tc.getJSONObject("function").getString("name"),
                            arguments = tc.getJSONObject("function").getString("arguments")
                        )
                    }
                    val reasoning = message.optString("content", "")
                    ApiResult.ToolCalls(reasoning, calls)
                } else {
                    val text = message.optString("content", "")
                    ApiResult.Text(text)
                }
            }
        } catch (e: Exception) {
            Logger.e(TAG, "API call failed", e)
            ApiResult.Error("[network error] ${e.message}")
        }
    }

    // -------------------------------------------------------------------------
    // Tool executors
    // -------------------------------------------------------------------------

    private fun executeTool(tool: AgentTool, args: Map<String, String>): String {
        val root = workspace.workspaceRoot
        return try {
            when (tool) {
                is AgentTool.ReadFile -> {
                    val file = safeResolve(root, args["path"] ?: return "Error: missing path")
                    if (!file.exists()) return "Error: file not found: ${args["path"]}"
                    if (file.length() > 512_000) return "Error: file too large (>${512_000 / 1000}KB)"
                    file.readText()
                }

                is AgentTool.WriteFile -> {
                    val file = safeResolve(root, args["path"] ?: return "Error: missing path")
                    val content = args["content"] ?: return "Error: missing content"
                    file.parentFile?.mkdirs()
                    file.writeText(content)
                    "Written ${content.length} characters to ${args["path"]}"
                }

                is AgentTool.ListDirectory -> {
                    val dir = safeResolve(root, args["path"] ?: ".")
                    if (!dir.exists()) return "Error: directory not found"
                    if (!dir.isDirectory) return "Error: path is a file, not a directory"
                    val entries = dir.listFiles() ?: return "[]"
                    val arr = JSONArray()
                    entries.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
                        .take(200)
                        .forEach { f ->
                            arr.put(JSONObject().apply {
                                put("name", f.name)
                                put("type", if (f.isDirectory) "dir" else "file")
                                put("size", if (f.isFile) f.length() else 0)
                            })
                        }
                    arr.toString(2)
                }

                is AgentTool.SearchFiles -> {
                    val pattern = args["pattern"] ?: return "Error: missing pattern"
                    val searchRoot = safeResolve(root, args["path"] ?: ".")
                    val regex = try { Regex(pattern) } catch (e: Exception) {
                        return "Error: invalid regex: ${e.message}"
                    }
                    val results = StringBuilder()
                    var matchCount = 0
                    searchRoot.walkTopDown()
                        .filter { it.isFile && it.length() < 512_000 }
                        .filter { !it.name.startsWith(".") }
                        .filter { it.extension !in listOf("png", "jpg", "jpeg", "gif", "webp", "class", "jar", "apk") }
                        .take(500)
                        .forEach { file ->
                            file.readLines().forEachIndexed { lineIdx, line ->
                                if (regex.containsMatchIn(line)) {
                                    val rel = file.relativeTo(root).path
                                    results.appendLine("$rel:${lineIdx + 1}: $line")
                                    matchCount++
                                    if (matchCount >= 50) return "Results truncated at 50 matches:\n$results"
                                }
                            }
                        }
                    if (results.isEmpty()) "No matches found for: $pattern"
                    else "$matchCount match(es):\n$results"
                }

                is AgentTool.RunCommand -> {
                    val command = args["command"] ?: return "Error: missing command"
                    val result = termux.runSync(command)
                    buildString {
                        if (result.stdout.isNotBlank()) append("stdout:\n${result.stdout}\n")
                        if (result.stderr.isNotBlank()) append("stderr:\n${result.stderr}\n")
                        append("exit code: ${result.exitCode}")
                    }
                }

                is AgentTool.DeleteFile -> {
                    val file = safeResolve(root, args["path"] ?: return "Error: missing path")
                    if (!file.exists()) return "Error: file not found"
                    val deleted = if (file.isDirectory) file.deleteRecursively() else file.delete()
                    if (deleted) "Deleted: ${args["path"]}" else "Error: could not delete ${args["path"]}"
                }

                is AgentTool.RenameFile -> {
                    val from = safeResolve(root, args["from"] ?: return "Error: missing from")
                    val to = safeResolve(root, args["to"] ?: return "Error: missing to")
                    if (!from.exists()) return "Error: source not found: ${args["from"]}"
                    to.parentFile?.mkdirs()
                    val ok = from.renameTo(to)
                    if (ok) "Renamed ${args["from"]} → ${args["to"]}" else "Error: rename failed"
                }
                is AgentTool.WebSearch -> {
                    val query = args["query"] ?: return "Error: missing query"
                    try {
                        // DuckDuckGo lite — no API key required
                        val encoded = java.net.URLEncoder.encode(query, "UTF-8")
                        val req = okhttp3.Request.Builder()
                            .url("https://lite.duckduckgo.com/lite/?q=$encoded")
                            .addHeader("User-Agent", "NexusIDE/1.0")
                            .get()
                            .build()
                        client.newCall(req).execute().use { resp ->
                            if (!resp.isSuccessful) return "Search failed: HTTP ${resp.code}"
                            val html = resp.body?.string() ?: return "Search failed: empty response"
                            // Extract result snippets from DuckDuckGo lite HTML
                            val snippets = mutableListOf<String>()
                            val linkRe = Regex("""<a[^>]+class="result-link"[^>]*>([^<]+)</a>""")
                            val snippetRe = Regex("""<td class="result-snippet">([^<]+)</td>""")
                            val links = linkRe.findAll(html).map { it.groupValues[1].trim() }.toList()
                            val snips = snippetRe.findAll(html).map { it.groupValues[1].trim() }.toList()
                            for (i in links.indices.take(5)) {
                                snippets.add("${i+1}. ${links.getOrNull(i) ?: ""}\n   ${snips.getOrNull(i) ?: ""}")
                            }
                            if (snippets.isEmpty()) "No results found for: $query"
                            else "Search results for \"$query\":\n\n${snippets.joinToString("\n\n")}"
                        }
                    } catch (e: Exception) {
                        "Search error: ${e.message}"
                    }
                }
            }
        } catch (e: SecurityException) {
            "Error: access denied — path escapes workspace"
        } catch (e: Exception) {
            Logger.e(TAG, "tool execution failed: ${tool.name}", e)
            "Error: ${e.message}"
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /** Resolve a relative path against root and verify it stays inside root. */
    private fun safeResolve(root: File, relative: String): File {
        val resolved = File(root, relative).canonicalFile
        if (!resolved.absolutePath.startsWith(root.canonicalFile.absolutePath)) {
            throw SecurityException("Path escapes workspace: $relative")
        }
        return resolved
    }

    private fun parseArgs(json: String): Map<String, String> {
        return try {
            val obj = JSONObject(json)
            obj.keys().asSequence().associateWith { obj.optString(it, "") }
        } catch (e: Exception) {
            emptyMap()
        }
    }

    private fun buildApiMessages(history: List<Message>): JSONArray {
        val arr = JSONArray()
        arr.put(JSONObject().apply {
            put("role", "system")
            put("content", SYSTEM_PROMPT)
        })
        history.forEach { msg ->
            when (msg.role) {
                "assistant" -> {
                    val obj = JSONObject().apply { put("role", "assistant") }
                    if (!msg.content.isNullOrBlank()) obj.put("content", msg.content)
                    if (!msg.toolCalls.isNullOrEmpty()) {
                        val tcArr = JSONArray()
                        msg.toolCalls.forEach { tc ->
                            tcArr.put(JSONObject().apply {
                                put("id", tc.id)
                                put("type", "function")
                                put("function", JSONObject().apply {
                                    put("name", tc.name)
                                    put("arguments", tc.arguments)
                                })
                            })
                        }
                        obj.put("tool_calls", tcArr)
                    }
                    arr.put(obj)
                }
                "tool" -> {
                    arr.put(JSONObject().apply {
                        put("role", "tool")
                        put("content", msg.content)
                        put("tool_call_id", msg.toolCallId ?: "")
                    })
                }
                else -> {
                    arr.put(JSONObject().apply {
                        put("role", msg.role)
                        put("content", msg.content)
                    })
                }
            }
        }
        return arr
    }
}
