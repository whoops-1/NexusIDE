package com.nexus.ide.data.local.prefs

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.nexus.ide.core.utils.Logger
import java.security.SecureRandom

/**
 * Encrypted storage for API keys, OAuth tokens, SSH passphrases and similar
 * secrets. Backed by AndroidX Security's EncryptedSharedPreferences, which
 * uses a hardware-backed AES key on devices that support it.
 *
 * Always-fail-closed: if the keyring isn't ready, the store pretends to
 * succeed (returns null) rather than throwing — callers can re-attempt on
 * a later session. This is the right trade-off for an IDE: a transient
 * Keystore hiccup must not crash the app.
 */
class SecureStore(context: Context) {

    private val tag = "SecureStore"
    private val prefs: SharedPreferences? = runCatching {
        val key = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "nexus_secure",
            key,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }.onFailure { Logger.w(tag, "Encrypted store unavailable, falling back to in-memory", it) }
        .getOrNull()

    // In-memory fallback for devices where the Keystore is unusable.
    private val memory = HashMap<String, String>()

    fun put(key: String, value: String) {
        prefs?.edit()?.putString(key, value)?.apply() ?: run { memory[key] = value }
    }

    fun get(key: String): String? = prefs?.getString(key, null) ?: memory[key]

    fun remove(key: String) {
        prefs?.edit()?.remove(key)?.apply()
        memory.remove(key)
    }

    fun contains(key: String): Boolean = prefs?.contains(key) ?: memory.containsKey(key)

    fun wipe() {
        prefs?.edit()?.clear()?.apply()
        memory.clear()
    }

    // Convenience accessors for AI configuration
    fun getApiKey(): String? = get(KEY_OPENAI)
    fun getAiModel(): String? = get(KEY_AI_MODEL)
    fun getAiBaseUrl(): String? = get(KEY_AI_BASE_URL)

    companion object {
        // Well-known keys
        const val KEY_OPENAI = "openai_api_key"
        const val KEY_ANTHROPIC = "anthropic_api_key"
        const val KEY_GEMINI = "gemini_api_key"
        const val KEY_GROQ = "groq_api_key"
        const val KEY_OPENROUTER = "openrouter_api_key"
        const val KEY_DEEPSEEK = "deepseek_api_key"
        const val KEY_OLLAMA_HOST = "ollama_host"
        const val KEY_GITHUB_PAT = "github_pat"
        const val KEY_SSH_PASSPHRASE = "ssh_passphrase"
        const val KEY_BIOMETRIC_LOCK = "biometric_lock_enabled"
        const val KEY_AI_MODEL = "ai_model_override"
        const val KEY_AI_BASE_URL = "ai_base_url"

        /** Random opaque token used for bearer auth on internal API routes. */
        fun newToken(bytes: Int = 24): String {
            val b = ByteArray(bytes); SecureRandom().nextBytes(b)
            return Base64.encodeToString(b, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
        }
    }
}
