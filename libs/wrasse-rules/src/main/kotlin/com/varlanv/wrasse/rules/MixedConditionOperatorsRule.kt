package com.varlanv.wrasse.rules

import com.varlanv.wrasse.lang.WEdit
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
 * A maximal chain of directly-nested `&&`/`||` [WNodeType.BINARY_EXPRESSION]s that uses both
 * operators somewhere in the chain is reported (see [MixedConditionOperatorsDecision]) once, at
 * the chain's own outermost span. Every logical `BINARY_EXPRESSION` stores its own "which
 * operators appear in my own chain so far" flags at its own exit, keyed by its own offsets; a
 * direct child that is itself a logical `BINARY_EXPRESSION` has its flags merged in and removed
 * from the map, never reported independently. A parenthesized sub-expression, or any non-logical
 * operand, is always treated as one opaque operand and never flattened through. Whatever survives
 * unconsumed by [afterFile] is each chain's own true outermost node, decided and reported there.
 *
 * Every merge also asks [MixedConditionOperatorsDecision.wrapEdits] whether the child just folded
 * in is a maximal `&&` sub-chain sitting as a direct operand of a `||` node; any such wrap, plus
 * whatever wraps the child itself already carried, rides along in the chain's own [ChainNode.edits]
 * up to whichever node survives to [afterFile].
 */
class MixedConditionOperatorsRule : WUninitializedRule {
    override val id: String = "mixed-condition-operators"
    override val canAutofix: Boolean = true

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
                val isAnd =
                    when {
                        opText.contentEquals("&&") -> true
                        opText.contentEquals("||") -> false
                        else -> null
                    } ?: return

                var hasAnd = isAnd
                var hasOr = !isAnd
                var edits: MutableList<WEdit>? = null
                edits = mergeChild(children, leftIdx, isAnd, edits) { and, or ->
                    hasAnd = hasAnd || and
                    hasOr = hasOr || or
                }
                edits = mergeChild(children, rightIdx, isAnd, edits) { and, or ->
                    hasAnd = hasAnd || and
                    hasOr = hasOr || or
                }

                chains[key(ctx.startOffset, ctx.endOffset)] =
                    ChainNode(ctx.startOffset, ctx.endOffset, hasAnd, hasOr, isAnd, edits ?: emptyList())
            }

            private fun mergeChild(
                children: ChildBuffer,
                idx: Int,
                parentIsAnd: Boolean,
                editsSoFar: MutableList<WEdit>?,
                merge: (Boolean, Boolean) -> Unit,
            ): MutableList<WEdit>? {
                if (children.type(idx) != WNodeType.BINARY_EXPRESSION) return editsSoFar
                val child = chains.remove(key(children.startOffset(idx), children.endOffset(idx))) ?: return editsSoFar
                merge(child.hasAnd, child.hasOr)
                val wrap = MixedConditionOperatorsDecision.wrapEdits(parentIsAnd, child.isAnd, child.start, child.end)
                if (child.edits.isEmpty() && wrap.isEmpty()) return editsSoFar
                val result = editsSoFar ?: mutableListOf()
                result.addAll(child.edits)
                result.addAll(wrap)
                return result
            }

            private fun key(start: Int, end: Int): Long = (start.toLong() shl 32) or (end.toLong() and 0xFF_FFF_FFFL)

            override fun afterFile(ctx: WContext, reporter: WReporter) {
                for (chain in chains.values) {
                    val message = MixedConditionOperatorsDecision.decide(chain.hasAnd, chain.hasOr) ?: continue
                    reporter.report(ruleId, message, chain.start, chain.end, this, edits = chain.edits)
                }
            }
        }
    }

    private class ChainNode(
        val start: Int,
        val end: Int,
        val hasAnd: Boolean,
        val hasOr: Boolean,
        val isAnd: Boolean,
        val edits: List<WEdit>,
    )
}
