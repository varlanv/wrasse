package com.varlanv.wrasse.rules

/**
 * Verdict logic for a range built from two literal integer bounds that can never iterate (e.g.
 * `2..1`, `1 downTo 2`, `2 until 1`), compiler-free so it is unit-testable without a kotlinc
 * dependency. Narrowed from the upstream rule this derives from: only a bare integer literal on
 * both sides is considered — a variable or computed bound cannot be evaluated without
 * resolution, so it is never a candidate, matching upstream's own identical restriction.
 */
object InvalidRangeDecision {
    const val MESSAGE = "This loop will never be executed due to its expression"

    fun decide(
        operatorText: CharSequence,
        lower: Int,
        upper: Int,
    ): String? {
        val isInvalid = when {
            operatorText.contentEquals("..") -> lower > upper
            operatorText.contentEquals("downTo") -> lower < upper
            operatorText.contentEquals("until") || operatorText.contentEquals("..<") -> lower >= upper
            else -> false
        }
        return if (isInvalid) MESSAGE else null
    }
}
