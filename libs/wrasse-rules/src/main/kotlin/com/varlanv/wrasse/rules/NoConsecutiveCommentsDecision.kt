package com.varlanv.wrasse.rules

import com.varlanv.wrasse.model.WNodeType

/**
 * Verdict logic for a comment immediately preceded by another comment, compiler-free so it is
 * unit-testable without a kotlinc dependency. Consecutive EOL comments are always allowed. A KDoc
 * or a block comment preceding another comment is disallowed even across a blank line. Any other
 * mismatched pair is allowed only when separated by a blank line.
 */
object NoConsecutiveCommentsDecision {
    fun decide(
        previous: WNodeType,
        current: WNodeType,
        separatedByBlankLine: Boolean,
    ): String? = when {
        previous == WNodeType.KDOC && current == WNodeType.KDOC ->
            "${describe(current)} may not be preceded by ${describe(previous)}"
        previous == WNodeType.KDOC ->
            "${describe(
                    current,
                )} may not be preceded by ${describe(previous)}. Reversed order is allowed though when " +
                "separated by a newline."
        previous == WNodeType.BLOCK_COMMENT && current == WNodeType.BLOCK_COMMENT ->
            "${describe(current)} may not be preceded by ${describe(previous)}"
        previous == WNodeType.EOL_COMMENT && current == WNodeType.EOL_COMMENT -> null
        previous != current && separatedByBlankLine -> null
        else -> "${describe(current)} may not be preceded by ${describe(previous)} unless separated by a blank line"
    }

    private fun describe(type: WNodeType): String = when (type) {
        WNodeType.EOL_COMMENT -> "an EOL comment"
        WNodeType.BLOCK_COMMENT -> "a block comment"
        WNodeType.KDOC -> "a KDoc"
        else -> type.name.lowercase()
    }
}
