package com.nexus.ide.features.database

import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import com.nexus.ide.core.utils.Logger
import java.io.File

/**
 * Read-only SQLite inspector. Opens a .db / .sqlite file and lets the
 * UI list tables, view schemas, and run arbitrary SELECT statements.
 *
 * Uses Android's built-in [SQLiteDatabase] rather than a JDBC driver.
 * org.xerial:sqlite-jdbc loads its native library via System.loadLibrary(),
 * a desktop-style resolution mechanism that does not work inside Android's
 * app sandbox — it throws UnsatisfiedLinkError on every device, not as an
 * edge case. Android already ships SQLite natively, so there is nothing to
 * bundle.
 *
 * Opening with [SQLiteDatabase.OPEN_READONLY] makes "never allow writes"
 * an OS-enforced guarantee rather than just a comment: any write attempt
 * throws before it touches the file.
 */
class DatabaseService {

    data class TableInfo(val name: String, val rootPage: Int, val sql: String?)
    data class QueryResult(val columns: List<String>, val rows: List<List<String>>)

    private var db: SQLiteDatabase? = null

    fun open(file: File): Boolean {
        return try {
            db = SQLiteDatabase.openDatabase(file.absolutePath, null, SQLiteDatabase.OPEN_READONLY)
            true
        } catch (e: SQLiteException) {
            Logger.e("DB", "open failed", e); false
        }
    }

    fun listTables(): List<TableInfo> {
        val database = db ?: return emptyList()
        val out = mutableListOf<TableInfo>()
        database.rawQuery("SELECT name, rootpage, sql FROM sqlite_master WHERE type='table'", null).use { cursor ->
            while (cursor.moveToNext()) {
                out.add(TableInfo(cursor.getString(0), cursor.getInt(1), cursor.getString(2)))
            }
        }
        return out
    }

    fun query(sql: String): QueryResult {
        val database = db ?: return QueryResult(emptyList(), emptyList())
        return try {
            database.rawQuery(sql, null).use { cursor ->
                val cols = cursor.columnNames.toList()
                val rows = mutableListOf<List<String>>()
                while (cursor.moveToNext()) {
                    rows.add((0 until cursor.columnCount).map { idx -> cursor.getString(idx) ?: "" })
                }
                QueryResult(cols, rows)
            }
        } catch (e: SQLiteException) {
            Logger.e("DB", "query failed", e)
            QueryResult(listOf("error"), listOf(listOf(e.message ?: "error")))
        }
    }

    fun close() {
        try { db?.close() } catch (_: Exception) {}
        db = null
    }
}
