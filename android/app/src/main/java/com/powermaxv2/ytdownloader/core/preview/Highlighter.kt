package com.powermaxv2.ytdownloader.core.preview

import com.powermaxv2.ytdownloader.core.files.SafeNames

// A small syntax highlighter for the Aperçu: one forward pass over the text, no regular expressions, no
// recursion (a hostile file cannot overflow the stack), a bounded number of tokens. It colours keywords, strings,
// comments, numbers, types, annotations, tags and attribute / key names; anything else stays plain. Pure.

enum class CodeLang(val label: String) {
    KOTLIN("Kotlin"),
    JAVA("Java"),
    JAVASCRIPT("JavaScript"),
    TYPESCRIPT("TypeScript"),
    PYTHON("Python"),
    CLIKE("C / C++ / Go / Rust…"),
    SHELL("Shell"),
    MARKUP("XML / HTML"),
    CSS("CSS"),
    JSON("JSON"),
    YAML("YAML"),
    PLAIN("Texte"),
    ;

    companion object {
        /** Language of a file from its name ("MainActivity.kt" → KOTLIN, "Dockerfile" → SHELL). */
        fun fromName(name: String): CodeLang {
            val lower = name.lowercase()
            if (lower == "dockerfile" || lower == "makefile" || lower.endsWith(".dockerfile")) return SHELL
            return when (SafeNames.extensionOf(name)) {
                "kt", "kts", "gradle" -> KOTLIN
                "java", "groovy", "scala" -> JAVA
                "js", "mjs", "cjs", "jsx" -> JAVASCRIPT
                "ts", "tsx", "mts", "cts" -> TYPESCRIPT
                "py", "pyw", "pyi" -> PYTHON
                "c", "h", "cpp", "hpp", "cc", "cxx", "hh", "cs", "go", "rs", "swift", "dart", "php", "m", "mm", "lua", "sql", "r", "pl" -> CLIKE
                "sh", "bash", "zsh", "fish", "ksh", "env", "bat", "cmd", "ps1", "dockerfile", "makefile" -> SHELL
                "xml", "svg", "html", "htm", "xhtml", "vue", "plist", "xsd", "xsl", "fxml", "tex" -> MARKUP
                "css", "scss", "less", "sass" -> CSS
                "json", "geojson", "jsonl", "ndjson", "webmanifest" -> JSON
                "yaml", "yml", "toml", "properties", "ini", "cfg", "conf" -> YAML
                else -> PLAIN
            }
        }
    }
}

enum class TokenType { KEYWORD, STRING, COMMENT, NUMBER, TYPE, ANNOTATION, TAG, ATTR }

/** A coloured stretch of text, [start] inclusive, [end] exclusive. */
data class Token(val start: Int, val end: Int, val type: TokenType)

object Highlighter {
    /** Most tokens produced for one file; the rest of a huge file stays plain. */
    const val MAX_TOKENS = 300_000

    /** Tokens of [text] in order, never overlapping. Line ends are expected as "\n" (see [normalizeLineEnds]). */
    fun tokenize(text: String, lang: CodeLang): List<Token> = when (lang) {
        CodeLang.PLAIN -> emptyList()
        CodeLang.MARKUP -> Markup(text).run()
        CodeLang.JSON -> JsonScan(text).run()
        CodeLang.YAML -> YamlScan(text).run()
        CodeLang.CSS -> CssScan(text).run()
        else -> CLike(text, specOf(lang)).run()
    }

    /** "\r\n" and lone "\r" become "\n". */
    fun normalizeLineEnds(text: String): String =
        if (text.indexOf('\r') < 0) text else text.replace("\r\n", "\n").replace('\r', '\n')

    /** Offsets where each line of [text] starts (the first is 0); a final "\n" opens no extra line. */
    fun lineStarts(text: String): IntArray {
        var count = 1
        for (i in 0 until text.length - 1) if (text[i] == '\n') count++
        val starts = IntArray(count)
        var n = 1
        for (i in 0 until text.length - 1) if (text[i] == '\n') starts[n++] = i + 1
        return starts
    }

    /** End of line [index] (exclusive, before its "\n"). */
    fun lineEnd(text: String, starts: IntArray, index: Int): Int {
        if (index + 1 < starts.size) return starts[index + 1] - 1
        return if (text.isNotEmpty() && text.last() == '\n' && text.length - 1 >= starts[index]) text.length - 1 else text.length
    }

