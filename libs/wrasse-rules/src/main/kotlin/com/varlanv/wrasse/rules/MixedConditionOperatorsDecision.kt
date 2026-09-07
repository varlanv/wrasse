package com.varlanv.wrasse.rules

import com.varlanv.wrasse.lang.WEdit

/**
 * Verdict logic for a chain of `&&`/`||` operands mixing both operators, compiler-free so it is
 * unit-testable without a kotlinc dependency.
 *
 * [wrapEdits] parenthesizes a maximal `&&` sub-chain the moment it is found to be a direct operand
 * of a `||` node: [parentIsAnd] is the enclosing node's own operator, [childIsAnd] the merged
 * child's own operator — a wrap is due only when the parent is `||` and the child is `&&`, in
 * which case [childStart]/[childEnd] (the child's own span, already covering every `&&` nested
 * inside it) get an opening and closing paren inserted around them. Kotlin's grammar can never
 * nest a bare `||` as a direct child of `&&` (it would have parsed as the outer operator instead),
 * so the reverse parent/child pairing never arises; an operand already wrapped in explicit
 * parentheses is a `PARENTHESIZED` node rather than `BINARY_EXPRESSION`, so it is never passed
 * here as a merge candidate in the first place.
 */
object MixedConditionOperatorsDecision {
    const val MESSAGE =
        "A condition with mixed usage of '&&' and '||' is hard to read. Use parentheses to clarify the (sub)condition."

    fun decide(hasAnd: Boolean, hasOr: Boolean): String? = if (hasAnd && hasOr) MESSAGE else null

    fun wrapEdits(parentIsAnd: Boolean, childIsAnd: Boolean, childStart: Int, childEnd: Int): List<WEdit> =
        if (!parentIsAnd && childIsAnd) {
            listOf(WEdit(childStart, childStart, "("), WEdit(childEnd, childEnd, ")"))
        } else {
            emptyList()
        }
}
