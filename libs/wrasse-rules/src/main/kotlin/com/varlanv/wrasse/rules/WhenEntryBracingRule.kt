package com.varlanv.wrasse.rules

import com.varlanv.wrasse.model.ChildBuffer
import com.varlanv.wrasse.model.WBufferedNodeRule
import com.varlanv.wrasse.model.WContext
import com.varlanv.wrasse.model.WNodeType
import com.varlanv.wrasse.model.WReporter
import com.varlanv.wrasse.model.WUninitializedRule
import com.varlanv.wrasse.model.WrasseRuleConfig
import com.varlanv.wrasse.model.isWhitespaceOrComment

/**
 * Wraps a bare (unbraced) `when`-entry body in braces, one wrasse id covering ktlint's
 * `when-entry-bracing` and detekt's `BracesOnWhenStatements`.
 *
 * Ground-truthed against both real engines (design.md §13): ktlint's own rule braces every bare
 * entry in a `when` as soon as *either* one entry already has a block body *or* one entry's body
 * doesn't start on the same line as its own `ARROW` — either condition alone is enough. detekt's
 * shipped defaults (`singleLine = "necessary"`, `multiLine = "consistent"`) only ever want entries
 * braced when *both* hold together: `multiLine = "consistent"` (the policy in force once some
 * entry's body is multiline) flags a braced/bare mix, but a fully-bare `when` — however it's laid
 * out — is accepted outright; `singleLine = "necessary"` (the policy while every entry's body stays
 * on the arrow's own line) never examines bare entries at all, only ever flagging an already-braced
 * entry for *removal*, the opposite direction from what ktlint's consistency-forcing would do.
 * Requiring both conditions is therefore the strict intersection: a fully-bare `when` and a fully
 * single-line braced/bare mix are both left completely untouched (no report, no fix).
 *
 * Never touches (no report, no fix): an already-braced entry (first significant child after
 * `ARROW` is a `BLOCK`); an empty block entry (`1 -> {}` is legal Kotlin) — matches detekt's own
 * `hasUnnecessaryBraces` exemption for an empty block, which never counts toward its consistency
 * tally either.
 *
 * Reports but never autofixes (report-only bail): a comment anywhere in the gap around the entry's
 * body — between the `ARROW` and the body, or trailing the body on its own line before the next
 * sibling (established uniform-bail precedent, same posture as `if-else-bracing`); an entry
 * whose own bare-expression text already spans multiple lines (a chained call split across lines,
 * or — the key cross-rule shape — a bare `if`/`when` expression `if-else-bracing`/this same rule's
 * own subsequent visit fixes independently) — ktlint's own real formatter output for this shape is
 * not reindented either without a second, separate `IndentationRule` pass, so replicating it
 * byte-for-byte would not itself be born-clean (design.md §5.1). Both bail categories report a
 * zero-width point at the entry's own content start, mirroring `if-else-bracing`'s own bail-report
 * convention (see [IfElseBracingDecision] KDoc for why: the wider span could otherwise spuriously
 * overlap an inner fix's edits under the idempotence harness's overlap-based heuristic).
 *
 * See [WhenEntryBracingDecision] for the pure verdict/edit logic and [BraceInsertion] for the
 * indentation/edit-construction core shared with `if-else-bracing`.
 */
class WhenEntryBracingRule : WUninitializedRule {
    override val id: String = "when-entry-bracing"
    override val canAutofix: Boolean = true

