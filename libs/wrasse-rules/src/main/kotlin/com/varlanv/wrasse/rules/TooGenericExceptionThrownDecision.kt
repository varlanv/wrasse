package com.varlanv.wrasse.rules

/**
 * Verdict logic for a `throw` expression whose constructed type is too generic, compiler-free so
 * it is unit-testable without a kotlinc dependency. `calleeName` is the thrown expression's own
 * callee simple name (already stripped of any package/receiver qualification by the caller).
 */
object TooGenericExceptionThrownDecision {
    private val GENERIC_TYPES = setOf("Error", "Exception", "RuntimeException", "Throwable")

    fun decide(calleeName: String): String? {
        if (calleeName !in GENERIC_TYPES) return null
        return "$calleeName is a too generic Exception. Prefer throwing specific exceptions that indicate a specific error case."
    }
}
