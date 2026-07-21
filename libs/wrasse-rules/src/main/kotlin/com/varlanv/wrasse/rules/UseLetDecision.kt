package com.varlanv.wrasse.rules

/**
 * Verdict logic for an `if (x != null) { ... } else null` / `if (x == null) null else { ... }`
 * shape, compiler-free so it is unit-testable without a kotlinc dependency. `nullBranchText` is
 * that branch's own single-statement text with any wrapping `{ }` block already stripped by the
 * caller (see [UseLetRule]). Report-only: rewriting to `?.let { }` restructures the non-null
 * branch's own body into a lambda, which this rule leaves to the author.
 */
object UseLetDecision {
    const val MESSAGE = "Use '?.let { }' instead of this if/else null check"

    fun decide(nullBranchText: String): String? = if (nullBranchText == "null") MESSAGE else null
}
