package com.nexus.ide.features.editor.completion

import com.nexus.ide.features.editor.language.LanguageRegistry

/**
 * Identifier completion. Indexes every word in the buffer once, then
 * filters by the current prefix on every keystroke. Designed to be
 * cheap enough to run synchronously in the input handler.
 */
class CompletionEngine {
    data class Item(val text: String, val kind: Kind, val detail: String? = null)
    enum class Kind { Keyword, Builtin, Type, Variable, Function, Snippet }

    private val keywordCache = HashMap<String, Set<String>>()
    private var bufferSnapshot: String = ""
    private var indexedText: String = ""
    private val variables = HashSet<String>()
    private val functions = HashSet<String>()

    fun reindex(fullText: String) {
        if (fullText === bufferSnapshot) return
        bufferSnapshot = fullText
        indexedText = fullText
        variables.clear(); functions.clear()
        val rx = Regex("""[A-Za-z_$][A-Za-z0-9_$]*""")
        for (m in rx.findAll(fullText)) {
            val w = m.value
            if (w.length < 2) continue
            if (w[0].isUpperCase()) continue // skip type-like
            // Cheap function detection: followed by (
            val end = m.range.last + 1
            if (end < fullText.length && fullText[end] == '(') functions.add(w) else variables.add(w)
        }
    }

    fun suggest(prefix: String, languageId: String, max: Int = 20): List<Item> {
        if (prefix.isEmpty()) return emptyList()
        val lower = prefix.lowercase()
        val out = ArrayList<Item>(max)
        for (kw in keywordsFor(languageId)) {
            if (kw.lowercase().startsWith(lower)) out.add(Item(kw, Kind.Keyword, "keyword"))
            if (out.size >= max) return out
        }
        for (kw in builtinsFor(languageId)) {
            if (kw.lowercase().startsWith(lower)) out.add(Item(kw, Kind.Builtin, "builtin"))
            if (out.size >= max) return out
        }
        for (v in variables) {
            if (v.lowercase().startsWith(lower)) out.add(Item(v, Kind.Variable, "variable"))
            if (out.size >= max) return out
        }
        for (f in functions) {
            if (f.lowercase().startsWith(lower)) out.add(Item(f, Kind.Function, "function"))
            if (out.size >= max) return out
        }
        return out
    }

    private fun keywordsFor(id: String): Set<String> = keywordCache.getOrPut("k_$id") {
        when (id) {
            "python" -> setOf("False","None","True","and","as","assert","async","await","break","class","continue","def","del","elif","else","except","finally","for","from","global","if","import","in","is","lambda","nonlocal","not","or","pass","raise","return","try","while","with","yield","match","case")
            "javascript","typescript","tsx","jsx" -> setOf("abstract","as","async","await","break","case","catch","class","const","continue","debugger","default","delete","do","else","enum","export","extends","finally","for","from","function","if","implements","import","in","instanceof","interface","let","new","null","of","private","protected","public","return","static","super","switch","this","throw","true","false","try","typeof","undefined","var","void","while","with","yield")
            "rust" -> setOf("as","async","await","break","const","continue","crate","dyn","else","enum","extern","false","fn","for","if","impl","in","let","loop","match","mod","move","mut","pub","ref","return","Self","self","static","struct","super","trait","true","type","unsafe","use","where","while")
            "go" -> setOf("break","case","chan","const","continue","default","defer","else","fallthrough","for","func","go","goto","if","import","interface","map","package","range","return","select","struct","switch","type","var","true","false","nil")
            "java","cpp","c" -> setOf("abstract","assert","boolean","break","byte","case","catch","char","class","const","continue","default","do","double","else","enum","extends","final","finally","float","for","goto","if","implements","import","instanceof","int","interface","long","native","new","package","private","protected","public","return","short","static","strictfp","super","switch","synchronized","this","throw","throws","transient","try","void","volatile","while","true","false","null")
            "bash","sh" -> setOf("if","then","else","elif","fi","case","esac","for","while","until","do","done","function","return","break","continue","in","export","local","readonly","declare","set","unset")
            else -> emptySet()
        }
    }

    private fun builtinsFor(id: String): Set<String> = keywordCache.getOrPut("b_$id") {
        when (id) {
            "python" -> setOf("print","len","range","str","int","float","list","dict","set","tuple","open","input","type","isinstance","hasattr","getattr","setattr","sum","min","max","abs","map","filter","zip","sorted","reversed","enumerate","all","any","bool","bytes","bytearray")
            "javascript","typescript","tsx","jsx" -> setOf("console","window","document","Array","Object","String","Number","Boolean","Math","JSON","Promise","Set","Map","Date","RegExp","Error","Symbol","BigInt","globalThis")
            "rust" -> setOf("println","print","format","vec","Vec","String","str","Option","Result","Box","Rc","Arc","HashMap","HashSet","BTreeMap","BTreeSet")
            "go" -> setOf("println","printf","make","len","cap","append","copy","delete","new","panic","recover","close","print")
            else -> emptySet()
        }
    }
}
