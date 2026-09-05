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
 * A maximal chain of directly-nested `&&`/`||` [WNodeType.BINARY_EXPRESSION]s that uses both
 * operators somewhere in the chain is reported (see [MixedConditionOperatorsDecision]) once, at
 * the chain's own outermost span. Every logical `BINARY_EXPRESSION` stores its own "which
 * operators appear in my own chain so far" flags at its own exit, keyed by its own offsets; a
 * direct child that is itself a logical `BINARY_EXPRESSION` has its flags merged in and removed
 * from the map (never independently reported) regardless of whether its own operator matches —
 * unlike [UnnecessaryPartOfBinaryExpressionRule]'s same-operator-only merge, mixing is exactly
 * the different-operator case. A parenthesized sub-expression, or any non-logical operand, is
 * never itself a recorded chain entry, so it is always treated as one opaque operand and never
 * flattened through — the same boundary the upstream rule this derives from respects via its own
 * recursive descent. Whatever survives unconsumed by [afterFile] is each chain's own true
 * outermost node, decided and reported there.
 */
class MixedConditionOperatorsRule : WUninitializedRule {
    override val id: String = "mixed-condition-operators"

    override fun initRule(config: WrasseRuleConfig): WBufferedNodeRule {
        val ruleId = id
        return object : WBufferedNodeRule {
            override val id = ruleId
            override val config = config
            override val targetTypes = setOf(WNodeType.BINARY_EXPRESSION)

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
                val isAnd =
                    when {
                        opText.contentEquals("&&") -> true
                        opText.contentEquals("||") -> false
                        else -> null
                    } ?: return

                var hasAnd = isAnd
                var hasOr = !isAnd
                mergeChild(children, leftIdx) { and, or ->
                    hasAnd = hasAnd || and
                    hasOr = hasOr || or
                }
                mergeChild(children, rightIdx) { and, or ->
                    hasAnd = hasAnd || and
                    hasOr = hasOr || or
                }

                chains[key(ctx.startOffset, ctx.endOffset)] = ChainNode(ctx.startOffset, ctx.endOffset, hasAnd, hasOr)
            }

            private fun mergeChild(
                children: ChildBuffer,
                idx: Int,
                merge: (Boolean, Boolean) -> Unit,
            ) {
                if (children.type(idx) != WNodeType.BINARY_EXPRESSION) return
                val child = chains.remove(key(children.startOffset(idx), children.endOffset(idx))) ?: return
                merge(child.hasAnd, child.hasOr)
            }

            private fun key(start: Int, end: Int): Long = (start.toLong() shl 32) or (end.toLong() and 0xFF_FFF_FFFL)

            override fun afterFile(ctx: WContext, reporter: WReporter) {
                for (chain in chains.values) {
                    val message = MixedConditionOperatorsDecision.decide(chain.hasAnd, chain.hasOr) ?: continue
                    reporter.report(ruleId, message, chain.start, chain.end, this)
                }
            }
        }
    }

    private class ChainNode(
        val start: Int,
        val end: Int,
        val hasAnd: Boolean,
        val hasOr: Boolean,
    )
}
