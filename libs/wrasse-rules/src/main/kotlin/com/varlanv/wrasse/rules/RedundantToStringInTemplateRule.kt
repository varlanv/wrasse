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
 * A `${receiver.toString()}` string-template entry whose entire content is one dot-qualified
 * `.toString()` call is reported (see [RedundantToStringInTemplateDecision]) at the whole
 * expression's own span — the template already stringifies its own expression, so the explicit
 * call is redundant. Only the direct `.toString()` slice of the upstream `string-template` rule
 * this derives from is ported here; its sibling "redundant curly braces" concern is a
 * rewrite/format-shaped decision, out of this batch's scope.
 */
class RedundantToStringInTemplateRule : WUninitializedRule {
    override val id: String = "redundant-to-string-in-template"

    override fun initRule(config: WrasseRuleConfig): WBufferedNodeRule {
        val ruleId = id
        return object : WBufferedNodeRule {
            override val id = ruleId
            override val config = config
            override val targetTypes = setOf(WNodeType.DOT_QUALIFIED_EXPRESSION)

            override fun exitNode(
                ctx: WContext,
                children: ChildBuffer,
                reporter: WReporter,
            ) {
                if (ctx.ancestors.peekType() != WNodeType.LONG_STRING_TEMPLATE_ENTRY) return
                val significant = (0 until children.size).filter { !children.type(it).isWhitespaceOrComment }
                if (significant.size != 3) return
                val (receiverIdx, opIdx, selectorIdx) = Triple(significant[0], significant[1], significant[2])
                if (children.type(opIdx) != WNodeType.DOT) return

                val message =
                    RedundantToStringInTemplateDecision.decide(
                        receiverType = children.type(receiverIdx),
                        selectorType = children.type(selectorIdx),
                        selectorText = children.textSpan(selectorIdx, ctx.sourceText),
                    ) ?: return
                reporter.report(ruleId, message, ctx.startOffset, ctx.endOffset, this)
            }
        }
    }
}
