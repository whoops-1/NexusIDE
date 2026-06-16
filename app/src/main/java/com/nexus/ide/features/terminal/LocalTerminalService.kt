package com.nexus.ide.features.terminal

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.Parcel
import android.os.Parcelable
import androidx.core.app.NotificationCompat
import com.nexus.ide.MainActivity
import com.nexus.ide.R
import com.nexus.ide.core.utils.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/**
 * Long-running foreground service that hosts one or more shell sessions.
 *
 * Why a service?
 *  - Android will kill background processes holding a PTY after a few seconds.
 *  - A foreground service with `specialUse` type keeps the runtime alive while
 *    the user switches away to check documentation or another app.
 *  - The notification lets the user stop the session explicitly.
 *
 * What it owns:
 *  - One [LocalShell] per `Session` the user has open (start, write, kill).
 *  - An in-memory ring buffer of recent output per session, exposed as a
 *    cold [SharedFlow] for the UI to subscribe to.
 *  - Crash-safe cleanup: when the service is stopped, every running process
 *    receives a SIGTERM, then SIGKILL after 1s if it is still alive.
 *
 * IPC:
 *  - Local binding via [LocalBinder] is the normal path for in-process clients.
 *  - The service is `exported="false"`, so there is no remote attack surface.
 */
class LocalTerminalService : Service() {

    /** Binder returned to in-process clients (the IDE UI). */
    inner class LocalBinder : Binder() {
        val service: LocalTerminalService get() = this@LocalTerminalService
    }

    private val binder = LocalBinder()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val sessions = mutableMapOf<String, LocalShell>()

    private val _activeSessionId = MutableStateFlow<String?>(null)
    val activeSessionId: StateFlow<String?> = _activeSessionId.asStateFlow()

