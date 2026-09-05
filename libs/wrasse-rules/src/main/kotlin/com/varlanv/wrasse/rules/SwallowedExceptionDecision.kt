package com.varlanv.wrasse.rules

/**
 * Verdict logic for a caught exception that is never referenced anywhere in its own catch body,
 * compiler-free so it is unit-testable without a kotlinc dependency. Narrowed from the upstream
 * rule this derives from: only the "never referenced at all" case is checked — its sibling case
 * ("referenced only via a derived field, e.g. `e.message`, while rethrowing a new exception that
 * never carries `e` itself as a cause") is dropped, strictly fewer reports, never a false
 * positive relative to upstream.
 */
object SwallowedExceptionDecision {
    const val MESSAGE = "The caught exception is swallowed. The original exception could be lost."

    private val IGNORED_TYPES = listOf(
        "InterruptedException",
        "MalformedURLException",
        "NumberFormatException",
        "ParseException",
    )

    fun decide(
        typeText: String,
        catchParameterName: String,
        isReferenced: Boolean,
    ): String? {
        if (IGNORED_TYPES.any { typeText.contains(it, ignoreCase = true) }) return null
        if (AllowedExceptionName.isAllowed(catchParameterName)) return null
        return if (isReferenced) null else MESSAGE
    }

    fun isUsageText(
        text: String,
        catchParameterName: String,
    ): Boolean = text == catchParameterName || text in IGNORED_TYPES
}
