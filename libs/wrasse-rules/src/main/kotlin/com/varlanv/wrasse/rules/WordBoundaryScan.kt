package com.varlanv.wrasse.rules

/**
 * Plain substring/word-boundary scanning over a [CharSequence], shared by rules that need to
 * check raw source text for a keyword without a node-level check being available (an opaque
 * buffered child's own span, e.g. a `MODIFIER_LIST`'s raw text).
 */
object WordBoundaryScan {
    fun containsWord(haystack: CharSequence, word: String): Boolean {
        var from = 0
        while (true) {
            val idx = indexOf(haystack, word, from)
            if (idx < 0) return false
            val before = if (idx == 0) ' ' else haystack[idx - 1]
            val afterPos = idx + word.length
            val after = if (afterPos >= haystack.length) ' ' else haystack[afterPos]
            if (!isWordChar(before) && !isWordChar(after)) return true
            from = idx + 1
        }
    }

    private fun isWordChar(c: Char): Boolean = c.isLetterOrDigit() || c == '_'

    fun indexOf(source: CharSequence, part: String, from: Int): Int {
        val last = source.length - part.length
        outer@ for (i in from..last) {
            for (j in part.indices) {
                if (source[i + j] != part[j]) continue@outer
            }
            return i
        }
        return -1
    }

    fun indexOfChar(text: CharSequence, target: Char, from: Int = 0): Int {
        for (i in from until text.length) if (text[i] == target) return i
        return -1
    }
}
