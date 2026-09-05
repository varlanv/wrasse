package com.varlanv.wrasse.rules

/**
 * Verdict logic for a chain of `&&`/`||` operands (whitespace-insensitive text comparison,
 * matching the upstream rule this derives from exactly) containing a duplicate, compiler-free so
 * it is unit-testable without a kotlinc dependency.
 */
object UnnecessaryPartOfBinaryExpressionDecision {
    const val MESSAGE = "This binary expression repeats one of its own operands unnecessarily"

    fun decide(operandTexts: List<String>): String? {
        if (operandTexts.size == operandTexts.distinct().size) return null
        return MESSAGE
    }

    /** [text] with every whitespace character outside string and character literals removed. */
    fun normalizeOperand(text: CharSequence): String {
        val builder = StringBuilder(text.length)
        var quote = 0.toChar()
        var i = 0
        while (i < text.length) {
            val c = text[i]
            if (quote != 0.toChar()) {
                builder.append(c)
                if (c == '\\' && i + 1 < text.length) {
                    builder.append(text[i + 1])
                    i++
                } else if (c == quote) {
                    quote = 0.toChar()
                }
            } else if (c == '"' || c == '\'') {
                quote = c
                builder.append(c)
            } else if (!c.isWhitespace()) {
                builder.append(c)
            }
            i++
        }
        return builder.toString()
    }
}
