package com.varlanv.wrasse.rules

import com.varlanv.wrasse.model.ChildBuffer
import com.varlanv.wrasse.model.WBufferedNodeRule
import com.varlanv.wrasse.model.WContext
import com.varlanv.wrasse.model.WNodeType
import com.varlanv.wrasse.model.WReporter
import com.varlanv.wrasse.model.WRuleOptionSpec
import com.varlanv.wrasse.model.WRuleOptionType
import com.varlanv.wrasse.model.WRuleOptionValue
import com.varlanv.wrasse.model.WUninitializedRule
import com.varlanv.wrasse.model.WrasseRuleConfig

private val TARGET_TYPES = setOf(WNodeType.PROPERTY)

/**
 * See [VariableNameMaxLengthDecision]. Targets every `PROPERTY` (top-level, member, object,
 * local — the same uniform scope the upstream rule this id derives from uses), a different axis
 * (length, not casing) than `property-naming` over the same node population.
 */
class VariableNameMaxLengthRule : WUninitializedRule {
    override val id: String = "variable-name-max-length"
    override val options: List<WRuleOptionSpec> = listOf(
        WRuleOptionSpec.Optional(
            name = THRESHOLD,
            type = WRuleOptionType.INTEGER,
            description = "Highest number of characters a property or variable name may have",
            default = WRuleOptionValue.Num(VariableNameMaxLengthDecision.DEFAULT_THRESHOLD.toLong()),
            minimum = 1,
            maximum = Int.MAX_VALUE.toLong(),
        ),
    )

    override fun initRule(config: WrasseRuleConfig): WBufferedNodeRule {
        val ruleId = id
        val threshold = config.options.integer(THRESHOLD).toInt()
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

                val modifierIdx = children.firstChildOfType(WNodeType.MODIFIER_LIST)
                val modifierText = if (modifierIdx >= 0) children.textSpan(modifierIdx, ctx.sourceText) else ""
                val hasOverride = WordScan.containsWord(modifierText, "override")

                val message = VariableNameMaxLengthDecision.decide(
                    children.textSpan(idIdx, ctx.sourceText),
                    hasOverride,
                    threshold,
                ) ?: return
                reporter.report(ruleId, message, children.startOffset(idIdx), children.endOffset(idIdx), this)
            }
        }
    }

    private companion object {
        const val THRESHOLD = "threshold"
    }
}
