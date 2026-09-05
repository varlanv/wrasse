package com.varlanv.wrasse.rules

/**
 * A function name shorter than [MIN_LENGTH] or longer than [MAX_LENGTH] is reported. An override
 * or an `operator` function is exempt either way: an override's name is fixed by its supertype,
 * and operator names (`plus`, `get`, ...) are fixed by the language, not a length choice.
 */
object FunctionNameLengthDecision {
    const val MIN_LENGTH = 3
    const val MAX_LENGTH = 30

    fun decideMin(
        name: String,
        isOverride: Boolean,
        isOperator: Boolean,
    ): String? {
        if (isOverride || isOperator) return null
        if (name.length >= MIN_LENGTH) return null
        return "Function name '$name' is shorter than the minimum length of $MIN_LENGTH"
    }

    fun decideMax(
        name: String,
        isOverride: Boolean,
        isOperator: Boolean,
    ): String? {
        if (isOverride || isOperator) return null
        if (name.length <= MAX_LENGTH) return null
        return "Function name '$name' is longer than the maximum length of $MAX_LENGTH"
    }
}
