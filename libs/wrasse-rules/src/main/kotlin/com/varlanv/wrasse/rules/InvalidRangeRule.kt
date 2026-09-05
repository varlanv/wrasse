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
 * A `..`/`downTo`/`until`/`..<` binary expression whose left and right operands are both bare
 * integer literals is reported (see [InvalidRangeDecision]) at the whole expression's own span.
 * Only a direct `INTEGER_CONSTANT` on either side is a candidate — a unary-minus-prefixed literal
 * (`-1..1`) is wrapped in its own `PREFIX_EXPRESSION` node, never a direct
 * `BINARY_EXPRESSION` child, so it is never a candidate either, matching the upstream rule this
 * derives from exactly (its own cast to a plain constant expression fails the same way).
 */
class InvalidRangeRule : WUninitializedRule {
    override val id: String = "invalid-range"

    override fun initRule(config: WrasseRuleConfig): WBufferedNodeRule {
        val ruleId = id
        return object : WBufferedNodeRule {
            override val id = ruleId
            override val config = config
            override val targetTypes = setOf(WNodeType.BINARY_EXPRESSION)

            override fun exitNode(
                ctx: WContext,
                children: ChildBuffer,
                reporter: WReporter,
            ) {
                val significant = (0 until children.size).filter { !children.type(it).isWhitespaceOrComment }
                if (significant.size != 3) return
                val (leftIdx, opIdx, rightIdx) = significant
                if (children.type(opIdx) != WNodeType.OPERATION_REFERENCE) return
                if (children.type(
                    leftIdx,
                ) != WNodeType.INTEGER_CONSTANT || children.type(rightIdx) != WNodeType.INTEGER_CONSTANT) {
                    return
                }
                val lower = children.textSpan(leftIdx, ctx.sourceText).toString().toIntOrNull() ?: return
                val upper = children.textSpan(rightIdx, ctx.sourceText).toString().toIntOrNull() ?: return
                val operatorText = children.textSpan(opIdx, ctx.sourceText)

                val message = InvalidRangeDecision.decide(operatorText, lower, upper) ?: return
                reporter.report(ruleId, message, ctx.startOffset, ctx.endOffset, this)
            }
        }
    }
}
