package com.varlanv.wrasse.rules

/**
 * A destructuring declaration (`val (a, b, c, ...) = x`) with more than the threshold
 * ([DEFAULT_THRESHOLD] unless configured) entries is reported.
 */
object DestructuringTooManyEntriesDecision {
    const val DEFAULT_THRESHOLD = 3

    fun decide(
        entryCount: Int,
        threshold: Int = DEFAULT_THRESHOLD,
    ): String? {
        if (entryCount <= threshold) return null
        return "Destructuring declaration has $entryCount entries; the maximum allowed is $threshold"
    }
}
