package com.nexus.ide.features.filesystem

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import com.nexus.ide.core.utils.Logger
import com.nexus.ide.core.utils.NexusResult
import com.nexus.ide.domain.models.FileNode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets

/**
 * Filesystem operations for the IDE. Reads/writes text files, walks
 * directory trees, and exposes high-level operations (copy/move/delete)
 * used by the file explorer.
 *
 * Threading: all disk I/O is dispatched to [Dispatchers.IO]. Callers
 * can call from the main thread without blocking.
 */
class WorkspaceService(private val context: Context) {

    /** Root directory for the IDE's primary workspace, inside app private storage. */
    val workspaceRoot: File = File(context.filesDir, "workspace").apply { if (!exists()) mkdirs() }

    suspend fun readText(file: File, charset: Charset = StandardCharsets.UTF_8): NexusResult<String> = withContext(Dispatchers.IO) {
        runCatching {
            file.inputStream().use { it.readBytes().toString(charset) }
        }.onFailure { Logger.e("WS", "readText failed: $file", it) }
            .let { if (it.isSuccess) NexusResult.Ok(it.getOrThrow()) else NexusResult.Err(it.exceptionOrNull()!!) }
    }

    suspend fun writeText(file: File, content: String, charset: Charset = StandardCharsets.UTF_8): NexusResult<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            file.parentFile?.mkdirs()
            file.outputStream().use { it.write(content.toByteArray(charset)) }
        }.onFailure { Logger.e("WS", "writeText failed: $file", it) }
            .let { if (it.isSuccess) NexusResult.Ok(Unit) else NexusResult.Err(it.exceptionOrNull()!!) }
    }

    suspend fun listTree(root: File, maxDepth: Int = 12, hidden: Boolean = false): NexusResult<List<FileNode>> = withContext(Dispatchers.IO) {
        runCatching {
            val out = ArrayList<FileNode>(128)
            walkInto(root, out, 0, maxDepth, hidden)
            out
        }.let { if (it.isSuccess) NexusResult.Ok(it.getOrThrow()) else NexusResult.Err(it.exceptionOrNull()!!) }
    }

    private fun walkInto(dir: File, out: MutableList<FileNode>, depth: Int, maxDepth: Int, hidden: Boolean) {
        if (depth > maxDepth) return
        val children = dir.listFiles() ?: return
        children.sortBy { it.name.lowercase() }
        for (child in children) {
            if (!hidden && child.name.startsWith(".")) continue
            if (child.name == "build" || child.name == ".gradle" || child.name == "node_modules") continue
            val node = FileNode(
                path = child.absolutePath,
                name = child.name,
                isDirectory = child.isDirectory,
                size = if (child.isFile) child.length() else 0L,
                lastModified = child.lastModified()
            )
            out.add(node)
            if (child.isDirectory) walkInto(child, out, depth + 1, maxDepth, hidden)
        }
    }

    suspend fun mkdirs(path: File): NexusResult<Unit> = withContext(Dispatchers.IO) {
        runCatching { path.mkdirs() }.let { if (it.isSuccess) NexusResult.Ok(Unit) else NexusResult.Err(it.exceptionOrNull()!!) }
    }

    suspend fun delete(file: File): NexusResult<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            if (file.isDirectory) file.deleteRecursively() else file.delete()
        }.let { if (it.isSuccess) NexusResult.Ok(Unit) else NexusResult.Err(it.exceptionOrNull()!!) }
    }

    suspend fun copy(src: File, dest: File): NexusResult<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            dest.parentFile?.mkdirs()
            if (src.isDirectory) src.copyRecursively(dest, overwrite = true)
            else { FileInputStream(src).use { input -> FileOutputStream(dest).use { out -> input.copyTo(out) } } }
        }.let { if (it.isSuccess) NexusResult.Ok(Unit) else NexusResult.Err(it.exceptionOrNull()!!) }
    }

    suspend fun rename(file: File, newName: String): NexusResult<File> = withContext(Dispatchers.IO) {
        runCatching {
            val target = File(file.parentFile, newName)
            if (file.renameTo(target)) target else throw RuntimeException("rename failed")
        }.let { if (it.isSuccess) NexusResult.Ok(it.getOrThrow()) else NexusResult.Err(it.exceptionOrNull()!!) }
    }

    /**
     * Read the content of a content:// Uri (e.g. from SAF). Used when
     * the user opens a file from the system file picker.
     */
    suspend fun readUri(uri: Uri, charset: Charset = StandardCharsets.UTF_8): NexusResult<String> = withContext(Dispatchers.IO) {
        runCatching {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BufferedReader(InputStreamReader(stream, charset)).readText()
            } ?: throw RuntimeException("Unable to open URI stream")
        }.let { if (it.isSuccess) NexusResult.Ok(it.getOrThrow()) else NexusResult.Err(it.exceptionOrNull()!!) }
    }
}
