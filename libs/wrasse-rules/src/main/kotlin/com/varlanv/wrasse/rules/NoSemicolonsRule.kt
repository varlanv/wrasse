package com.varlanv.wrasse.rules

import com.varlanv.wrasse.config.WrasseRuleToggle
import com.varlanv.wrasse.model.*

class NoSemicolonsRule(private val config: WrasseRuleToggle) : NodeVisitorWRule {
    override val id: String = "no-semicolons"
    override val targetTypes: Set<WNodeType> = setOf(WNodeType.SEMICOLON)

    override fun visit(node: WNode, violations: MutableCollection<WViolation>) {
        if (isRequiredSemicolon(node)) return
        violations.add(
            WViolation(
                ruleId = id,
                message = "Unnecessary semicolon",
                node = node,
            )
        )
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
