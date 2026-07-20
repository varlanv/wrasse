package com.varlanv.wrasse.rules

import com.varlanv.wrasse.lang.WEdit

/**
 * One bare (unbraced) `THEN`/`ELSE` branch already classified by the caller as a genuine
 * candidate for bracing — not empty, not already a `BLOCK`, and (for `ELSE`) not a bare `if`
 * (an `else if` continuation, handled when that nested `IF` is visited on its own).
 *
 * [leadingGapStart] is the offset right after the branch's opening delimiter (the condition's
 * `RPAR` for `THEN`, the `else` keyword for `ELSE`); [contentStart]/[contentEnd] is the branch's
 * own bare-statement span. [trailingGapEnd] is the offset where the next sibling begins
 * ([hasFollowingBranch] `true`, only for a `THEN` immediately followed by an `else`) or equal to
 * [contentEnd] (a plain zero-width insertion point) otherwise.
 */
class IfElseBracingCandidate(
    val leadingGapStart: Int,
    val contentStart: Int,
    val contentEnd: Int,
    val trailingGapEnd: Int,
    val hasFollowingBranch: Boolean,
    val hasAdjacentComment: Boolean,
)

/** [reportStart]/[reportEnd] locate the diagnostic; [edits] is empty for a report-only bail. */
class IfElseBracingVerdict(
    val reportStart: Int,
    val reportEnd: Int,
    val edits: List<WEdit>,
)

/**
 * Pure verdict logic for `if-else-bracing`, compiler-free and unit-testable without kotlinc.
 *
 * [chainSpansMultipleLines] gates the whole rule: no branch is touched unless the if/else-if/else
 * chain, as physically written, spans more than one source line.
 *
 * [decideBranch] returns a report-only bail (empty [IfElseBracingVerdict.edits]) whenever
 * [IfElseBracingCandidate.hasAdjacentComment] is set or the branch's own bare-statement text
 * already spans multiple lines; the bail reports a zero-width point at
 * [IfElseBracingCandidate.contentStart] rather than the branch's full span, so a wider span never
 * spuriously overlaps an independent nested-`if` fix starting at that same offset. Otherwise, both
 * edits are computed purely from [baseIndentColumn] and [indentWidth] via [BraceInsertion] — never
 * copied from whatever whitespace happened to already be there.
 */
object IfElseBracingDecision {

    fun chainSpansMultipleLines(sourceText: CharSequence, chainStart: Int, chainEnd: Int): Boolean =
        sourceText.subSequence(chainStart, chainEnd).contains('\n')

    fun decideBranch(
        sourceText: CharSequence,
        candidate: IfElseBracingCandidate,
        baseIndentColumn: Int,
        indentWidth: Int,
    ): IfElseBracingVerdict {
        val hasEmbeddedNewline = sourceText.subSequence(candidate.contentStart, candidate.contentEnd).contains('\n')
        if (candidate.hasAdjacentComment || hasEmbeddedNewline) {
            return IfElseBracingVerdict(candidate.contentStart, candidate.contentStart, emptyList())
        }

        return IfElseBracingVerdict(
            reportStart = candidate.contentStart,
            reportEnd = candidate.contentEnd,
            edits = BraceInsertion.wrapEdits(
                leadingGapStart = candidate.leadingGapStart,
                contentStart = candidate.contentStart,
                contentEnd = candidate.contentEnd,
                trailingGapEnd = candidate.trailingGapEnd,
                hasFollowingBranch = candidate.hasFollowingBranch,
                baseIndentColumn = baseIndentColumn,
                indentWidth = indentWidth,
            ),
        )
    }
}