    /**
     * Tokens overlapping [from, to), clipped to it and shifted so that [from] is 0 (what a line's
     * AnnotatedString needs). Binary search: cheap for any line of a big file.
     */
    fun spansIn(tokens: List<Token>, from: Int, to: Int): List<Token> {
        if (tokens.isEmpty() || to <= from) return emptyList()
        var lo = 0
        var hi = tokens.size
        while (lo < hi) {
            val mid = (lo + hi) ushr 1
            if (tokens[mid].end <= from) lo = mid + 1 else hi = mid
        }
        val out = ArrayList<Token>()
        var i = lo
        while (i < tokens.size && tokens[i].start < to) {
            val t = tokens[i]
            val s = maxOf(t.start, from)
            val e = minOf(t.end, to)
            if (e > s) out.add(Token(s - from, e - from, t.type))
            i++
        }
        return out
    }

    // MARK: - C-like languages (Kotlin, Java, JS/TS, Python, C family, shell)

    private class Spec(
        val keywords: Set<String>,
        val lineComments: List<String>,
        val blockComment: Boolean,
        val tripleQuotes: Boolean,
        val backtick: Boolean,
        val annotations: Boolean,
        val types: Boolean,
        val dollarVars: Boolean,
        val preprocessor: Boolean,
    )

    private fun words(s: String): Set<String> = s.trim().split(Regex("\\s+")).toHashSet()

    private val kotlinWords = words(
        """
        package import class interface fun val var object companion data sealed enum annotation open abstract override
        private protected internal public if else when for while do return break continue try catch finally throw is
        in as typealias this super null true false const lateinit suspend inline reified by init constructor where out
        vararg operator infix tailrec external crossinline noinline value
        """,
    )
    private val javaWords = words(
        """
        abstract assert boolean break byte case catch char class const continue default do double else enum extends
        final finally float for goto if implements import instanceof int interface long native new package private
        protected public return short static strictfp super switch synchronized this throw throws transient try void
        volatile while true false null var record yield sealed permits def
        """,
    )
    private val jsWords = words(
        """
        break case catch class const continue debugger default delete do else export extends finally for function if
        import in instanceof let new return super switch this throw try typeof var void while with yield async await of
        static get set true false null undefined from
        """,
    )
    private val tsWords = jsWords + words(
        """
        interface type enum implements namespace declare abstract private protected public readonly as any number
        string boolean never unknown keyof is satisfies
        """,
    )
    private val pythonWords = words(
        """
        False None True and as assert async await break class continue def del elif else except finally for from
        global if import in is lambda nonlocal not or pass raise return try while with yield self match case
        """,
    )
    private val cWords = words(
        """
        auto break case char const continue default do double else enum extern float for goto if inline int long
        register return short signed sizeof static struct switch typedef union unsigned void volatile while bool true
        false nullptr NULL class namespace template typename public private protected virtual override new delete this
        using try catch throw func package import go defer chan map range type var interface fn let mut impl trait pub
        use mod match self Self crate where loop as ref move async await dyn guard extension protocol init deinit nil
        final late required function echo foreach elseif null string internal readonly select from insert update
        delete into values create table and or not local end then elsif
        """,
    )
    private val shellWords = words(
        """
        if then else elif fi for while until do done case esac in function return local export source alias unset
        readonly declare echo exit break continue set shift trap cd FROM RUN CMD COPY ADD ENV WORKDIR ENTRYPOINT
        EXPOSE ARG USER VOLUME LABEL
        """,
    )

    private fun specOf(lang: CodeLang): Spec = when (lang) {
        CodeLang.KOTLIN -> Spec(kotlinWords, listOf("//"), true, true, false, true, true, false, false)
        CodeLang.JAVA -> Spec(javaWords, listOf("//"), true, true, false, true, true, false, false)
        CodeLang.JAVASCRIPT -> Spec(jsWords, listOf("//"), true, false, true, true, true, false, false)
        CodeLang.TYPESCRIPT -> Spec(tsWords, listOf("//"), true, false, true, true, true, false, false)
        CodeLang.PYTHON -> Spec(pythonWords, listOf("#"), false, true, false, true, true, false, false)
        CodeLang.SHELL -> Spec(shellWords, listOf("#"), false, false, true, false, false, true, false)
        else -> Spec(cWords, listOf("//", "--"), true, false, true, true, true, false, true)
    }

