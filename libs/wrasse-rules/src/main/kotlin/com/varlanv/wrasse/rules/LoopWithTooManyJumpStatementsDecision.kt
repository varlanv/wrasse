package com.varlanv.wrasse.rules

/** A loop with more `break`/`continue` statements than the threshold ([DEFAULT_THRESHOLD] unless configured) is reported. */
object LoopWithTooManyJumpStatementsDecision {
    const val DEFAULT_THRESHOLD = 1

    fun decide(
        jumpCount: Int,
        threshold: Int = DEFAULT_THRESHOLD,
    ): String? = if (jumpCount > threshold) {
        "The loop contains $jumpCount break or continue ${statementNoun(jumpCount)}; the maximum allowed is $threshold"
    } else {
        null
    }
}
