package com.varlanv.wrasse.rules

import com.varlanv.wrasse.model.WNodeType

/**
 * Verdict logic for a `${receiver.toString()}` string-template entry, compiler-free so it is
 * unit-testable without a kotlinc dependency. `super.toString()` is exempt (there is no bare
 * `$super` shorthand to fall back to); the call is matched as literal whole-span text
 * (`toString()`, no arguments, no internal whitespace), matching the upstream rule this derives
 * from exactly.
 */
object RedundantToStringInTemplateDecision {
    const val MESSAGE = "Redundant '.toString()' call in string template"

    fun decide(
        receiverType: WNodeType,
        selectorType: WNodeType,
        selectorText: CharSequence,
    ): String? {
        if (receiverType == WNodeType.SUPER_EXPRESSION) return null
        if (selectorType != WNodeType.CALL_EXPRESSION) return null
        if (!selectorText.contentEquals("toString()")) return null
        return MESSAGE
    }
}
