package com.nexus.ide.core.utils

import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Catches uncaught exceptions, writes a compact crash dump to the app's
 * cache directory (rotated, capped), then delegates to the platform default
 * so the OS can still show the "App stopped" dialog.
 *
 * The Log viewer in-app surfaces these dumps.
 */
object CrashHandler {

    private const val MAX_FILES = 8
    private val formatter = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US)

    fun install() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val dump = buildString {
                    appendLine("=== Nexus crash ${formatter.format(Date())} ===")
                    appendLine("thread: ${thread.name} (id=${thread.id})")
                    appendLine("type:   ${throwable.javaClass.name}")
                    appendLine("msg:    ${throwable.message}")
                    appendLine()
                    val sw = StringWriter()
                    throwable.printStackTrace(PrintWriter(sw))
                    append(sw)
                }
                Logger.e("Crash", dump)
                writeDump(dump)
            } catch (_: Throwable) {
                // never let the crash handler itself crash
            }
            previous?.uncaughtException(thread, throwable)
        }
    }

    private fun writeDump(text: String) {
        val dir = java.io.File(android.os.Environment.getExternalStorageDirectory(), "Android/data/com.nexus.ide/cache/crash").apply { mkdirs() }
        val out = java.io.File(dir, "crash-${formatter.format(Date())}.log")
        out.writeText(text)
        dir.listFiles()
            ?.sortedByDescending { it.lastModified() }
            ?.drop(MAX_FILES)
            ?.forEach { it.delete() }
    }
}
