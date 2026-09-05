package com.varlanv.wrasse.rules

import com.varlanv.wrasse.model.ChildBuffer
import com.varlanv.wrasse.model.WBufferedNodeRule
import com.varlanv.wrasse.model.WContext
import com.varlanv.wrasse.model.WNodeType
import com.varlanv.wrasse.model.WReporter
import com.varlanv.wrasse.model.WUninitializedRule
import com.varlanv.wrasse.model.WrasseRuleConfig
import com.varlanv.wrasse.model.isWhitespaceOrComment

private val TARGET_TYPES = setOf(WNodeType.BINARY_EXPRESSION)

/**
 * A chain of `&&` or `||` operands containing a duplicate (after stripping all whitespace, an
 * exact textual match) is reported (see [UnnecessaryPartOfBinaryExpressionDecision]) at the whole
 * chain's own span — matches the upstream rule this derives from exactly, including its own
 * crudeness: text comparison, not semantic equivalence, so `a` and `(a)` count as different
 * operands.
 *
 * Every `&&`/`||` [WNodeType.BINARY_EXPRESSION] stores its own flattened operand list at its own
 * exit, keyed by its own offsets; a same-operator direct child's own list is merged in (removed
 * from the map, so it is never independently reported), matching this whole chain through
 * explicit parens as a boundary (a parenthesized sub-expression is never itself
 * `BINARY_EXPRESSION`-typed, so it is always treated as one opaque operand, never flattened
 * through) — the same boundary the upstream rule's own recursive descent respects. Whatever
 * survives unconsumed by [afterFile] is each chain's own true outermost node, decided and
 * reported there.
 */
class UnnecessaryPartOfBinaryExpressionRule : WUninitializedRule {
    override val id: String = "unnecessary-part-of-binary-expression"

    override fun initRule(config: WrasseRuleConfig): WBufferedNodeRule {
        val ruleId = id
        return object : WBufferedNodeRule {
            override val id = ruleId
            override val config = config
            override val targetTypes = TARGET_TYPES

            private val chains = mutableMapOf<Long, ChainNode>()

            override fun exitNode(
                ctx: WContext,
                children: ChildBuffer,
                reporter: WReporter,
            ) {
                val significant = (0 until children.size).filter { !children.type(it).isWhitespaceOrComment }
                if (significant.size != 3) return
                val (leftIdx, opIdx, rightIdx) = Triple(significant[0], significant[1], significant[2])
                if (children.type(opIdx) != WNodeType.OPERATION_REFERENCE) return
                val opText = children.textSpan(opIdx, ctx.sourceText)
                val operator = when {
                    opText.contentEquals("&&") -> "&&"
                    opText.contentEquals("||") -> "||"
                    else -> null
                } ?: return

                val operands = mutableListOf<String>()
                collectOperand(children, leftIdx, operator, ctx.sourceText, operands)
                collectOperand(children, rightIdx, operator, ctx.sourceText, operands)

                chains[key(
                    ctx.startOffset,
                    ctx.endOffset,
                )] = ChainNode(ctx.startOffset, ctx.endOffset, operator, operands)
            }

            private fun collectOperand(
                children: ChildBuffer,
                idx: Int,
                operator: String,
                sourceText: CharSequence,
                out: MutableList<String>,
            ) {
                if (children.type(idx) == WNodeType.BINARY_EXPRESSION) {
                    val childKey = key(children.startOffset(idx), children.endOffset(idx))
                    val child = chains[childKey]
                    if (child != null && child.operator == operator) {
                        out.addAll(child.operands)
                        chains.remove(childKey)
                        return
                    }
                }
                out.add(UnnecessaryPartOfBinaryExpressionDecision.normalizeOperand(children.textSpan(idx, sourceText)))
            }

            private fun key(start: Int, end: Int): Long = (start.toLong() shl 32) or (end.toLong() and 0xFF_FFF_FFFL)

            override fun afterFile(ctx: WContext, reporter: WReporter) {
                for (chain in chains.values) {
                    val message = UnnecessaryPartOfBinaryExpressionDecision.decide(chain.operands) ?: continue
                    reporter.report(ruleId, message, chain.start, chain.end, this)
                }
            }
        }
    }

    private class ChainNode(
        val start: Int,
        val end: Int,
        val operator: String,
        val operands: List<String>,
    )
}
