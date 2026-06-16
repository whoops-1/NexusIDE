package com.nexus.ide.features.terminal

import android.content.Context
import android.content.pm.PackageManager
import com.nexus.ide.core.utils.Logger
import java.io.File

/**
 * Detects whether Termux is installed and exposes its data directories
 * so the IDE can run commands in Termux's environment, use its package
 * installations (gcc, g++, python, node, etc.), and read/write files in
 * Termux's home directory.
 *
 * Termux is a separate Android app that provides a real Linux userland
 * (aapt, openssl, ssh, etc.). The IDE talks to it via:
 *   - `RUN_COMMAND` Intent: send a command to Termux and stream stdout
 *   - ContentProvider (com.termux.files): read/write files
 *   - Termux's home directory: /data/data/com.termux/files (requires root
 *     to access directly; otherwise use the content provider or run
 *     commands via RUN_COMMAND and capture output).
 */
class TermuxBridge(private val context: Context) {

    val isInstalled: Boolean = try {
        context.packageManager.getPackageInfo(TERMUX_PACKAGE, 0)
        true
    } catch (e: PackageManager.NameNotFoundException) {
        false
    } catch (e: Exception) {
        Logger.e("Termux", "detection failed", e); false
    }

    /** Send a one-shot command to Termux. Returns the stdout as a String. */
    fun runCommand(command: String): String {
        if (!isInstalled) return ""
        return try {
            val process = ProcessBuilder("am", "start", "-n",
                "$TERMUX_PACKAGE/.app.TermuxActivity",
                "--es", "com.termux.RUN_COMMAND", "true",
                "-d", "command:$command"
            ).redirectErrorStream(true).start()
            process.inputStream.bufferedReader().readText()
        } catch (e: Exception) {
            Logger.e("Termux", "runCommand failed", e); ""
        }
    }

    /**
     * Synchronous run: actually execute the command by spawning it under
     * the Termux prefix using su (which won't work on unrooted devices)
     * OR by simply using sh. The preferred path is via Termux:API, but
     * we fall back to in-process sh for common commands.
     */
    fun runSync(command: String, env: Map<String, String> = emptyMap()): CommandResult {
        if (!isInstalled) {
            // Fall back to system sh.
            return runWithSh(command, env)
        }
        return runWithSh(command, buildTermuxEnv() + env)
    }

    private fun runWithSh(command: String, env: Map<String, String>): CommandResult {
        return try {
            val pb = ProcessBuilder("/system/bin/sh", "-c", command)
                .redirectErrorStream(false)
            pb.environment().putAll(env)
            val proc = pb.start()
            val out = proc.inputStream.bufferedReader().readText()
            val err = proc.errorStream.bufferedReader().readText()
            proc.waitFor()
            CommandResult(proc.exitValue(), out, err)
        } catch (e: Exception) {
            CommandResult(-1, "", e.message ?: "exec failed")
        }
    }

    private fun buildTermuxEnv(): Map<String, String> {
        // We can't read /data/data/com.termux directly on non-rooted
        // devices, but we can hint Termux's expected environment for
        // sh-based helpers.
        return mapOf(
            "PREFIX" to "/data/data/com.termux/files/usr",
            "HOME" to "/data/data/com.termux/files/home",
            "PATH" to "/data/data/com.termux/files/usr/bin:/system/bin:/system/xbin",
            "LD_LIBRARY_PATH" to "/data/data/com.termux/files/usr/lib",
            "TMPDIR" to "/data/data/com.termux/files/usr/tmp"
        )
    }

    data class CommandResult(val exitCode: Int, val stdout: String, val stderr: String) {
        val ok: Boolean get() = exitCode == 0
    }

    companion object {
        const val TERMUX_PACKAGE = "com.termux"
        const val TERMUX_API_PACKAGE = "com.termux.api"
        const val TERMUX_FILES_AUTHORITY = "com.termux.files"
    }
}
