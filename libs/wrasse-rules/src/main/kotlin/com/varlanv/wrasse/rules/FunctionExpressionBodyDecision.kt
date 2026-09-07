package com.varlanv.wrasse.rules

import com.varlanv.wrasse.model.WNodeType

/**
 * Verdict logic for a function's `BLOCK` body whose only non-brace, non-whitespace content is one
 * `RETURN` or `THROW`, compiler-free so it is unit-testable without a kotlinc dependency. Any
 * comment present disqualifies the block (matches the upstream rule this derives from exactly,
 * whose own child filter never drops comments either). A `RETURN` case is additionally dropped
 * when the block contains more than one `return` keyword anywhere inside it (a nested `return`
 * inside the returned expression itself would change meaning if hoisted to `=`).
 *
 * [expressionText] turns the sole statement's own source text into the text that follows `= ` in
 * the expression-body form, or `null` when that substitution would not be safe: a `THROW` is used
 * verbatim; a `RETURN` is safe only when it carries an actual expression that isn't itself a
 * labeled return (`return@label ...`), since a bare or labeled `return` cannot be hoisted to `=`.
 */
object FunctionExpressionBodyDecision {
    const val MESSAGE = "Function body should be replaced with body expression"
    private const val RETURN_KEYWORD = "return"

    fun decide(soleChildType: WNodeType?, returnKeywordCount: Int): String? = when (soleChildType) {
        WNodeType.RETURN -> if (returnKeywordCount <= 1) MESSAGE else null
        WNodeType.THROW -> MESSAGE
        else -> null
    }

    fun expressionText(statementType: WNodeType, statementText: CharSequence): String? = when (statementType) {
        WNodeType.THROW -> statementText.toString()
        WNodeType.RETURN -> returnExpressionText(statementText.toString())
        else -> null
    }

    private fun returnExpressionText(text: String): String? {
        if (!text.startsWith(RETURN_KEYWORD)) return null
        val rest = text.substring(RETURN_KEYWORD.length)
        if (rest.isEmpty() || rest.startsWith("@")) return null
        return rest.trimStart(' ', '\t')
    }
}
