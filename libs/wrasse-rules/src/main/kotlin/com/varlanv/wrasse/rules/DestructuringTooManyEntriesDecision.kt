package com.varlanv.wrasse.rules

/**
 * A destructuring declaration (`val (a, b, c, ...) = x`) with more than [MAX_ENTRIES] entries is
 * reported.
 */
object DestructuringTooManyEntriesDecision {
    const val MAX_ENTRIES = 3

    fun decide(entryCount: Int): String? {
        if (entryCount <= MAX_ENTRIES) return null
        return "Destructuring declaration has $entryCount entries; the maximum allowed is $MAX_ENTRIES"
    }
}
