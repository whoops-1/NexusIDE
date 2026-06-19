package com.nexus.ide.features.ai

import android.content.Context
import com.nexus.ide.data.local.dao.AiMessageDao
import com.nexus.ide.data.local.entities.AiMessageEntity
import com.nexus.ide.data.local.prefs.SecureStore
import com.nexus.ide.data.local.prefs.SettingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * Persistent chat history backed by Room.
 *
 * A "conversation" is identified by a UUID string. Messages within a
 * conversation are ordered by insertion time. This layer is intentionally
 * thin — it is not a repository in the Clean-Architecture sense, just a
 * direct wrapper around the DAO that avoids leaking Room types into the
 * ViewModel.
 */
class ChatSession(private val dao: AiMessageDao) {

    data class Conversation(
        val id: String,
        val preview: String,   // first user message, truncated
        val messageCount: Int,
        val lastMs: Long
    )

    data class Msg(
        val id: Long,
        val conversationId: String,
        val role: String,       // "user" | "assistant" | "system"
        val content: String,
        val model: String?,
        val ts: Long
    )

    /** Observe the list of distinct conversation IDs, newest first. */
    fun observeConversations(): Flow<List<String>> = dao.observeConversations()

    suspend fun loadMessages(conversationId: String): List<Msg> = withContext(Dispatchers.IO) {
        dao.messages(conversationId).map { it.toMsg() }
    }

    suspend fun insert(conversationId: String, role: String, content: String, model: String? = null): Msg =
        withContext(Dispatchers.IO) {
            val entity = AiMessageEntity(
                conversationId = conversationId,
                role = role,
                content = content,
                model = model
            )
            val id = dao.insert(entity)
            entity.copy(id = id).toMsg()
        }

    suspend fun deleteConversation(conversationId: String) = withContext(Dispatchers.IO) {
        dao.clear(conversationId)
    }

    suspend fun pruneOlderThan(days: Int) = withContext(Dispatchers.IO) {
        val cutoff = System.currentTimeMillis() - days * 86_400_000L
        dao.prune(cutoff)
    }

    fun newConversationId(): String = UUID.randomUUID().toString()

    private fun AiMessageEntity.toMsg() = Msg(
        id = id,
        conversationId = conversationId,
        role = role,
        content = content,
        model = model,
        ts = ts
    )
}
