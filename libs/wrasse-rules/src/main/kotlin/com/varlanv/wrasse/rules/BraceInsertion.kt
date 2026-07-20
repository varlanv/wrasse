package com.varlanv.wrasse.rules

import com.varlanv.wrasse.lang.WEdit

/**
 * Shared core for the brace-insertion family ([IfElseBracingDecision], [WhenEntryBracingDecision]):
 * the physical-line indentation lookup and the two-edit brace-wrap construction, identical between
 * both rules down to the byte.
 *
 * [physicalLineIndentColumn] returns the indentation of the physical line containing [offset] — the
 * count of leading whitespace before that line's first non-whitespace character, not [offset]'s own
 * column. These coincide whenever the wrapped construct is itself the first token on its line (the
 * common case); they diverge when it sits mid-line (`fun foo() = if (...)`), where [offset]'s own
 * column would misalign the inserted braces to that arbitrary mid-line position instead of the
 * enclosing statement's real indentation depth (design.md §13, the wave-2 if-else-bracing backfill).
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
}
