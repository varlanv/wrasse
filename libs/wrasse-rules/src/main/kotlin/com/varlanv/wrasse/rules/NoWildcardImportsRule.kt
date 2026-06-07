package com.varlanv.wrasse.rules

import com.varlanv.wrasse.config.WrasseRuleToggle
import com.varlanv.wrasse.model.*

class NoWildcardImportsRule(private val config: WrasseRuleToggle) : NodeVisitorWRule {
    override val id: String = "no-wildcard-imports"
    override val targetTypes: Set<WNodeType> = setOf(WNodeType.IMPORT_DIRECTIVE)

    override fun visit(node: WNode, violations: MutableCollection<WViolation>) {
        if (node.children.any { it.type == WNodeType.MUL }) {
            violations.add(
                WViolation(
                    ruleId = id,
                    message = "Replace wildcard import with explicit imports",
                    node = node,
                    severity = config.severity,
                )
            )
        }
    }
}
