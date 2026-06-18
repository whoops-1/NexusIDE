package com.nexus.ide.features.filesystem

import android.content.Context
import android.net.Uri
import com.nexus.ide.core.utils.Logger
import com.nexus.ide.core.utils.NxResult
import com.nexus.ide.domain.models.FsEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import java.text.DateFormat
import java.util.Date
import kotlin.math.ln
import kotlin.math.pow

/**
 * Filesystem operations for the IDE. Reads/writes text files, walks
 * directory trees, and exposes high-level operations (copy/move/delete)
 * used by the file explorer.
 *
 * Threading: deep tree walks and disk I/O are dispatched to
 * [Dispatchers.IO] via suspend functions. [listDirectory] is a shallow,
 * synchronous single-level read - cheap enough (one [File.listFiles]
 * call) to call directly from Compose state initializers and click
 * handlers without a coroutine scope.
 */
class WorkspaceService(private val context: Context) {

    /** Root directory for the IDE's primary workspace, inside app private storage. */
    val workspaceRoot: File = File(context.filesDir, "workspace").apply { if (!exists()) mkdirs() }

    data class Entry(val file: File, val sizeLabel: String, val modifiedLabel: String)

    /** Shallow single-level directory listing. Synchronous - safe to call from Compose state. */
    fun listDirectory(dir: File): List<Entry> {
        val children = dir.listFiles() ?: return emptyList()
        return children
            .filter { !it.name.startsWith(".") }
            .sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
            .map { f ->
                Entry(
                    file = f,
                    sizeLabel = if (f.isDirectory) "" else humanSize(f.length()),
                    modifiedLabel = humanTime(f.lastModified())
                )
            }
    }

    suspend fun readText(file: File, charset: Charset = StandardCharsets.UTF_8): NxResult<String> = withContext(Dispatchers.IO) {
        runCatching {
            file.inputStream().use { it.readBytes().toString(charset) }
        }.onFailure { Logger.e("WS", "readText failed: $file", it) }
            .let { if (it.isSuccess) NxResult.Ok(it.getOrThrow()) else NxResult.Err(it.exceptionOrNull()!!) }
    }

    suspend fun writeText(file: File, content: String, charset: Charset = StandardCharsets.UTF_8): NxResult<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            file.parentFile?.mkdirs()
            file.outputStream().use { it.write(content.toByteArray(charset)) }
        }.onFailure { Logger.e("WS", "writeText failed: $file", it) }
            .let { if (it.isSuccess) NxResult.Ok(Unit) else NxResult.Err(it.exceptionOrNull()!!) }
    }

    /** Deep recursive tree walk - used for project-wide search/indexing, not the file explorer UI. */
    suspend fun listTree(root: File, maxDepth: Int = 12, hidden: Boolean = false): NxResult<List<FsEntry>> = withContext(Dispatchers.IO) {
        runCatching {
            val out = ArrayList<FsEntry>(128)
            walkInto(root, out, 0, maxDepth, hidden)
            out
        }.let { if (it.isSuccess) NxResult.Ok(it.getOrThrow()) else NxResult.Err(it.exceptionOrNull()!!) }
    }

    private fun walkInto(dir: File, out: MutableList<FsEntry>, depth: Int, maxDepth: Int, hidden: Boolean) {
        if (depth > maxDepth) return
        val children = dir.listFiles() ?: return
        children.sortBy { it.name.lowercase() }
        for (child in children) {
            if (!hidden && child.name.startsWith(".")) continue
            if (child.name == "build" || child.name == ".gradle" || child.name == "node_modules") continue
            val childCount = if (child.isDirectory) child.listFiles()?.size ?: 0 else 0
            out.add(
                FsEntry(
                    file = child,
                    name = child.name,
                    isDir = child.isDirectory,
                    size = if (child.isFile) child.length() else 0L,
                    lastModified = child.lastModified(),
                    children = childCount
                )
            )
            if (child.isDirectory) walkInto(child, out, depth + 1, maxDepth, hidden)
        }
    }

    suspend fun mkdirs(path: File): NxResult<Unit> = withContext(Dispatchers.IO) {
        runCatching { path.mkdirs() }.let { if (it.isSuccess) NxResult.Ok(Unit) else NxResult.Err(it.exceptionOrNull()!!) }
    }

    suspend fun delete(file: File): NxResult<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            if (file.isDirectory) file.deleteRecursively() else file.delete()
        }.let { if (it.isSuccess) NxResult.Ok(Unit) else NxResult.Err(it.exceptionOrNull()!!) }
    }

    suspend fun copy(src: File, dest: File): NxResult<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            dest.parentFile?.mkdirs()
            if (src.isDirectory) src.copyRecursively(dest, overwrite = true)
            else { FileInputStream(src).use { input -> FileOutputStream(dest).use { out -> input.copyTo(out) } } }
        }.let { if (it.isSuccess) NxResult.Ok(Unit) else NxResult.Err(it.exceptionOrNull()!!) }
    }

    suspend fun rename(file: File, newName: String): NxResult<File> = withContext(Dispatchers.IO) {
        runCatching {
            val target = File(file.parentFile, newName)
            if (file.renameTo(target)) target else throw RuntimeException("rename failed")
        }.let { if (it.isSuccess) NxResult.Ok(it.getOrThrow()) else NxResult.Err(it.exceptionOrNull()!!) }
    }

    /**
     * Read the content of a content:// Uri (e.g. from SAF). Used when
     * the user opens a file from the system file picker.
     */
    suspend fun readUri(uri: Uri, charset: Charset = StandardCharsets.UTF_8): NxResult<String> = withContext(Dispatchers.IO) {
        runCatching {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BufferedReader(InputStreamReader(stream, charset)).readText()
            } ?: throw RuntimeException("Unable to open URI stream")
        }.let { if (it.isSuccess) NxResult.Ok(it.getOrThrow()) else NxResult.Err(it.exceptionOrNull()!!) }
    }

    private fun humanSize(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        val units = arrayOf("KB", "MB", "GB", "TB")
        val exp = (ln(bytes.toDouble()) / ln(1024.0)).toInt().coerceIn(1, units.size)
        val value = bytes / 1024.0.pow(exp.toDouble())
        return "%.1f %s".format(value, units[exp - 1])
    }

    private fun humanTime(millis: Long): String =
        DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(millis))
}
