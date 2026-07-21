package com.varlanv.wrasse.rules

/**
 * Verdict logic for a string literal carrying more than [MAX_ESCAPED_CHARACTERS] escape sequences
 * (`\t`, `\"`, `\\`, `\n`), compiler-free so it is unit-testable without a kotlinc dependency.
 * Report-only: converting to a raw string changes every escape sequence's own spelling, which
 * this rule leaves to the author.
 */
object StringShouldBeRawStringDecision {
    const val MAX_ESCAPED_CHARACTERS = 2
    const val MESSAGE = "String with escape characters should be converted to a raw string"

    fun decide(escapeCount: Int): String? = if (escapeCount > MAX_ESCAPED_CHARACTERS) MESSAGE else null
}
