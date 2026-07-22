package com.varlanv.wrasse.rules

import com.varlanv.wrasse.model.WNodeType

/**
 * Verdict logic for one `+` [WNodeType.BINARY_EXPRESSION] whose own left operand is textually a
 * string (a string template literal, or a `.toString()`-suffixed call), compiler-free so it is
 * unit-testable without a kotlinc dependency. A string-literal left operand is decidable from pure
 * grammar alone — no resolution needed to know a quoted literal's type is `String` — matching the
 * upstream rule this derives from's own equally syntax-only check.
 */
object StringConcatenationDecision {
    const val MESSAGE = "String concatenation via '+'; prefer a string template"

    fun isStringConcatenationStart(leftType: WNodeType, leftText: CharSequence, rightType: WNodeType): Boolean = when {
        leftType == WNodeType.STRING_TEMPLATE -> true
        leftType == WNodeType.DOT_QUALIFIED_EXPRESSION && rightType == WNodeType.STRING_TEMPLATE -> leftText.endsWith("toString()")
        else -> false
    }
}
