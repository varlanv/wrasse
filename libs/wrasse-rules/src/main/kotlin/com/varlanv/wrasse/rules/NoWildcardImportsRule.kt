package com.varlanv.wrasse.rules

import com.varlanv.wrasse.model.*

class NoWildcardImportsRule : WUninitializedRule {
    override val id: String = "no-wildcard-imports"

    override fun initRule(config: WrasseRuleConfig): WNodeRule {
        val ruleId = id
        return object : WNodeRule {
            override val id = ruleId
            override val config = config
            override val targetTypes: Set<WNodeType> = setOf(WNodeType.IMPORT_DIRECTIVE)

            override fun visit(node: WNode, reporter: WReporter) {
                if (node.children.any { it.type == WNodeType.MUL }) {
                    reporter.report(
                        WViolation(
                            ruleId = id,
                            message = "Replace wildcard import with explicit imports",
                            node = node,
                        ),
                        rule = this
                    )
                }
            }
        }
    }
}
