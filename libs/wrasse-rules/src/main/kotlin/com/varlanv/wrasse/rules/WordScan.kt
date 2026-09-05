package com.varlanv.wrasse.rules

/**
 * Word-bounded literal search over source text with the semantics of a `\bword\b` regex (word
 * characters are ASCII letters, digits and `_`), without compiling or matching a regex per call.
 */
object WordScan {
    fun containsWord(text: CharSequence, word: String): Boolean = indexOfWord(text, word, 0) >= 0

    fun indexOfWord(
        text: CharSequence,
        word: String,
        from: Int,
    ): Int {
        if (word.isEmpty()) return -1
        var searchFrom = from
        while (true) {
            val at = indexOfLiteral(text, word, searchFrom)
            if (at < 0) return -1
            if (isBoundary(text, at) && isBoundary(text, at + word.length)) return at
            searchFrom = at + 1
        }
    }

    fun wordOccurrences(text: CharSequence, word: String): List<IntRange> {
        var result: MutableList<IntRange>? = null
        var from = 0
        while (true) {
            val at = indexOfWord(text, word, from)
            if (at < 0) break
            if (result == null) result = ArrayList(2)
            result.add(at until at + word.length)
            from = at + word.length
        }
        return result ?: emptyList()
    }

    /** True if [word] occurs word-bounded and, after optional whitespace, is followed by [next]. */
    fun containsWordFollowedBy(
        text: CharSequence,
        word: String,
        next: Char,
    ): Boolean {
        var from = 0
        while (true) {
            val at = indexOfWord(text, word, from)
            if (at < 0) return false
            var i = at + word.length
            while (i < text.length && text[i].isWhitespace()) i++
            if (i < text.length && text[i] == next) return true
            from = at + 1
        }
    }

    /** True if [text] is `()` once every whitespace character is ignored. */
    fun isEmptyParens(text: CharSequence): Boolean {
        var seen = 0
        for (i in 0 until text.length) {
            val c = text[i]
            if (c.isWhitespace()) continue
            if (seen == 0 && c != '(') return false
            if (seen == 1 && c != ')') return false
            if (seen >= 2) return false
            seen++
        }
        return seen == 2
    }

    private fun indexOfLiteral(
        text: CharSequence,
        word: String,
        from: Int,
    ): Int {
        val last = text.length - word.length
        var i = from
        outer@ while (i <= last) {
            if (text[i] == word[0]) {
                for (j in 1 until word.length) if (text[i + j] != word[j]) {
                    i++
                    continue@outer
                }
                return i
            }
            i++
        }
        return -1
    }

    private fun isBoundary(text: CharSequence, index: Int): Boolean {
        val before = index > 0 && isWordChar(text[index - 1])
        val after = index < text.length && isWordChar(text[index])
        return before != after
    }

    private fun isWordChar(c: Char): Boolean = c in 'a'..'z' || c in 'A'..'Z' || c in '0'..'9' || c == '_'
}
