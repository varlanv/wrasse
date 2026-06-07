package com.varlanv.wrasse.rules

import com.varlanv.wrasse.config.WrasseRuleToggle
import com.varlanv.wrasse.model.*

class TrailingNewlineRule(private val config: WrasseRuleToggle) : FileVisitorWRule {
    override val id: String = "trailing-newline"

    override fun visit(file: WFile, violations: MutableCollection<WViolation>) {
        val text = file.sourceText
        if (text.isEmpty() || text[text.length - 1] != '\n') {
            violations.add(
                WViolation(
                    ruleId = id,
                    message = "File must end with a newline",
                    node = file.root,
                    severity = config.severity,
                )
            )
        }
    }
}
