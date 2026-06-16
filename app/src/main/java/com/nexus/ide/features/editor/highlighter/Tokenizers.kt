package com.nexus.ide.features.editor.highlighter

/**
 * Collection of small, allocation-light tokenizers for the languages
 * NexusIDE highlights out of the box.
 *
 * Design notes:
 *  - All tokenizers are stateless and accept a String, returning a
 *    `List<Token>`. The editor caches tokenization per line and only
 *    re-tokenizes lines whose source has changed.
 *  - Tokenizers are *deliberately* simple regular-expression state
 *    machines. They intentionally do not implement a full grammar —
 *    they just need to look right and stay fast on 100MB+ files.
 *  - The only allocation per line is a single ArrayList<Token>. We
 *    pre-size it generously and let it grow only when the line is
 *    unusually dense.
 */
object Tokenizers {

    fun interface Lexer {
        fun lex(src: String): List<Token>
    }

    private val numRegex = Regex("""0[xX][0-9A-Fa-f_]+[uUlLfF]?|\d[\d_]*(\.\d[\d_]*)?([eE][+-]?\d+)?[fFlLuU]?""")
    private val identRegex = Regex("""[A-Za-z_$][A-Za-z0-9_$]*""")

    // ---- C-like keyword sets ----
    private val cLikeKeywords: Set<String> = setOf(
        "auto", "break", "case", "catch", "class", "const", "continue", "default",
        "delete", "do", "else", "enum", "explicit", "export", "extern", "false",
        "for", "friend", "goto", "if", "inline", "mutable", "namespace", "new",
        "operator", "private", "protected", "public", "register", "return",
        "sizeof", "static", "static_cast", "struct", "switch", "template", "this",
        "throw", "true", "try", "typedef", "typeid", "typename", "union", "using",
        "virtual", "void", "volatile", "while", "nullptr", "and", "or", "not"
    )

    private val cLikeBuiltins: Set<String> = setOf(
        "int", "short", "long", "char", "float", "double", "signed", "unsigned",
        "bool", "size_t", "printf", "scanf", "malloc", "free", "memset", "memcpy",
        "strlen", "strcpy", "strcat", "strcmp", "fopen", "fclose", "fread", "fwrite",
        "std", "cout", "cin", "endl", "string", "vector", "map", "set", "pair",
        "queue", "stack", "list", "array", "tuple", "unique_ptr", "shared_ptr"
    )

    private val jsKeywords: Set<String> = cLikeKeywords + setOf(
        "async", "await", "yield", "let", "var", "function", "const", "of", "in",
        "instanceof", "typeof", "void", "debugger", "with", "interface", "type",
        "implements", "abstract", "as", "from", "import", "export", "default"
    )

    private val jsBuiltins: Set<String> = setOf(
        "console", "window", "document", "globalThis", "process", "require",
        "module", "exports", "Array", "Object", "String", "Number", "Boolean",
        "Map", "Set", "WeakMap", "WeakSet", "Promise", "Symbol", "Error",
        "TypeError", "RangeError", "JSON", "Math", "Date", "RegExp", "Buffer"
    )

    private val pyKeywords: Set<String> = setOf(
        "False", "None", "True", "and", "as", "assert", "async", "await", "break",
        "class", "continue", "def", "del", "elif", "else", "except", "finally",
        "for", "from", "global", "if", "import", "in", "is", "lambda", "nonlocal",
        "not", "or", "pass", "raise", "return", "try", "while", "with", "yield",
        "match", "case"
    )

    private val pyBuiltins: Set<String> = setOf(
        "abs", "all", "any", "bool", "bytearray", "bytes", "callable", "chr",
        "classmethod", "compile", "complex", "delattr", "dict", "dir", "divmod",
        "enumerate", "eval", "exec", "filter", "float", "format", "frozenset",
        "getattr", "globals", "hasattr", "hash", "help", "hex", "id", "input",
        "int", "isinstance", "issubclass", "iter", "len", "list", "locals", "map",
        "max", "memoryview", "min", "next", "object", "oct", "open", "ord",
        "pow", "print", "property", "range", "repr", "reversed", "round", "set",
        "setattr", "slice", "sorted", "staticmethod", "str", "sum", "super",
        "tuple", "type", "vars", "zip", "__init__", "__name__", "__main__",
        "__doc__", "__file__", "__all__", "__import__", "__dict__", "__str__",
        "__repr__", "__len__", "__iter__", "__next__", "__call__", "__getitem__",
        "__setitem__", "__delitem__", "__contains__", "__enter__", "__exit__"
    )

