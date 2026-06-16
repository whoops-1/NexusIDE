package com.nexus.ide.presentation.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexus.ide.core.di.ServiceLocator
import com.nexus.ide.data.local.prefs.SettingsStore
import com.nexus.ide.domain.models.OpenFile
import com.nexus.ide.features.editor.EditorSession
import com.nexus.ide.features.filesystem.RecentFiles
import com.nexus.ide.features.filesystem.WorkspaceService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

class ProjectViewModel : ViewModel() {
    private val workspace: WorkspaceService = ServiceLocator.workspace
    private val recents: RecentFiles = ServiceLocator.recents
    private val settings: SettingsStore = ServiceLocator.settings

    private val _openFiles = MutableStateFlow<List<OpenFile>>(emptyList())
    val openFiles: StateFlow<List<OpenFile>> = _openFiles.asStateFlow()

    private val _activeIndex = MutableStateFlow(-1)
    val activeIndex: StateFlow<Int> = _activeIndex.asStateFlow()

    val activeFile: StateFlow<OpenFile?> = combine(_openFiles, _activeIndex) { files, idx ->
        files.getOrNull(idx.coerceIn(-1, files.lastIndex))
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _sessions = mutableMapOf<File, EditorSession>()
    val sessions: Map<File, EditorSession> get() = _sessions

    fun sessionFor(file: File): EditorSession = _sessions.getOrPut(file) {
        val initial = runCatching { file.readText() }.getOrDefault("")
        EditorSession(file = file, initialText = initial)
    }

    fun openFile(file: File) {
        if (file.isDirectory) return
        val existing = _openFiles.value.indexOfFirst { it.file == file }
        if (existing >= 0) { _activeIndex.value = existing; return }
        val session = sessionFor(file)
        val openFile = OpenFile(file = file, displayName = file.name, isDirty = false)
        _openFiles.value = _openFiles.value + openFile
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
    }
}
