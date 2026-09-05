package com.varlanv.wrasse.rules

import com.varlanv.wrasse.model.WNodeType

/**
 * Verdict logic for a non-KDoc comment placed inside a type parameter list, compiler-free so it
 * is unit-testable without a kotlinc dependency. A comment inside a `type_parameter` is always
 * disallowed; one that is a direct child of the `type_parameter_list` itself is allowed only when
 * it sits alone on its own line (the immediately preceding leaf is whitespace containing a
 * newline).
 */
object TypeParameterCommentDecision {
    fun decide(parent: WNodeType, precededByNewline: Boolean): String? = when (parent) {
        WNodeType.TYPE_PARAMETER ->
            "A comment inside or on the same line after a type parameter is not allowed. Place it on a separate line above."
        WNodeType.TYPE_PARAMETER_LIST ->
            if (precededByNewline) null else "A comment in a type parameter list is only allowed when placed on a separate line"
        else -> null
    }
}
