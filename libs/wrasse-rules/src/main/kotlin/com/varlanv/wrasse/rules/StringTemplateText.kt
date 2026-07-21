package com.varlanv.wrasse.rules

/**
 * Text-only predicates over a string template literal's own raw source span (quotes included),
 * shared by every rule that needs to know "is this string template interpolated" without
 * buffering its individual entry nodes.
 */
object StringTemplateText {
    /**
     * True if an unescaped `$` occurs anywhere in [text] — the only way Kotlin string
     * interpolation (`$name` / `${expr}`) is written. A `\$` escape is skipped over along with
     * every other two-character escape sequence, so an escaped dollar never counts.
     */
    fun hasInterpolation(text: CharSequence): Boolean {
        var i = 0
        while (i < text.length) {
            val c = text[i]
            if (c == '\\') {
                i += 2
                continue
            }
            if (c == '$') return true
            i++
        }
        return false
    }
}
