package com.varlanv.wrasse.rules

import com.varlanv.wrasse.lang.WEdit

/**
 * One bare (unbraced) `WHEN_ENTRY` body already classified by the caller as a genuine candidate
 * for bracing — not empty, not already a `BLOCK`.
 *
 * [leadingGapStart] is the offset right after the entry's `ARROW`; [contentStart]/[contentEnd] is
 * the entry's own bare-expression span.
 */
class WhenEntryBracingCandidate(
    val leadingGapStart: Int,
    val contentStart: Int,
    val contentEnd: Int,
    val hasAdjacentComment: Boolean,
)

/** [reportStart]/[reportEnd] locate the diagnostic; [edits] is empty for a report-only bail. */
class WhenEntryBracingVerdict(
    val reportStart: Int,
    val reportEnd: Int,
    val edits: List<WEdit>,
)

/**
 * Pure verdict logic for `when-entry-bracing`, compiler-free and unit-testable without kotlinc.
 *
 * [shouldBraceEntries] gates the whole rule for one `when` expression: bracing only fires when
 * some entry already has a non-empty block body *and* some entry's body doesn't start on the same
 * line as its own `ARROW`. Either condition alone, or neither, leaves every entry untouched.
 *
 * [decideEntry] returns a report-only bail (empty [WhenEntryBracingVerdict.edits]) whenever
 * [WhenEntryBracingCandidate.hasAdjacentComment] is set or the entry's own bare-expression text
 * already spans multiple lines. Otherwise, both edits are computed purely from
 * [baseIndentColumn] and [indentWidth] via the shared [BraceInsertion] helper — never copied from
 * whatever whitespace happened to already be there.
 */
object WhenEntryBracingDecision {

    fun shouldBraceEntries(anyEntryHasBlockBody: Boolean, anyEntryHasMultilineBody: Boolean): Boolean =
        anyEntryHasBlockBody && anyEntryHasMultilineBody

    fun decideEntry(
        sourceText: CharSequence,
        candidate: WhenEntryBracingCandidate,
        baseIndentColumn: Int,
        indentWidth: Int,
    ): WhenEntryBracingVerdict {
        val hasEmbeddedNewline = sourceText.subSequence(candidate.contentStart, candidate.contentEnd).contains('\n')
        if (candidate.hasAdjacentComment || hasEmbeddedNewline) {
            return WhenEntryBracingVerdict(candidate.contentStart, candidate.contentStart, emptyList())
        }

        return WhenEntryBracingVerdict(
            reportStart = candidate.contentStart,
            reportEnd = candidate.contentEnd,
            edits = BraceInsertion.wrapEdits(
                leadingGapStart = candidate.leadingGapStart,
                contentStart = candidate.contentStart,
                contentEnd = candidate.contentEnd,
                trailingGapEnd = candidate.contentEnd,
                hasFollowingBranch = false,
                baseIndentColumn = baseIndentColumn,
                indentWidth = indentWidth,
            ),
        )
    }
}
