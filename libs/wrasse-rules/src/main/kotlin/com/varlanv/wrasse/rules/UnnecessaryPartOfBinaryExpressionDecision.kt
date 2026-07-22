package com.varlanv.wrasse.rules

/**
 * Verdict logic for a chain of `&&`/`||` operands (whitespace-insensitive text comparison,
 * matching the upstream rule this derives from exactly) containing a duplicate, compiler-free so
 * it is unit-testable without a kotlinc dependency.
 */
object UnnecessaryPartOfBinaryExpressionDecision {
    const val MESSAGE = "This binary expression repeats one of its own operands unnecessarily"

    fun decide(operandTexts: List<String>): String? {
        if (operandTexts.size == operandTexts.distinct().size) return null
        return MESSAGE
    }
}