    private val rustKeywords: Set<String> = setOf(
        "as", "async", "await", "break", "const", "continue", "crate", "dyn",
        "else", "enum", "extern", "false", "fn", "for", "if", "impl", "in", "let",
        "loop", "match", "mod", "move", "mut", "pub", "ref", "return", "Self",
        "self", "static", "struct", "super", "trait", "true", "type", "unsafe",
        "use", "where", "while", "box", "do", "final", "macro", "override",
        "priv", "try", "typeof", "unsized", "virtual", "yield"
    )

    private val rustBuiltins: Set<String> = setOf(
        "i8", "i16", "i32", "i64", "i128", "isize", "u8", "u16", "u32", "u64",
        "u128", "usize", "f32", "f64", "bool", "char", "str", "String", "Vec",
        "Option", "Result", "Ok", "Err", "Some", "None", "Box", "Rc", "Arc",
        "Cell", "RefCell", "HashMap", "HashSet", "BTreeMap", "BTreeSet",
        "println", "print", "format", "eprintln", "eprint", "panic", "assert",
        "assert_eq", "assert_ne", "vec", "include_str", "include_bytes",
        "env", "cfg", "dbg", "unimplemented", "unreachable", "todo"
    )

    private val goKeywords: Set<String> = setOf(
        "break", "case", "chan", "const", "continue", "default", "defer", "else",
        "fallthrough", "for", "func", "go", "goto", "if", "import", "interface",
        "map", "package", "range", "return", "select", "struct", "switch", "type",
        "var", "true", "false", "nil", "iota"
    )

    private val goBuiltins: Set<String> = setOf(
        "int", "int8", "int16", "int32", "int64", "uint", "uint8", "uint16",
        "uint32", "uint64", "uintptr", "float32", "float64", "complex64",
        "complex128", "byte", "rune", "string", "bool", "error", "any",
        "make", "len", "cap", "append", "copy", "delete", "new", "panic",
        "recover", "print", "println", "fmt", "os", "io", "http", "context",
        "sync", "time", "log", "errors", "strings", "strconv", "json"
    )

    private val kotlinKeywords: Set<String> = setOf(
        "as", "break", "class", "continue", "do", "else", "false", "for",
        "fun", "if", "in", "interface", "is", "null", "object", "package",
        "return", "super", "this", "throw", "true", "try", "typealias", "typeof",
        "val", "var", "when", "while", "by", "catch", "constructor", "delegate",
        "dynamic", "field", "file", "finally", "get", "import", "init", "param",
        "property", "receiver", "set", "setparam", "value", "where", "abstract",
        "actual", "annotation", "companion", "const", "crossinline", "data",
        "enum", "expect", "external", "final", "infix", "inline", "inner",
        "internal", "lateinit", "noinline", "open", "operator", "out", "override",
        "private", "protected", "public", "reified", "sealed", "suspend", "tailrec"
    )

    private val kotlinBuiltins: Set<String> = setOf(
        "Int", "Long", "Short", "Byte", "Float", "Double", "Boolean", "Char",
        "String", "Any", "Unit", "Nothing", "Array", "List", "MutableList",
        "Set", "MutableSet", "Map", "MutableMap", "Pair", "Triple", "Sequence",
        "IntArray", "LongArray", "BooleanArray", "ByteArray", "CharArray",
        "FloatArray", "DoubleArray", "println", "print", "arrayOf", "listOf",
        "setOf", "mapOf", "mutableListOf", "mutableSetOf", "mutableMapOf",
        "emptyList", "emptySet", "emptyMap", "Pair", "Triple"
    )

    private val dartKeywords: Set<String> = setOf(
        "abstract", "as", "assert", "async", "await", "break", "case", "catch",
        "class", "const", "continue", "covariant", "default", "deferred", "do",
        "dynamic", "else", "enum", "export", "extends", "extension", "external",
        "factory", "false", "final", "finally", "for", "Function", "get", "hide",
        "if", "implements", "import", "in", "interface", "is", "late", "library",
        "mixin", "new", "null", "on", "operator", "part", "rethrow", "return",
        "sealed", "set", "show", "static", "super", "switch", "sync", "this",
        "throw", "true", "try", "typedef", "var", "void", "while", "with", "yield",
        "required", "override"
    )

    private val dartBuiltins: Set<String> = setOf(
        "int", "double", "num", "bool", "String", "List", "Map", "Set", "Iterable",
        "Future", "Stream", "void", "dynamic", "Object", "Null", "Symbol",
        "print", "identityHashCode", "identical", "jsonEncode", "jsonDecode",
        "asConst", "unawaited", "runZoned", "runZonedGuarded"
    )

