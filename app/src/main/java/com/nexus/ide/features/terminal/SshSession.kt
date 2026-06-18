package com.nexus.ide.features.terminal

import com.jcraft.jsch.ChannelExec
import com.jcraft.jsch.ChannelShell
import com.jcraft.jsch.JSch
import com.jcraft.jsch.Session
import com.nexus.ide.core.utils.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.OutputStream
import java.util.Properties

/**
 * SSH session wrapper around JSch. Supports both one-shot command
 * execution ([runOnce]) and interactive shell streaming.
 *
 * Authentication: password, public key, or agent. Keys can be loaded
 * from the [SettingsStore] (the user pastes a private key into the SSH
 * settings page; we never write keys to disk in cleartext).
 */
class SshSession {

    private var session: Session? = null
    private var shellChannel: ChannelShell? = null
    private var shellInput: OutputStream? = null
    private val _output = MutableSharedFlow<String>(extraBufferCapacity = 64)
    val output: SharedFlow<String> = _output

    suspend fun connect(host: String, port: Int, user: String, auth: Auth) = withContext(Dispatchers.IO) {
        val jsch = JSch()
        when (auth) {
            is Auth.Key -> jsch.addIdentity("nexus", auth.privateKey.toByteArray(), null, auth.passphrase?.toByteArray())
            is Auth.Password -> { /* set below */ }
            is Auth.Agent -> { /* TODO: jsch.setIdentityRepository(...) */ }
        }
        val sess = jsch.getSession(user, host, port)
        val props = Properties()
        props["StrictHostKeyChecking"] = "no"
        sess.setConfig(props)
        if (auth is Auth.Password) sess.setPassword(auth.password)
        sess.connect(CONNECT_TIMEOUT_MS)
        session = sess
    }

    suspend fun runOnce(command: String): String = withContext(Dispatchers.IO) {
        val sess = session ?: throw IllegalStateException("not connected")
        val channel = sess.openChannel("exec") as ChannelExec
        channel.setCommand(command)
        channel.setInputStream(null)
        val out = StringBuilder()
        val input = channel.inputStream
        channel.connect()
        val buf = ByteArray(4096)
        while (true) {
            val n = input.read(buf)
            if (n < 0) break
            out.append(String(buf, 0, n))
        }
        channel.disconnect()
        out.toString()
    }

    suspend fun openShell(terminalType: String = "xterm-256color", cols: Int = 120, rows: Int = 30) = withContext(Dispatchers.IO) {
        val sess = session ?: throw IllegalStateException("not connected")
        val ch = sess.openChannel("shell") as ChannelShell
        ch.setPtyType(terminalType)
        ch.setPtySize(cols, rows, cols * 8, rows * 16)
        ch.connect()
        shellChannel = ch
        shellInput = ch.outputStream
        // Pump shell output to SharedFlow
        val input: InputStream = ch.inputStream
        Thread {
            val buf = ByteArray(4096)
            while (!Thread.currentThread().isInterrupted && ch.isConnected) {
                val n = try { input.read(buf) } catch (e: Exception) { break }
                if (n < 0) break
                val s = String(buf, 0, n, Charsets.UTF_8)
                kotlinx.coroutines.runBlocking { _output.emit(s) }
            }
        }.apply { isDaemon = true; name = "ssh-shell-pump-${sess.host}" }.start()
    }

    fun sendShellInput(data: String) {
        try {
            shellInput?.write(data.toByteArray())
            shellInput?.flush()
        } catch (e: Exception) { Logger.e("SSH", "shell input failed", e) }
    }

    fun resize(cols: Int, rows: Int) {
        try { shellChannel?.setPtySize(cols, rows, cols * 8, rows * 16) } catch (_: Exception) {}
    }

    fun disconnect() {
        try { shellChannel?.disconnect() } catch (_: Exception) {}
        try { session?.disconnect() } catch (_: Exception) {}
        session = null; shellChannel = null; shellInput = null
    }

    sealed class Auth {
        data class Password(val password: String) : Auth()
        data class Key(val privateKey: String, val passphrase: String? = null) : Auth()
        object Agent : Auth()
    }

    companion object { private const val CONNECT_TIMEOUT_MS = 15000 }
}
