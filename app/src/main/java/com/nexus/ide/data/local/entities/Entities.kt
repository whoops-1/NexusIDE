package com.nexus.ide.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "projects")
data class ProjectEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val rootPath: String,
    val template: String? = null,
    val lastOpened: Long = System.currentTimeMillis(),
    val created: Long = System.currentTimeMillis(),
    val pinned: Boolean = false,
    val iconColor: Int = 0xFF58A6FF.toInt()
)

@Entity(tableName = "snippets")
data class SnippetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val prefix: String,
    val body: String,
    val lang: String,
    val builtin: Boolean = false
)

@Entity(tableName = "git_commits")
data class GitCommitEntity(
    @PrimaryKey val hash: String,
    val projectRoot: String,
    val author: String,
    val message: String,
    val timestamp: Long,
    val parents: String // newline-separated
)

@Entity(tableName = "plugins")
data class PluginEntity(
    @PrimaryKey val id: String,
    val name: String,
    val version: String,
    val author: String,
    val description: String,
    val sizeKb: Int,
    val enabled: Boolean = false,
    val installed: Boolean = false
)

@Entity(tableName = "ai_messages")
data class AiMessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val conversationId: String,
    val role: String, // "user" | "assistant" | "system"
    val content: String,
    val model: String? = null,
    val tokensIn: Int = 0,
    val tokensOut: Int = 0,
    val ts: Long = System.currentTimeMillis()
)
