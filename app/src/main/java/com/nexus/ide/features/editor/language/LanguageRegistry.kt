package com.nexus.ide.features.editor.language

import java.io.File

/**
 * Detects the programming language of a file by extension (and a few
 * filename exceptions like Dockerfile, .bashrc, etc.).
 *
 * The returned id is canonical and stable — the editor's tokenizer
 * registry keys off of it.
 */
object LanguageRegistry {

    data class Language(
        val id: String,
        val displayName: String,
        val extensions: Set<String>,
        val fileNames: Set<String> = emptySet(),
        val mimeTypes: Set<String> = emptySet(),
        val runCommand: RunCommand? = null,
        val buildCommand: BuildCommand? = null,
        val supportsLsp: Boolean = false,
        val formatter: String? = null,
        val linter: String? = null
    )

    /** How to run a single file. */
    data class RunCommand(val program: String, val args: List<String> = emptyList())

    /** How to build/compile a single file. Returns the binary path or null. */
    data class BuildCommand(
        val program: String,
        val args: List<String>,
        val output: String? = null
    )

    private val LANGUAGES: List<Language> = listOf(
        Language(
            id = "c", displayName = "C",
            extensions = setOf("c", "h"),
            runCommand = RunCommand("sh", listOf("-c", "cc %f -o %n && ./%n")),
            buildCommand = BuildCommand("cc", listOf("%f", "-o", "%n"), output = "%n")
        ),
        Language(
            id = "cpp", displayName = "C++",
            extensions = setOf("cpp", "cxx", "cc", "hpp", "hxx", "hh"),
            runCommand = RunCommand("sh", listOf("-c", "c++ %f -o %n && ./%n")),
            buildCommand = BuildCommand("c++", listOf("%f", "-o", "%n"), output = "%n")
        ),
        Language(
            id = "python", displayName = "Python",
            extensions = setOf("py", "pyw"),
            runCommand = RunCommand("python3", listOf("%f")),
            supportsLsp = true, formatter = "black", linter = "ruff"
        ),
        Language(
            id = "java", displayName = "Java",
            extensions = setOf("java"),
            runCommand = RunCommand("sh", listOf("-c", "javac %f && java %n")),
            buildCommand = BuildCommand("javac", listOf("%f"))
        ),
        Language(
            id = "javascript", displayName = "JavaScript",
            extensions = setOf("js", "mjs", "cjs"),
            runCommand = RunCommand("node", listOf("%f")),
            supportsLsp = true, formatter = "prettier", linter = "eslint"
        ),
        Language(
            id = "typescript", displayName = "TypeScript",
            extensions = setOf("ts", "tsx"),
            runCommand = RunCommand("node", listOf("--import", "tsx", "%f")),
            supportsLsp = true, formatter = "prettier", linter = "eslint"
        ),
        Language(
            id = "node", displayName = "Node.js",
            extensions = setOf("mjs", "cjs"),
            runCommand = RunCommand("node", listOf("%f"))
        ),
        Language(
            id = "rust", displayName = "Rust",
            extensions = setOf("rs"),
            runCommand = RunCommand("sh", listOf("-c", "rustc %f -o %n 2>/dev/null && ./%n")),
            buildCommand = BuildCommand("rustc", listOf("%f", "-o", "%n"), output = "%n")
        ),
        Language(
            id = "go", displayName = "Go",
            extensions = setOf("go"),
            runCommand = RunCommand("go", listOf("run", "%f")),
            supportsLsp = true, formatter = "gofmt"
        ),
        Language(
            id = "php", displayName = "PHP",
            extensions = setOf("php"),
            runCommand = RunCommand("php", listOf("%f"))
        ),
        Language(
            id = "kotlin", displayName = "Kotlin",
            extensions = setOf("kt", "kts"),
            runCommand = RunCommand("sh", listOf("-c", "kotlinc %f -include-runtime -d %n.jar 2>/dev/null && java -jar %n.jar"))
        ),
        Language(
            id = "dart", displayName = "Dart",
            extensions = setOf("dart"),
            runCommand = RunCommand("dart", listOf("run", "%f"))
        ),
        Language(
            id = "bash", displayName = "Shell",
            extensions = setOf("sh", "bash", "zsh"),
            fileNames = setOf(".bashrc", ".zshrc", ".profile", ".bash_profile"),
            runCommand = RunCommand("bash", listOf("%f"))
        ),
        Language(id = "html", displayName = "HTML", extensions = setOf("html", "htm"), formatter = "prettier"),
        Language(id = "css", displayName = "CSS", extensions = setOf("css"), formatter = "prettier"),
        Language(id = "scss", displayName = "SCSS", extensions = setOf("scss"), formatter = "prettier"),
        Language(id = "json", displayName = "JSON", extensions = setOf("json"), formatter = "prettier"),
        Language(id = "yaml", displayName = "YAML", extensions = setOf("yaml", "yml")),
        Language(id = "xml", displayName = "XML", extensions = setOf("xml")),
        Language(id = "sql", displayName = "SQL", extensions = setOf("sql")),
        Language(id = "markdown", displayName = "Markdown", extensions = setOf("md", "markdown")),
        Language(id = "toml", displayName = "TOML", extensions = setOf("toml")),
        Language(id = "ini", displayName = "INI", extensions = setOf("ini", "cfg", "conf")),
        Language(id = "dockerfile", displayName = "Dockerfile", fileNames = setOf("Dockerfile", "dockerfile"), extensions = setOf("dockerfile")),
        Language(id = "makefile", displayName = "Makefile", fileNames = setOf("Makefile", "makefile", "GNUmakefile"), extensions = setOf("mk")),
        Language(id = "plaintext", displayName = "Plain Text", extensions = emptySet())
    )

    private val byId: Map<String, Language> = LANGUAGES.associateBy { it.id }
    private val byExt: Map<String, Language> = LANGUAGES.flatMap { lang ->
        lang.extensions.map { it to lang }
    }.toMap()
    private val byName: Map<String, Language> = LANGUAGES.flatMap { lang ->
        lang.fileNames.map { it.lowercase() to lang }
    }.toMap()

    fun detect(file: File): Language {
        val name = file.name.lowercase()
        byName[name]?.let { return it }
        if (file.extension.isNotEmpty()) {
            byExt[file.extension.lowercase()]?.let { return it }
        }
        return byId.getValue("plaintext")
    }

    fun byId(id: String): Language = byId[id] ?: byId.getValue("plaintext")
    fun all(): List<Language> = LANGUAGES
    fun runnableLanguages(): List<Language> = LANGUAGES.filter { it.runCommand != null }

    /** Substitutes %f / %n / %d in a run/build command template. */
    fun renderTemplate(tpl: List<String>, file: File): List<String> {
        val dir = file.parentFile?.absolutePath ?: "."
        val name = file.nameWithoutExtension
        return tpl.map { tok ->
            tok.replace("%f", file.absolutePath)
                .replace("%d", dir)
                .replace("%n", name)
        }
    }
}