    private val shKeywords: Set<String> = setOf(
        "if", "then", "else", "elif", "fi", "case", "esac", "for", "while",
        "until", "do", "done", "function", "select", "in", "return", "break",
        "continue", "exit", "export", "local", "readonly", "declare", "set",
        "unset", "shift", "source", "trap", "true", "false"
    )

    private val shBuiltins: Set<String> = setOf(
        "cd", "pwd", "ls", "cat", "echo", "printf", "read", "test", "eval",
        "exec", "wait", "kill", "jobs", "fg", "bg", "alias", "unalias", "type",
        "hash", "history", "let", "local", "logout", "popd", "pushd", "dirs",
        "shopt", "ulimit", "umask", "command", "compgen", "complete", "compopt",
        "mapfile", "readarray", "getopts", "basename", "dirname", "realpath",
        "awk", "sed", "grep", "cut", "tr", "sort", "uniq", "wc", "head", "tail",
        "xargs", "find", "xattr", "xcode"
    )

    // ---- C-family generic tokenizer ----

    fun cLike(
        keywords: Set<String> = cLikeKeywords,
        builtins: Set<String> = cLikeBuiltins,
        allowHashPreprocessor: Boolean = false,
        allowAtAttributes: Boolean = false
    ): Lexer = Lexer { src ->
        val out = ArrayList<Token>(64)
        var i = 0
        val n = src.length
        while (i < n) {
            val c = src[i]
            if (c == ' ' || c == '\t' || c == '\n' || c == '\r') {
                val start = i
                while (i < n && (src[i] == ' ' || src[i] == '\t' || src[i] == '\n' || src[i] == '\r')) i++
                out.add(Token(start, i, TokenKind.Whitespace))
                continue
            }
            if (c == '/' && i + 1 < n && src[i + 1] == '/') {
                val start = i
                while (i < n && src[i] != '\n') i++
                out.add(Token(start, i, TokenKind.Comment))
                continue
            }
            if (c == '/' && i + 1 < n && src[i + 1] == '*') {
                val start = i
                i += 2
                while (i < n && !(i + 1 < n && src[i] == '*' && src[i + 1] == '/')) i++
                if (i < n) i += 2
                out.add(Token(start, i, TokenKind.Comment))
                continue
            }
            if (allowHashPreprocessor && c == '#') {
                val start = i
                while (i < n && src[i] != '\n') i++
                out.add(Token(start, i, TokenKind.Preprocessor))
                continue
            }
            if (allowAtAttributes && c == '@') {
                val start = i
                i++
                while (i < n && (src[i].isLetterOrDigit() || src[i] == '_' || src[i] == '.')) i++
                out.add(Token(start, i, TokenKind.Annotation))
                continue
            }
            if (c == '"' || c == '\'') {
                val start = i
                val quote = c
                i++
                while (i < n && src[i] != quote && src[i] != '\n') {
                    if (src[i] == '\\' && i + 1 < n) i += 2 else i++
                }
                if (i < n && src[i] == quote) i++
                out.add(Token(start, i, TokenKind.String))
                continue
            }
            if (c == '`') {
                val start = i
                i++
                while (i < n && src[i] != '`') {
                    if (src[i] == '\\' && i + 1 < n) i += 2 else i++
                }
                if (i < n) i++
                out.add(Token(start, i, TokenKind.String))
                continue
            }
            val mNum = numRegex.find(src, i)
            if (mNum != null && mNum.range.first == i) {
                out.add(Token(i, mNum.range.last + 1, TokenKind.Number))
                i = mNum.range.last + 1
                continue
            }
            val mId = identRegex.find(src, i)
            if (mId != null && mId.range.first == i) {
                val word = mId.value
                val end = mId.range.last + 1
                val kind = when {
                    keywords.contains(word) -> TokenKind.Keyword
                    builtins.contains(word) -> TokenKind.Builtin
                    word.first().isUpperCase() -> TokenKind.Type
                    i > 0 && (src[i - 1] == '.' || src[i - 1] == '\"') -> TokenKind.Property
                    else -> TokenKind.Variable
                }
                out.add(Token(i, end, kind))
                i = end
                continue
            }
            out.add(Token(i, i + 1, if (c in "+-*/%=<>!&|^~?:") TokenKind.Operator else TokenKind.Punctuation))
            i++
        }
        out
    }

