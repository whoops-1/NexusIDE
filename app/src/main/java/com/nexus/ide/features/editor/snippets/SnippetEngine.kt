package com.nexus.ide.features.editor.snippets

/**
 * Tab-trigger snippet engine. Each snippet has a trigger (e.g. "for"),
 * a description, and a template body with placeholders $1, $2, ... and
 * a final $0 (cursor end). When inserted, the placeholders become
 * tab-stops.
 */
class SnippetEngine {

    data class Snippet(val trigger: String, val description: String, val template: String)

    private val snippets = mutableListOf<Snippet>().apply {
        add(Snippet("for", "for loop", "for (let i = 0; i < $1; i++) {\n  $0\n}"))
        add(Snippet("fn", "function", "function $1($2) {\n  $0\n}"))
        add(Snippet("if", "if statement", "if ($1) {\n  $0\n}"))
        add(Snippet("ife", "if/else", "if ($1) {\n  $2\n} else {\n  $0\n}"))
        add(Snippet("class", "class", "class $1 {\n  constructor($2) {\n    $0\n  }\n}"))
        add(Snippet("try", "try/catch", "try {\n  $1\n} catch (e) {\n  $0\n}"))
        add(Snippet("ret", "return", "return $0;"))
        add(Snippet("log", "console.log", "console.log($0);"))
        add(Snippet("pfor", "Python for", "for $1 in $2:\n    $0"))
        add(Snippet("pdef", "Python def", "def $1($2):\n    $0"))
        add(Snippet("pif", "Python if", "if $1:\n    $0"))
    }

    fun all(): List<Snippet> = snippets.toList()
    fun find(prefix: String): List<Snippet> = snippets.filter { it.trigger.startsWith(prefix) }
    fun expand(s: Snippet, builder: StringBuilder, offset: Int): Int {
        // Returns new cursor offset (position of first $1 or $0).
        var i = 0
        var firstStop = -1
        while (i < s.template.length) {
            val c = s.template[i]
            if (c == '$' && i + 1 < s.template.length) {
                val next = s.template[i + 1]
                if (next.isDigit() || next == '0') {
                    if (firstStop < 0 && next != '0') firstStop = builder.length
                    // Skip placeholder text
                    i += 2
                    continue
                }
            }
            builder.append(c)
            i++
        }
        return if (firstStop < 0) builder.length else firstStop
    }
}
