package com.varlanv.wrasse.rules

import com.varlanv.wrasse.model.WNodeType

/**
 * Verdict logic for a non-KDoc comment placed inside a type argument list, compiler-free so it is
 * unit-testable without a kotlinc dependency. A comment inside a `type_projection` is always
 * disallowed; one that is a direct child of the `type_argument_list` itself is allowed only when
 * it sits alone on its own line (the immediately preceding leaf is whitespace containing a
 * newline).
 */
object TypeArgumentCommentDecision {
    fun decide(parent: WNodeType, precededByNewline: Boolean): String? = when (parent) {
        WNodeType.TYPE_PROJECTION ->
            "A comment inside or on the same line after a type projection is not allowed. Place it on a separate line above."
        WNodeType.TYPE_ARGUMENT_LIST ->
            if (precededByNewline) null else "A comment in a type argument list is only allowed when placed on a separate line"
        else -> null
    }
}