    // ---- Indentation-sensitive tokenizer (Python) ----
    // Simple approach: treat '#' as a comment-to-EOL and otherwise reuse cLike.
    // Indent/dedent is the editor's job, not the highlighter's.
    private val pythonLexer: Lexer = Lexer { src ->
        val out = ArrayList<Token>(64)
        var i = 0
        val n = src.length
        while (i < n) {
            val c = src[i]
            if (c == ' ' || c == '\t') {
                val start = i
                while (i < n && (src[i] == ' ' || src[i] == '\t')) i++
                out.add(Token(start, i, TokenKind.Whitespace))
                continue
            }
            if (c == '#') {
                val start = i
                while (i < n && src[i] != '\n') i++
                out.add(Token(start, i, TokenKind.Comment))
                continue
            }
            if (c == '"' || c == '\'') {
                // Triple-quoted string
                if (i + 2 < n && src[i + 1] == c && src[i + 2] == c) {
                    val start = i
                    i += 3
                    while (i < n && !(i + 2 < n && src[i] == c && src[i + 1] == c && src[i + 2] == c)) i++
                    if (i < n) i += 3
                    out.add(Token(start, i, TokenKind.String))
                    continue
                }
                val start = i
                val quote = c
                i++
                while (i < n && src[i] != quote && src[i] != '\n') {
                    if (src[i] == '\\' && i + 1 < n) i += 2 else i++
                }
                if (i < n && src[i] == quote) i++
                out.add(Token(start, i, TokenKind.String))
                continue
            }
            if (c == '@') {
                val start = i
                i++
                while (i < n && (src[i].isLetterOrDigit() || src[i] == '_' || src[i] == '.')) i++
                out.add(Token(start, i, TokenKind.Annotation))
                continue
            }
            val mNum = numRegex.find(src, i)
            if (mNum != null && mNum.range.first == i) {
                out.add(Token(i, mNum.range.last + 1, TokenKind.Number))
                i = mNum.range.last + 1
                continue
            }
            val mId = identRegex.find(src, i)
            if (mId != null && mId.range.first == i) {
                val word = mId.value
                val end = mId.range.last + 1
                val kind = when {
                    pyKeywords.contains(word) -> TokenKind.Keyword
                    pyBuiltins.contains(word) -> TokenKind.Builtin
                    word.first().isUpperCase() -> TokenKind.Type
                    i > 0 && src[i - 1] == '.' -> TokenKind.Property
                    else -> TokenKind.Variable
                }
                out.add(Token(i, end, kind))
                i = end
                continue
            }
            out.add(Token(i, i + 1, if (c in "+-*/%=<>!&|^~?:") TokenKind.Operator else TokenKind.Punctuation))
            i++
        }
        out
    }

    // ---- JSON ----
    private val jsonLexer: Lexer = Lexer { src ->
        val out = ArrayList<Token>(32)
        var i = 0
        val n = src.length
        while (i < n) {
            val c = src[i]
            if (c == ' ' || c == '\t' || c == '\n' || c == '\r') {
                val start = i
                while (i < n && (src[i] == ' ' || src[i] == '\t' || src[i] == '\n' || src[i] == '\r')) i++
                out.add(Token(start, i, TokenKind.Whitespace))
                continue
            }
            if (c == '"') {
                val start = i
                i++
                while (i < n && src[i] != '"') {
                    if (src[i] == '\\' && i + 1 < n) i += 2 else i++
                }
                if (i < n) i++
                // Heuristic: if the previous non-whitespace token is `:` we're a value, else a key.
                val kind = TokenKind.String
                out.add(Token(start, i, kind, if (i + 1 < n && src[i] == ':') TokenModifier.Property else TokenModifier.None))
                continue
            }
            if (c == '-' || c.isDigit()) {
                val mNum = numRegex.find(src, i)
                if (mNum != null && mNum.range.first == i) {
                    out.add(Token(i, mNum.range.last + 1, TokenKind.Number))
                    i = mNum.range.last + 1
                    continue
                }
            }
            if (c == 't' || c == 'f' || c == 'n') {
                val mId = identRegex.find(src, i)
                if (mId != null && mId.range.first == i) {
                    if (mId.value == "true" || mId.value == "false" || mId.value == "null") {
                        out.add(Token(i, mId.range.last + 1, TokenKind.Keyword))
                        i = mId.range.last + 1
                        continue
                    }
                }
            }
            out.add(Token(i, i + 1, if (c in "{}[],:") TokenKind.Punctuation else TokenKind.Plain))
            i++
        }
        out
    }

