package com.varlanv.wrasse.rules

import com.varlanv.wrasse.model.ChildBuffer
import com.varlanv.wrasse.model.WBufferedNodeRule
import com.varlanv.wrasse.model.WContext
import com.varlanv.wrasse.model.WFileRule
import com.varlanv.wrasse.model.WNodeType
import com.varlanv.wrasse.model.WReporter
import com.varlanv.wrasse.model.WRule
import com.varlanv.wrasse.model.WUninitializedRuleGroup
import com.varlanv.wrasse.model.WrasseRuleConfig

/**
 * Fuses `function-name-max-length`/`function-name-min-length` into one decision-maker: both
 * inspect the exact same `FUN` name identifier and its `override`/`operator` modifiers, so one
 * buffered walk answers both instead of two rules independently re-deriving the same modifier
 * check.
 */
class FunctionNameLengthEngine : WUninitializedRuleGroup {
    override val ids: Set<String> = setOf(MAX_LENGTH_ID, MIN_LENGTH_ID)

    override fun initGroup(configs: Map<String, WrasseRuleConfig>): WRule {
        val maxRule = configs[MAX_LENGTH_ID]?.let { ReportFacade(MAX_LENGTH_ID, it) }
        val minRule = configs[MIN_LENGTH_ID]?.let { ReportFacade(MIN_LENGTH_ID, it) }

        return object : WBufferedNodeRule {
            override val id = ENGINE_ID
            override val config = configs.values.first()
            override val targetTypes = setOf(WNodeType.FUN)

            override fun exitNode(
                ctx: WContext,
                children: ChildBuffer,
                reporter: WReporter,
            ) {
                val idIdx = children.firstChildOfType(WNodeType.IDENTIFIER)
                if (idIdx < 0) return
                val name = IdentifierCasing.unquote(children.textSpan(idIdx, ctx.sourceText))
                val modifierIdx = children.firstChildOfType(WNodeType.MODIFIER_LIST)
                val modifiersText = if (modifierIdx >= 0) children.textSpan(modifierIdx, ctx.sourceText) else ""
                val isOverride = WordScan.containsWord(modifiersText, "override")
                val isOperator = WordScan.containsWord(modifiersText, "operator")

                if (minRule != null) {
                    FunctionNameLengthDecision.decideMin(name, isOverride, isOperator)?.let {
                        reporter.report(
                            MIN_LENGTH_ID,
                            it,
                            children.startOffset(idIdx),
                            children.endOffset(idIdx),
                            minRule,
                        )
                    }
                }
                if (maxRule != null) {
                    FunctionNameLengthDecision.decideMax(name, isOverride, isOperator)?.let {
                        reporter.report(
                            MAX_LENGTH_ID,
                            it,
                            children.startOffset(idIdx),
                            children.endOffset(idIdx),
                            maxRule,
                        )
                    }
                }
            }
        }
    }

    private class ReportFacade(override val id: String, override val config: WrasseRuleConfig) : WFileRule {
        override fun visit(ctx: WContext, reporter: WReporter) {}
    }

    private companion object {
        const val ENGINE_ID = "function-name-length-engine"
        const val MAX_LENGTH_ID = "function-name-max-length"
        const val MIN_LENGTH_ID = "function-name-min-length"
    }
}
