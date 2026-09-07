package com.varlanv.wrasse.rules

/**
 * Verdict logic for a string literal carrying more than the threshold ([DEFAULT_THRESHOLD] unless
 * configured) escape sequences (`\t`, `\"`, `\\`, `\n`), compiler-free so it is unit-testable
 * without a kotlinc dependency. Report-only: converting to a raw string changes every escape
 * sequence's own spelling, which this rule leaves to the author.
 */
object StringShouldBeRawStringDecision {
    const val DEFAULT_THRESHOLD = 2
    const val MESSAGE = "String with escape characters should be converted to a raw string"

    fun decide(
        escapeCount: Int,
        threshold: Int = DEFAULT_THRESHOLD,
    ): String? =
        if (escapeCount > threshold) MESSAGE else null
}
