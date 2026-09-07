package com.varlanv.wrasse.rules

/**
 * A function name shorter than the min-length threshold ([DEFAULT_MIN_LENGTH] unless configured)
 * or longer than the max-length threshold ([DEFAULT_MAX_LENGTH] unless configured) is reported. An
 * override or an `operator` function is exempt either way: an override's name is fixed by its
 * supertype, and operator names (`plus`, `get`, ...) are fixed by the language, not a length
 * choice.
 */
object FunctionNameLengthDecision {
    const val DEFAULT_MIN_LENGTH = 3
    const val DEFAULT_MAX_LENGTH = 30

    fun decideMin(
        name: String,
        isOverride: Boolean,
        isOperator: Boolean,
        threshold: Int = DEFAULT_MIN_LENGTH,
    ): String? {
        if (isOverride || isOperator) return null
        if (name.length >= threshold) return null
        return "Function name '$name' is shorter than the minimum length of $threshold"
    }

    fun decideMax(
        name: String,
        isOverride: Boolean,
        isOperator: Boolean,
        threshold: Int = DEFAULT_MAX_LENGTH,
    ): String? {
        if (isOverride || isOperator) return null
        if (name.length <= threshold) return null
        return "Function name '$name' is longer than the maximum length of $threshold"
    }
}
