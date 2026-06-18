package com.nexus.ide.features.terminal

import com.nexus.ide.core.utils.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * A persistent terminal session. Each session is a host process
 * (usually /system/bin/sh or a remote shell via [SshSession]) and
 * accumulates output. The UI binds to [output] and renders lines.
 */
class TerminalSession(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val workingDir: File,
    private val process: Process,
    private val onExit: (Int) -> Unit
) {
    private val _output = MutableSharedFlow<String>(extraBufferCapacity = 256)
    val output: SharedFlow<String> = _output

    private val _exitCode = MutableStateFlow<Int?>(null)
    val exitCode: StateFlow<Int?> = _exitCode

    init {
        // Pump stdout
        Thread {
            val buf = ByteArray(4096)
            val stream = process.inputStream
            try {
                while (true) {
                    val n = stream.read(buf)
                    if (n < 0) break
                    val s = String(buf, 0, n, Charsets.UTF_8)
                    kotlinx.coroutines.runBlocking { _output.emit(s) }
                }
            } catch (e: Exception) { Logger.e("Term", "stdout pump", e) }
        }.apply { isDaemon = true; name = "term-out-$id" }.start()
        // Pump stderr
        Thread {
            val buf = ByteArray(4096)
            val stream = process.errorStream
            try {
                while (true) {
                    val n = stream.read(buf)
                    if (n < 0) break
                    val s = String(buf, 0, n, Charsets.UTF_8)
                    kotlinx.coroutines.runBlocking { _output.emit("[err] $s") }
                }
            } catch (e: Exception) { /* closed */ }
        }.apply { isDaemon = true; name = "term-err-$id" }.start()
        // Waiter
        Thread {
            val code = try { process.waitFor() } catch (e: Exception) { -1 }
            _exitCode.value = code
            onExit(code)
        }.apply { isDaemon = true; name = "term-wait-$id" }.start()
    }

    suspend fun write(data: String) = withContext(Dispatchers.IO) {
        try { process.outputStream.write(data.toByteArray()); process.outputStream.flush() } catch (e: Exception) {
            Logger.e("Term", "write failed", e)
        }
    }

    fun resize(cols: Int, rows: Int) {
        try { /* Process doesn't natively support resize on Android */ } catch (_: Exception) {}
    }

    fun kill() {
        try { process.destroyForcibly() } catch (_: Exception) {}
    }
}

/**
 * Host-level manager for terminal sessions. Tracks open tabs and spawns
 * new shells on demand. Sessions are kept in a [ConcurrentHashMap] for
 * safe access from coroutines.
 */
class TerminalHost(
    private val termux: TermuxBridge,
    private val onSessionClosed: (String) -> Unit
) {
    private val sessions = ConcurrentHashMap<String, TerminalSession>()
    private val _active = MutableStateFlow<String?>(null)
    val active: StateFlow<String?> = _active

    /**
     * Spawn a new local shell. Returns [Result.failure] instead of throwing
     * if the process can't start — most commonly because [workingDir]
     * doesn't exist, or because this process lacks permission to chdir into
     * it (e.g. passing the filesystem root, which Android's app sandbox
     * cannot access even though `File("/").isDirectory` reports true).
     * Callers should pass a directory the app actually owns, such as
     * [com.nexus.ide.features.filesystem.WorkspaceService.workspaceRoot].
     */
    fun newLocal(workingDir: File, title: String = "sh"): Result<TerminalSession> = runCatching {
        require(workingDir.isDirectory) { "Not a directory: ${workingDir.absolutePath}" }
        val pb = ProcessBuilder("/system/bin/sh", "-i")
            .directory(workingDir)
            .redirectErrorStream(false)
        // Inject Termux env if available
        if (termux.isInstalled) {
            pb.environment()["PATH"] = "/data/data/com.termux/files/usr/bin:/system/bin:/system/xbin"
        }
        val proc = pb.start()
        val sessionId = UUID.randomUUID().toString()
        val session = TerminalSession(
            id = sessionId,
            title = title,
            workingDir = workingDir,
            process = proc,
            onExit = { _ -> onSessionClosed(sessionId) }
        )
        sessions[session.id] = session
        if (_active.value == null) _active.value = session.id
        session
    }.onFailure { e -> Logger.e("Term", "newLocal failed for ${workingDir.absolutePath}", e) }

    fun get(id: String): TerminalSession? = sessions[id]
    fun all(): List<TerminalSession> = sessions.values.toList()
    fun setActive(id: String) { if (sessions.containsKey(id)) _active.value = id }
    fun close(id: String) {
        sessions.remove(id)?.kill()
        if (_active.value == id) _active.value = sessions.keys.firstOrNull()
        onSessionClosed(id)
    }
}
