package com.nexus.ide.data.local.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.sqlite.db.SupportSQLiteDatabase
import com.nexus.ide.data.local.dao.AiMessageDao
import com.nexus.ide.data.local.dao.GitCommitDao
import com.nexus.ide.data.local.dao.PluginDao
import com.nexus.ide.data.local.dao.ProjectDao
import com.nexus.ide.data.local.dao.SnippetDao
import com.nexus.ide.data.local.entities.AiMessageEntity
import com.nexus.ide.data.local.entities.GitCommitEntity
import com.nexus.ide.data.local.entities.PluginEntity
import com.nexus.ide.data.local.entities.ProjectEntity
import com.nexus.ide.data.local.entities.SnippetEntity

/**
 * Single Room database for all persistent state. Designed to be small
 * (the editor itself never touches the DB for file content) and fast on
 * cold start. We use the *manual* migration / fallback-to-destruct policy
 * for early versions — there is no user data we cannot recreate from the
 * filesystem.
 */
@Database(
    entities = [
        ProjectEntity::class,
        SnippetEntity::class,
        GitCommitEntity::class,
        PluginEntity::class,
        AiMessageEntity::class,
    ],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class NexusDatabase : RoomDatabase() {

    abstract fun projectDao(): ProjectDao
    abstract fun snippetDao(): SnippetDao
    abstract fun gitCommitDao(): GitCommitDao
    abstract fun pluginDao(): PluginDao
    abstract fun aiMessageDao(): AiMessageDao

    companion object {
        fun build(context: Context): NexusDatabase = Room
            .databaseBuilder(context, NexusDatabase::class.java, "nexus.db")
            .fallbackToDestructiveMigration()
            .addCallback(object : RoomDatabase.Callback() {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    // Seed built-in snippets
                    db.execSQL(
                        """INSERT INTO snippets(name,prefix,body,lang,builtin) VALUES
                          ('Println', 'pln', 'println!("', 'rust', 1),
                          ('For loop', 'forof', 'for (const ${'$'}{1:x} of ${'$'}{2:arr}) {\n  ${'$'}0\n}', 'js', 1),
                          ('Main', 'main', 'fn main() {\n    ${'$'}0\n}', 'rust', 1)
                        """.trimIndent()
                    )
                }
            })
            .build()
    }
}

class Converters {
    @TypeConverter fun fromList(value: List<String>?): String? = value?.joinToString("")
    @TypeConverter fun toList(value: String?): List<String> =
        if (value.isNullOrEmpty()) emptyList() else value.split('')
}
