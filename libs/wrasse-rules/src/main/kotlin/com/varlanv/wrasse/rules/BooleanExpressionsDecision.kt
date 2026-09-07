package com.varlanv.wrasse.rules

import com.varlanv.wrasse.model.WNodeType

/**
 * Verdict logic for a directly-simplifiable `&&`/`||` boolean condition, compiler-free so it is
 * unit-testable without a kotlinc dependency. Two shapes only, both decidable from an operand's own
 * node type and raw text with no resolution: a bare `true`/`false` literal operand (`x && false`,
 * `x || true`, an annihilator/identity law), and a directly-negated complement of the other operand
 * (`a && !a`, `a || !a`, comparing raw text — the same crude equality this whole batch's binary-
 * chain rules already rely on, e.g. [UnnecessaryPartOfBinaryExpressionDecision]). This is a
 * deliberately narrow slice of the upstream rule this derives from, whose own general propositional
 * simplifier (De Morgan's laws, the distributive law, arbitrary-depth chain flattening via a
 * bundled third-party boolean-algebra library) is a different order of implementation than any
 * other rule in this project and is not attempted; the idempotent-duplicate-operand law it also
 * covers already ships as this project's own [UnnecessaryPartOfBinaryExpressionDecision], and its
 * double-negation law already ships as [DoubleNegativeDecision] — neither is re-detected here to
 * avoid two ids firing on the same violation.
 */
object BooleanExpressionsDecision {
    const val MESSAGE = "This boolean condition can be simplified"

    fun isBooleanLiteral(text: CharSequence): Boolean = text.contentEquals("true") || text.contentEquals("false")

    fun isLiteralAbsorption(
        leftType: WNodeType,
        leftText: CharSequence,
        rightType: WNodeType,
        rightText: CharSequence,
    ): Boolean =
        (leftType == WNodeType.BOOLEAN_CONSTANT && isBooleanLiteral(leftText)) ||
            (rightType == WNodeType.BOOLEAN_CONSTANT && isBooleanLiteral(rightText))

    fun decide(
        isAndOrOperator: Boolean,
        isLiteralAbsorption: Boolean,
        isDirectComplementPair: Boolean,
    ): String? =
        if (isAndOrOperator && (isLiteralAbsorption || isDirectComplementPair)) MESSAGE else null
}
