package com.varlanv.wrasse.rules

/** More than one `break`/`continue` in a single loop is hard to follow (sole upstream default: `maxJumpCount = 1`). */
object LoopWithTooManyJumpStatementsDecision {
    private const val MAX_JUMP_COUNT = 1

    fun decide(jumpCount: Int): String? =
        if (jumpCount > MAX_JUMP_COUNT) {
            "The loop contains more than one break or continue statement (found $jumpCount); the code should be " +
                "refactored to increase readability"
        } else {
            null
        }
}