    // ---- HTML (very small subset: tags + attributes + strings) ----
    private val htmlLexer: Lexer = Lexer { src ->
        val out = ArrayList<Token>(32)
        var i = 0
        val n = src.length
        while (i < n) {
            val c = src[i]
            if (c == '<') {
                if (i + 3 < n && src[i + 1] == '!') {
                    val start = i
                    i += 2
                    if (i < n && src[i] == '-') {
                        while (i + 2 < n && !(src[i] == '-' && src[i + 1] == '-' && src[i + 2] == '>')) i++
                        if (i + 2 < n) i += 3
                    } else {
                        while (i < n && src[i] != '>') i++
                        if (i < n) i++
                    }
                    out.add(Token(start, i, TokenKind.Comment))
                    continue
                }
                val start = i
                val endTag = i + 1 < n && src[i + 1] == '/'
                if (endTag) i++
                i++
                out.add(Token(start, i, TokenKind.Punctuation))
                // tag name
                val mId = identRegex.find(src, i)
                if (mId != null && mId.range.first == i) {
                    out.add(Token(i, mId.range.last + 1, TokenKind.Tag))
                    i = mId.range.last + 1
                }
                // attributes
                while (i < n && src[i] != '>') {
                    val cc = src[i]
                    if (cc == ' ' || cc == '\t' || cc == '\n' || cc == '\r') {
                        val ws = i
                        while (i < n && (src[i] == ' ' || src[i] == '\t' || src[i] == '\n' || src[i] == '\r')) i++
                        out.add(Token(ws, i, TokenKind.Whitespace))
                        continue
                    }
                    if (cc == '/') { out.add(Token(i, i + 1, TokenKind.Punctuation)); i++; continue }
                    if (cc == '=') { out.add(Token(i, i + 1, TokenKind.Operator)); i++; continue }
                    if (cc == '"' || cc == '\'') {
                        val s = i
                        val q = cc
                        i++
                        while (i < n && src[i] != q) {
                            if (src[i] == '\\' && i + 1 < n) i += 2 else i++
                        }
                        if (i < n) i++
                        out.add(Token(s, i, TokenKind.String))
                        continue
                    }
                    val m = identRegex.find(src, i)
                    if (m != null && m.range.first == i) {
                        out.add(Token(i, m.range.last + 1, TokenKind.Attribute))
                        i = m.range.last + 1
                        continue
                    }
                    out.add(Token(i, i + 1, TokenKind.Plain))
                    i++
                }
                if (i < n) {
                    out.add(Token(i, i + 1, TokenKind.Punctuation))
                    i++
                }
                continue
            }
            if (c == '&') {
                val start = i
                while (i < n && src[i] != ';' && src[i] != ' ' && src[i] != '\n') i++
                if (i < n) i++
                out.add(Token(start, i, TokenKind.Preprocessor))
                continue
            }
            val start = i
            while (i < n && src[i] != '<') i++
            if (i > start) out.add(Token(start, i, TokenKind.Plain))
        }
        out
    }

