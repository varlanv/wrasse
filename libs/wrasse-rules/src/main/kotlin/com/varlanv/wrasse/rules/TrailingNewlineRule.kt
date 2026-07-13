package com.varlanv.wrasse.rules

import com.varlanv.wrasse.model.WContext
import com.varlanv.wrasse.model.WFileRule
import com.varlanv.wrasse.model.WReporter
import com.varlanv.wrasse.model.WUninitializedRule
import com.varlanv.wrasse.model.WrasseRuleConfig

class TrailingNewlineRule : WUninitializedRule {
    override val id: String = "trailing-newline"
    override fun initRule(config: WrasseRuleConfig): WFileRule {
        val ruleId = id
        return object : WFileRule {
            override val id = ruleId
            override val config = config

            override fun visit(ctx: WContext, reporter: WReporter) {
                val lastText = ctx.prevLeafText
                if (lastText.isNullOrEmpty() || lastText[lastText.length - 1] != '\n') {
                    reporter.report(ruleId, "File must end with a newline",
                        0, maxOf(ctx.prevLeafEnd, 1), this)
                }
            }
        }
    }
}
