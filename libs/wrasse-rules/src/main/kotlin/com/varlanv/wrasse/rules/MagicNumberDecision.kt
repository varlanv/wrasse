package com.varlanv.wrasse.rules

/**
 * Verdict logic for a numeric literal that is not one of the small ignore-listed values and does
 * not sit in one of the exempt structural positions (a property's own initializer, a
 * parameter's own default value, a named call argument, a `hashCode` function's own body, a
 * dot-call's own receiver, or a function's own bare-constant return value), compiler-free so it
 * is unit-testable without a kotlinc dependency. Report-only: naming the constant is an authored
 * decision.
 */
object MagicNumberDecision {
    val IGNORED_VALUES = setOf(-1.0, 0.0, 1.0, 2.0)
    const val MESSAGE = "This expression contains a magic number; consider defining it as a well-named constant"

    fun decide(
        value: Double?,
        isInsideProperty: Boolean,
        isParameterDefaultValue: Boolean,
        isNamedArgument: Boolean,
        isHashCodeFunction: Boolean,
        isCallReceiver: Boolean,
        isBareFunctionReturnValue: Boolean,
    ): String? {
        if (value == null || value in IGNORED_VALUES) return null
        if (isInsideProperty || isParameterDefaultValue || isNamedArgument) return null
        if (isHashCodeFunction || isCallReceiver || isBareFunctionReturnValue) return null
        return MESSAGE
    }
}
