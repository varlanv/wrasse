package com.varlanv.wrasse.rules

/**
 * Verdict logic for a function that is never expected to throw, compiler-free so it is
 * unit-testable without a kotlinc dependency.
 */
object ExceptionRaisedInUnexpectedLocationDecision {
    const val MESSAGE = "This method is not expected to throw exceptions. This can cause weird behavior."

    private val UNEXPECTED_THROWING_METHOD_NAMES = setOf("equals", "finalize", "hashCode", "toString")

    fun decide(functionName: String, hasThrow: Boolean): String? =
        if (functionName in UNEXPECTED_THROWING_METHOD_NAMES && hasThrow) MESSAGE else null
}
