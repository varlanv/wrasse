package com.varlanv.wrasse.rules

import com.varlanv.wrasse.lang.WEdit
import com.varlanv.wrasse.model.WNodeType

/** `null` means the entry is out of this rule's scope entirely — no report at all. */
class RedundantToStringInTemplateVerdict(val edits: List<WEdit>)

/**
 * Verdict logic for a `${receiver.toString()}` string-template entry,
 * compiler-free so it is unit-testable without a kotlinc dependency. `super.toString()` is exempt
 * (there is no bare `$super` shorthand to fall back to); the call is matched as literal whole-span
 * text (`toString()`, no arguments, no internal whitespace), matching the upstream rule this
 * derives from exactly.
 *
 * [decide] returns edits — replacing the whole entry with `$receiver` — only for a bare-dot call on
 * a plain, non-backtick identifier: that shorthand is the only rewrite a template's own null-to-
 * `"null"` handling can't distinguish from the original. Any other bare-dot receiver (a dotted
 * chain, a call, a backtick identifier) keeps its `${ }` braces and drops only the `.toString()`
 * call. A bare `this.toString()` is reported with no edit.
 */
object RedundantToStringInTemplateDecision {
    const val MESSAGE = "Redundant '.toString()' call in string template"

    fun decide(
        receiverType: WNodeType,
        selectorType: WNodeType,
        selectorText: CharSequence,
        receiverText: CharSequence,
        entryStart: Int,
        entryEnd: Int,
        dotStart: Int,
        callEnd: Int,
    ): RedundantToStringInTemplateVerdict? {
        if (receiverType == WNodeType.SUPER_EXPRESSION) return null
        if (selectorType != WNodeType.CALL_EXPRESSION) return null
        if (!selectorText.contentEquals("toString()")) return null

        val edits = when {
            receiverType == WNodeType.THIS_EXPRESSION -> emptyList()
            receiverType == WNodeType.REFERENCE_EXPRESSION &&
                receiverText.isNotEmpty() &&
                receiverText[0] != '`' -> listOf(WEdit(entryStart, entryEnd, "$" + receiverText))
            else -> listOf(WEdit(dotStart, callEnd, ""))
        }
        return RedundantToStringInTemplateVerdict(edits)
    }
}
