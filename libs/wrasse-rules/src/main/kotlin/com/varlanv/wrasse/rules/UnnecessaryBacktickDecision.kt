package com.varlanv.wrasse.rules

/**
 * Necessity check for a backtick-quoted identifier token's own raw text (backticks included).
 *
 * Returns the unquoted name when the backticks are provably redundant: the unquoted text is a
 * valid plain Kotlin identifier (starts with a Unicode letter or `_`, continues with letters,
 * digits, or `_`), is not one of Kotlin's hard keywords, and is not made up entirely of `_`
 * characters (Kotlin's own placeholder-name convention). Returns `null` when the backticks are
 * load-bearing: the text isn't backtick-quoted at all, the unquoted text contains a character a
 * plain identifier can't (spaces, punctuation, a leading digit), it names a hard keyword
 * (`class`, `fun`, `typealias`, `typeof`, ...), or it is all underscores. Modifier keywords
 * (`public`, `override`, `data`, `get`, `set`, ...) are contextual, not reserved, so a backtick-
 * quoted modifier keyword used as a plain name is reported same as any other identifier.
 */
object UnnecessaryBacktickDecision {
    private val HARD_KEYWORDS =
    setOf(
        "package",
        "as",
        "typealias",
        "class",
        "interface",
        "this",
        "super",
        "val",
        "var",
        "fun",
        "for",
        "null",
        "true",
        "false",
        "is",
        "in",
        "throw",
        "return",
        "break",
        "continue",
        "object",
        "if",
        "else",
        "while",
        "do",
        "try",
        "when",
        "typeof",
    )

    fun unquote(rawIdentifierText: CharSequence): CharSequence? {
        if (rawIdentifierText.length < 3) return null
        if (rawIdentifierText[0] != '`' || rawIdentifierText[rawIdentifierText.length - 1] != '`') return null
        val unquoted = rawIdentifierText.subSequence(1, rawIdentifierText.length - 1)
        if (unquoted.all { it == '_' }) return null
        if (!isPlainIdentifier(unquoted)) return null
        return if (unquoted.toString() in HARD_KEYWORDS) null else unquoted
    }

    private fun isPlainIdentifier(text: CharSequence): Boolean {
        var index = 0
        while (index < text.length) {
            val codePoint = Character.codePointAt(text, index)
            val valid = codePoint == '_'.code || Character.isLetter(codePoint) || (index > 0 && Character.isDigit(codePoint))
            if (!valid) return false
            index += Character.charCount(codePoint)
        }
        return true
    }
}
