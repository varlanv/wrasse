package com.varlanv.wrasse.rules

import com.varlanv.wrasse.model.ChildBuffer
import com.varlanv.wrasse.model.WBufferedNodeRule
import com.varlanv.wrasse.model.WContext
import com.varlanv.wrasse.model.WNodeType
import com.varlanv.wrasse.model.WReporter
import com.varlanv.wrasse.model.WUninitializedRule
import com.varlanv.wrasse.model.WrasseRuleConfig

private val TARGET_TYPES = setOf(WNodeType.PROPERTY)

/** Property names must be camelCase, or SCREAMING_SNAKE_CASE for `const val` (see [PropertyNamingDecision]). */
class PropertyNamingRule : WUninitializedRule {
    override val id: String = "property-naming"

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

                val modifierIdx = children.firstChildOfType(WNodeType.MODIFIER_LIST)
                val modifierText = if (modifierIdx >= 0) children.textSpan(modifierIdx, ctx.sourceText) else ""
                val hasConst = WordScan.containsWord(modifierText, "const")
                val hasOverride = WordScan.containsWord(modifierText, "override")

                val hasCustomGetter = (0 until children.size).any { i ->
                    children.type(i) ==
                        WNodeType.PROPERTY_ACCESSOR &&
                        WordScan.containsWordFollowedBy(children.textSpan(i, ctx.sourceText), "get", '(')
                }

                val hasValKeyword = children.hasChildOfType(WNodeType.KW_VAL)
                val ancestors = ctx.ancestors
                val immediateParent = if (ancestors.size >= 1) ancestors.peekType() else null
                val grandparent = if (ancestors.size >= 2) ancestors.typeAt(ancestors.size - 2) else null

                val isTopLevelVal = hasValKeyword && immediateParent == WNodeType.FILE
                val isObjectMemberVal =
                    hasValKeyword &&
                        !hasOverride &&
                        immediateParent == WNodeType.CLASS_BODY &&
                        grandparent == WNodeType.OBJECT_DECLARATION

                val message = PropertyNamingDecision.decide(
                    identifierText,
                    hasConst,
                    hasCustomGetter,
                    isTopLevelVal,
                    isObjectMemberVal,
                ) ?: return
                reporter.report(ruleId, message, children.startOffset(idIdx), children.endOffset(idIdx), this)
            }
        }
    }
}
