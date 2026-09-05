package com.varlanv.wrasse.rules

import com.varlanv.wrasse.lang.WEdit

/**
 * Deletion-span policy for an unused import directive, compiler-free and unit-testable without
 * kotlinc.
 *
 * If the directive is alone on its line(s) ([ImportLineSpan.isAloneOnLine]), the whole line is
 * removed including its terminating `\n` (or to end-of-text on a final line with none) — this also
 * covers a semicolon-terminated directive, since the Kotlin grammar binds the semicolon into the
 * directive's own node span.
 *
 * Otherwise (a semicolon-separated sibling import or trailing comment shares the line), returns
 * `null`: no edit, report-only. Deleting only this directive's own span would leave a dangling
 * separator semicolon on the remaining sibling that `no-semicolons` would then flag — a cross-rule
 * effect this function deliberately bails on rather than guessing.
 */
object ImportRemovalSpan {
    fun compute(
        sourceText: CharSequence,
        startOffset: Int,
        endOffset: Int,
    ): WEdit? {
        if (!ImportLineSpan.isAloneOnLine(sourceText, startOffset, endOffset)) return null
        val lineStart = ImportLineSpan.lineStartBefore(sourceText, startOffset)
        val newlineAtOrAfterEnd = ImportLineSpan.indexOfNewlineFrom(sourceText, endOffset)
        val deleteEnd = if (newlineAtOrAfterEnd >= 0) newlineAtOrAfterEnd + 1 else sourceText.length
        return WEdit(lineStart, deleteEnd, "")
    }
}