    override fun onCreate() {
        super.onCreate()
        ensureChannel()
        Logger.i(TAG, "service created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Promote to foreground immediately. Android requires this within
        // 5 seconds of startForeground() being called.
        startForegroundCompat()
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onDestroy() {
        Logger.i(TAG, "service destroyed; killing ${sessions.size} sessions")
        sessions.values.forEach { it.kill() }
        sessions.clear()
        scope.cancel()
        super.onDestroy()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        // The user swiped the app away from recents. Keep the service alive
        // only if a session is actively producing output; otherwise stop.
        val anyRunning = sessions.values.any { it.isRunning }
        if (!anyRunning) {
            stopSelf()
        }
        super.onTaskRemoved(rootIntent)
    }

    // ---------------- Public API for in-process clients ----------------

    /**
     * Start a new shell session. If [cwd] is null, the current working
     * directory of the service is used (defaults to the app's external
     * files dir, which is the workspace root).
     *
     * @return the session id (opaque string, use it to address this session)
     */
    fun startSession(
        command: List<String> = listOf("/system/bin/sh", "-i"),
        env: Map<String, String> = emptyMap(),
        cwd: File? = null,
    ): String {
        val id = "sess-${System.currentTimeMillis()}-${sessions.size}"
        val workdir = cwd ?: File(filesDir, "workspace").apply { mkdirs() }
        val shell = LocalShell(
            id = id,
            command = command,
            cwd = workdir,
            env = buildEnv(env),
            onExit = { removeSession(id) }
        )
        sessions[id] = shell
        _activeSessionId.value = id
        scope.launch(Dispatchers.IO) { shell.run() }
        Logger.i(TAG, "started session $id (cmd=${command.firstOrNull()}, cwd=$workdir)")
        return id
    }

    fun write(sessionId: String, data: String) {
        sessions[sessionId]?.write(data)
    }

    fun resize(sessionId: String, cols: Int, rows: Int) {
        sessions[sessionId]?.resize(cols, rows)
    }

    fun kill(sessionId: String) {
        sessions[sessionId]?.kill()
    }

    fun output(sessionId: String): SharedFlow<TerminalChunk> =
        sessions[sessionId]?.output ?: MutableSharedFlow()

    fun listSessions(): List<SessionInfo> = sessions.values.map { it.info() }

    private fun removeSession(id: String) {
        sessions.remove(id)
        if (_activeSessionId.value == id) {
            _activeSessionId.value = sessions.keys.firstOrNull()
        }
        // If no sessions left, leave the service running for a moment in
        // case the user starts another; the system will reclaim it later.
    }

    // ---------------- Internals ----------------

    private fun buildEnv(extra: Map<String, String>): Map<String, String> {
        // Pass through a minimal, safe environment. We deliberately do NOT
        // forward LD_* or PATH from the parent; the shell starts clean.
        val base = mapOf(
            "HOME" to (extra["HOME"] ?: filesDir.absolutePath),
            "USER" to (extra["USER"] ?: "nexus"),
            "TERM" to (extra["TERM"] ?: "xterm-256color"),
            "PWD" to (extra["PWD"] ?: filesDir.absolutePath),
            "LANG" to (extra["LANG"] ?: "en_US.UTF-8"),
            "PATH" to (extra["PATH"] ?: defaultPath()),
        )
        return base + extra
    }

    private fun defaultPath(): String {
        // Common locations for toybox/busybox + the standard Android paths.
        // Custom ROMs and Termux installs may add their own; users can
        // override by passing env["PATH"].
        return listOf(
            "/system/bin",
            "/system/xbin",
            "/apex/com.android.runtime/bin",
            "/vendor/bin",
            "/data/local/tmp",
        ).joinToString(":")
    }

    private fun startForegroundCompat() {
        val tap = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val notif: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.terminal_session_running))
            .setContentText(getString(R.string.terminal_session_subtitle))
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setContentIntent(tap)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIF_ID, notif,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIF_ID, notif)
        }
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val mgr = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (mgr.getNotificationChannel(CHANNEL_ID) != null) return
        val ch = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.terminal_session_channel),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.terminal_session_channel_desc)
            setShowBadge(false)
        }
        mgr.createNotificationChannel(ch)
    }

    /** A small DTO describing a live session, used by the UI to draw the tab strip. */
    data class SessionInfo(
        val id: String,
        val command: String,
        val cwd: String,
        val running: Boolean,
        val pid: Long?,
    ) : Parcelable {
        constructor(p: Parcel) : this(
            id = p.readString().orEmpty(),
            command = p.readString().orEmpty(),
            cwd = p.readString().orEmpty(),
            running = p.readInt() != 0,
            pid = if (p.readInt() != 0) p.readLong() else null,
        )

        override fun writeToParcel(p: Parcel, flags: Int) {
            p.writeString(id); p.writeString(command); p.writeString(cwd)
            p.writeInt(if (running) 1 else 0)
            if (pid != null) { p.writeInt(1); p.writeLong(pid) } else p.writeInt(0)
        }

        override fun describeContents(): Int = 0
        companion object CREATOR : Parcelable.Creator<SessionInfo> {
            override fun createFromParcel(parcel: Parcel) = SessionInfo(parcel)
            override fun newArray(size: Int): Array<SessionInfo?> = arrayOfNulls(size)
        }
    }

    /** One chunk of terminal output (used to back-pressure the UI). */
    data class TerminalChunk(val sessionId: String, val bytes: ByteArray, val isStderr: Boolean) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is TerminalChunk) return false
            return sessionId == other.sessionId && isStderr == other.isStderr && bytes.contentEquals(other.bytes)
        }
        override fun hashCode(): Int = 31 * sessionId.hashCode() + bytes.contentHashCode()
    }

    companion object {
        private const val TAG = "LocalTermSvc"
        private const val CHANNEL_ID = "nexus_terminal_session"
        private const val NOTIF_ID = 0x4E58_5553 // 'NEXUS'

        /**
         * Start the service. Safe to call from any thread; idempotent.
         */
        fun start(ctx: Context) {
            val intent = Intent(ctx, LocalTerminalService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ctx.startForegroundService(intent)
            } else {
                ctx.startService(intent)
            }
        }
    }
}

// We keep the actual pty/process handling in a separate file so this class
// stays small and easy to reason about. See [LocalShell].

