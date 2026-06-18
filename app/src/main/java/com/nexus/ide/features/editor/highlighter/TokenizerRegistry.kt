package com.nexus.ide.features.editor.highlighter

/**
 * Public registry mapping language id -> lexer. The [Tokenizers] object
 * owns the actual lexers; this class is a thin alias so callers don't
 * need to know which one is the registry.
 */
object TokenizerRegistry {
    fun forLanguage(id: String): Lexer = Tokenizers.forLanguage(id)
    fun available(): Set<String> = Tokenizers.availableLanguageIds()
}
