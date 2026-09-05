package com.varlanv.wrasse.rules

import com.varlanv.wrasse.model.WContext
import com.varlanv.wrasse.model.WLeafRule
import com.varlanv.wrasse.model.WNodeType
import com.varlanv.wrasse.model.WReporter
import com.varlanv.wrasse.model.WUninitializedRule
import com.varlanv.wrasse.model.WrasseRuleConfig

private val TARGET_TYPES = setOf(WNodeType.INTEGER_LITERAL, WNodeType.FLOAT_LITERAL)

/**
 * An integer or float literal whose digit run is long enough to be hard to scan at a glance gains
 * underscore separators (`1000000` becomes `1_000_000`). See [LongNumericalValuesDecision] for the
 * exact threshold, grouping, and the literals this rule declines to touch.
 */
class LongNumericalValuesRule : WUninitializedRule {
    override val id: String = "long-numerical-values"
    override val canAutofix: Boolean = true

    override fun initRule(config: WrasseRuleConfig): WLeafRule {
        val ruleId = id
        return object : WLeafRule {
            override val id = ruleId
            override val config = config
            override val targetTypes = TARGET_TYPES

            override fun visitLeaf(ctx: WContext, reporter: WReporter) {
                val text = ctx.leafString() ?: return
                val edit =
                    if (ctx.type == WNodeType.INTEGER_LITERAL) {
                        LongNumericalValuesDecision.decideInteger(text, ctx.startOffset)
                    } else {
                        LongNumericalValuesDecision.decideFloat(text, ctx.startOffset)
                    } ?: return
                reporter.report(
                    ruleId,
                    LongNumericalValuesDecision.MESSAGE,
                    ctx.startOffset,
                    ctx.endOffset,
                    this,
                    edits = listOf(edit),
                )
            }
        }
    }
}
