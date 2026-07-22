package com.varlanv.wrasse.rules

/**
 * Verdict logic for a nested `if` collapsible into its own enclosing `if`'s own condition,
 * compiler-free so it is unit-testable without a kotlinc dependency. A nested `if` merges into its
 * own parent only when neither carries an `else` branch at all — merging past either would change
 * which branch executes for some input, not just its shape.
 */
object CollapseIfDecision {
    const val MESSAGE = "Nested if-statement could be collapsed into its own enclosing condition"

    fun decide(outerHasElse: Boolean, innerHasElse: Boolean): String? = if (!outerHasElse && !innerHasElse) MESSAGE else null
}
