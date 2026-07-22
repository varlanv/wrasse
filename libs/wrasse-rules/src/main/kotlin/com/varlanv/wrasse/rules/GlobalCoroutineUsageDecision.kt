package com.varlanv.wrasse.rules

/**
 * Verdict logic for `GlobalScope.launch`/`GlobalScope.async`, compiler-free so it is
 * unit-testable without a kotlinc dependency. Report-only: replacing `GlobalScope` with an
 * application-scoped `CoroutineScope` is an authored structural decision.
 */
object GlobalCoroutineUsageDecision {
    const val MESSAGE = "This use of GlobalScope should be replaced by a CoroutineScope or coroutineScope"

    private val FLAGGED_CALLEES = setOf("launch", "async")

    fun decide(receiverText: CharSequence, calleeText: CharSequence?): String? {
        if (!receiverText.contentEquals("GlobalScope")) return null
        if (calleeText == null || calleeText.toString() !in FLAGGED_CALLEES) return null
        return MESSAGE
    }
}
