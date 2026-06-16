package com.nexus.ide.features.git

import com.nexus.ide.core.utils.Logger
import java.io.File

/**
 * Lightweight Git wrapper that shells out to the system `git` binary
 * (or Termux's git). For full control we could embed a pure-JVM
 * implementation (e.g. JGit), but shelling out keeps the integration
 * surface small and reliable.
 */
class GitService(private val workDir: File) {

    fun isRepo(): Boolean = File(workDir, ".git").exists()
    fun init(): CommandResult = exec("init")
    fun status(): CommandResult = exec("status --porcelain")
    fun addAll(): CommandResult = exec("add -A")
    fun add(path: String): CommandResult = exec("add --", path)
    fun commit(message: String): CommandResult = exec("commit -m", message)
    fun log(maxCount: Int = 50): CommandResult = exec("log --oneline -n $maxCount")
    fun diff(staged: Boolean = false): CommandResult = exec(if (staged) "diff --staged" else "diff")
    fun branch(): CommandResult = exec("branch -vv")
    fun checkout(branch: String): CommandResult = exec("checkout", branch)
    fun createBranch(name: String): CommandResult = exec("checkout -b", name)
    fun fetch(remote: String = "origin"): CommandResult = exec("fetch", remote)
    fun pull(): CommandResult = exec("pull --rebase")
    fun push(remote: String = "origin", branch: String? = null): CommandResult =
        if (branch == null) exec("push", remote) else exec("push", remote, branch)
    fun remotes(): CommandResult = exec("remote -v")
    fun clone(url: String, dest: File): CommandResult = runCmd(File("."), "clone", url, dest.absolutePath)

    private fun exec(vararg args: String): CommandResult = runCmd(workDir, *args)

    private fun runCmd(cwd: File, vararg args: String): CommandResult {
        return try {
            val pb = ProcessBuilder(listOf("git") + args.toList())
                .directory(cwd)
                .redirectErrorStream(false)
            if (!File("/data/data/com.termux/files/usr/bin/git").exists()) {
                pb.environment()["PATH"] = "/data/data/com.termux/files/usr/bin:/system/bin:/system/xbin"
            }
            val proc = pb.start()
            val out = proc.inputStream.bufferedReader().readText()
            val err = proc.errorStream.bufferedReader().readText()
            proc.waitFor()
            CommandResult(proc.exitValue(), out, err)
        } catch (e: Exception) {
            Logger.e("Git", "exec failed: ${args.joinToString(" ")}", e)
            CommandResult(-1, "", e.message ?: "git not found")
        }
    }

    data class CommandResult(val exitCode: Int, val stdout: String, val stderr: String) {
        val ok: Boolean get() = exitCode == 0
    }
}
