package com.varlanv.wrasse.rules

import com.varlanv.wrasse.model.WNodeType

/**
 * Verdict logic for a function's `BLOCK` body whose only non-brace, non-whitespace content is one
 * `RETURN` or `THROW`, compiler-free so it is unit-testable without a kotlinc dependency. Any
 * comment present disqualifies the block (matches the upstream rule this derives from exactly,
 * whose own child filter never drops comments either). A `RETURN` case is additionally dropped
 * when the block contains more than one `return` keyword anywhere inside it (a nested `return`
 * inside the returned expression itself would change meaning if hoisted to `=`).
 */
object FunctionExpressionBodyDecision {
    const val MESSAGE = "Function body should be replaced with body expression"

    fun decide(soleChildType: WNodeType?, returnKeywordCount: Int): String? = when (soleChildType) {
        WNodeType.RETURN -> if (returnKeywordCount <= 1) MESSAGE else null
        WNodeType.THROW -> MESSAGE
        else -> null
    }
}
