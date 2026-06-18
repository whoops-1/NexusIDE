package com.nexus.ide.features.webserver

import com.nexus.ide.core.utils.Logger
import com.nexus.ide.data.local.prefs.SettingsStore
import fi.iki.elonen.NanoHTTPD
import java.io.File

/**
 * Built-in HTTP server (NanoHTTPD) that serves a workspace directory on
 * the device. Use cases:
 *   - Mobile dev: open a static site, test API mocks
 *   - Cross-device: hit the IDE from a laptop on the same network
 *   - Demos: a single tap to serve the current project
 *
 * Bind address: 0.0.0.0:<port> (configurable). Port 8080 by default.
 */
class WebServer(
    private val root: File,
    private val settings: SettingsStore,
    initialPort: Int = 8080
) : NanoHTTPD("0.0.0.0", initialPort) {

    fun actualPort(): Int = listeningPort
    fun isRunning(): Boolean = isAlive

    override fun serve(session: IHTTPSession): Response {
        val uri = session.uri.removePrefix("/").ifEmpty { "index.html" }
        val safe = uri.replace("..", "_")
        val file = File(root, safe)
        if (!file.exists() || file.isHidden) {
            return newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Not found: $uri")
        }
        if (file.isDirectory) {
            val idx = File(file, "index.html")
            if (idx.exists()) return serveFile(idx)
            return newFixedLengthResponse(Response.Status.OK, "text/html", "<h1>${file.name}</h1><ul>" +
                (file.listFiles()?.sortedBy { it.isFile } ?: emptyList()).joinToString("") {
                    val n = it.name
                    "<li><a href=\"/${file.name}/$n\">$n</a></li>"
                } + "</ul>")
        }
        return serveFile(file)
    }

    private fun serveFile(file: File): Response {
        val mime = mimeFor(file.extension)
        return try {
            newFixedLengthResponse(Response.Status.OK, mime, file.inputStream(), file.length())
        } catch (e: Exception) {
            Logger.e("Web", "serveFile failed", e)
            newFixedLengthResponse(Response.Status.INTERNAL_ERROR, MIME_PLAINTEXT, e.message ?: "error")
        }
    }

    private fun mimeFor(ext: String): String = when (ext.lowercase()) {
        "html", "htm" -> "text/html"
        "css" -> "text/css"
        "js" -> "application/javascript"
        "json" -> "application/json"
        "png" -> "image/png"
        "jpg", "jpeg" -> "image/jpeg"
        "gif" -> "image/gif"
        "svg" -> "image/svg+xml"
        "txt", "md", "log" -> "text/plain"
        else -> "application/octet-stream"
    }
}
