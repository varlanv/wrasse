package com.varlanv.wrasse.rules

/**
 * Verdict logic for a top-level, public, non-override `main` function (0 or 1 parameters, the
 * only legal Kotlin entry-point shapes) whose body throws anywhere in its own subtree, compiler-
 * free so it is unit-testable without a kotlinc dependency. Report-only: whether an exception
 * should propagate out of an entry point instead is an authored decision.
 */
object ThrowingExceptionInMainDecision {
    const val MESSAGE = "The main function should not throw an exception"

    fun decide(name: String, isTopLevel: Boolean, isOverride: Boolean, hasNonPublicVisibility: Boolean, paramCount: Int, hasThrow: Boolean): String? {
        if (name != "main" || !isTopLevel || isOverride || hasNonPublicVisibility) return null
        if (paramCount > 1) return null
        if (!hasThrow) return null
        return MESSAGE
    }
}
