package com.varlanv.wrasse.rules

import com.varlanv.wrasse.config.WrasseConfig
import com.varlanv.wrasse.model.WFile
import com.varlanv.wrasse.model.WNode
import com.varlanv.wrasse.model.WNodeType
import com.varlanv.wrasse.model.WRule
import com.varlanv.wrasse.model.WViolation

class NoSemicolonsRule : WRule {
    override val id: String = "no-semicolons"

    override fun check(file: WFile, config: WrasseConfig): List<WViolation> {
        val ruleConfig = config.rules.noSemicolons
        if (!ruleConfig.enabled) return emptyList()

        val violations = mutableListOf<WViolation>()
        for (node in file.root.descendants()) {
            if (node.type != WNodeType.SEMICOLON) continue
            if (isRequiredSemicolon(node)) continue
            violations.add(
                WViolation(
                    ruleId = id,
                    message = "Unnecessary semicolon",
                    node = node,
                    severity = ruleConfig.severity,
                )
            )
        }
        return violations
    }

    private fun isRequiredSemicolon(node: WNode): Boolean {
        if (node.isInsideNodeOfType(WNodeType.FOR)) return true
        if (node.isInsideNodeOfType(WNodeType.ENUM_ENTRY)) return true
        return false
    }
}
