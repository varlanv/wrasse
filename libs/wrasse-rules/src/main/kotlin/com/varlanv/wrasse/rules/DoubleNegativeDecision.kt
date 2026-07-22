package com.varlanv.wrasse.rules

/**
 * Counts a chain of consecutive `!` prefix operators (optional whitespace between them) starting
 * exactly at a `PREFIX_EXPRESSION`'s own span. Two or more means the expression is negated more
 * than once and could be simplified — matching the non-resolution-requiring half of the upstream
 * rule this id derives from (its `.not()`/`not()` qualified-call forms are dropped: telling a
 * genuine `Boolean.not()` apart from a user's own same-named function needs resolution).
 */
object DoubleNegativeDecision {
    const val MESSAGE = "Expression negated more than once; this can be simplified"

    fun exclamationChainLength(sourceText: CharSequence, start: Int, end: Int): Int {
        var i = start
        var count = 0
        while (i < end) {
            when {
                sourceText[i] == '!' -> {
                    count++
                    i++
                }

                sourceText[i].isWhitespace() -> i++
                else -> return count
            }
        }
        return count
    }

    fun decide(chainLength: Int): String? = if (chainLength >= 2) MESSAGE else null
}
