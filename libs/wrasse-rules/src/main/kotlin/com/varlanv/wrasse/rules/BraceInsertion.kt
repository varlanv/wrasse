package com.varlanv.wrasse.rules

import com.varlanv.wrasse.lang.IndentScope
import com.varlanv.wrasse.lang.WEdit

/**
 * Shared core for the brace-insertion family ([IfElseBracingDecision], [WhenEntryBracingDecision]):
 * the physical-line indentation lookup and the two brace-wrap edit constructions — one per
 * [com.varlanv.wrasse.model.WrasseRuleConfig.formatEnabled] state.
 *
 * [physicalLineIndentColumn] returns the indentation of the physical line containing [offset] — the
 * count of leading whitespace before that line's first non-whitespace character, not [offset]'s own
 * column. These coincide when the wrapped construct is the first token on its line; they diverge
 * when it sits mid-line, where [offset]'s own column would misalign the inserted braces. Used only
 * by [wrapEdits], the `formatEnabled == false` path.
 */
object BraceInsertion {

    fun physicalLineIndentColumn(sourceText: CharSequence, offset: Int): Int {
        var lineStart = offset - 1
        while (lineStart >= 0 && sourceText[lineStart] != '\n') lineStart--
        lineStart++
        var column = 0
        while (lineStart + column < sourceText.length &&
            (sourceText[lineStart + column] == ' ' || sourceText[lineStart + column] == '\t')
        ) {
            column++
        }
        return column
    }

    fun wrapEdits(
        leadingGapStart: Int,
        contentStart: Int,
        contentEnd: Int,
        trailingGapEnd: Int,
        hasFollowingBranch: Boolean,
        baseIndentColumn: Int,
        indentWidth: Int,
    ): List<WEdit> {
        val bodyIndent = " ".repeat(baseIndentColumn + indentWidth)
        val closeIndent = " ".repeat(baseIndentColumn)
        val trailingReplacement = if (hasFollowingBranch) "\n$closeIndent} " else "\n$closeIndent}"
        return listOf(
            WEdit(leadingGapStart, contentStart, " {\n$bodyIndent"),
            WEdit(contentEnd, trailingGapEnd, trailingReplacement),
        )
    }

    /**
     * The `formatEnabled == true` counterpart of [wrapEdits]: no physical column, no indent width,
     * no baked whitespace — just the brace characters and the two structural newlines the new
     * `BLOCK` needs. [IndentScope.OPEN]/[IndentScope.CLOSE] mark the pair so
     * `com.varlanv.wrasse.format.DocSplicer` can wrap [contentStart]..[contentEnd] in one ambient
     * indent level and let `Layout` derive every line's actual column from tree depth, exactly as
     * it would for a `BLOCK` the parser had produced directly (§5.3).
     */
    fun wrapEditsMinimal(
        leadingGapStart: Int,
        contentStart: Int,
        contentEnd: Int,
        trailingGapEnd: Int,
        hasFollowingBranch: Boolean,
    ): List<WEdit> {
        val trailingReplacement = if (hasFollowingBranch) "\n} " else "\n}"
        return listOf(
            WEdit(leadingGapStart, contentStart, " {\n", indentScope = IndentScope.OPEN),
            WEdit(contentEnd, trailingGapEnd, trailingReplacement, indentScope = IndentScope.CLOSE),
        )
    }
}