    // ---- CSS-like (also covers SCSS) ----
    private val cssLexer: Lexer = Lexer { src ->
        val out = ArrayList<Token>(32)
        var i = 0
        val n = src.length
        val atRules = setOf("media", "import", "charset", "keyframes", "font-face", "supports", "use", "mixin", "include", "extend", "function", "if", "else", "for", "each", "while", "return", "mixin")
        val units = setOf("px", "em", "rem", "vh", "vw", "vmin", "vmax", "%", "deg", "rad", "turn", "s", "ms", "fr", "ex", "ch", "pt", "pc", "in", "cm", "mm")
        while (i < n) {
            val c = src[i]
            if (c == ' ' || c == '\t' || c == '\n' || c == '\r') {
                val start = i
                while (i < n && (src[i] == ' ' || src[i] == '\t' || src[i] == '\n' || src[i] == '\r')) i++
                out.add(Token(start, i, TokenKind.Whitespace))
                continue
            }
            if (c == '/' && i + 1 < n && src[i + 1] == '*') {
                val start = i
                i += 2
                while (i < n && !(i + 1 < n && src[i] == '*' && src[i + 1] == '/')) i++
                if (i < n) i += 2
                out.add(Token(start, i, TokenKind.Comment))
                continue
            }
            if (c == '"' || c == '\'') {
                val start = i
                val q = c
                i++
                while (i < n && src[i] != q) {
                    if (src[i] == '\\' && i + 1 < n) i += 2 else i++
                }
                if (i < n) i++
                out.add(Token(start, i, TokenKind.String))
                continue
            }
            if (c == '#' && (i == 0 || src[i - 1] == ' ' || src[i - 1] == '\t' || src[i - 1] == '\n' || src[i - 1] == '(')) {
                val start = i
                i++
                while (i < n && (src[i].isLetterOrDigit() || src[i] == '_')) i++
                out.add(Token(start, i, TokenKind.Number))
                continue
            }
            if (c == '@') {
                val start = i
                i++
                val m = identRegex.find(src, i)
                if (m != null && m.range.first == i) {
                    val word = m.value
                    if (atRules.contains(word)) {
                        out.add(Token(start, m.range.last + 1, TokenKind.Preprocessor))
                    } else {
                        out.add(Token(start, m.range.last + 1, TokenKind.Annotation))
                    }
                    i = m.range.last + 1
                }
                continue
            }
            if (c == '-' || c == '.' || c.isDigit()) {
                val mNum = numRegex.find(src, i)
                if (mNum != null && mNum.range.first == i) {
                    val end = mNum.range.last + 1
                    out.add(Token(i, end, TokenKind.Number))
                    i = end
                    // optional unit
                    val mUnit = identRegex.find(src, i)
                    if (mUnit != null && mUnit.range.first == i && mUnit.value in units) {
                        out.add(Token(i, mUnit.range.last + 1, TokenKind.Builtin))
                        i = mUnit.range.last + 1
                    }
                    continue
                }
            }
            if (c == '$') {
                val start = i
                i++
                val m = identRegex.find(src, i)
                if (m != null && m.range.first == i) {
                    out.add(Token(start, m.range.last + 1, TokenKind.Variable))
                    i = m.range.last + 1
                }
                continue
            }
            val mId = identRegex.find(src, i)
            if (mId != null && mId.range.first == i) {
                val word = mId.value
                val end = mId.range.last + 1
                val kind = when {
                    word.startsWith("--") -> TokenKind.Variable
                    word == "true" || word == "false" || word == "null" || word == "undefined" -> TokenKind.Keyword
                    word == "important" || word == "inherit" || word == "initial" || word == "unset" ||
                            word == "revert" || word == "auto" || word == "none" -> TokenKind.Keyword
                    else -> TokenKind.Property
                }
                out.add(Token(i, end, kind))
                i = end
                continue
            }
            out.add(Token(i, i + 1, if (c in "{}()[];:,>+~*" || c == '|') TokenKind.Punctuation else TokenKind.Operator))
            i++
        }
        out
    }

    // ---- YAML ----
    private val yamlLexer: Lexer = Lexer { src ->
        val out = ArrayList<Token>(16)
        var i = 0
        val n = src.length
        var atLineStart = true
        var indent = 0
        while (i < n) {
            val c = src[i]
            if (c == '\n') {
                out.add(Token(i, i + 1, TokenKind.Whitespace))
                i++
                atLineStart = true
                continue
            }
            if (atLineStart && (c == ' ' || c == '\t')) {
                val start = i
                while (i < n && (src[i] == ' ' || src[i] == '\t')) i++
                out.add(Token(start, i, TokenKind.Whitespace))
                continue
            }
            atLineStart = false
            if (c == '#') {
                val start = i
                while (i < n && src[i] != '\n') i++
                out.add(Token(start, i, TokenKind.Comment))
                continue
            }
            if (c == '"' || c == '\'') {
                val start = i
                val q = c
                i++
                while (i < n && src[i] != q && src[i] != '\n') {
                    if (src[i] == '\\' && i + 1 < n) i += 2 else i++
                }
                if (i < n && src[i] == q) i++
                out.add(Token(start, i, TokenKind.String))
                continue
            }
            if (c == '-' || c.isDigit()) {
                val mNum = numRegex.find(src, i)
                if (mNum != null && mNum.range.first == i) {
                    out.add(Token(i, mNum.range.last + 1, TokenKind.Number))
                    i = mNum.range.last + 1
                    continue
                }
            }
            val mId = identRegex.find(src, i)
            if (mId != null && mId.range.first == i) {
                val word = mId.value
                val end = mId.range.last + 1
                when (word) {
                    "true", "false", "null", "yes", "no", "on", "off" -> out.add(Token(i, end, TokenKind.Keyword))
                    else -> {
                        if (end < n && src[end] == ':') out.add(Token(i, end, TokenKind.Property))
                        else out.add(Token(i, end, TokenKind.Plain))
                    }
                }
                i = end
                continue
            }
            out.add(Token(i, i + 1, TokenKind.Punctuation))
            i++
        }
        out
    }

