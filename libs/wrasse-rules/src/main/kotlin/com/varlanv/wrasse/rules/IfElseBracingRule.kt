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
 * Wraps a bare (unbraced) `if`/`else` branch in braces, one wrasse id covering ktlint's
 * `multiline-if-else` + `if-else-bracing` and detekt's `BracesOnIfStatements` (default
 * `singleLine = "never"`, `multiLine = "always"`).
 *
 * Ground-truthed against both real engines (probe harnesses against the actual jars/checkouts,
 * reverted byte-clean — see design.md §13): the two upstreams disagree sharply outside one
 * narrow overlap. ktlint's `if-else-bracing` forces *consistency* (any already-braced sibling
 * braces the rest) and its `multiline-if-else` unconditionally braces any `else if` chain even
 * when the whole chain sits on one physical source line; detekt's default config never wants
 * either of those (a fully single-line chain is `singleLine = "never"` — braces would be
 * *removed*, never added). The one thing both engines' *defaults* actually agree on adding
 * braces for: a currently-bare branch belonging to an if/else-if/else chain that, as physically
 * written, spans more than one source line. That intersection is this rule's entire scope —
 * consistency-only bracing and the single-line-else-if quirk are both deliberately left alone.
 *
 * Never touches (no report, no fix): an already-braced branch; an empty branch (`if (false)
 * else { ... }` is legal Kotlin and must not throw); an `else` whose sole content is itself a
 * bare `if` (an `else if` continuation — wrapping it as `else { if ... }` would be a first-class
 * semantic hazard, never attempted, handled instead when that nested `IF` is visited on its own
 * so the whole chain still gets braced branch-by-branch); any chain that, as physically written,
 * is entirely one source line (matches detekt's `singleLine = "never"`, which never wants braces
 * added there, even for an `else if` chain ktlint alone would force).
 *
 * Reports but never autofixes (report-only bail): a comment anywhere in the gap around the
 * branch (established project precedent — never autofix past a comment); a branch whose own
 * bare-statement text already spans multiple lines (a chained call split across lines, a wrapped
 * binary expression, …) — ground-truthed to be the one shape where ktlint's own real formatter
 * output is *not* reindented for the newly-nested content, so replicating it byte-for-byte would
 * not itself be born-clean (design.md §5.1); D19's idempotence + compile-safety guard would catch
 * this if it were ever emitted as an edit, but it is simpler and safer to never emit one.
 *
 * See [IfElseBracingDecision] for the pure verdict/edit logic.
 */
class IfElseBracingRule : WUninitializedRule {
    override val id: String = "if-else-bracing"

