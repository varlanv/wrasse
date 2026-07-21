package com.varlanv.wrasse.rules

/**
 * Verdict logic for a lambda declaring more than one parameter where one of them is explicitly
 * named `it`, compiler-free so it is unit-testable without a kotlinc dependency.
 *
 * Report-only: renaming the parameter to something meaningful is an authored decision this rule
 * cannot make on the user's behalf.
 */
object ExplicitItLambdaMultipleParametersDecision {
    const val MESSAGE = "'it' should not be used as a name for a lambda parameter when the lambda declares more than one parameter"

    fun decide(parameterCount: Int, hasItParameter: Boolean): String? {
        if (parameterCount <= 1 || !hasItParameter) return null
        return MESSAGE
    }
}
