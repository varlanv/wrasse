package com.varlanv.wrasse.rules

import com.varlanv.wrasse.model.ChildBuffer
import com.varlanv.wrasse.model.WBufferedNodeRule
import com.varlanv.wrasse.model.WContext
import com.varlanv.wrasse.model.WNodeType
import com.varlanv.wrasse.model.WReporter
import com.varlanv.wrasse.model.WUninitializedRule
import com.varlanv.wrasse.model.WrasseRuleConfig

/**
 * A file whose `PACKAGE_DIRECTIVE` carries no dotted name is reported (see
 * [MissingPackageDeclarationDecision]) at offset 0. `PACKAGE_DIRECTIVE` always exists as a node
 * regardless of whether the file has a `package` statement; the same "does it have a name child"
 * check [PackageNamingRule] already uses tells them apart.
 */
class MissingPackageDeclarationRule : WUninitializedRule {
    override val id: String = "missing-package-declaration"

    override fun initRule(config: WrasseRuleConfig): WBufferedNodeRule {
        val ruleId = id
        return object : WBufferedNodeRule {
            override val id = ruleId
            override val config = config
            override val targetTypes = setOf(WNodeType.PACKAGE_DIRECTIVE)

            override fun exitNode(
                ctx: WContext,
                children: ChildBuffer,
                reporter: WReporter,
            ) {
                val hasName = (0 until children.size).any {
                    children.type(
                        it,
                    ) == WNodeType.DOT_QUALIFIED_EXPRESSION || children.type(it) == WNodeType.REFERENCE_EXPRESSION
                }
                val message = MissingPackageDeclarationDecision.decide(hasName) ?: return
                reporter.report(ruleId, message, 0, 0, this)
            }
        }
    }
}
