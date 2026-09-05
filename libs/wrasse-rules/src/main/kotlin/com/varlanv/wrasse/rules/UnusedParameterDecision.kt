package com.varlanv.wrasse.rules

/**
 * Verdict logic for a single function parameter never referenced in its function's own body,
 * compiler-free so it is unit-testable without a kotlinc dependency. Report-only: deleting an
 * unused parameter changes the function's own signature (a public API break for a non-private
 * function), a decision this rule leaves to the author.
 */
object UnusedParameterDecision {
    private val ALLOWED_NAMES = Regex("ignored|expected")

    fun decide(
        functionExempt: Boolean,
        parameterName: String,
        wasUsed: Boolean,
    ): String? {
        if (functionExempt || wasUsed) return null
        if (ALLOWED_NAMES.matches(parameterName)) return null
        return "Function parameter '$parameterName' is unused"
    }
}
