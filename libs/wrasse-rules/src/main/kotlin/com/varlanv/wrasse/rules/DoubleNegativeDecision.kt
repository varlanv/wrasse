package com.varlanv.wrasse.rules

import com.varlanv.wrasse.lang.WEdit

/**
 * Verdict logic for a chain of two or more `!` prefix operators — bare (`!!x`) or each layer
 * wrapped in its own parentheses (`!(!x)`) — compiler-free so it is unit-testable without a
 * kotlinc dependency. [depth] is the total count of `!` layers the caller already walked down to
 * reach [operandText] (the first sub-expression that isn't itself a negation); two or more means
 * the chain can be simplified.
 *
 * [editsFor] replaces the caller-supplied span (the outermost `!` chain's own span) with
 * [operandText] verbatim when [depth] is even (the negations cancel out, any parentheses [
 * operandText] itself still carries — e.g. around a `&&` — are kept as-is) or with a single leading
 * `!` in front of it when [depth] is odd.
 */
object DoubleNegativeDecision {
    const val MESSAGE = "Expression negated more than once; this can be simplified"

    fun decide(depth: Int): String? = if (depth >= 2) MESSAGE else null

    fun editsFor(
        depth: Int,
        replaceStart: Int,
        replaceEnd: Int,
        operandText: CharSequence,
    ): List<WEdit> {
        val replacement = if (depth % 2 == 0) operandText.toString() else "!$operandText"
        return listOf(WEdit(replaceStart, replaceEnd, replacement))
    }
}
