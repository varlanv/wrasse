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
 * [chainSpansMultipleLines] gates the whole feature: ktlint's own `multiline-if-else` and
 * detekt's own `BracesOnIfStatements` (default `multiLine = "always"`) only agree on requiring
 * braces when the if/else-if/else chain, as physically written, spans more than one source line
 * — see design.md §13 for the ground-truthed disagreement inventory this narrows down from.
 *
 * [decideBranch] bails (empty [IfElseBracingVerdict.edits], report-only) whenever
 * [IfElseBracingCandidate.hasAdjacentComment] is set (established project precedent: never
 * autofix past a comment rather than risk misplacing it) or the branch's own bare-statement text
 * already spans multiple lines (a chained call, wrapped binary expression, …) — reindenting that
 * content correctly would require re-flowing every interior line, which is out of this rule's
 * single-edit-pair scope; ktlint's own real formatter output for this shape is demonstrably
 * *not* reindented either (ground-truthed), so replicating it byte-for-byte would ship code that
 * is not itself born-clean. Otherwise, both new edits are computed purely from
 * [baseIndentColumn] (the enclosing chain-head statement's own column) and [indentWidth]
 * (D21's `indentWidth` style parameter, not yet wired as config — hardcoded to its documented
 * default by the caller) — never copied from whatever whitespace happened to already be there.
 *
 * A bail reports a zero-width point at [IfElseBracingCandidate.contentStart] rather than the
 * branch's full span: the branch's bare statement can itself be — or contain — an independent
 * nested `if` this same rule fixes on its own subsequent visit to that inner `IF` node, and a
 * wider report span could spuriously overlap that inner fix's edits (which start exactly where
 * this bail's content does), breaking the idempotence harness's "this D1 diagnostic disappears
 * in D2" prediction for a report that in fact never had an edit of its own and is expected to
 * persist unchanged.
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

        val bodyIndent = " ".repeat(baseIndentColumn + indentWidth)
        val closeIndent = " ".repeat(baseIndentColumn)
        val trailingReplacement = if (candidate.hasFollowingBranch) "\n$closeIndent} " else "\n$closeIndent}"

        return IfElseBracingVerdict(
            reportStart = candidate.contentStart,
            reportEnd = candidate.contentEnd,
            edits = listOf(
                WEdit(candidate.leadingGapStart, candidate.contentStart, " {\n$bodyIndent"),
                WEdit(candidate.contentEnd, candidate.trailingGapEnd, trailingReplacement),
            ),
        )
    }
}