    override fun initRule(config: WrasseRuleConfig): WBufferedNodeRule {
        val ruleId = id
        return object : WBufferedNodeRule {
            override val id = ruleId
            override val config = config
            override val targetTypes = setOf(WNodeType.WHEN, WNodeType.WHEN_ENTRY)

            private val pendingWhens = mutableListOf<PendingWhen>()

            override fun enterNode(ctx: WContext, reporter: WReporter): Boolean {
                if (ctx.type == WNodeType.WHEN) {
                    pendingWhens.add(PendingWhen())
                }
                return true
            }

            override fun exitNode(ctx: WContext, children: ChildBuffer, reporter: WReporter) {
                if (ctx.type == WNodeType.WHEN_ENTRY) {
                    recordEntry(ctx, children)
                    return
                }

                val pending = pendingWhens.removeAt(pendingWhens.size - 1)
                if (!WhenEntryBracingDecision.shouldBraceEntries(pending.anyEntryHasBlockBody, pending.anyEntryHasMultilineBody)) {
                    return
                }
                val baseIndentColumn = BraceInsertion.physicalLineIndentColumn(ctx.sourceText, ctx.startOffset) + INDENT_WIDTH
                var siblingIdx = 0
                for (candidate in pending.candidates) {
                    while (siblingIdx < children.size &&
                        !(children.type(siblingIdx) == WNodeType.WHEN_ENTRY && children.startOffset(siblingIdx) == candidate.entryStartOffset)
                    ) {
                        siblingIdx++
                    }
                    val hasTrailingComment = siblingIdx < children.size && hasCommentImmediatelyAfter(ctx, children, siblingIdx)
                    report(ctx, reporter, candidate, hasTrailingComment, baseIndentColumn)
                }
            }

            private fun recordEntry(ctx: WContext, children: ChildBuffer) {
                val pending = pendingWhens.lastOrNull() ?: return
                val arrowIdx = children.firstChildOfType(WNodeType.ARROW)
                if (arrowIdx < 0) return
                val bodyIdx = findBodyStartIdx(children, arrowIdx)
                if (bodyIdx < 0) return

                val arrowEnd = children.endOffset(arrowIdx)
                val contentStart = children.startOffset(bodyIdx)
                val contentEnd = ctx.endOffset

                if (ctx.sourceText.subSequence(arrowEnd, contentEnd).contains('\n')) {
                    pending.anyEntryHasMultilineBody = true
                }

                if (children.type(bodyIdx) == WNodeType.BLOCK) {
                    if (!isEmptyBlock(ctx.sourceText, contentStart, contentEnd)) {
                        pending.anyEntryHasBlockBody = true
                    }
                    return
                }

                pending.candidates.add(
                    PendingCandidate(
                        entryStartOffset = ctx.startOffset,
                        leadingGapStart = arrowEnd,
                        contentStart = contentStart,
                        contentEnd = contentEnd,
                        hasAdjacentComment = hasCommentBetween(children, arrowIdx + 1, bodyIdx),
                    )
                )
            }

            private fun report(
                ctx: WContext,
                reporter: WReporter,
                candidate: PendingCandidate,
                hasTrailingComment: Boolean,
                baseIndentColumn: Int,
            ) {
                val verdict = WhenEntryBracingDecision.decideEntry(
                    ctx.sourceText,
                    WhenEntryBracingCandidate(
                        leadingGapStart = candidate.leadingGapStart,
                        contentStart = candidate.contentStart,
                        contentEnd = candidate.contentEnd,
                        hasAdjacentComment = candidate.hasAdjacentComment || hasTrailingComment,
                    ),
                    baseIndentColumn,
                    INDENT_WIDTH,
                )
                reporter.report(
                    ruleId, MESSAGE,
                    verdict.reportStart, verdict.reportEnd, this,
                    edits = verdict.edits,
                )
            }

            private fun hasCommentImmediatelyAfter(ctx: WContext, children: ChildBuffer, entryIdx: Int): Boolean {
                var i = entryIdx + 1
                while (i < children.size) {
                    val type = children.type(i)
                    if (type == WNodeType.WHITE_SPACE) {
                        if (children.textSpan(i, ctx.sourceText).contains('\n')) return false
                        i++
                        continue
                    }
                    return type.isWhitespaceOrComment
                }
                return false
            }

            private fun findBodyStartIdx(children: ChildBuffer, arrowIdx: Int): Int {
                for (i in arrowIdx + 1 until children.size) {
                    if (!children.type(i).isWhitespaceOrComment) return i
                }
                return -1
            }

            private fun hasCommentBetween(children: ChildBuffer, from: Int, until: Int): Boolean {
                for (i in from until until) {
                    val type = children.type(i)
                    if (type.isWhitespaceOrComment && type != WNodeType.WHITE_SPACE) return true
                }
                return false
            }

            private fun isEmptyBlock(sourceText: CharSequence, contentStart: Int, contentEnd: Int): Boolean =
                sourceText.subSequence(contentStart + 1, contentEnd - 1).isBlank()
        }
    }

    private class PendingWhen {
        var anyEntryHasBlockBody = false
        var anyEntryHasMultilineBody = false
        val candidates = mutableListOf<PendingCandidate>()
    }

    private class PendingCandidate(
        val entryStartOffset: Int,
        val leadingGapStart: Int,
        val contentStart: Int,
        val contentEnd: Int,
        val hasAdjacentComment: Boolean,
    )

    private companion object {
        const val INDENT_WIDTH = 4
        const val MESSAGE = "Missing braces on when-entry body"
    }
}
