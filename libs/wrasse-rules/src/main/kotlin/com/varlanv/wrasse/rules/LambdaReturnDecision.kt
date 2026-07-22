package com.varlanv.wrasse.rules

/**
 * Verdict logic for a labeled `return@label` carrying a value as a lambda's own last statement,
 * where `label` names that same lambda rather than some outer scope, compiler-free so it is
 * unit-testable without a kotlinc dependency.
 */
object LambdaReturnDecision {
    const val MESSAGE = "Unnecessary labeled return as the last statement in a lambda"

    fun decide(labelMatchesOwnLambda: Boolean): String? = if (labelMatchesOwnLambda) MESSAGE else null
}