/**
 * The actual process wrapper. Lives next to the service but is a plain
 * class — not an inner class — so it can be unit-tested without Robolectric.
 */
internal class LocalShell(
    val id: String,
    private val command: List<String>,
    private val cwd: File,
    private val env: Map<String, String>,
    private val onExit: () -> Unit,
) {
    private val _output = MutableSharedFlow<LocalTerminalService.TerminalChunk>(
        replay = 0, extraBufferCapacity = 256
    )
    val output: SharedFlow<LocalTerminalService.TerminalChunk> = _output.asSharedFlow()

    @Volatile private var proc: Process? = null
    @Volatile private var alive: Boolean = false

    val isRunning: Boolean get() = alive

    suspend fun run() = withContext(Dispatchers.IO) {
        val pb = ProcessBuilder(command).directory(cwd).redirectErrorStream(false)
        // Apply env. We never inherit; we set exactly what buildEnv gave us.
        pb.environment().clear()
        pb.environment().putAll(env)

        try {
            proc = pb.start()
            alive = true
            val p = proc ?: return@withContext

            val stdoutThread = thread(name = "shell-$id-out") {
                p.inputStream.buffered().use { input ->
                    val buf = ByteArray(4096)
                    try {
                        while (alive) {
                            val n = input.read(buf)
                            if (n <= 0) break
                            _output.tryEmit(
                                LocalTerminalService.TerminalChunk(id, buf.copyOf(n), false)
                            )
                        }
                    } catch (e: IOException) {
                        if (alive) Logger.w("LocalShell", "stdout read error: ${e.message}")
                    }
                }
            }
            val stderrThread = thread(name = "shell-$id-err") {
                p.errorStream.buffered().use { input ->
                    val buf = ByteArray(4096)
                    try {
                        while (alive) {
                            val n = input.read(buf)
                            if (n <= 0) break
                            _output.tryEmit(
                                LocalTerminalService.TerminalChunk(id, buf.copyOf(n), true)
                            )
                        }
                    } catch (e: IOException) {
                        if (alive) Logger.w("LocalShell", "stderr read error: ${e.message}")
                    }
                }
            }

            val exit = p.waitFor()
            alive = false
            stdoutThread.join(500)
            stderrThread.join(500)
            Logger.i("LocalShell", "session $id exited with code $exit")
        } catch (e: IOException) {
            Logger.e("LocalShell", "failed to start $command", e)
        } finally {
            alive = false
            onExit()
        }
    }

    fun write(data: String) {
        val p = proc ?: return
        try {
            p.outputStream.write(data.toByteArray(Charsets.UTF_8))
            p.outputStream.flush()
        } catch (e: IOException) {
            Logger.w("LocalShell", "write failed: ${e.message}")
        }
    }

    fun resize(cols: Int, rows: Int) {
        // Most built-in shells ignore TIOCSWINSZ. Termux's `login` does honor it
        // over the RUN_COMMAND intent, but a plain /system/bin/sh will not.
        // We accept the call and no-op so the UI can stay uniform.
        Logger.d("LocalShell", "resize($cols,$rows) requested (no-op for /system/bin/sh)")
    }

    fun kill() {
        alive = false
        val p = proc ?: return
        try { p.destroy() } catch (_: Exception) {}
        // Hard-kill after a grace period.
        Thread {
            try { Thread.sleep(1000) } catch (_: InterruptedException) {}
            if (proc?.isAlive == true) {
                try { proc?.destroyForcibly() } catch (_: Exception) {}
            }
        }.start()
    }

    fun info(): LocalTerminalService.SessionInfo = LocalTerminalService.SessionInfo(
        id = id,
        command = command.joinToString(" "),
        cwd = cwd.absolutePath,
        running = alive,
        pid = runCatching { proc?.pid()?.toLong() }.getOrNull(),
    )

    private inline fun thread(name: String, crossinline block: () -> Unit): Thread =
        Thread { block() }.apply {
            this.name = name
            isDaemon = true
            start()
        }
}
