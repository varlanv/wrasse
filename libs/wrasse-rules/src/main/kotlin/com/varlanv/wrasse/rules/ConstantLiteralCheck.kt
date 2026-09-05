package com.varlanv.wrasse.rules

import com.varlanv.wrasse.model.WNodeType

/**
 * Shared "is this a bare compile-time-constant literal" check: a numeric/character/boolean
 * literal, or a non-interpolated string template — nothing else. Used by every rule in this
 * batch that inspects a single expression's own node type plus its raw text.
 */
object ConstantLiteralCheck {
    private val NUMERIC_OR_BOOLEAN_OR_CHAR = setOf(
        WNodeType.INTEGER_CONSTANT,
        WNodeType.FLOAT_CONSTANT,
        WNodeType.CHARACTER_CONSTANT,
        WNodeType.BOOLEAN_CONSTANT,
    )

    fun isConstant(type: WNodeType, text: CharSequence): Boolean = when {
        type in NUMERIC_OR_BOOLEAN_OR_CHAR -> true
        type == WNodeType.STRING_TEMPLATE -> !StringTemplateText.hasInterpolation(text)
        else -> false
    }
}
