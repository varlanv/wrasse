package com.varlanv.wrasse.rules

/**
 * Verdict logic for a private class never named anywhere else in the file, compiler-free so it
 * is unit-testable without a kotlinc dependency. Report-only: deleting dead code is a decision
 * this rule leaves to the author.
 */
object UnusedPrivateClassDecision {
    fun decide(isPrivate: Boolean, isUsed: Boolean, className: String): String? {
        if (!isPrivate || isUsed) return null
        return "Private class '$className' is unused"
    }
}
