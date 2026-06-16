package com.nexus.ide.features.editor.highlighter

import com.nexus.ide.features.editor.TextBuffer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Per-line highlighter cache.
 *
 * The editor pane is potentially thousands of lines tall but only
 * renders a small window. We tokenize on demand and cache the result
 * keyed by (lineIndex, contentHash). When the buffer mutates we evict
 * stale lines downstream of the change.
 */
class HighlightCache(private val lexer: Lexer) {

    private data class Entry(val source: String, val tokens: List<Token>, val hash: Int)

    private val cache = HashMap<Int, Entry>(1024)
    private val _stateVersion = MutableStateFlow(0L)
    val stateVersion: StateFlow<Long> = _stateVersion.asStateFlow()

    fun invalidateFromLine(line: Int) {
        // Drop entries from this line onwards
        val it = cache.entries.iterator()
        while (it.hasNext()) if (it.next().key >= line) it.remove()
        _stateVersion.value = _stateVersion.value + 1
    }

    fun invalidateAll() {
        cache.clear()
        _stateVersion.value = _stateVersion.value + 1
    }

    fun tokensFor(buffer: TextBuffer, line: Int): List<Token> {
        val src = buffer.getLine(line)
        val hash = src.hashCode()
        val existing = cache[line]
        if (existing != null && existing.hash == hash) return existing.tokens
        val tokens = lexer.lex(src)
        cache[line] = Entry(src, tokens, hash)
        return tokens
    }

    fun rangeTokensFor(buffer: TextBuffer, startLine: Int, endLine: Int): Map<Int, List<Token>> {
        val out = HashMap<Int, List<Token>>(endLine - startLine + 1)
        for (i in startLine..endLine) out[i] = tokensFor(buffer, i)
        return out
    }
}
