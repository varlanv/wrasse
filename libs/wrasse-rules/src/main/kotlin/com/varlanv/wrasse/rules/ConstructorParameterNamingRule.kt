package com.varlanv.wrasse.rules

import com.varlanv.wrasse.model.ChildBuffer
import com.varlanv.wrasse.model.WBufferedNodeRule
import com.varlanv.wrasse.model.WContext
import com.varlanv.wrasse.model.WNodeType
import com.varlanv.wrasse.model.WReporter
import com.varlanv.wrasse.model.WUninitializedRule
import com.varlanv.wrasse.model.WrasseRuleConfig

/** See [ConstructorParameterNamingDecision]. Only a primary/secondary constructor's own value parameters are candidates. */
class ConstructorParameterNamingRule : WUninitializedRule {
    override val id: String = "constructor-parameter-naming"

    override fun initRule(config: WrasseRuleConfig): WBufferedNodeRule {
        val ruleId = id
        return object : WBufferedNodeRule {
            override val id = ruleId
            override val config = config
            override val targetTypes = setOf(WNodeType.VALUE_PARAMETER)

            override fun exitNode(ctx: WContext, children: ChildBuffer, reporter: WReporter) {
                if (!isConstructorParameter(ctx)) return
                val idIdx = children.firstChildOfType(WNodeType.IDENTIFIER)
                if (idIdx < 0) return

                val modifierIdx = children.firstChildOfType(WNodeType.MODIFIER_LIST)
                val modifierText = if (modifierIdx >= 0) children.textSpan(modifierIdx, ctx.sourceText) else ""
                val hasOverride = Regex("\\boverride\\b").containsMatchIn(modifierText)

                val message = ConstructorParameterNamingDecision.decide(children.textSpan(idIdx, ctx.sourceText), hasOverride) ?: return
                reporter.report(ruleId, message, children.startOffset(idIdx), children.endOffset(idIdx), this)
            }

            private fun isConstructorParameter(ctx: WContext): Boolean {
                val ancestors = ctx.ancestors
                if (ancestors.size < 2 || ancestors.peekType() != WNodeType.VALUE_PARAMETER_LIST) return false
                val owner = ancestors.typeAt(ancestors.size - 2)
                return owner == WNodeType.PRIMARY_CONSTRUCTOR || owner == WNodeType.SECONDARY_CONSTRUCTOR
            }
        }
    }
}
