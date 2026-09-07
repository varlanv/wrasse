package com.varlanv.wrasse.rules

import com.varlanv.wrasse.model.WContext
import com.varlanv.wrasse.model.WNodeRule
import com.varlanv.wrasse.model.WNodeType
import com.varlanv.wrasse.model.WReporter
import com.varlanv.wrasse.model.WRuleOptionSpec
import com.varlanv.wrasse.model.WRuleOptionType
import com.varlanv.wrasse.model.WRuleOptionValue
import com.varlanv.wrasse.model.WUninitializedRule
import com.varlanv.wrasse.model.WrasseRuleConfig

private val TARGET_TYPES = setOf(WNodeType.CONDITION)

/**
 * Reports an `if`/`while`/`do-while` condition combining too many boolean operators (see
 * [ComplexConditionDecision]). A plain [WNodeRule] on `CONDITION` suffices — the check reads the
 * node's own source span directly, no child inspection needed.
 */
class ComplexConditionRule : WUninitializedRule {
    override val id: String = "complex-condition"
    override val options: List<WRuleOptionSpec> = listOf(
        WRuleOptionSpec.Optional(
            name = THRESHOLD,
            type = WRuleOptionType.INTEGER,
            description = "Lowest number of combined boolean operators a condition may have before it is reported",
            default = WRuleOptionValue.Num(ComplexConditionDecision.DEFAULT_THRESHOLD.toLong()),
            minimum = 1,
        ),
    )

    override fun initRule(config: WrasseRuleConfig): WNodeRule {
        val ruleId = id
        val threshold = config.options.integer(THRESHOLD).toInt()
        return object : WNodeRule {
            override val id = ruleId
            override val config = config
            override val targetTypes = TARGET_TYPES

            override fun enterNode(ctx: WContext, reporter: WReporter): Boolean {
                val text = ctx.sourceText.subSequence(ctx.startOffset, ctx.endOffset)
                val message = ComplexConditionDecision.decide(text, threshold) ?: return false
                reporter.report(ruleId, message, ctx.startOffset, ctx.endOffset, this)
                return false
            }
        }
    }

    private companion object {
        const val THRESHOLD = "threshold"
    }
}