    // ---- SQL ----
    private val sqlKeywords: Set<String> = setOf(
        "SELECT", "FROM", "WHERE", "INSERT", "INTO", "VALUES", "UPDATE", "SET",
        "DELETE", "CREATE", "TABLE", "DROP", "ALTER", "ADD", "COLUMN", "INDEX",
        "PRIMARY", "KEY", "FOREIGN", "REFERENCES", "JOIN", "INNER", "LEFT",
        "RIGHT", "OUTER", "FULL", "ON", "AS", "AND", "OR", "NOT", "NULL", "IS",
        "IN", "EXISTS", "BETWEEN", "LIKE", "ORDER", "BY", "GROUP", "HAVING",
        "LIMIT", "OFFSET", "UNION", "ALL", "DISTINCT", "CASE", "WHEN", "THEN",
        "ELSE", "END", "BEGIN", "COMMIT", "ROLLBACK", "TRANSACTION", "WITH",
        "RECURSIVE", "VIEW", "TRIGGER", "FUNCTION", "PROCEDURE", "RETURNS",
        "DECLARE", "IF", "WHILE", "FOR", "LOOP", "RETURN", "CASCADE", "RESTRICT",
        "DEFAULT", "CHECK", "CONSTRAINT", "UNIQUE", "AUTO_INCREMENT", "SERIAL",
        "INT", "INTEGER", "BIGINT", "SMALLINT", "VARCHAR", "CHAR", "TEXT",
        "BOOLEAN", "DATE", "TIMESTAMP", "DATETIME", "TIME", "FLOAT", "DOUBLE",
        "REAL", "DECIMAL", "NUMERIC", "BLOB", "CLOB", "ENUM", "UUID", "JSON"
    )

    private val sqlLexer: Lexer = Lexer { src ->
        val out = ArrayList<Token>(32)
        var i = 0
        val n = src.length
        while (i < n) {
            val c = src[i]
            if (c == ' ' || c == '\t' || c == '\n' || c == '\r') {
                val start = i
                while (i < n && (src[i] == ' ' || src[i] == '\t' || src[i] == '\n' || src[i] == '\r')) i++
                out.add(Token(start, i, TokenKind.Whitespace))
                continue
            }
            if (c == '-' && i + 1 < n && src[i + 1] == '-') {
                val start = i
                while (i < n && src[i] != '\n') i++
                out.add(Token(start, i, TokenKind.Comment))
                continue
            }
            if (c == '/' && i + 1 < n && src[i + 1] == '*') {
                val start = i
                i += 2
                while (i < n && !(i + 1 < n && src[i] == '*' && src[i + 1] == '/')) i++
                if (i < n) i += 2
                out.add(Token(start, i, TokenKind.Comment))
                continue
            }
            if (c == '\'' || c == '"') {
                val start = i
                val q = c
                i++
                while (i < n && src[i] != q) {
                    if (src[i] == '\'' && i + 1 < n && src[i + 1] == '\'') { i += 2; continue }
                    if (src[i] == '\\' && i + 1 < n) i += 2 else i++
                }
                if (i < n) i++
                out.add(Token(start, i, TokenKind.String))
                continue
            }
            val mNum = numRegex.find(src, i)
            if (mNum != null && mNum.range.first == i) {
                out.add(Token(i, mNum.range.last + 1, TokenKind.Number))
                i = mNum.range.last + 1
                continue
            }
            val mId = identRegex.find(src, i)
            if (mId != null && mId.range.first == i) {
                val word = mId.value
                val end = mId.range.last + 1
                if (sqlKeywords.contains(word) || sqlKeywords.contains(word.uppercase())) {
                    out.add(Token(i, end, TokenKind.Keyword))
                } else {
                    out.add(Token(i, end, TokenKind.Variable))
                }
                i = end
                continue
            }
            out.add(Token(i, i + 1, if (c in "+-*/%=<>!") TokenKind.Operator else TokenKind.Punctuation))
            i++
        }
        out
    }

