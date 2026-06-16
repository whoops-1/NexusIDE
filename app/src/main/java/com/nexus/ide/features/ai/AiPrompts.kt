package com.nexus.ide.features.ai

/**
 * Curated prompt templates for IDE AI features. The system message is
 * designed to make the model behave as a code-aware assistant that
 * returns precise, minimal patches.
 */
object AiPrompts {
    const val SYSTEM_ASSISTANT = """You are Nexus, a senior software engineer inside the NexusIDE mobile editor.
- Be precise. Prefer concrete code over prose.
- When asked to fix a bug, show only the minimal diff.
- Match the user's existing code style and indentation.
- If uncertain, ask one clarifying question."""

    fun explain(code: String, language: String): AiEngine.Message =
        AiEngine.Message("user", "Explain this $language code:
```$language
$code
```")

    fun doc(code: String, language: String): AiEngine.Message =
        AiEngine.Message("user", "Add doc comments to this $language code:
```$language
$code
```")

    fun fix(code: String, error: String, language: String): AiEngine.Message =
        AiEngine.Message("user", "Fix this $language code. Error: $error
```$language
$code
```")

    fun refactor(code: String, instruction: String, language: String): AiEngine.Message =
        AiEngine.Message("user", "Refactor this $language code: $instruction
```$language
$code
```")

    fun generateTests(code: String, language: String): AiEngine.Message =
        AiEngine.Message("user", "Generate unit tests for this $language code:
```$language
$code
```")
}
