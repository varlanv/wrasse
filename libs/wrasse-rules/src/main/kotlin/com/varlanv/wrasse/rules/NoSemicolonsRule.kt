package com.varlanv.wrasse.rules

import com.varlanv.wrasse.model.*

class NoSemicolonsRule : WUninitializedRule {
    override val id: String = "no-semicolons"
    override fun initRule(config: WrasseRuleConfig): WNodeRule {
        val ruleId = id
        return object : WNodeRule {
            override val id = ruleId
            override val config = config
            override val targetTypes: Set<WNodeType> = setOf(WNodeType.SEMICOLON)

            override fun visit(node: WNode, reporter: WReporter) {
                if (isRequiredSemicolon(node)) {
                    return
                }
                reporter.report(
                    WViolation(
                        ruleId = id,
                        message = "Unnecessary semicolon",
                        node = node,
                    ),
                    rule = this
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
    }
}
