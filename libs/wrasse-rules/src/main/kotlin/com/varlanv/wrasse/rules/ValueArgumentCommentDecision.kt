package com.varlanv.wrasse.rules

import com.varlanv.wrasse.model.WNodeType

/**
 * Verdict logic for a comment placed inside a value argument, compiler-free so it is
 * unit-testable without a kotlinc dependency. Any comment kind (KDoc, block, or EOL) whose
 * immediate parent is a `value_argument` is disallowed unconditionally.
 */
object ValueArgumentCommentDecision {
    fun decide(parent: WNodeType): String? =
    if (parent == WNodeType.VALUE_ARGUMENT) {
        "A comment inside or on the same line after a value argument is not allowed. Place it on a separate line above."
    } else {
        null
    }
}
