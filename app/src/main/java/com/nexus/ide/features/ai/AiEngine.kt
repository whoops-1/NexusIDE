package com.nexus.ide.features.ai

import com.nexus.ide.core.utils.Logger
import com.nexus.ide.data.local.prefs.SecureStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Talks to OpenAI-compatible chat completion APIs. NexusIDE supports
 * multiple providers (OpenAI, Anthropic via OpenRouter, custom
 * OpenAI-style endpoints) and exposes a uniform [complete] stream.
 *
 * API key is read from [SecureStore] and never logged or persisted to
 * disk in cleartext.
 */
class AiEngine(private val store: SecureStore) {

    data class Message(val role: String, val content: String)
    data class Options(
        val model: String = "gpt-4o-mini",
        val baseUrl: String = "https://api.openai.com/v1",
        val temperature: Float = 0.2f,
        val maxTokens: Int = 1024,
        val stream: Boolean = true
    )

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()

    /** Stream tokens from the provider. */
    fun stream(messages: List<Message>, options: Options): Flow<String> = flow {
        val key = store.getApiKey() ?: run { emit("[no API key configured]"); return@flow }
        val body = JSONObject().apply {
            put("model", options.model)
            put("temperature", options.temperature)
            put("max_tokens", options.maxTokens)
            put("stream", true)
            val arr = JSONArray()
            messages.forEach { arr.put(JSONObject().put("role", it.role).put("content", it.content)) }
            put("messages", arr)
        }
        val req = Request.Builder()
            .url("${options.baseUrl}/chat/completions")
            .addHeader("Authorization", "Bearer $key")
            .addHeader("Accept", "text/event-stream")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()
        try {
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    emit("[error ${resp.code}] ${resp.body?.string()}")
                    return@flow
                }
                val source = resp.body?.source() ?: return@flow
                while (!source.exhausted()) {
                    val line = source.readUtf8Line() ?: break
                    if (!line.startsWith("data:")) continue
                    val payload = line.removePrefix("data:").trim()
                    if (payload == "[DONE]") break
                    try {
                        val obj = JSONObject(payload)
                        val delta = obj.getJSONArray("choices")
                            .getJSONObject(0)
                            .optJSONObject("delta")
                            ?.optString("content", "")
                            ?: ""
                        if (delta.isNotEmpty()) emit(delta)
                    } catch (e: Exception) { /* ignore */ }
                }
            }
        } catch (e: Exception) {
            Logger.e("AI", "stream failed", e)
            emit("[error] ${e.message}")
        }
    }.flowOn(Dispatchers.IO)

    suspend fun complete(messages: List<Message>, options: Options): String = withContext(Dispatchers.IO) {
        val key = store.getApiKey() ?: return@withContext "No API key configured."
        val body = JSONObject().apply {
            put("model", options.model)
            put("temperature", options.temperature)
            put("max_tokens", options.maxTokens)
            put("stream", false)
            val arr = JSONArray()
            messages.forEach { arr.put(JSONObject().put("role", it.role).put("content", it.content)) }
            put("messages", arr)
        }
        val req = Request.Builder()
            .url("${options.baseUrl}/chat/completions")
            .addHeader("Authorization", "Bearer $key")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()
        try {
            client.newCall(req).execute().use { resp ->
                val raw = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) return@withContext "[error ${resp.code}] $raw"
                val obj = JSONObject(raw)
                obj.getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content")
            }
        } catch (e: Exception) { Logger.e("AI", "complete failed", e); "[error] ${e.message}" }
    }
}