    /** Shared token list with the [MAX_TOKENS] bound. */
    private open class Scanner(val text: String) {
        val tokens = ArrayList<Token>()
        val full: Boolean get() = tokens.size >= MAX_TOKENS
        fun add(start: Int, end: Int, type: TokenType) {
            if (end > start && !full) tokens.add(Token(start, end, type))
        }
        fun at(i: Int, s: String): Boolean = text.startsWith(s, i)
        fun lineEndFrom(i: Int): Int = text.indexOf('\n', i).let { if (it < 0) text.length else it }

        /** End of a quoted string opened at [i] by [quote] (escapes with backslash; stops at the line end unless [multiline]). */
        fun stringEnd(i: Int, quote: Char, multiline: Boolean): Int {
            var j = i + 1
            while (j < text.length) {
                val c = text[j]
                if (c == '\\') {
                    j += 2
                    continue
                }
                if (c == quote) return j + 1
                if (c == '\n' && !multiline) return j
                j++
            }
            return text.length
        }

        /** End of a number starting at [i]: digits, letters (hex, suffixes), "_", a dot before a digit, exponent signs. */
        fun numberEnd(i: Int): Int {
            var j = i
            while (j < text.length) {
                val c = text[j]
                val ok = c.isLetterOrDigit() || c == '_' ||
                    (c == '.' && j + 1 < text.length && text[j + 1].isDigit()) ||
                    ((c == '+' || c == '-') && j > i && (text[j - 1] == 'e' || text[j - 1] == 'E') && !text.startsWith("0x", i, true))
                if (!ok) break
                j++
            }
            return j
        }

        fun isIdentStart(c: Char) = c.isLetter() || c == '_'
        fun isIdentPart(c: Char) = c.isLetterOrDigit() || c == '_'
    }

    private class CLike(text: String, val spec: Spec) : Scanner(text) {
        fun run(): List<Token> {
            var i = 0
            var lineStart = true
            while (i < text.length && !full) {
                val c = text[i]
                if (c == '\n') {
                    lineStart = true
                    i++
                    continue
                }
                if (c == ' ' || c == '\t') {
                    i++
                    continue
                }
                val atLineStart = lineStart
                lineStart = false
                // Comments.
                if (spec.blockComment && at(i, "/*")) {
                    val end = text.indexOf("*/", i + 2).let { if (it < 0) text.length else it + 2 }
                    add(i, end, TokenType.COMMENT)
                    i = end
                    continue
                }
                val line = spec.lineComments.firstOrNull { at(i, it) }
                if (line != null && (line != "#" || i == 0 || !isIdentPart(text[i - 1])) && (line != "--" || spec.preprocessor)) {
                    // "--" only starts a comment at a line start or after a space (SQL, Lua), not in "i--".
                    if (line != "--" || atLineStart || text[i - 1] == ' ') {
                        val end = lineEndFrom(i)
                        add(i, end, TokenType.COMMENT)
                        i = end
                        continue
                    }
                }
                if (spec.preprocessor && c == '#' && atLineStart) {
                    val end = lineEndFrom(i)
                    add(i, end, TokenType.ANNOTATION)
                    i = end
                    continue
                }
                // Strings.
                if (spec.tripleQuotes && (at(i, "\"\"\"") || at(i, "'''"))) {
                    val q = text.substring(i, i + 3)
                    val end = text.indexOf(q, i + 3).let { if (it < 0) text.length else it + 3 }
                    add(i, end, TokenType.STRING)
                    i = end
                    continue
                }
                if (c == '"' || c == '\'' || (c == '`' && spec.backtick)) {
                    // Kotlin / Java / Rust char literals and Python strings alike; a lone apostrophe in prose stays harmless.
                    val end = stringEnd(i, c, multiline = c == '`')
                    add(i, end, TokenType.STRING)
                    i = end
                    continue
                }
                if (spec.dollarVars && c == '$' && i + 1 < text.length) {
                    var j = i + 1
                    if (text[j] == '{') {
                        j = text.indexOf('}', j).let { if (it < 0) lineEndFrom(i) else it + 1 }
                    } else {
                        while (j < text.length && (isIdentPart(text[j]) || (j == i + 1 && text[j] in "@#?$!*0123456789"))) j++
                    }
                    if (j > i + 1) {
                        add(i, j, TokenType.ATTR)
                        i = j
                        continue
                    }
                }
                if (spec.annotations && c == '@' && i + 1 < text.length && isIdentStart(text[i + 1])) {
                    var j = i + 1
                    while (j < text.length && (isIdentPart(text[j]) || text[j] == '.')) j++
                    add(i, j, TokenType.ANNOTATION)
                    i = j
                    continue
                }
                if (c.isDigit() || (c == '.' && i + 1 < text.length && text[i + 1].isDigit())) {
                    if (i == 0 || !isIdentPart(text[i - 1])) {
                        val end = numberEnd(i)
                        add(i, end, TokenType.NUMBER)
                        i = end
                        continue
                    }
                }
                if (isIdentStart(c) || (c == '$' && !spec.dollarVars)) {
                    var j = i + 1
                    while (j < text.length && (isIdentPart(text[j]) || text[j] == '$')) j++
                    val word = text.substring(i, j)
                    when {
                        word in spec.keywords -> add(i, j, TokenType.KEYWORD)
                        spec.types && word[0].isUpperCase() && word.length > 1 && word.any { it.isLowerCase() } -> add(i, j, TokenType.TYPE)
                    }
                    i = j
                    continue
                }
                i++
            }
            return tokens
        }
    }

