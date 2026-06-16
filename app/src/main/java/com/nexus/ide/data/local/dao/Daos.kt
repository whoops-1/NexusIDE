package com.nexus.ide.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.nexus.ide.data.local.entities.AiMessageEntity
import com.nexus.ide.data.local.entities.GitCommitEntity
import com.nexus.ide.data.local.entities.PluginEntity
import com.nexus.ide.data.local.entities.ProjectEntity
import com.nexus.ide.data.local.entities.SnippetEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ProjectDao {
    @Query("SELECT * FROM projects ORDER BY pinned DESC, lastOpened DESC") fun observeAll(): Flow<List<ProjectEntity>>
    @Query("SELECT * FROM projects WHERE id = :id") suspend fun byId(id: Long): ProjectEntity?
    @Query("SELECT * FROM projects WHERE rootPath = :path LIMIT 1") suspend fun byPath(path: String): ProjectEntity?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(p: ProjectEntity): Long
    @Query("DELETE FROM projects WHERE id = :id") suspend fun delete(id: Long)
    @Query("UPDATE projects SET lastOpened = :ts WHERE id = :id") suspend fun touch(id: Long, ts: Long = System.currentTimeMillis())
    @Query("UPDATE projects SET pinned = :pinned WHERE id = :id") suspend fun setPinned(id: Long, pinned: Boolean)
}

@Dao
interface SnippetDao {
    @Query("SELECT * FROM snippets WHERE builtin = 1 OR lang = :lang OR lang = '*' ORDER BY builtin DESC, name") fun observe(lang: String): Flow<List<SnippetEntity>>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(s: SnippetEntity): Long
    @Query("DELETE FROM snippets WHERE id = :id AND builtin = 0") suspend fun delete(id: Long)
}

@Dao
interface GitCommitDao {
    @Query("SELECT * FROM git_commits WHERE projectRoot = :root ORDER BY timestamp DESC LIMIT :limit")
    suspend fun recent(root: String, limit: Int = 200): List<GitCommitEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertAll(commits: List<GitCommitEntity>)
    @Query("DELETE FROM git_commits WHERE projectRoot = :root") suspend fun clear(root: String)
}

@Dao
interface PluginDao {
    @Query("SELECT * FROM plugins ORDER BY enabled DESC, name") fun observeAll(): Flow<List<PluginEntity>>
    @Query("SELECT * FROM plugins WHERE id = :id") suspend fun byId(id: String): PluginEntity?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(p: PluginEntity)
    @Query("UPDATE plugins SET enabled = :enabled WHERE id = :id") suspend fun setEnabled(id: String, enabled: Boolean)
    @Query("DELETE FROM plugins WHERE id = :id") suspend fun delete(id: String)
}

@Dao
interface AiMessageDao {
    @Query("SELECT * FROM ai_messages WHERE conversationId = :cid ORDER BY ts") suspend fun messages(cid: String): List<AiMessageEntity>
    @Query("SELECT DISTINCT conversationId FROM ai_messages ORDER BY ts DESC") fun observeConversations(): Flow<List<String>>
    @Insert suspend fun insert(m: AiMessageEntity): Long
    @Query("DELETE FROM ai_messages WHERE conversationId = :cid") suspend fun clear(cid: String)
    @Query("DELETE FROM ai_messages WHERE ts < :beforeMs") suspend fun prune(beforeMs: Long)
}
