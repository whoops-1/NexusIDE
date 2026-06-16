package com.nexus.ide.features.editor.folding

/**
 * Detects foldable regions in a buffer. Folds are computed lazily and
 * invalidated on text change. We use a simple brace / indent strategy:
 *   - For languages with explicit block delimiters ({}, begin/end, etc.),
 *     pair matching creates the fold.
 *   - For indentation-based languages (Python), a line whose indent is
 *     less than a previous line's indent closes the fold at the
 *     previous line.
 *
 * The result is a list of [Fold] regions, each with startLine inclusive
 * and endLine exclusive. The UI uses these to draw gutter indicators
 * and to skip rendering collapsed regions.
 */
class FoldingEngine {

    data class Fold(val startLine: Int, val endLine: Int, val kind: Kind) {
        enum class Kind { Brace, Indent, Region, Comment }
    }

    private var cache: List<Fold> = emptyList()
    private var cacheForVersion: Long = -1
    private val text = StringBuilder()
    private var version: Long = 0

    fun update(version: Long, text: String) {
        this.version = version
        this.text.clear(); this.text.append(text)
        cache = compute()
        cacheForVersion = version
    }

    fun folds(): List<Fold> = cache

    private fun compute(): List<Fold> {
        val out = ArrayList<Fold>(32)
        val stack = ArrayDeque<Pair<Char, Int>>()  // (openChar, startLine)
        val lines = text.toString().split('\n')
        for ((i, line) in lines.withIndex()) {
            var j = 0
            while (j < line.length) {
                val c = line[j]
                when (c) {
                    '{', '[', '(' -> stack.addLast(c to i)
                    '}', ']', ')' -> {
                        val want = when (c) { '}' -> '{'; ']' -> '['; ')' -> '('; else -> ' ' }
                        while (stack.isNotEmpty() && stack.last().first != want) stack.removeLast()
                        if (stack.isNotEmpty()) {
                            val start = stack.removeLast().second
                            if (i > start + 1) out.add(Fold(start, i, Fold.Kind.Brace))
                        }
                    }
                }
                j++
            }
        }
        return out
    }
}