    // MARK: - XML / HTML

    private class Markup(text: String) : Scanner(text) {
        fun run(): List<Token> {
            var i = 0
            while (i < text.length && !full) {
                if (at(i, "<!--")) {
                    val end = text.indexOf("-->", i + 4).let { if (it < 0) text.length else it + 3 }
                    add(i, end, TokenType.COMMENT)
                    i = end
                    continue
                }
                if (at(i, "<![CDATA[")) {
                    val end = text.indexOf("]]>", i).let { if (it < 0) text.length else it + 3 }
                    add(i, end, TokenType.STRING)
                    i = end
                    continue
                }
                if (text[i] == '<' && i + 1 < text.length && (text[i + 1].isLetter() || text[i + 1] in "/!?")) {
                    i = tag(i)
                    continue
                }
                i++
            }
            return tokens
        }

        /** A tag from its "<" to its ">": name, attributes, quoted values. Returns where it ends. */
        private fun tag(start: Int): Int {
            var i = start + 1
            while (i < text.length && text[i] in "/!?") i++
            while (i < text.length && (text[i].isLetterOrDigit() || text[i] in ":-_.")) i++
            add(start, i, TokenType.TAG)
            while (i < text.length && !full) {
                val c = text[i]
                when {
                    c == '>' -> {
                        add(i, i + 1, TokenType.TAG)
                        return i + 1
                    }
                    (c == '/' || c == '?') && i + 1 < text.length && text[i + 1] == '>' -> {
                        add(i, i + 2, TokenType.TAG)
                        return i + 2
                    }
                    c == '"' || c == '\'' -> {
                        val end = text.indexOf(c, i + 1).let { if (it < 0) text.length else it + 1 }
                        add(i, end, TokenType.STRING)
                        i = end
                    }
                    c.isLetter() || c == '_' || c == ':' || c == '@' -> {
                        var j = i + 1
                        while (j < text.length && (text[j].isLetterOrDigit() || text[j] in ":-_.")) j++
                        add(i, j, TokenType.ATTR)
                        i = j
                    }
                    c == '<' -> return i
                    else -> i++
                }
            }
            return i
        }
    }

    // MARK: - JSON

    private class JsonScan(text: String) : Scanner(text) {
        fun run(): List<Token> {
            var i = 0
            while (i < text.length && !full) {
                val c = text[i]
                when {
                    c == '"' -> {
                        val end = stringEnd(i, '"', multiline = false)
                        var k = end
                        while (k < text.length && text[k].isWhitespace()) k++
                        add(i, end, if (k < text.length && text[k] == ':') TokenType.ATTR else TokenType.STRING)
                        i = end
                    }
                    c == '-' || c.isDigit() -> {
                        val end = numberEnd(i + 1)
                        add(i, end, TokenType.NUMBER)
                        i = end
                    }
                    c.isLetter() -> {
                        var j = i
                        while (j < text.length && text[j].isLetter()) j++
                        val word = text.substring(i, j)
                        if (word == "true" || word == "false" || word == "null") add(i, j, TokenType.KEYWORD)
                        i = j
                    }
                    else -> i++
                }
            }
            return tokens
        }
    }

    // MARK: - YAML, TOML, properties, ini

    private class YamlScan(text: String) : Scanner(text) {
        private val constants = setOf("true", "false", "null", "yes", "no", "on", "off", "True", "False", "Null", "~")

        fun run(): List<Token> {
            var lineStart = 0
            while (lineStart < text.length && !full) {
                val end = lineEndFrom(lineStart)
                line(lineStart, end)
                lineStart = end + 1
            }
            return tokens
        }

