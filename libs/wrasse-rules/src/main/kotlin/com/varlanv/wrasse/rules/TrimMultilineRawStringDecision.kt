package com.varlanv.wrasse.rules

/**
 * Verdict logic for a multiline raw string with no `.trimIndent()`/`.trimMargin()` call chained
 * onto it, compiler-free so it is unit-testable without a kotlinc dependency. Report-only:
 * appending a trim call changes the string's own runtime value (it strips leading whitespace),
 * so this is never autofixed.
 */
object TrimMultilineRawStringDecision {
    const val MESSAGE = "Multiline raw strings should be followed by trimIndent() or trimMargin()"

    fun decide(
        isRawWithLineBreak: Boolean,
        isTrimmed: Boolean,
        isExpectedAsConstant: Boolean,
    ): String? {
        if (!isRawWithLineBreak || isTrimmed || isExpectedAsConstant) return null
        return MESSAGE
    }
}
