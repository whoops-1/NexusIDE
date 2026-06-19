package com.nexus.ide.core.di

import android.content.Context
import androidx.compose.runtime.staticCompositionLocalOf
import com.nexus.ide.data.local.db.NexusDatabase
import com.nexus.ide.data.local.prefs.SecureStore
import com.nexus.ide.data.local.prefs.SettingsStore
import com.nexus.ide.features.ai.ChatSession
import com.nexus.ide.features.filesystem.RecentFiles
import com.nexus.ide.features.filesystem.WorkspaceService
import com.nexus.ide.features.terminal.TermuxBridge

/**
 * Lightweight dependency container.
 *
 * We use a hand-rolled service locator instead of Hilt / Koin for two
 * reasons:
 *   1. Cold-start budget is < 1 s. Hilt's generated graph has non-trivial
 *      cold cost; a plain object graph lets us construct only what the
 *      current screen needs (see [scope]).
 *   2. The IDE has many *optional* modules (database tools, debugger,
 *      web preview) — the locator pattern makes it trivial to defer
 *      construction until the user actually opens the feature.
 */
object ServiceLocator {

    private lateinit var appContext: Context

    // Core / always-on
    val database: NexusDatabase by lazy { NexusDatabase.build(appContext) }
    val secureStore: SecureStore by lazy { SecureStore(appContext) }
    val settings: SettingsStore by lazy { SettingsStore(appContext) }
    val termux: TermuxBridge by lazy { TermuxBridge(appContext) }
    val workspace: WorkspaceService by lazy { WorkspaceService(appContext) }
    val recents: RecentFiles by lazy { RecentFiles(appContext, settings) }
    val chatSession: ChatSession by lazy { ChatSession(database.aiMessageDao()) }

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    fun context(): Context = appContext
}

val LocalServiceLocator = staticCompositionLocalOf<ServiceLocator> { error("ServiceLocator not provided") }
