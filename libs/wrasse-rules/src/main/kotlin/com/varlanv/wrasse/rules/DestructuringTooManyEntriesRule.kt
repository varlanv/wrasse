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

private val TARGET_TYPES = setOf(WNodeType.DESTRUCTURING_DECLARATION)

/**
 * Reports a destructuring declaration with too many entries (see
 * [DestructuringTooManyEntriesDecision]). Destructuring declarations never nest inside one
 * another, so a single [WBufferedNodeRule] on the declaration itself needs no frame stack.
 */
class DestructuringTooManyEntriesRule : WUninitializedRule {
    override val id: String = "destructuring-declaration-with-too-many-entries"
    override val options: List<WRuleOptionSpec> = listOf(
        WRuleOptionSpec.Optional(
            name = THRESHOLD,
            type = WRuleOptionType.INTEGER,
            description = "Highest number of entries a destructuring declaration may have",
            default = WRuleOptionValue.Num(DestructuringTooManyEntriesDecision.DEFAULT_THRESHOLD.toLong()),
            minimum = 1,
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
                var count = 0
                for (i in 0 until children.size) {
                    if (children.type(i) == WNodeType.DESTRUCTURING_DECLARATION_ENTRY) count++
                }
                val message = DestructuringTooManyEntriesDecision.decide(count, threshold) ?: return
                reporter.report(ruleId, message, ctx.startOffset, ctx.endOffset, this)
            }
        }
    }

    private companion object {
        const val THRESHOLD = "threshold"
    }
}
