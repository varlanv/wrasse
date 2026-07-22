package com.varlanv.wrasse.rules

/**
 * Text-only predicates over a string template literal's own raw source span (quotes included),
 * shared by every rule that needs to know "is this string template interpolated" without
 * buffering its individual entry nodes.
 */
object StringTemplateText {
    /**
     * True if an unescaped `$` occurs in [text] and is followed by `{`, a letter, or `_` — the
     * only forms that start Kotlin string interpolation (`$name` / `${expr}`). A `$` followed by
     * anything else (or nothing) is a literal dollar, not interpolation. A `\$` escape is skipped
     * over along with every other two-character escape sequence, so an escaped dollar never counts.
     */
    fun hasInterpolation(text: CharSequence): Boolean {
        var i = 0
        while (i < text.length) {
            val c = text[i]
            if (c == '\\') {
                i += 2
                continue
            }
            if (c == '$') {
                val next = if (i + 1 < text.length) text[i + 1] else ' '
                if (next == '{' || next == '_' || next.isLetter()) return true
            }
            i++
        }
        return false
    }
}
