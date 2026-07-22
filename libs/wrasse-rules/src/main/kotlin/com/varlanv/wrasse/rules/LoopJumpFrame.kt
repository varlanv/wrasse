package com.varlanv.wrasse.rules

/**
 * Counts `break`/`continue` occurrences within one loop's own body, excluding whatever falls
 * inside a nested loop (which gets its own independent frame instead — never merges upward).
 */
class LoopJumpFrame(val loopStart: Int, val loopEnd: Int) {
    var jumpCount: Int = 0
        private set

    fun recordJump() {
        jumpCount++
    }
}
