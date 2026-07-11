package com.varlanv.wrasse.rules

import com.varlanv.wrasse.model.*

class TrailingNewlineVisitor : WUninitializedRule {
    override val id: String = "trailing-newline"
    override fun initRule(config: WrasseRuleConfig): WFileRule {
        val ruleId = id
        return object : WFileRule {
            override val id = ruleId
            override val config = config

            override fun visit(file: WFile, reporter: WReporter) {
                val text = file.sourceText
                if (text.isEmpty() || text[text.length - 1] != '\n') {
                    reporter.report(
                        WViolation(
                            ruleId = id,
                            message = "File must end with a newline",
                            node = file.root,
                        ),
                        rule = this
                    )
                }
            }
        }
    }
}
