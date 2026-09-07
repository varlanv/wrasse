package com.varlanv.wrasse.rules

import com.varlanv.wrasse.model.ChildBuffer
import com.varlanv.wrasse.model.WBufferedNodeRule
import com.varlanv.wrasse.model.WContext
import com.varlanv.wrasse.model.WNodeType
import com.varlanv.wrasse.model.WReporter
import com.varlanv.wrasse.model.WUninitializedRule
import com.varlanv.wrasse.model.WrasseRuleConfig
import com.varlanv.wrasse.model.isWhitespaceOrComment

private val TARGET_TYPES = setOf(WNodeType.PREFIX_EXPRESSION, WNodeType.PARENTHESIZED)

/**
 * See [DoubleNegativeDecision]. A `!` [WNodeType.PREFIX_EXPRESSION] whose own operand is itself
 * another `!` prefix — directly, or through any number of [WNodeType.PARENTHESIZED] wrappers that
 * carry nothing else — accumulates depth from its child, computed bottom-up via [carries] as the
 * walk exits each nested layer. Reports once, at the outermost `!` of a chain: an ancestor is
 * "outermost" when walking up from it (skipping parentheses) never reaches another `!` prefix.
 */
class DoubleNegativeRule : WUninitializedRule {
    override val id: String = "double-negative"
    override val canAutofix: Boolean = true

    override fun initRule(config: WrasseRuleConfig): WBufferedNodeRule {
        val ruleId = id
        return object : WBufferedNodeRule {
            override val id = ruleId
            override val config = config
            override val targetTypes = TARGET_TYPES

            private val carries = mutableListOf<Carry?>()

            override fun enterNode(ctx: WContext, reporter: WReporter): Boolean {
                carries.add(null)
                return true
            }

            override fun exitNode(
                ctx: WContext,
                children: ChildBuffer,
                reporter: WReporter,
            ) {
                when (ctx.type) {
                    WNodeType.PREFIX_EXPRESSION -> finalizePrefix(ctx, children, reporter)
                    WNodeType.PARENTHESIZED -> finalizeParenthesized(ctx)
                    else -> {}
                }
            }

            private fun finalizeParenthesized(ctx: WContext) {
                val inner = carries.removeAt(carries.size - 1) ?: return
                if (isChainLink(ctx.ancestors.peekType())) {
                    carries[carries.size - 1] = inner
                }
            }

            private fun finalizePrefix(
                ctx: WContext,
                children: ChildBuffer,
                reporter: WReporter,
            ) {
                val inner = carries.removeAt(carries.size - 1)
                val significant = (0 until children.size).filter { !children.type(it).isWhitespaceOrComment }
                if (significant.size != 2) return
                val (opIdx, operandIdx) = significant[0] to significant[1]
                if (!children.textSpan(opIdx, ctx.sourceText).contentEquals("!")) return

                val depth: Int
                val operandStart: Int
                val operandEnd: Int
                if (inner != null) {
                    depth = 1 + inner.depth
                    operandStart = inner.operandStart
                    operandEnd = inner.operandEnd
                } else {
                    depth = 1
                    operandStart = children.startOffset(operandIdx)
                    operandEnd = children.endOffset(operandIdx)
                }

                if (isChainLink(ctx.ancestors.peekType())) {
                    carries[carries.size - 1] = Carry(depth, operandStart, operandEnd)
                }

                if (isNestedInsideExclamationChain(ctx)) return
                val message = DoubleNegativeDecision.decide(depth) ?: return
                val operandText = ctx.sourceText.subSequence(operandStart, operandEnd)
                reporter.report(
                    ruleId,
                    message,
                    ctx.startOffset,
                    ctx.endOffset,
                    this,
                    edits = DoubleNegativeDecision.editsFor(depth, ctx.startOffset, ctx.endOffset, operandText),
                )
            }

            private fun isChainLink(type: WNodeType) =
                type == WNodeType.PREFIX_EXPRESSION || type == WNodeType.PARENTHESIZED

            private fun isNestedInsideExclamationChain(ctx: WContext): Boolean {
                val ancestors = ctx.ancestors
                var i = ancestors.size - 1
                while (i >= 0 && ancestors.typeAt(i) == WNodeType.PARENTHESIZED) i--
                if (i < 0 || ancestors.typeAt(i) != WNodeType.PREFIX_EXPRESSION) return false
                return ctx.sourceText[ancestors.startOffsetAt(i)] == '!'
            }
        }
    }

    private class Carry(val depth: Int, val operandStart: Int, val operandEnd: Int)
}
