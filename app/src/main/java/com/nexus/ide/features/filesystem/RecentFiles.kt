package com.nexus.ide.features.filesystem

import android.content.Context
import com.nexus.ide.data.local.prefs.SettingsStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Tracks the most recently opened files for the home dashboard and
 * "Recent" tab in the explorer.
 */
class RecentFiles(context: Context, private val settings: SettingsStore) {
    private val _recent = MutableStateFlow<List<String>>(loadFromSettings())
    val recent: StateFlow<List<String>> = _recent

    private fun loadFromSettings(): List<String> = settings.recentFiles

    fun add(path: String) {
        if (path.isBlank()) return
        val current = _recent.value.toMutableList()
        current.remove(path)
        current.add(0, path)
        if (current.size > 20) current.subList(20, current.size).clear()
        _recent.value = current
        settings.recentFiles = current
    }

    fun clear() {
        _recent.value = emptyList()
        settings.recentFiles = emptyList()
    }
}
