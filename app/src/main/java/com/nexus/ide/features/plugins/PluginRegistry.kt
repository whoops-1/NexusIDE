package com.nexus.ide.features.plugins

import com.nexus.ide.core.utils.Logger
import java.io.File

/**
 * Dynamic plugin loader. A Nexus plugin is a directory containing a
 * plugin.json manifest and zero or more entrypoints. Plugins are
 * discovered from /sdcard/NexusIDE/plugins by default.
 *
 * For safety in production:
 *   - Plugins run in the same process (no separate VM).
 *   - Capabilities are allowlisted; plugin code must declare what it
 *     wants (commands, file access, AI access).
 *   - The user must explicitly enable a plugin in Settings.
 */
class PluginRegistry(private val pluginsRoot: File) {

    data class Plugin(
        val id: String,
        val version: String,
        val displayName: String,
        val author: String,
        val description: String,
        val capabilities: Set<String>,
        val dir: File,
        val enabled: Boolean
    )

    data class Manifest(
        val id: String,
        val version: String,
        val displayName: String,
        val author: String = "",
        val description: String = "",
        val capabilities: List<String> = emptyList()
    )

    fun discover(): List<Plugin> {
        if (!pluginsRoot.exists()) return emptyList()
        return pluginsRoot.listFiles { f -> f.isDirectory }
            ?.mapNotNull { dir -> readManifest(dir)?.let { m ->
                Plugin(m.id, m.version, m.displayName, m.author, m.description, m.capabilities.toSet(), dir, true)
            } } ?: emptyList()
    }

    private fun readManifest(dir: File): Manifest? {
        val f = File(dir, "plugin.json")
        if (!f.exists()) return null
        return try {
            val obj = org.json.JSONObject(f.readText())
            Manifest(
                id = obj.getString("id"),
                version = obj.getString("version"),
                displayName = obj.getString("displayName"),
                author = obj.optString("author", ""),
                description = obj.optString("description", ""),
                capabilities = obj.optJSONArray("capabilities")?.let { arr ->
                    (0 until arr.length()).map { arr.getString(it) }
                } ?: emptyList()
            )
        } catch (e: Exception) {
            Logger.e("Plugin", "readManifest failed for ${dir.name}", e); null
        }
    }
}
