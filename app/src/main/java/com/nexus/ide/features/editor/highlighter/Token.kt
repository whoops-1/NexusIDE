package com.nexus.ide.features.editor.highlighter

/**
 * A single token produced by a [Lexer].
 *
 *  - `start` and `end` are character offsets into the source string
 *  - `kind` picks the theme color (see ThemeColors in domain models)
 *  - `modifiers` are optional flags (e.g. for declaration vs reference)
 *
 * Tokens are intentionally flat — no linked lists, no allocation per
 * character, no per-line state for languages we can lex without context.
 * That keeps the highlighter at O(n) with very low constant cost.
 */
data class Token(
    val start: Int,
    val end: Int,
    val kind: TokenKind,
    val modifier: TokenModifier = TokenModifier.None
)

enum class TokenKind {
    Keyword, String, Number, Comment, Function, Type, Variable,
    Operator, Punctuation, Whitespace, Plain, Regex, Annotation,
    Tag, Attribute, Property, Builtin, Preprocessor
}

enum class TokenModifier { None, Declaration, Reference, Documentation, Deprecated, Modifier }

/** A lexer turns source text into a token list. */
fun interface Lexer {
    fun lex(source: String): List<Token>
}
