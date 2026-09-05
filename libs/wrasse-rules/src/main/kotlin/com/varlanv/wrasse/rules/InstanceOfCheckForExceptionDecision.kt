package com.varlanv.wrasse.rules

/**
 * Verdict logic for an `is`/unsafe-`as` check against a catch parameter inside its own catch
 * body, compiler-free so it is unit-testable without a kotlinc dependency. `checkedTypeText` is
 * the checked type's own trimmed text; a type textually equal to or ending in
 * `CancellationException` is exempt outright — a coroutines-idiom carve-out the upstream rule
 * this derives from applies via resolution, replicated here as a textual name check instead.
 */
object InstanceOfCheckForExceptionDecision {
    const val MESSAGE = "Instead of catching for a general exception type and checking for a specific exception type, " +
        "use multiple catch blocks."

    fun decide(checkedTypeText: String): String? =
        if (checkedTypeText.endsWith("CancellationException")) null else MESSAGE
}
