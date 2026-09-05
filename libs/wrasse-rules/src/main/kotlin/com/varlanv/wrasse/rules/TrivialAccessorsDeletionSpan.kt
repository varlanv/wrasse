package com.varlanv.wrasse.rules

import com.varlanv.wrasse.lang.WEdit

/**
 * Deletion-span policy for a trivial property accessor, compiler-free so it is unit-testable
 * without a kotlinc dependency.
 *
 * Walks backward from the accessor's own start over whitespace characters only (never past a
 * comment or any other token), so a same-line accessor collapses its leading space and a
 * standalone-line accessor collapses the preceding newline too, leaving any comment before it
 * completely untouched.
 */
object TrivialAccessorsDeletionSpan {
    fun compute(
        sourceText: CharSequence,
        accessorStart: Int,
        accessorEnd: Int,
    ): WEdit {
        var start = accessorStart
        while (start > 0 && sourceText[start - 1].isWhitespace()) {
            start--
        }
        return WEdit(start, accessorEnd, "")
    }
}
