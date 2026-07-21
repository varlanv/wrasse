package com.varlanv.wrasse.rules

import com.varlanv.wrasse.model.WContext
import com.varlanv.wrasse.model.WNodeRule
import com.varlanv.wrasse.model.WNodeType
import com.varlanv.wrasse.model.WReporter
import com.varlanv.wrasse.model.WUninitializedRule
import com.varlanv.wrasse.model.WrasseRuleConfig

/**
 * Reports an `if`/`while`/`do-while` condition combining too many boolean operators (see
 * [ComplexConditionDecision]). A plain [WNodeRule] on `CONDITION` suffices — the check reads the
 * node's own source span directly, no child inspection needed.
 */
class ComplexConditionRule : WUninitializedRule {
    override val id: String = "complex-condition"

    override fun initRule(config: WrasseRuleConfig): WNodeRule {
        val ruleId = id
        return object : WNodeRule {
            override val id = ruleId
            override val config = config
            override val targetTypes = setOf(WNodeType.CONDITION)

            override fun enterNode(ctx: WContext, reporter: WReporter): Boolean {
                val text = ctx.sourceText.subSequence(ctx.startOffset, ctx.endOffset)
                val message = ComplexConditionDecision.decide(text) ?: return false
                reporter.report(ruleId, message, ctx.startOffset, ctx.endOffset, this)
                return false
            }
        }
    }
}
