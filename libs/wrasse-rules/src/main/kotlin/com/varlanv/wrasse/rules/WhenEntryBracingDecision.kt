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
 * [shouldBraceEntries] gates the whole feature for one `when` expression: ktlint's own
 * `when-entry-bracing` braces every bare entry as soon as *either* some entry already has a block
 * body *or* some entry's body doesn't start on the same line as its own `ARROW`; detekt's own
 * `BracesOnWhenStatements` (shipped defaults `singleLine = "necessary"`, `multiLine = "consistent"`)
 * only ever wants entries braced when both hold at once — a bare-vs-braced mix (`consistent`) is
 * only evaluated for the whole `when` once some entry's body is multiline (its own policy switches
 * from `singleLine` to `multiLine` on that basis; `singleLine = "necessary"` never examines bare
 * entries at all, only ever flags an already-braced entry for *removal*). Requiring both conditions
 * together is the strict intersection: a fully-bare `when` — single or multi-line entries alike —
 * is never touched (matches detekt's `multiLine = "consistent"` "no braces are accepted" verdict,
 * even though ktlint alone would brace it), and a fully single-line, braced/bare-mixed `when` is
 * never touched either (matches detekt's `singleLine = "necessary"`, which only ever wants brace
 * *removal* there, the opposite direction from what ktlint's own consistency-forcing would do) —
 * see design.md §13 for the ground-truthed disagreement inventory this narrows down from.
 *
 * [decideEntry] bails (empty [WhenEntryBracingVerdict.edits], report-only) whenever
 * [WhenEntryBracingCandidate.hasAdjacentComment] is set or the entry's own bare-expression text
 * already spans multiple lines — same established uniform-bail precedent and reindentation-scope
 * limit as [IfElseBracingDecision]. Otherwise, both new edits are computed purely from
 * [baseIndentColumn] (the entry's own physical line indentation) and [indentWidth] via the shared
 * [BraceInsertion] helper — never copied from whatever whitespace happened to already be there.
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
