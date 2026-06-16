package com.nexus.ide.core.utils

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Process-wide event bus used for things that are not worth a dedicated
 * coroutine scope, e.g. "file changed on disk" notifications.
 */
object AppBus {
    private val _events = MutableSharedFlow<AppEvent>(extraBufferCapacity = 64)
    val events: Flow<AppEvent> = _events.asSharedFlow()

    fun emit(e: AppEvent) { _events.tryEmit(e) }
}

sealed interface AppEvent {
    data class FileChanged(val path: String) : AppEvent
    data class GitStatusChanged(val root: String) : AppEvent
    data class TermuxStateChanged(val available: Boolean) : AppEvent
    data class ProjectOpened(val projectId: Long) : AppEvent
    data class ProjectClosed(val projectId: Long) : AppEvent
    data class Error(val tag: String, val message: String) : AppEvent
}
