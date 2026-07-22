package com.varlanv.wrasse.rules

/**
 * Verdict logic for a `++`/`--` postfix expression whose own base-expression text exactly matches
 * the other operand of the binary expression it sits in, compiler-free so it is unit-testable
 * without a kotlinc dependency. Ported slice of the upstream rule this derives from: a postfix
 * expression directly assigned back onto (or compared against) the same variable it mutates —
 * `i = i++`, `i = 1 + i++` — where the incremented/decremented value is provably discarded. Text
 * comparison only, matching the upstream rule's own approach; no operator restriction (the
 * upstream check fires for any binary operator, not only `=`).
 */
object UselessPostfixExpressionDecision {
    fun isIncrementOrDecrement(operatorText: CharSequence): Boolean = operatorText.contentEquals("++") || operatorText.contentEquals("--")

    fun decide(isIncrementOrDecrement: Boolean, baseText: CharSequence, otherOperandText: CharSequence, postfixText: CharSequence): String? {
        if (!isIncrementOrDecrement) return null
        if (!baseText.contentEquals(otherOperandText)) return null
        return "The result of the postfix expression '$postfixText' will not be used and is therefore useless"
    }
}
