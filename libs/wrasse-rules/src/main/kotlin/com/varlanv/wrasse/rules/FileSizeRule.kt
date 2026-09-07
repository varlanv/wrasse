package com.varlanv.wrasse.rules

import com.varlanv.wrasse.model.WContext
import com.varlanv.wrasse.model.WFileRule
import com.varlanv.wrasse.model.WReporter
import com.varlanv.wrasse.model.WRuleOptionSpec
import com.varlanv.wrasse.model.WRuleOptionType
import com.varlanv.wrasse.model.WRuleOptionValue
import com.varlanv.wrasse.model.WUninitializedRule
import com.varlanv.wrasse.model.WrasseRuleConfig

/** Reports a file with too many lines (see [FileSizeDecision]). */
class FileSizeRule : WUninitializedRule {
    override val id: String = "file-size"
    override val options: List<WRuleOptionSpec> = listOf(
        WRuleOptionSpec.Optional(
            name = THRESHOLD,
            type = WRuleOptionType.INTEGER,
            description = "Highest number of lines a file may have",
            default = WRuleOptionValue.Num(FileSizeDecision.DEFAULT_THRESHOLD.toLong()),
            minimum = 1,
            maximum = Int.MAX_VALUE.toLong(),
        ),
    )

    override fun initRule(config: WrasseRuleConfig): WFileRule {
        val ruleId = id
        val threshold = config.options.integer(THRESHOLD).toInt()
        return object : WFileRule {
            override val id = ruleId
            override val config = config

            override fun visit(ctx: WContext, reporter: WReporter) {
                var lines = 1
                val text = ctx.sourceText
                for (i in 0 until text.length) {
                    if (text[i] == '\n') lines++
                }
                val message = FileSizeDecision.decide(lines, threshold) ?: return
                reporter.report(ruleId, message, 0, 0, this)
            }
        }
    }

    private companion object {
        const val THRESHOLD = "threshold"
    }
}