        private fun line(start: Int, end: Int) {
            var i = start
            while (i < end && (text[i] == ' ' || text[i] == '\t')) i++
            if (i >= end) return
            if (text[i] == '#' || text[i] == ';') {
                add(i, end, TokenType.COMMENT)
                return
            }
            if (text[i] == '[') {
                // TOML / ini section header.
                add(i, end, TokenType.TAG)
                return
            }
            if (text[i] == '-' && i + 1 < end && text[i + 1] == ' ') i += 2
            // key: value / key = value
            var j = i
            while (j < end && text[j] != ':' && text[j] != '=' && text[j] != '#' && text[j] != '"' && text[j] != '\'') j++
            if (j < end && (text[j] == ':' || text[j] == '=') && j > i && (j + 1 >= end || text[j + 1] == ' ' || text[j] == '=')) {
                add(i, j, TokenType.ATTR)
                i = j + 1
            }
            value(i, end)
        }

        private fun value(from: Int, end: Int) {
            var i = from
            while (i < end && !full) {
                val c = text[i]
                when {
                    c == '#' && (i == from || text[i - 1] == ' ') -> {
                        add(i, end, TokenType.COMMENT)
                        return
                    }
                    c == '"' || c == '\'' -> {
                        val e = minOf(stringEnd(i, c, multiline = false), end)
                        add(i, e, TokenType.STRING)
                        i = e
                    }
                    c == ' ' || c == '\t' -> i++
                    else -> {
                        var j = i
                        while (j < end && text[j] != ' ' && text[j] != ',' && text[j] != ']' && text[j] != '}') j++
                        val word = text.substring(i, j)
                        when {
                            word in constants -> add(i, j, TokenType.KEYWORD)
                            word.isNotEmpty() && (word[0].isDigit() || (word[0] == '-' && word.length > 1 && word[1].isDigit())) &&
                                word.all { it.isDigit() || it in ".-+_eExXabcdefABCDEF:" } -> add(i, j, TokenType.NUMBER)
                        }
                        i = if (j == i) j + 1 else j
                    }
                }
            }
        }
    }

    // MARK: - CSS

    private class CssScan(text: String) : Scanner(text) {
        fun run(): List<Token> {
            var i = 0
            var depth = 0
            while (i < text.length && !full) {
                val c = text[i]
                when {
                    at(i, "/*") -> {
                        val end = text.indexOf("*/", i + 2).let { if (it < 0) text.length else it + 2 }
                        add(i, end, TokenType.COMMENT)
                        i = end
                    }
                    at(i, "//") && depth >= 0 && (i == 0 || text[i - 1] != ':') -> {
                        // SCSS / Less line comments ("://" in a url() stays a value).
                        val end = lineEndFrom(i)
                        add(i, end, TokenType.COMMENT)
                        i = end
                    }
                    c == '"' || c == '\'' -> {
                        val end = stringEnd(i, c, multiline = false)
                        add(i, end, TokenType.STRING)
                        i = end
                    }
                    c == '{' -> {
                        depth++
                        i++
                    }
                    c == '}' -> {
                        if (depth > 0) depth--
                        i++
                    }
                    c == '@' -> {
                        var j = i + 1
                        while (j < text.length && (text[j].isLetterOrDigit() || text[j] == '-')) j++
                        add(i, j, TokenType.KEYWORD)
                        i = j
                    }
                    c == '#' && depth > 0 -> {
                        var j = i + 1
                        while (j < text.length && text[j].isLetterOrDigit()) j++
                        add(i, j, TokenType.NUMBER)
                        i = j
                    }
                    c.isDigit() || (c == '.' && i + 1 < text.length && text[i + 1].isDigit() && depth > 0) -> {
                        var j = numberEnd(i)
                        if (j < text.length && text[j] == '%') j++
                        add(i, j, TokenType.NUMBER)
                        i = j
                    }
                    c.isLetter() || c == '-' || c == '_' -> {
                        var j = i + 1
                        while (j < text.length && (text[j].isLetterOrDigit() || text[j] == '-' || text[j] == '_')) j++
                        if (depth > 0) {
                            var k = j
                            while (k < text.length && (text[k] == ' ' || text[k] == '\t')) k++
                            if (k < text.length && text[k] == ':') add(i, j, TokenType.ATTR)
                        } else if (i == 0 || text[i - 1] !in ".#:") {
                            add(i, j, TokenType.TAG)
                        }
                        i = j
                    }
                    else -> i++
                }
            }
            return tokens
        }
    }
}
