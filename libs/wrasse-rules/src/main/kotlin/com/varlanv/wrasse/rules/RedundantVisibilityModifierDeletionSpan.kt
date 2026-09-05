package com.varlanv.wrasse.rules

import com.varlanv.wrasse.lang.WEdit

/**
 * Deletion-span policy for a redundant explicit `public` visibility modifier, compiler-free so it
 * is unit-testable without a kotlinc dependency.
 *
 * The `public` keyword's own span (`publicStart` to `publicEnd`) plus whatever whitespace directly
 * follows it collapses to nothing in one contiguous deletion — `public class Foo` becomes
 * `class Foo`, `public open class Foo` becomes `open class Foo`, never touching anything before
 * `publicStart` (an annotation, indentation, an unrelated leading comment) or, past the trailing
 * whitespace, whatever real token or comment comes next.
 *
 * Returns `null` (report-only, no edit) when [hasCommentInList] is true (a comment already found
 * elsewhere in the modifier list by the caller) or when a comment starts immediately after the
 * trailing whitespace this would otherwise delete — matching the project's uniform comment-bail
 * precedent (`no-unit-return`, `modifier-order`) even though, unlike those rules, no upstream-native
 * corruption was found here: the forward whitespace scan always stops at the comment's own start,
 * so the comment itself is never swallowed either way.
 */
object RedundantVisibilityModifierDeletionSpan {
    fun compute(
        sourceText: CharSequence,
        publicStart: Int,
        publicEnd: Int,
        hasCommentInList: Boolean,
    ): WEdit? {
        var end = publicEnd
        while (end < sourceText.length && sourceText[end].isWhitespace()) {
            end++
        }
        val trailingComment = end +
        1 <
            sourceText.length &&
            sourceText[end] ==
            '/' &&
            (sourceText[end + 1] == '/' || sourceText[end + 1] == '*')

        if (hasCommentInList || trailingComment) return null

        return WEdit(publicStart, end, "")
    }
}