    // ---- Markdown ----
    private val markdownLexer: Lexer = Lexer { src ->
        val out = ArrayList<Token>(16)
        var i = 0
        val n = src.length
        // headings
        if (n > 0 && src[0] == '#') {
            var k = 0
            while (k < n && k < 6 && src[k] == '#') k++
            out.add(Token(0, k, TokenKind.Tag))
            i = k
        }
        // inline code
        var inCode = false
        while (i < n) {
            val c = src[i]
            if (c == '`') {
                val start = i
                i++
                while (i < n && src[i] != '`') i++
                if (i < n) i++
                out.add(Token(start, i, TokenKind.String))
                inCode = !inCode
                continue
            }
            if (!inCode && c == '*' && i + 1 < n && src[i + 1] == '*') {
                out.add(Token(i, i + 2, TokenKind.Operator))
                i += 2
                continue
            }
            if (!inCode && c == '[') {
                val start = i
                while (i < n && src[i] != ']') i++
                if (i < n) i++
                out.add(Token(start, i, TokenKind.Property))
                continue
            }
            if (!inCode && c == '!' && i + 1 < n && src[i + 1] == '[') {
                val start = i
                i += 2
                while (i < n && src[i] != ']') i++
                if (i < n) i++
                out.add(Token(start, i, TokenKind.Function))
                continue
            }
            if (!inCode && c == 'h' && i + 1 < n && src[i + 1] == 't' && i + 2 < n && src[i + 2] == 't' && (i + 3 < n && src[i + 3] == 'p' || i + 3 < n && src[i + 3] == 's')) {
                val start = i
                while (i < n && !src[i].isWhitespace() && src[i] != ')') i++
                out.add(Token(start, i, TokenKind.Function))
                continue
            }
            val start = i
            while (i < n && src[i] != '`' && src[i] != '[' && !(src[i] == '!' && i + 1 < n && src[i + 1] == '[')) i++
            if (i > start) out.add(Token(start, i, if (inCode) TokenKind.String else TokenKind.Plain))
        }
        out
    }

    private val plaintextLexer: Lexer = Lexer { src -> listOf(Token(0, src.length, TokenKind.Plain)) }

    private val shLexer: Lexer = Lexer { src ->
        val out = ArrayList<Token>(32)
        var i = 0
        val n = src.length
        while (i < n) {
            val c = src[i]
            if (c == ' ' || c == '\t') { val s = i; while (i < n && (src[i] == ' ' || src[i] == '\t')) i++; out.add(Token(s, i, TokenKind.Whitespace)); continue }
            if (c == '#') { val s = i; while (i < n && src[i] != '\n') i++; out.add(Token(s, i, TokenKind.Comment)); continue }
            if (c == '"' || c == '\'') { val s = i; val q = c; i++; while (i < n && src[i] != q) { if (src[i] == '\\' && i + 1 < n) i += 2 else i++ }; if (i < n) i++; out.add(Token(s, i, TokenKind.String)); continue }
            if (c == '$' && i + 1 < n && src[i + 1] == '{') { val s = i; var depth = 1; i += 2; while (i < n && depth > 0) { if (src[i] == '{') depth++; else if (src[i] == '}') depth--; i++ }; out.add(Token(s, i, TokenKind.Builtin)); continue }
            if (c == '$') { val s = i; i++; while (i < n && (src[i].isLetterOrDigit() || src[i] == '_')) i++; out.add(Token(s, i, TokenKind.Builtin)); continue }
            val mNum = numRegex.find(src, i); if (mNum != null && mNum.range.first == i) { out.add(Token(i, mNum.range.last + 1, TokenKind.Number)); i = mNum.range.last + 1; continue }
            val mId = identRegex.find(src, i); if (mId != null && mId.range.first == i) { val word = mId.value; val end = mId.range.last + 1; val kind = when { shKeywords.contains(word) -> TokenKind.Keyword; shBuiltins.contains(word) -> TokenKind.Builtin; word.first().isUpperCase() -> TokenKind.Type; else -> TokenKind.Variable }; out.add(Token(i, end, kind)); i = end; continue }
            out.add(Token(i, i + 1, if (c in "|&;<>=!?(){}\\[\\]") TokenKind.Operator else TokenKind.Punctuation)); i++
        }
        out
    }

    private val byLanguageId: Map<String, Lexer> = mapOf(
        "c" to cLike(),
        "cpp" to cLike(),
        "java" to cLike(),
        "javascript" to cLike(jsKeywords, jsBuiltins),
        "typescript" to cLike(jsKeywords, jsBuiltins),
        "tsx" to cLike(jsKeywords, jsBuiltins),
        "jsx" to cLike(jsKeywords, jsBuiltins),
        "php" to cLike(),
        "python" to pythonLexer,
        "rust" to cLike(rustKeywords, rustBuiltins, allowHashPreprocessor = true, allowAtAttributes = true),
        "go" to cLike(goKeywords, goBuiltins),
        "kotlin" to cLike(kotlinKeywords, kotlinBuiltins),
        "dart" to cLike(dartKeywords, dartBuiltins),
        "bash" to Lexer { src -> shLexer.lex(src) },
        "sh" to shLexer,
        "html" to htmlLexer,
        "css" to cssLexer,
        "scss" to cssLexer,
        "json" to jsonLexer,
        "yaml" to yamlLexer,
        "sql" to sqlLexer,
        "markdown" to markdownLexer,
        "plaintext" to plaintextLexer
    )

    fun forLanguage(id: String): Lexer = byLanguageId[id] ?: byLanguageId.getValue("plaintext")
}
