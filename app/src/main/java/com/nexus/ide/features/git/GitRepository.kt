package com.nexus.ide.features.git

import com.nexus.ide.core.utils.Logger
import java.io.File

/**
 * High-level repository abstraction. Wraps [GitService] with status
 * parsing and pretty state for the UI.
 */
class GitRepository(val workDir: File) {
    val service = GitService(workDir)

    data class StatusEntry(val path: String, val indexStatus: Char, val workTreeStatus: Char) {
        val isStaged: Boolean get() = indexStatus != ' ' && indexStatus != '?'
        val isModified: Boolean get() = workTreeStatus == 'M'
        val isUntracked: Boolean get() = indexStatus == '?' && workTreeStatus == '?'
    }

    fun parseStatus(): List<StatusEntry> {
        val r = service.status()
        if (!r.ok) return emptyList()
        return r.stdout.lines().filter { it.length >= 3 }.map { line ->
            val idx = line[0]
            val wt = line[1]
            val path = line.substring(3)
            StatusEntry(path, idx, wt)
        }
    }

    fun currentBranch(): String? {
        val r = service.currentBranch()
        if (!r.ok) return null
        val b = r.stdout.trim()
        return if (b.isNotEmpty()) b else null
    }
}
