package com.varlanv.wrasse.rules

import com.varlanv.wrasse.model.WNodeType

/**
 * Verdict logic for a comment placed inside a value parameter, compiler-free so it is
 * unit-testable without a kotlinc dependency. Any comment kind whose immediate parent is a
 * `value_parameter` is disallowed, except a KDoc that is that parameter's own first child (its
 * own dedicated, allowed placement, matching [KdocPlacementDecision]'s own allowance for the same
 * shape).
 */
object ValueParameterCommentDecision {
    fun decide(parent: WNodeType, isKdocFirstChild: Boolean): String? {
        if (parent != WNodeType.VALUE_PARAMETER || isKdocFirstChild) return null
        return "A comment inside or on the same line after a value parameter is not allowed. Place it on a separate line above."
    }
}
