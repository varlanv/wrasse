package com.varlanv.wrasse.rules

/**
 * Verdict logic for a call to `equals(null)`, compiler-free so it is unit-testable without a
 * kotlinc dependency. Report-only: rewriting to `==` changes the call's own receiver expression
 * shape, which is a judgment call this rule leaves to the author.
 */
object EqualsNullCallDecision {
    const val MESSAGE = "Calling equals() with null as the argument; use '==' to compare with null instead"

    fun decide(calleeText: CharSequence, singleArgumentText: CharSequence?): String? {
        if (!calleeText.contentEquals("equals")) return null
        if (singleArgumentText == null || !singleArgumentText.contentEquals("null")) return null
        return MESSAGE
    }
}
