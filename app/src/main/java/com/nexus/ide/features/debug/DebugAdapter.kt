package com.nexus.ide.features.debug

import com.nexus.ide.core.utils.Logger
import java.io.File

/**
 * Pluggable debug adapter interface. Concrete adapters wrap a
 * language's debug runtime (jdb, pdb, gdb, node-inspect) behind this
 * uniform API. The IDE's debug UI binds to [DebugSession] events.
 */
interface DebugAdapter {
    val name: String
    fun supports(language: String): Boolean
    suspend fun start(program: String, args: List<String>, workingDir: File, breakpoints: List<Breakpoint>): DebugSession
    fun parseBreakpoint(language: String, file: String, line: Int): Breakpoint
}

data class Breakpoint(val file: String, val line: Int, val condition: String? = null, val enabled: Boolean = true)

sealed class DebugEvent {
    object Stopped : DebugEvent()
    object Continued : DebugEvent()
    data class Output(val stream: String, val text: String) : DebugEvent()
    data class StackFrame(val file: String, val line: Int, val function: String) : DebugEvent()
    object Terminated : DebugEvent()
}

class DebugSession internal constructor(
    val id: String,
    val program: String,
    val language: String
) {
    private val listeners = mutableListOf<(DebugEvent) -> Unit>()

    fun onEvent(cb: (DebugEvent) -> Unit) { listeners.add(cb) }
    internal fun emit(ev: DebugEvent) { listeners.forEach { it(ev) } }
    var stopped: Boolean = false
        internal set

    fun stepOver() { Logger.d("Debug", "step over $program") }
    fun stepInto() { Logger.d("Debug", "step into $program") }
    fun stepOut()  { Logger.d("Debug", "step out $program") }
    fun continue_() {
        stopped = false
        emit(DebugEvent.Continued)
    }
    fun pause() { stopped = true; emit(DebugEvent.Stopped) }
    fun terminate() { emit(DebugEvent.Terminated) }
    fun setBreakpoint(b: Breakpoint) { Logger.d("Debug", "set BP ${b.file}:${b.line}") }
    fun removeBreakpoint(file: String, line: Int) { Logger.d("Debug", "remove BP $file:$line") }
}

/** Stub adapter: pure no-op implementation that emits fake frames so the UI is testable. */
class StubDebugAdapter : DebugAdapter {
    override val name = "stub"
    override fun supports(language: String) = true
    override suspend fun start(program: String, args: List<String>, workingDir: File, breakpoints: List<Breakpoint>): DebugSession {
        val s = DebugSession("stub-${System.nanoTime()}", program, "any")
        s.onEvent { ev -> if (ev is DebugEvent.Terminated) Logger.d("Debug", "stub terminated") }
        return s
    }
    override fun parseBreakpoint(language: String, file: String, line: Int) = Breakpoint(file, line)
}

/** JDB adapter for Java/Kotlin programs. */
class JdbDebugAdapter : DebugAdapter {
    override val name = "jdb"
    override fun supports(language: String) = language == "java" || language == "kotlin"
    override suspend fun start(program: String, args: List<String>, workingDir: File, breakpoints: List<Breakpoint>): DebugSession {
        val s = DebugSession("jdb-${System.nanoTime()}", program, "jvm")
        // In a real impl we'd spawn `jdb -attach ...` or compile with -g and run.
        // The shell-out pattern is similar to GitService.runCmd.
        Thread {
            val pb = ProcessBuilder("jdb", program)
                .directory(workingDir)
                .redirectErrorStream(true)
            val proc = pb.start()
            s.emit(DebugEvent.Output("stdout", "jdb starting: $program
"))
            // Stream output
            Thread {
                val buf = ByteArray(4096)
                while (true) {
                    val n = try { proc.inputStream.read(buf) } catch (e: Exception) { break }
                    if (n < 0) break
                    s.emit(DebugEvent.Output("stdout", String(buf, 0, n)))
                }
            }.start()
            proc.waitFor()
            s.emit(DebugEvent.Terminated)
        }.start()
        return s
    }
    override fun parseBreakpoint(language: String, file: String, line: Int) = Breakpoint(file, line)
}
