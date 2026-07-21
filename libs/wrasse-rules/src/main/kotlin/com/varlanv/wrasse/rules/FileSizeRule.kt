package com.varlanv.wrasse.rules

import com.varlanv.wrasse.model.WContext
import com.varlanv.wrasse.model.WFileRule
import com.varlanv.wrasse.model.WReporter
import com.varlanv.wrasse.model.WUninitializedRule
import com.varlanv.wrasse.model.WrasseRuleConfig

/** Reports a file with too many lines (see [FileSizeDecision]). */
class FileSizeRule : WUninitializedRule {
    override val id: String = "file-size"

    override fun initRule(config: WrasseRuleConfig): WFileRule {
        val ruleId = id
        return object : WFileRule {
            override val id = ruleId
            override val config = config

            override fun visit(ctx: WContext, reporter: WReporter) {
                var lines = 1
                val text = ctx.sourceText
                for (i in 0 until text.length) {
                    if (text[i] == '\n') lines++
                }
                val message = FileSizeDecision.decide(lines) ?: return
                reporter.report(ruleId, message, 0, 0, this)
            }
        }
    }
}
