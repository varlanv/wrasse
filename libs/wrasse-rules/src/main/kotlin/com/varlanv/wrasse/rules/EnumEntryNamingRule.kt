package com.varlanv.wrasse.rules

import com.varlanv.wrasse.model.ChildBuffer
import com.varlanv.wrasse.model.WBufferedNodeRule
import com.varlanv.wrasse.model.WContext
import com.varlanv.wrasse.model.WNodeType
import com.varlanv.wrasse.model.WReporter
import com.varlanv.wrasse.model.WUninitializedRule
import com.varlanv.wrasse.model.WrasseRuleConfig

private val TARGET_TYPES = setOf(WNodeType.ENUM_ENTRY)

/** Enum entry names must be PascalCase or SCREAMING_SNAKE_CASE (see [EnumEntryNamingDecision]). */
class EnumEntryNamingRule : WUninitializedRule {
    override val id: String = "enum-entry-naming"

    override fun initRule(config: WrasseRuleConfig): WBufferedNodeRule {
        val ruleId = id
        return object : WBufferedNodeRule {
            override val id = ruleId
            override val config = config
            override val targetTypes = TARGET_TYPES

            override fun exitNode(
                ctx: WContext,
                children: ChildBuffer,
                reporter: WReporter,
            ) {
                val idIdx = children.firstChildOfType(WNodeType.IDENTIFIER)
                if (idIdx < 0) return
                val identifierText = children.textSpan(idIdx, ctx.sourceText)
                val message = EnumEntryNamingDecision.decide(identifierText) ?: return
                reporter.report(ruleId, message, children.startOffset(idIdx), children.endOffset(idIdx), this)
            }
        }
    }
}
