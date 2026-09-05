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
 * A `++`/`--` [WNodeType.POSTFIX_EXPRESSION] that is either the direct right operand of a binary
 * expression whose left operand has the exact same text (`i = i++`), or a direct child of that
 * right operand (`i = 1 + i++`), is reported (see [UselessPostfixExpressionDecision]) at the
 * postfix's own span. Every `POSTFIX_EXPRESSION`'s own base text and operator are recorded at its
 * own exit, keyed by offset; every `BINARY_EXPRESSION`'s own direct postfix children are likewise
 * recorded, keyed by offset, so an enclosing binary expression whose right operand is itself a
 * binary expression can look up that operand's own direct children — one level below
 * `expression.right`, matching the upstream rule this derives from's own `getChildrenOfType`
 * (direct children only, never recursed through further nesting). Only the assignment/comparison
 * self-reference slice of upstream is ported; its separate `return i++` detection — gated upstream
 * by a local/class-property name heuristic that requires collecting every property name declared
 * anywhere in the enclosing function's own subtree, in either source order, before a single-pass
 * walk reaches the `return` — is not attempted, a deliberate narrowing rather than an
 * under-verified approximation.
 */
class UselessPostfixExpressionRule : WUninitializedRule {
    override val id: String = "useless-postfix-expression"

    override fun initRule(config: WrasseRuleConfig): WBufferedNodeRule {
        val ruleId = id
        return object : WBufferedNodeRule {
            override val id = ruleId
            override val config = config
            override val targetTypes = setOf(WNodeType.BINARY_EXPRESSION, WNodeType.POSTFIX_EXPRESSION)

            private val postfixFacts = mutableMapOf<Long, PostfixFact>()
            private val binaryPostfixChildren = mutableMapOf<Long, MutableList<PostfixHit>>()

            override fun exitNode(
                ctx: WContext,
                children: ChildBuffer,
                reporter: WReporter,
            ) {
                when (ctx.type) {
                    WNodeType.POSTFIX_EXPRESSION -> finalizePostfix(ctx, children)
                    WNodeType.BINARY_EXPRESSION -> finalizeBinary(ctx, children, reporter)
                    else -> {}
                }
            }

            private fun finalizePostfix(ctx: WContext, children: ChildBuffer) {
                val significant = significantIndices(children)
                if (significant.size != 2) return
                val (operandIdx, opIdx) = significant[0] to significant[1]
                val opText = children.textSpan(opIdx, ctx.sourceText)
                postfixFacts[key(
                    ctx.startOffset,
                    ctx.endOffset,
                )] =
                    PostfixFact(
                        baseText = children.textSpan(operandIdx, ctx.sourceText).toString(),
                        isIncrementOrDecrement = UselessPostfixExpressionDecision.isIncrementOrDecrement(opText),
                        postfixText = ctx.sourceText.subSequence(ctx.startOffset, ctx.endOffset).toString(),
                    )
            }

            private fun finalizeBinary(
                ctx: WContext,
                children: ChildBuffer,
                reporter: WReporter,
            ) {
                val significant = significantIndices(children)
                if (significant.size != 3) return
                val (leftIdx, _, rightIdx) = Triple(significant[0], significant[1], significant[2])
                val leftText = children.textSpan(leftIdx, ctx.sourceText)

                if (children.type(rightIdx) == WNodeType.POSTFIX_EXPRESSION) {
                    reportIfMatch(
                        reporter = reporter,
                        fact = postfixFacts[key(children.startOffset(rightIdx), children.endOffset(rightIdx))],
                        otherOperandText = leftText,
                        start = children.startOffset(rightIdx),
                        end = children.endOffset(rightIdx),
                    )
                } else if (children.type(rightIdx) == WNodeType.BINARY_EXPRESSION) {
                    binaryPostfixChildren[key(
                        children.startOffset(rightIdx),
                        children.endOffset(rightIdx),
                    )]?.forEach { hit ->
                        reportIfMatch(
                            reporter = reporter,
                            fact = hit.fact,
                            otherOperandText = leftText,
                            start = hit.start,
                            end = hit.end,
                        )
                    }
                }

                recordOwnPostfixChildren(ctx, children, leftIdx, rightIdx)
            }

            private fun recordOwnPostfixChildren(
                ctx: WContext,
                children: ChildBuffer,
                leftIdx: Int,
                rightIdx: Int,
            ) {
                val hits = mutableListOf<PostfixHit>()
                for (idx in intArrayOf(leftIdx, rightIdx)) {
                    if (children.type(idx) != WNodeType.POSTFIX_EXPRESSION) continue
                    val fact = postfixFacts[key(children.startOffset(idx), children.endOffset(idx))] ?: continue
                    hits.add(PostfixHit(fact, children.startOffset(idx), children.endOffset(idx)))
                }
                if (hits.isNotEmpty()) binaryPostfixChildren[key(ctx.startOffset, ctx.endOffset)] = hits
            }

            private fun reportIfMatch(
                reporter: WReporter,
                fact: PostfixFact?,
                otherOperandText: CharSequence,
                start: Int,
                end: Int,
            ) {
                if (fact == null) return
                val message =
                    UselessPostfixExpressionDecision.decide(
                        fact.isIncrementOrDecrement,
                        fact.baseText,
                        otherOperandText,
                        fact.postfixText,
                    ) ?: return
                reporter.report(ruleId, message, start, end, this)
            }

            private fun significantIndices(children: ChildBuffer): List<Int> {
                val result = ArrayList<Int>(children.size)
                for (i in 0 until children.size) if (!children.type(i).isWhitespaceOrComment) result.add(i)
                return result
            }

            private fun key(start: Int, end: Int): Long = (start.toLong() shl 32) or (end.toLong() and 0xFF_FFF_FFFL)
        }
    }

    private class PostfixFact(
        val baseText: String,
        val isIncrementOrDecrement: Boolean,
        val postfixText: String,
    )

    private class PostfixHit(
        val fact: PostfixFact,
        val start: Int,
        val end: Int,
    )
}
