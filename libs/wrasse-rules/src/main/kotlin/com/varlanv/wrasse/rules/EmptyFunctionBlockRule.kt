package com.varlanv.wrasse.rules

import com.varlanv.wrasse.model.ChildBuffer
import com.varlanv.wrasse.model.WBufferedNodeRule
import com.varlanv.wrasse.model.WContext
import com.varlanv.wrasse.model.WNodeType
import com.varlanv.wrasse.model.WReporter
import com.varlanv.wrasse.model.WUninitializedRule
import com.varlanv.wrasse.model.WrasseRuleConfig

/**
 * Reports a function whose block body ([EmptyBlockCheck]) is empty. Exempt: an `open` function
 * (a subclass may still rely on the no-op default), and any member function declared directly
 * inside an interface (a common "optional callback with a no-op default" idiom) — both matching
 * the upstream rule this id derives from.
 */
class EmptyFunctionBlockRule : WUninitializedRule {
    override val id: String = "empty-function-block"

    override fun initRule(config: WrasseRuleConfig): WBufferedNodeRule {
        val ruleId = id
        return object : WBufferedNodeRule {
            override val id = ruleId
            override val config = config
            override val targetTypes = setOf(WNodeType.FUN)

            override fun exitNode(ctx: WContext, children: ChildBuffer, reporter: WReporter) {
                val blockIdx = children.firstChildOfType(WNodeType.BLOCK)
                if (blockIdx < 0) return
                if (!EmptyBlockCheck.isEmptySpan(ctx.sourceText, children.startOffset(blockIdx), children.endOffset(blockIdx))) return

                val modifierIdx = children.firstChildOfType(WNodeType.MODIFIER_LIST)
                val modifierText = if (modifierIdx >= 0) children.textSpan(modifierIdx, ctx.sourceText) else ""
                if (Regex("\\bopen\\b").containsMatchIn(modifierText)) return
                if (isInterfaceMember(ctx)) return

                reporter
                    .report(
                        ruleId,
                        "Empty function block detected. Empty blocks of code serve no purpose and should be removed",
                        children.startOffset(blockIdx),
                        children.endOffset(blockIdx),
                        this,
                    )
            }

            private fun isInterfaceMember(ctx: WContext): Boolean {
                val ancestors = ctx.ancestors
                if (ancestors.size < 2) return false
                if (ancestors.peekType() != WNodeType.CLASS_BODY) return false
                if (ancestors.typeAt(ancestors.size - 2) != WNodeType.CLASS) return false
                val classStart = ancestors.startOffsetAt(ancestors.size - 2)
                val classBodyStart = ancestors.startOffsetAt(ancestors.size - 1)
                return Regex("\\binterface\\b").containsMatchIn(ctx.sourceText.subSequence(classStart, classBodyStart))
            }
        }
    }
}
