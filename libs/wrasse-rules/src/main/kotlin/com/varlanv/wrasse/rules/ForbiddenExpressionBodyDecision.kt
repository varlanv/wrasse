package com.varlanv.wrasse.rules

import com.varlanv.wrasse.lang.IndentScope
import com.varlanv.wrasse.lang.WEdit

/**
 * Pure edit construction for [ForbiddenExpressionBodyFunctionsRule]: turns `= body` into a block
 * body. [gapStart] is where the whitespace before `=` begins (the end of the return type or
 * `where` clause), [bodyStart]/[bodyEnd] span the body expression. The body is prefixed with
 * `return ` unless [returnsUnit] (declared `Unit`) or [bodyIsThrow]. With [formatEnabled] the
 * edits carry [IndentScope] marks and no indentation, exactly like [BraceInsertion.wrapEditsMinimal];
 * otherwise the indentation is baked from [baseIndentColumn]/[indentWidth].
 */
object ForbiddenExpressionBodyDecision {
    fun edits(
        gapStart: Int,
        bodyStart: Int,
        bodyEnd: Int,
        returnsUnit: Boolean,
        bodyIsThrow: Boolean,
        formatEnabled: Boolean,
        baseIndentColumn: Int,
        indentWidth: Int,
    ): List<WEdit> {
        val prefix = if (returnsUnit || bodyIsThrow) "" else "return "
        if (formatEnabled) {
            return listOf(
                WEdit(gapStart, bodyStart, " {\n$prefix", indentScope = IndentScope.OPEN),
                WEdit(bodyEnd, bodyEnd, "\n}", indentScope = IndentScope.CLOSE),
            )
        }
        val bodyIndent = " ".repeat(baseIndentColumn + indentWidth)
        val closeIndent = " ".repeat(baseIndentColumn)
        return listOf(WEdit(gapStart, bodyStart, " {\n$bodyIndent$prefix"), WEdit(bodyEnd, bodyEnd, "\n$closeIndent}"))
    }

    fun isUnitTypeText(text: CharSequence): Boolean {
        val trimmed = text.trim()
        return trimmed == "Unit" || trimmed == "kotlin.Unit"
    }

    const val MESSAGE = "Function body must be a block, not an expression"
}
