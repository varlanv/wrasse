package com.varlanv.wrasse.rules

import com.varlanv.wrasse.lang.WEdit
import com.varlanv.wrasse.model.WContext
import com.varlanv.wrasse.model.WFileRule
import com.varlanv.wrasse.model.WReporter
import com.varlanv.wrasse.model.WUninitializedRule
import com.varlanv.wrasse.model.WrasseRuleConfig

class TrailingNewlineRule : WUninitializedRule {
    override val id: String = "trailing-newline"
    override val canAutofix: Boolean = true
    override fun initRule(config: WrasseRuleConfig): WFileRule {
        val ruleId = id
        return object : WFileRule {
            override val id = ruleId
            override val config = config

            override fun visit(ctx: WContext, reporter: WReporter) {
                val lastText = ctx.prevLeafText ?: return
                if (lastText.isEmpty() || lastText[lastText.length - 1] != '\n') {
                    val endOffset = ctx.prevLeafEnd
                    reporter.report(
                        ruleId, "File must end with a newline",
                        0, maxOf(endOffset, 1), this,
                        edits = listOf(WEdit(endOffset, endOffset, "\n"))
                    )
                }
            }
        }
    }
}
