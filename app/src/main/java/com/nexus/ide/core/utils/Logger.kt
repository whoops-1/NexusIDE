package com.nexus.ide.core.utils

import android.util.Log
import com.nexus.ide.BuildConfig

/**
 * Thin wrapper around android.util.Log that:
 *  - drops verbose/log in release builds
 *  - tags every line with "Nexus/<tag>"
 *  - keeps a small in-memory ring buffer for the in-app Log viewer
 */
object Logger {

    private const val MAX_RING = 1024
    private val ring = ArrayDeque<Entry>(MAX_RING)
    private val lock = Any()

    data class Entry(val ts: Long, val level: Int, val tag: String, val msg: String)

    fun v(tag: String, msg: String) { if (BuildConfig.DEBUG) log(Log.VERBOSE, tag, msg) }
    fun d(tag: String, msg: String) { if (BuildConfig.DEBUG) log(Log.DEBUG, tag, msg) }
    fun i(tag: String, msg: String) { log(Log.INFO, tag, msg) }
    fun w(tag: String, msg: String, t: Throwable? = null) { log(Log.WARN, tag, msg, t) }
    fun e(tag: String, msg: String, t: Throwable? = null) { log(Log.ERROR, tag, msg, t) }

    fun recent(limit: Int = 200): List<Entry> = synchronized(lock) { ring.toList().takeLast(limit) }

    private fun log(level: Int, tag: String, msg: String, t: Throwable? = null) {
        val fullTag = "Nexus/$tag"
        when (level) {
            Log.VERBOSE -> Log.v(fullTag, msg)
            Log.DEBUG -> Log.d(fullTag, msg)
            Log.INFO -> Log.i(fullTag, msg)
            Log.WARN -> if (t != null) Log.w(fullTag, msg, t) else Log.w(fullTag, msg)
            Log.ERROR -> if (t != null) Log.e(fullTag, msg, t) else Log.e(fullTag, msg)
        }
        synchronized(lock) {
            if (ring.size >= MAX_RING) ring.removeFirst()
            ring.addLast(Entry(System.currentTimeMillis(), level, fullTag, msg + (t?.let { " :: ${it.javaClass.simpleName}: ${it.message}" } ?: "")))
        }
    }
}
