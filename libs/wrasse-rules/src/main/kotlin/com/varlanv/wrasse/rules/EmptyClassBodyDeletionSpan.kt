package com.varlanv.wrasse.rules

import com.varlanv.wrasse.lang.WEdit

/**
 * Deletion-span policy for an empty class/interface/object body, compiler-free so it is
 * unit-testable without a kotlinc dependency.
 *
 * The body's own `{`..`}` span (`bodyStart` to `bodyEnd`) is always deleted; this additionally
 * walks backward from `bodyStart` over whitespace characters only (never past a comment or any
 * other token) so `class Foo {}` collapses to `class Foo` with no trailing space, and
 * `class Foo\n{\n}` collapses to `class Foo` rather than leaving a dangling blank line.
 */
object EmptyClassBodyDeletionSpan {
    fun compute(
        sourceText: CharSequence,
        bodyStart: Int,
        bodyEnd: Int,
    ): WEdit {
        var start = bodyStart
        while (start > 0 && sourceText[start - 1].isWhitespace()) {
            start--
        }
        return WEdit(start, bodyEnd, "")
    }
}
