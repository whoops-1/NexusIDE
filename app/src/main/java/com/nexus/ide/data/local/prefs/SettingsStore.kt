package com.nexus.ide.data.local.prefs

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Plain (non-encrypted) settings store. Anything that is not a secret
 * lives here: theme, font size, recent paths, AI model choice, etc.
 *
 * Exposes a StateFlow for the active theme id so Compose can recompose
 * without each screen having to re-read prefs synchronously. The theme
 * id is resolved to an actual EditorTheme by the presentation layer via
 * findTheme(id) - this class intentionally has no dependency on Compose
 * or the theme package.
 */
class SettingsStore(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("nexus_settings", Context.MODE_PRIVATE)

    private val _themeId = MutableStateFlow(prefs.getString(KEY_THEME, null) ?: "github-dark")
    val themeId: StateFlow<String> = _themeId.asStateFlow()

    fun setThemeId(id: String) {
        prefs.edit { putString(KEY_THEME, id) }
        _themeId.value = id
    }

    var fontSizeSp: Float
        get() = prefs.getFloat(KEY_FONT_SIZE, 14f)
        set(v) { prefs.edit { putFloat(KEY_FONT_SIZE, v) } }

    var fontFamily: String
        get() = prefs.getString(KEY_FONT_FAMILY, "JetBrains Mono") ?: "JetBrains Mono"
        set(v) { prefs.edit { putString(KEY_FONT_FAMILY, v) } }

    var useLigatures: Boolean
        get() = prefs.getBoolean(KEY_LIGATURES, true)
        set(v) { prefs.edit { putBoolean(KEY_LIGATURES, v) } }

    var wordWrap: Boolean
        get() = prefs.getBoolean(KEY_WORD_WRAP, false)
        set(v) { prefs.edit { putBoolean(KEY_WORD_WRAP, v) } }

    var showMinimap: Boolean
        get() = prefs.getBoolean(KEY_MINIMAP, true)
        set(v) { prefs.edit { putBoolean(KEY_MINIMAP, v) } }

    var tabSize: Int
        get() = prefs.getInt(KEY_TAB_SIZE, 4)
        set(v) { prefs.edit { putInt(KEY_TAB_SIZE, v) } }

    var useSpaces: Boolean
        get() = prefs.getBoolean(KEY_USE_SPACES, true)
        set(v) { prefs.edit { putBoolean(KEY_USE_SPACES, v) } }

    var aiProvider: String
        get() = prefs.getString(KEY_AI_PROVIDER, "openai") ?: "openai"
        set(v) { prefs.edit { putString(KEY_AI_PROVIDER, v) } }

    var aiModel: String
        get() = prefs.getString(KEY_AI_MODEL, "gpt-4o-mini") ?: "gpt-4o-mini"
        set(v) { prefs.edit { putString(KEY_AI_MODEL, v) } }

    var termuxAutoDetect: Boolean
        get() = prefs.getBoolean(KEY_TERMUX_AUTO, true)
        set(v) { prefs.edit { putBoolean(KEY_TERMUX_AUTO, v) } }

    var biometricLock: Boolean
        get() = prefs.getBoolean(KEY_BIOMETRIC, false)
        set(v) { prefs.edit { putBoolean(KEY_BIOMETRIC, v) } }

    var telemetryEnabled: Boolean
        get() = prefs.getBoolean(KEY_TELEMETRY, false)
        set(v) { prefs.edit { putBoolean(KEY_TELEMETRY, v) } }

    /** MRU file list stored as newline-delimited absolute paths. */
    var recentFiles: List<String>
        get() = prefs.getString(KEY_RECENT_FILES, null)
            ?.lines()
            ?.filter { it.isNotBlank() }
            ?: emptyList()
        set(v) { prefs.edit { putString(KEY_RECENT_FILES, v.joinToString("\n")) } }

    /**
     * Names of agent tools (see [com.nexus.ide.features.agent.AgentTool.name])
     * the user has marked as trusted, so the agent runs them without asking
     * for per-call approval. Only tools that normally require approval
     * (write_file, run_command, delete_file, rename_file) are meaningful
     * here — read-only tools never gate on approval in the first place.
     */
    var toolAutoApprove: Set<String>
        get() = prefs.getString(KEY_TOOL_AUTO_APPROVE, null)
            ?.split(",")
            ?.filter { it.isNotBlank() }
            ?.toSet()
            ?: emptySet()
        set(v) { prefs.edit { putString(KEY_TOOL_AUTO_APPROVE, v.joinToString(",")) } }

    companion object {
        private const val KEY_THEME = "theme"
        private const val KEY_FONT_SIZE = "font_size"
        private const val KEY_FONT_FAMILY = "font_family"
        private const val KEY_LIGATURES = "ligatures"
        private const val KEY_WORD_WRAP = "word_wrap"
        private const val KEY_MINIMAP = "minimap"
        private const val KEY_TAB_SIZE = "tab_size"
        private const val KEY_USE_SPACES = "use_spaces"
        private const val KEY_AI_PROVIDER = "ai_provider"
        private const val KEY_AI_MODEL = "ai_model"
        private const val KEY_TERMUX_AUTO = "termux_auto"
        private const val KEY_BIOMETRIC = "biometric"
        private const val KEY_TELEMETRY = "telemetry"
        private const val KEY_RECENT_FILES = "recent_files"
        private const val KEY_TOOL_AUTO_APPROVE = "tool_auto_approve"
    }
}

// Extension used by RecentFiles to persist the MRU list
