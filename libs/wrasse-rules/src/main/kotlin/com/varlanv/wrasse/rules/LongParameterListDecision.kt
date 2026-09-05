package com.varlanv.wrasse.rules

/** What kind of declaration a `VALUE_PARAMETER_LIST` belongs to, for [LongParameterListDecision]. */
enum class ParameterListOwner {
    FUNCTION,
    PRIMARY_CONSTRUCTOR,
    SECONDARY_CONSTRUCTOR,
}

/**
 * A function or constructor with more parameters than allowed is reported (never fixed — trimming
 * a parameter list is a call-site-breaking API change): [MAX_FUNCTION_PARAMETERS] for a function,
 * [MAX_CONSTRUCTOR_PARAMETERS] for a constructor.
 *
 * An override is exempt for the function case: its parameter list is fixed by the supertype, not
 * a local decision (same reasoning as `function-naming`'s override exemption). A data class's
 * primary or secondary constructor is exempt entirely — a data class's parameters are its whole
 * public shape by design, not a smell.
 */
object LongParameterListDecision {
    const val MAX_FUNCTION_PARAMETERS = 5
    const val MAX_CONSTRUCTOR_PARAMETERS = 6

    fun decide(
        owner: ParameterListOwner,
        parameterCount: Int,
        isOverride: Boolean,
        isDataClassConstructor: Boolean,
    ): String? {
        if (owner == ParameterListOwner.FUNCTION && isOverride) return null
        if (owner != ParameterListOwner.FUNCTION && isDataClassConstructor) return null
        val max = if (owner == ParameterListOwner.FUNCTION) MAX_FUNCTION_PARAMETERS else MAX_CONSTRUCTOR_PARAMETERS
        if (parameterCount <= max) return null
        val kind = if (owner == ParameterListOwner.FUNCTION) "function" else "constructor"
        return "The $kind has $parameterCount parameters; the maximum allowed is $max"
    }
}
