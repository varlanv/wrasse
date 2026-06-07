package com.varlanv.wrasse.rules

import com.varlanv.wrasse.config.WrasseRuleToggle
import com.varlanv.wrasse.model.*

class NoSemicolonsRule(private val config: WrasseRuleToggle) : WRule {
    override val id: String = "no-semicolons"

    override fun check(file: WFile): List<WViolation> {
        if (!config.enabled) return emptyList()

        val violations = mutableListOf<WViolation>()
        for (node in file.root.descendants()) {
            if (node.type != WNodeType.SEMICOLON) continue
            if (isRequiredSemicolon(node)) continue
            violations.add(
                WViolation(
                    ruleId = id,
                    message = "Unnecessary semicolon",
                    node = node,
                    severity = config.severity,
                )
            )
        }
        return violations
    }

    private fun isRequiredSemicolon(node: WNode): Boolean {
        return if (node.isInsideNodeOfType(WNodeType.FOR)) {
            true
        } else if (node.isInsideNodeOfType(WNodeType.ENUM_ENTRY)) {
            true
        } else {
            false
        }
    }
}
