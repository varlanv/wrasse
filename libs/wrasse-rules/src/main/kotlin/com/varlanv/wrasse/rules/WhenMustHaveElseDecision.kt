package com.varlanv.wrasse.rules

/**
 * Verdict logic for a statement-position `when` missing an `else` branch, compiler-free so it is
 * unit-testable without a kotlinc dependency. Matches the upstream rule this derives from's own
 * exemptions: a `when` that is itself a `return`ed value, the last statement of a lambda body, a
 * branch value of another `when`, or the initializer/expression-body/assignment value of a
 * declaration is never required to carry an `else`; neither is one whose own entries are, purely by
 * shape, entirely enum-entry-like conditions (`Color.RED`, `RED`) with no `is`-pattern anywhere.
 */
object WhenMustHaveElseDecision {
    const val MESSAGE = "'when' used as a statement should have an 'else' branch"

    fun decide(
        isExempt: Boolean,
        hasElse: Boolean,
        isEnumOnly: Boolean,
        isLambdaLastStatement: Boolean,
    ): String? =
        if (!isExempt && !isLambdaLastStatement && !hasElse && !isEnumOnly) MESSAGE else null
}
