package com.nexus.ide.presentation.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexus.ide.core.di.ServiceLocator
import com.nexus.ide.data.local.prefs.SettingsStore
import com.nexus.ide.features.editor.EditorSession
import com.nexus.ide.features.filesystem.RecentFiles
import com.nexus.ide.features.filesystem.WorkspaceService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

class ProjectViewModel : ViewModel() {
    private val workspace: WorkspaceService = ServiceLocator.workspace
    private val recents: RecentFiles = ServiceLocator.recents
    private val settings: SettingsStore = ServiceLocator.settings

    /** An open editor tab. Purely a UI-tracking concern for this ViewModel — not a domain model. */
    data class Tab(val file: File, val displayName: String, val isDirty: Boolean)

    private val _openFiles = MutableStateFlow<List<Tab>>(emptyList())
    val openFiles: StateFlow<List<Tab>> = _openFiles.asStateFlow()

    private val _activeIndex = MutableStateFlow(-1)
    val activeIndex: StateFlow<Int> = _activeIndex.asStateFlow()

    val activeFile: StateFlow<Tab?> = combine(_openFiles, _activeIndex) { files, idx ->
        files.getOrNull(idx.coerceIn(-1, files.lastIndex))
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _sessions = mutableMapOf<File, EditorSession>()
    val sessions: Map<File, EditorSession> get() = _sessions

    init {
        // Restore last session — skip files that no longer exist on disk
        val lastPaths = settings.lastOpenFiles
        val lastIdx = settings.lastActiveIndex
        if (lastPaths.isNotEmpty()) {
            val validFiles = lastPaths.mapNotNull { path ->
                File(path).takeIf { it.exists() && it.isFile }
            }
            if (validFiles.isNotEmpty()) {
                val tabs = validFiles.map { f ->
                    Tab(file = f, displayName = f.name, isDirty = false)
                }
                _openFiles.value = tabs
                _activeIndex.value = lastIdx.coerceIn(0, tabs.lastIndex)
            }
        }

        // Auto-save session state whenever tabs or active index change
        _openFiles.drop(1).onEach { tabs ->
            settings.lastOpenFiles = tabs.map { it.file.absolutePath }
        }.launchIn(viewModelScope)

        _activeIndex.drop(1).onEach { idx ->
            settings.lastActiveIndex = idx.coerceAtLeast(0)
        }.launchIn(viewModelScope)
    }

    fun sessionFor(file: File): EditorSession = _sessions.getOrPut(file) {
        val initial = runCatching { file.readText() }.getOrDefault("")
        EditorSession(file = file, initialText = initial)
    }

    fun openFile(file: File) {
        if (file.isDirectory) return
        val existing = _openFiles.value.indexOfFirst { it.file == file }
        if (existing >= 0) { _activeIndex.value = existing; return }
        sessionFor(file)  // pre-warm session
        val tab = Tab(file = file, displayName = file.name, isDirty = false)
        _openFiles.value = _openFiles.value + tab
        _activeIndex.value = _openFiles.value.lastIndex
        recents.add(file.absolutePath)
    }

    fun closeFile(index: Int) {
        val files = _openFiles.value
        if (index !in files.indices) return
        val removed = files[index]
        _sessions.remove(removed.file)
        _openFiles.value = files.toMutableList().also { it.removeAt(index) }
        _activeIndex.value = _activeIndex.value.coerceAtMost(_openFiles.value.lastIndex).coerceAtLeast(-1)
    }

    fun setActive(index: Int) { _activeIndex.value = index }

    fun saveActive() {
        val active = activeFile.value ?: return
        val session = sessionFor(active.file)
        viewModelScope.launch(Dispatchers.IO) {
            val text = session.buffer.getText()
            workspace.writeText(active.file, text)
        }
        session.markSaved()
        // Flip isDirty off in the tab list too
        _openFiles.value = _openFiles.value.mapIndexed { i, tab ->
            if (i == _activeIndex.value) tab.copy(isDirty = false) else tab
        }
    }

    /** Call this when an edit happens in a session so the tab dirty-dot lights up. */
    fun markDirty(file: File) {
        _openFiles.value = _openFiles.value.map { tab ->
            if (tab.file == file) tab.copy(isDirty = true) else tab
        }
    }
}
