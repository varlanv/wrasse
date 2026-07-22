package com.varlanv.wrasse.rules

/**
 * Verdict logic for a chain of `&&`/`||` operands mixing both operators, compiler-free so it is
 * unit-testable without a kotlinc dependency.
 */
object MixedConditionOperatorsDecision {
    const val MESSAGE =
    "A condition with mixed usage of '&&' and '||' is hard to read. Use parentheses to clarify the (sub)condition."

    fun decide(hasAnd: Boolean, hasOr: Boolean): String? = if (hasAnd && hasOr) MESSAGE else null
}
