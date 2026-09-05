package com.varlanv.wrasse.rules

/**
 * Line-boundary scanning shared by every import fix that must know whether a directive is the
 * only non-whitespace content on the line(s) it spans — [ImportRemovalSpan] (whole-line deletion
 * of an unused import) and [WildcardExpansionDecision] (the same-line bail for star expansion).
 * Compiler-free, unit-testable without kotlinc.
 */
object ImportLineSpan {
    fun isAloneOnLine(
        sourceText: CharSequence,
        startOffset: Int,
        endOffset: Int,
    ): Boolean {
        val lineStart = lineStartBefore(sourceText, startOffset)
        val lineEnd = lineEndAfter(sourceText, endOffset)
        return isBlank(sourceText, lineStart, startOffset) && isBlank(sourceText, endOffset, lineEnd)
    }

    fun lineStartBefore(sourceText: CharSequence, offset: Int): Int {
        var i = offset - 1
        while (i >= 0 && sourceText[i] != '\n') i--
        return i + 1
    }

    fun lineEndAfter(sourceText: CharSequence, offset: Int): Int {
        val newlineAtOrAfter = indexOfNewlineFrom(sourceText, offset)
        return if (newlineAtOrAfter >= 0) newlineAtOrAfter else sourceText.length
    }

    fun indexOfNewlineFrom(sourceText: CharSequence, offset: Int): Int {
        var i = offset
        while (i < sourceText.length) {
            if (sourceText[i] == '\n') return i
            i++
        }
        return -1
    }

    private fun isBlank(
        sourceText: CharSequence,
        start: Int,
        end: Int,
    ): Boolean {
        for (i in start until end) {
            if (!sourceText[i].isWhitespace()) return false
        }
        return true
    }
}
