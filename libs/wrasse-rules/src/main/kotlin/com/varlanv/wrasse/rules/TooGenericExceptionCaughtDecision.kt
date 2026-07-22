package com.varlanv.wrasse.rules

/**
 * Verdict logic for a `catch` clause whose declared type is too generic, compiler-free so it is
 * unit-testable without a kotlinc dependency.
 */
object TooGenericExceptionCaughtDecision {
    const val MESSAGE = "The caught exception is too generic. Prefer catching specific exceptions to the case that is currently handled."

    private val GENERIC_TYPES = setOf(
        "ArrayIndexOutOfBoundsException",
        "Error",
        "Exception",
        "IllegalMonitorStateException",
        "IndexOutOfBoundsException",
        "NullPointerException",
        "RuntimeException",
        "Throwable",
    )

    fun decide(typeText: String, catchParameterName: String): String? {
        if (typeText !in GENERIC_TYPES) return null
        if (AllowedExceptionName.isAllowed(catchParameterName)) return null
        return MESSAGE
    }
}
