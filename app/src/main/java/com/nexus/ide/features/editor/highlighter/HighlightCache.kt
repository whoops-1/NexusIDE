package com.nexus.ide.features.editor.highlighter

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
 *
 * Deliberately takes line text rather than a TextBuffer: the caller
 * (EditorView) already has the text on hand for rendering, so this
 * avoids a second line lookup per visible line per recomposition, and
 * keeps the cache decoupled from any particular buffer implementation.
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

    fun tokensFor(line: Int, text: String): List<Token> {
        val hash = text.hashCode()
        val existing = cache[line]
        if (existing != null && existing.hash == hash) return existing.tokens
        val tokens = lexer.lex(text)
        cache[line] = Entry(text, tokens, hash)
        return tokens
    }
}
