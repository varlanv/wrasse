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
 * Unaffected by [decideEntry]'s `formatEnabled` — it decides whether this `when` is in scope at
 * all, not how an in-scope entry gets edited.
 *
 * [decideEntry] returns a report-only bail (empty [WhenEntryBracingVerdict.edits]) whenever
 * [WhenEntryBracingCandidate.hasAdjacentComment] is set, or the entry's own bare-expression text
 * already spans multiple lines and [formatEnabled] is `false` — with the printer active,
 * re-indenting that interior is its job by construction (§5.3), so the bail no longer applies to
 * that case once [formatEnabled] is `true`. Otherwise, edits come from
 * [BraceInsertion.wrapEdits] (computed from [baseIndentColumn] and [indentWidth]) when
 * [formatEnabled] is `false`, or [BraceInsertion.wrapEditsMinimal] (no indentation computed at
 * all, left to the printer) when it's `true`.
 */
object WhenEntryBracingDecision {
    fun shouldBraceEntries(anyEntryHasBlockBody: Boolean, anyEntryHasMultilineBody: Boolean): Boolean =
        anyEntryHasBlockBody && anyEntryHasMultilineBody

    fun decideEntry(
        sourceText: CharSequence,
        candidate: WhenEntryBracingCandidate,
        baseIndentColumn: Int,
        indentWidth: Int,
        formatEnabled: Boolean,
    ): WhenEntryBracingVerdict {
        val hasEmbeddedNewline = sourceText.subSequence(candidate.contentStart, candidate.contentEnd).contains('\n')
        if (candidate.hasAdjacentComment || (hasEmbeddedNewline && !formatEnabled)) {
            return WhenEntryBracingVerdict(candidate.contentStart, candidate.contentStart, emptyList())
        }

        val edits =
            if (formatEnabled) {
                BraceInsertion.wrapEditsMinimal(
                    leadingGapStart = candidate.leadingGapStart,
                    contentStart = candidate.contentStart,
                    contentEnd = candidate.contentEnd,
                    trailingGapEnd = candidate.contentEnd,
                    hasFollowingBranch = false,
                )
            } else {
                BraceInsertion.wrapEdits(
                    leadingGapStart = candidate.leadingGapStart,
                    contentStart = candidate.contentStart,
                    contentEnd = candidate.contentEnd,
                    trailingGapEnd = candidate.contentEnd,
                    hasFollowingBranch = false,
                    baseIndentColumn = baseIndentColumn,
                    indentWidth = indentWidth,
                )
            }
        return WhenEntryBracingVerdict(
            reportStart = candidate.contentStart,
            reportEnd = candidate.contentEnd,
            edits = edits,
        )
    }
}
