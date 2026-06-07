package com.varlanv.wrasse.rules

import com.varlanv.wrasse.config.WrasseRuleToggle
import com.varlanv.wrasse.model.*

class NoSemicolonsRule(private val config: WrasseRuleToggle) : WRule {
    override val id: String = "no-semicolons"

    override fun check(file: WFile, violations: MutableList<WViolation>) {
        if (!config.enabled) return
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
    }

    private fun isRequiredSemicolon(node: WNode): Boolean = when {
        node.isInsideNodeOfType(WNodeType.FOR) -> true
        node.isInsideNodeOfType(WNodeType.ENUM_ENTRY) -> true
        isSeparator(node) -> true
        else -> false
    }

    private fun isSeparator(node: WNode): Boolean {
        val nextLeaf = node.nextLeaf() ?: return false
        if (nextLeaf.type != WNodeType.WHITE_SPACE) return true
        if (nextLeaf.isNewline) return false
        return node.nextCodeLeaf() != null
    }
}
