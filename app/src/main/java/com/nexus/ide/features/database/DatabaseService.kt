package com.nexus.ide.features.database

import com.nexus.ide.core.utils.Logger
import java.io.File

/**
 * Read-only SQLite inspector. Opens a .db / .sqlite file and lets the
 * UI list tables, view schemas, and run arbitrary SELECT statements.
 * We never allow writes to the user's database from the IDE.
 */
class DatabaseService {

    data class TableInfo(val name: String, val rootPage: Int, val sql: String?)
    data class QueryResult(val columns: List<String>, val rows: List<List<String>>)

    private var native: java.sql.Connection? = null

    fun open(file: File): Boolean {
        return try {
            Class.forName("org.sqlite.JDBC")
            native = java.sql.DriverManager.getConnection("jdbc:sqlite:${file.absolutePath}")
            true
        } catch (e: Exception) {
            Logger.e("DB", "open failed", e); false
        }
    }

    fun listTables(): List<TableInfo> {
        val conn = native ?: return emptyList()
        val out = mutableListOf<TableInfo>()
        conn.createStatement().executeQuery("SELECT name, rootpage, sql FROM sqlite_master WHERE type='table'").use { rs ->
            while (rs.next()) {
                out.add(TableInfo(rs.getString(1), rs.getInt(2), rs.getString(3)))
            }
        }
        return out
    }

    fun query(sql: String): QueryResult {
        val conn = native ?: return QueryResult(emptyList(), emptyList())
        return try {
            conn.prepareStatement(sql).executeQuery().use { rs ->
                val meta = rs.metaData
                val cols = (1..meta.columnCount).map { meta.getColumnLabel(it) }
                val rows = mutableListOf<List<String>>()
                while (rs.next()) rows.add((1..meta.columnCount).map { rs.getString(it) ?: "" })
                QueryResult(cols, rows)
            }
        } catch (e: Exception) {
            Logger.e("DB", "query failed", e)
            QueryResult(listOf("error"), listOf(listOf(e.message ?: "error")))
        }
    }

    fun close() {
        try { native?.close() } catch (_: Exception) {}
        native = null
    }
}
