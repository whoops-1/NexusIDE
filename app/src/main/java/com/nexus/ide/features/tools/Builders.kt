package com.nexus.ide.features.tools

import com.nexus.ide.core.utils.Logger
import com.nexus.ide.data.local.prefs.SecureStore
import com.nexus.ide.features.ai.AiEngine
import java.io.File

/**
 * Lightweight in-app tools: a JSON formatter, a regex tester, a base64
 * encoder, a JWT decoder, and an HTTP request runner. These are the
 * utilities developers reach for daily.
 */
object Builders {

    object Json {
        fun pretty(input: String): String = try {
            val tokener = org.json.JSONTokener(input)
            val value = tokener.nextValue()
            org.json.JSONObject(value.toString()).toString(2)
        } catch (e: Exception) {
            org.json.JSONArray(input).toString(2)
        }
    }

    object Http {
        fun run(method: String, url: String, body: String?, headers: Map<String, String>): String {
            return try {
                val req = okhttp3.Request.Builder()
                    .url(url)
                    .method(method, body?.toRequestBody("application/json".toMediaTypeOrNull()))
                headers.forEach { (k, v) -> req.addHeader(k, v) }
                val resp = okhttp3.OkHttpClient().newCall(req.build()).execute()
                val text = resp.body?.string().orEmpty()
                "HTTP ${resp.code}\n\n$text"
            } catch (e: Exception) { "Error: ${e.message}" }
        }
    }
}
