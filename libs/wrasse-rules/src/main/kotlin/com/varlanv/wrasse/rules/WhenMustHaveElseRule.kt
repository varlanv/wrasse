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
 * A statement-position `when` missing an `else` branch, whose own entries are not entirely
 * enum-entry-shaped, is reported (see [WhenMustHaveElseDecision]) at its own span. A stack of open
 * `WHEN` frames tracks, per `when`: whether any entry carries `else`, whether any entry's condition
 * is an `is`-pattern (always disqualifies the enum-only exemption), and whether every plain
 * expression condition is itself enum-entry-shaped (a bare or dot-qualified reference) — all
 * decidable the moment the `when` itself is entered or as its entries close. A `when` used as a
 * lambda's own last statement needs the enclosing `BLOCK`'s own last child, only known once that
 * block closes, so every candidate's final verdict is deferred to [afterFile]. A `when` nested
 * inside a `WHEN_CONDITION_IN_RANGE` (`in RED..BLUE`) is deliberately not inspected for the
 * enum-only heuristic — treated as always enum-like — a documented, safe-direction narrowing
 * (never a new false positive relative to the upstream rule this derives from, only a possible
 * missed one on a non-enum `in` range condition, a rare shape). A `when` whose immediate parent is
 * any `BINARY_EXPRESSION` is exempt regardless of operator — broader than the upstream rule this
 * derives from, which only exempts a preceding bare `=` sibling specifically (a plain assignment);
 * telling that one operator apart from any other would need the operand's own operator text, one
 * more level of bookkeeping for a shape rare enough that the safer, simpler direction is to exempt
 * unconditionally rather than risk a false positive.
 */
class WhenMustHaveElseRule : WUninitializedRule {
    override val id: String = "when-must-have-else"

    override fun initRule(config: WrasseRuleConfig): WBufferedNodeRule {
        val ruleId = id
        return object : WBufferedNodeRule {
            override val id = ruleId
            override val config = config
            override val targetTypes = setOf(WNodeType.WHEN, WNodeType.WHEN_ENTRY, WNodeType.WHEN_CONDITION_EXPRESSION, WNodeType.BLOCK)

            private val whenFrames = mutableListOf<WhenFrame>()
            private val candidates = mutableListOf<WhenCandidate>()
            private val lambdaLastStatementWhenStarts = mutableSetOf<Int>()

            override fun enterNode(ctx: WContext, reporter: WReporter): Boolean = when (ctx.type) {
                WNodeType.WHEN -> {
                    val ancestors = ctx.ancestors
                    val isExempt =
                    ctx.hasAncestor(WNodeType.RETURN) ||
                        ancestors.peekType() ==
                        WNodeType.WHEN_ENTRY ||
                        ancestors.peekType() ==
                        WNodeType.PROPERTY ||
                        ancestors.peekType() ==
                        WNodeType.FUN ||
                        ancestors.peekType() ==
                        WNodeType.BINARY_EXPRESSION
                    whenFrames.add(WhenFrame(isExempt))
                    true
                }

                WNodeType.WHEN_ENTRY, WNodeType.WHEN_CONDITION_EXPRESSION -> whenFrames.isNotEmpty()
                WNodeType.BLOCK -> ctx.ancestors.peekType() == WNodeType.FUNCTION_LITERAL
                else -> false
            }

            override fun exitNode(ctx: WContext, children: ChildBuffer, reporter: WReporter) {
                when (ctx.type) {
                    WNodeType.WHEN -> finalizeWhen(ctx)
                    WNodeType.WHEN_ENTRY -> finalizeEntry(children)
                    WNodeType.WHEN_CONDITION_EXPRESSION -> finalizeCondition(children)
                    WNodeType.BLOCK -> finalizeLambdaBlock(children)
                    else -> {}
                }
            }

            private fun finalizeWhen(ctx: WContext) {
                val frame = if (whenFrames.isNotEmpty()) whenFrames.removeAt(whenFrames.size - 1) else return
                val isEnumOnly = !frame.hasIsPattern && frame.allExpressionConditionsEnumLike
                candidates.add(WhenCandidate(ctx.startOffset, ctx.endOffset, frame.isExempt, frame.hasElse, isEnumOnly))
            }

            private fun finalizeEntry(children: ChildBuffer) {
                val frame = whenFrames.lastOrNull() ?: return
                if (children.hasChildOfType(WNodeType.KW_ELSE)) frame.hasElse = true
                if (children.hasChildOfType(WNodeType.WHEN_CONDITION_IS_PATTERN)) frame.hasIsPattern = true
            }

            private fun finalizeCondition(children: ChildBuffer) {
                val frame = whenFrames.lastOrNull() ?: return
                var significantCount = 0
                var soleType: WNodeType? = null
                for (i in 0 until children.size) {
                    if (children.type(i).isWhitespaceOrComment) continue
                    significantCount++
                    soleType = children.type(i)
                }
                val enumLike = significantCount ==
                    1 &&
                    (soleType == WNodeType.REFERENCE_EXPRESSION || soleType == WNodeType.DOT_QUALIFIED_EXPRESSION)
                if (!enumLike) frame.allExpressionConditionsEnumLike = false
            }

            private fun finalizeLambdaBlock(children: ChildBuffer) {
                var lastSignificant = -1
                for (i in 0 until children.size) {
                    if (children.type(i).isWhitespaceOrComment ||
                        children.type(i) ==
                        WNodeType.LBRACE ||
                        children.type(i) ==
                        WNodeType.RBRACE) {
                        continue
                    }
                    lastSignificant = i
                }
                if (lastSignificant >= 0 && children.type(lastSignificant) == WNodeType.WHEN) {
                    lambdaLastStatementWhenStarts.add(children.startOffset(lastSignificant))
                }
            }

            override fun afterFile(ctx: WContext, reporter: WReporter) {
                for (candidate in candidates) {
                    val isLambdaLastStatement = candidate.start in lambdaLastStatementWhenStarts
                    val message =
                    WhenMustHaveElseDecision.decide(candidate.isExempt, candidate.hasElse, candidate.isEnumOnly, isLambdaLastStatement)
                        ?: continue
                    reporter.report(ruleId, message, candidate.start, candidate.end, this)
                }
            }
        }
    }

    private class WhenFrame(
        val isExempt: Boolean,
        var hasElse: Boolean = false,
        var hasIsPattern: Boolean = false,
        var allExpressionConditionsEnumLike: Boolean = true,
    )

    private class WhenCandidate(val start: Int, val end: Int, val isExempt: Boolean, val hasElse: Boolean, val isEnumOnly: Boolean)
}