    override fun initRule(config: WrasseRuleConfig): WBufferedNodeRule {
        val ruleId = id
        return object : WBufferedNodeRule {
            override val id = ruleId
            override val config = config
            override val targetTypes = setOf(WNodeType.IF)

            override fun exitNode(ctx: WContext, children: ChildBuffer, reporter: WReporter) {
                var rparIdx = -1
                var thenIdx = -1
                var elseKwIdx = -1
                var elseIdx = -1
                for (i in 0 until children.size) {
                    when (children.type(i)) {
                        WNodeType.RPAR -> rparIdx = i
                        WNodeType.THEN -> thenIdx = i
                        WNodeType.KW_ELSE -> elseKwIdx = i
                        WNodeType.ELSE -> elseIdx = i
                        else -> {}
                    }
                }
                if (rparIdx < 0 || thenIdx < 0) return

                val (chainStart, chainEnd) = chainHeadSpan(ctx)
                if (!IfElseBracingDecision.chainSpansMultipleLines(ctx.sourceText, chainStart, chainEnd)) return

                val baseIndentColumn = columnOf(ctx.sourceText, chainStart)
                val hasElse = elseKwIdx >= 0 && elseIdx >= 0

                evaluateThen(
                    ctx = ctx,
                    children = children,
                    reporter = reporter,
                    rparIdx = rparIdx,
                    thenIdx = thenIdx,
                    elseKwIdx = if (hasElse) elseKwIdx else -1,
                    baseIndentColumn = baseIndentColumn,
                )
                if (hasElse) {
                    evaluateElse(
                        ctx = ctx,
                        children = children,
                        reporter = reporter,
                        elseKwIdx = elseKwIdx,
                        elseIdx = elseIdx,
                        baseIndentColumn = baseIndentColumn,
                    )
                }
            }

            private fun evaluateThen(
                ctx: WContext,
                children: ChildBuffer,
                reporter: WReporter,
                rparIdx: Int,
                thenIdx: Int,
                elseKwIdx: Int,
                baseIndentColumn: Int,
            ) {
                val contentStart = children.startOffset(thenIdx)
                val contentEnd = children.endOffset(thenIdx)
                if (contentStart == contentEnd || ctx.sourceText[contentStart] == '{') return

                val hasElse = elseKwIdx >= 0
                var hasComment = hasCommentBetween(children, rparIdx + 1, thenIdx)
                val trailingGapEnd =
                    if (hasElse) {
                        hasComment = hasComment || hasCommentBetween(children, thenIdx + 1, elseKwIdx)
                        children.startOffset(elseKwIdx)
                    } else {
                        contentEnd
                    }

                report(
                    ctx = ctx,
                    reporter = reporter,
                    baseIndentColumn = baseIndentColumn,
                    candidate = IfElseBracingCandidate(
                        leadingGapStart = children.endOffset(rparIdx),
                        contentStart = contentStart,
                        contentEnd = contentEnd,
                        trailingGapEnd = trailingGapEnd,
                        hasFollowingBranch = hasElse,
                        hasAdjacentComment = hasComment,
                    ),
                )
            }

            private fun evaluateElse(
                ctx: WContext,
                children: ChildBuffer,
                reporter: WReporter,
                elseKwIdx: Int,
                elseIdx: Int,
                baseIndentColumn: Int,
            ) {
                val contentStart = children.startOffset(elseIdx)
                val contentEnd = children.endOffset(elseIdx)
                if (contentStart == contentEnd) return
                if (ctx.sourceText[contentStart] == '{') return
                if (looksLikeBareIf(ctx.sourceText, contentStart, contentEnd)) return

                val hasComment = hasCommentBetween(children, elseKwIdx + 1, elseIdx)

                report(
                    ctx = ctx,
                    reporter = reporter,
                    baseIndentColumn = baseIndentColumn,
                    candidate = IfElseBracingCandidate(
                        leadingGapStart = children.endOffset(elseKwIdx),
                        contentStart = contentStart,
                        contentEnd = contentEnd,
                        trailingGapEnd = contentEnd,
                        hasFollowingBranch = false,
                        hasAdjacentComment = hasComment,
                    ),
                )
            }

            private fun report(
                ctx: WContext,
                reporter: WReporter,
                baseIndentColumn: Int,
                candidate: IfElseBracingCandidate,
            ) {
                val verdict = IfElseBracingDecision.decideBranch(ctx.sourceText, candidate, baseIndentColumn, INDENT_WIDTH)
                reporter.report(
                    ruleId, MESSAGE,
                    verdict.reportStart, verdict.reportEnd, this,
                    edits = verdict.edits,
                )
            }

            private fun hasCommentBetween(children: ChildBuffer, from: Int, until: Int): Boolean {
                for (i in from until until) {
                    val type = children.type(i)
                    if (type.isWhitespaceOrComment && type != WNodeType.WHITE_SPACE) return true
                }
                return false
            }
        }
    }

    private companion object {
        const val INDENT_WIDTH = 4
        const val MESSAGE = "Missing braces on branch of multi-line if-statement"

        /**
         * Walks up through consecutive (`ELSE`, `IF`) ancestor pairs to find the outermost
         * `if`-statement heading this `else if` chain (or this node itself, if it isn't one) —
         * the span both upstream engines actually key their multi-line decision on for every
         * branch in the chain, including a locally single-line `else if` tail.
         */
        fun chainHeadSpan(ctx: WContext): Pair<Int, Int> {
            var headStart = ctx.startOffset
            var headEnd = ctx.endOffset
            var idx = ctx.ancestors.size - 1
            while (idx >= 1 && ctx.ancestors.typeAt(idx) == WNodeType.ELSE && ctx.ancestors.typeAt(idx - 1) == WNodeType.IF) {
                headStart = ctx.ancestors.startOffsetAt(idx - 1)
                headEnd = ctx.ancestors.endOffsetAt(idx - 1)
                idx -= 2
            }
            return headStart to headEnd
        }

        /**
         * The indentation column of the physical line containing [offset] - the count of leading
         * whitespace before the line's first non-whitespace character, not [offset]'s own column.
         * These coincide whenever the chain head is itself the first token on its line (the common
         * case); they diverge when the chain head sits mid-line (`fun foo() = if (...)`), where
         * [offset]'s own column would misalign every brace in the chain to that arbitrary
         * mid-line position instead of the enclosing statement's real indentation depth.
         */
        fun columnOf(sourceText: CharSequence, offset: Int): Int {
            var lineStart = offset - 1
            while (lineStart >= 0 && sourceText[lineStart] != '\n') lineStart--
            lineStart++
            var column = 0
            while (lineStart + column < sourceText.length &&
                (sourceText[lineStart + column] == ' ' || sourceText[lineStart + column] == '\t')
            ) {
                column++
            }
            return column
        }

        fun looksLikeBareIf(sourceText: CharSequence, start: Int, end: Int): Boolean {
            if (end - start < 2 || sourceText[start] != 'i' || sourceText[start + 1] != 'f') return false
            if (end - start == 2) return true
            val next = sourceText[start + 2]
            return !next.isLetterOrDigit() && next != '_'
        }
    }
}
