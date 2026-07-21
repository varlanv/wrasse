package com.varlanv.wrasse.rules

import com.varlanv.wrasse.model.ChildBuffer
import com.varlanv.wrasse.model.WBufferedNodeRule
import com.varlanv.wrasse.model.WContext
import com.varlanv.wrasse.model.WNodeType
import com.varlanv.wrasse.model.WReporter
import com.varlanv.wrasse.model.WUninitializedRule
import com.varlanv.wrasse.model.WrasseRuleConfig

/** Package names must be all-lowercase dotted segments with no underscore (see [PackageNamingDecision]). */
class PackageNamingRule : WUninitializedRule {
    override val id: String = "package-naming"

    override fun initRule(config: WrasseRuleConfig): WBufferedNodeRule {
        val ruleId = id
        return object : WBufferedNodeRule {
            override val id = ruleId
            override val config = config
            override val targetTypes = setOf(WNodeType.PACKAGE_DIRECTIVE)

            override fun exitNode(ctx: WContext, children: ChildBuffer, reporter: WReporter) {
                val nameIdx = (0 until children.size).firstOrNull {
                        children.type(it) == WNodeType.DOT_QUALIFIED_EXPRESSION || children.type(it) == WNodeType.REFERENCE_EXPRESSION
                    }
                    ?: return
                val fqName = children.textSpan(nameIdx, ctx.sourceText).toString()
                val message = PackageNamingDecision.decide(fqName) ?: return
                reporter.report(ruleId, message, children.startOffset(nameIdx), children.endOffset(nameIdx), this)
            }
        }
    }
}
