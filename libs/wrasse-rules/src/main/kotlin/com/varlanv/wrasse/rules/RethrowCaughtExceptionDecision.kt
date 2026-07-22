package com.varlanv.wrasse.rules

/**
 * Given, for one `try`'s own catch clauses in source order, whether each one's body is exactly
 * a bare rethrow of its own parameter, returns the indices to report: the maximal trailing run of
 * `true` entries counted back from the last catch clause — mirrors the upstream rule this derives
 * from exactly (a `takeLastWhile` over the same per-clause verdicts), including its own quirk that
 * a single non-rethrowing catch clause anywhere after a trivial one suppresses every earlier
 * trivial one's own report. Compiler-free so it is unit-testable without a kotlinc dependency.
 */
object RethrowCaughtExceptionDecision {
    const val MESSAGE = "Do not rethrow a caught exception of the same type."

    fun trailingViolationIndices(isTrivialRethrow: List<Boolean>): List<Int> {
        val result = mutableListOf<Int>()
        for (i in isTrivialRethrow.indices.reversed()) {
            if (!isTrivialRethrow[i]) break
            result.add(0, i)
        }
        return result
    }
}
