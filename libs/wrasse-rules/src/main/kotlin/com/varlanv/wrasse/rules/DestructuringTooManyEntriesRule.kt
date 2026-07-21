package com.varlanv.wrasse.rules

import com.varlanv.wrasse.model.ChildBuffer
import com.varlanv.wrasse.model.WBufferedNodeRule
import com.varlanv.wrasse.model.WContext
import com.varlanv.wrasse.model.WNodeType
import com.varlanv.wrasse.model.WReporter
import com.varlanv.wrasse.model.WUninitializedRule
import com.varlanv.wrasse.model.WrasseRuleConfig

/**
 * Reports a destructuring declaration with too many entries (see
 * [DestructuringTooManyEntriesDecision]). Destructuring declarations never nest inside one
 * another, so a single [WBufferedNodeRule] on the declaration itself needs no frame stack.
 */
class DestructuringTooManyEntriesRule : WUninitializedRule {
    override val id: String = "destructuring-declaration-with-too-many-entries"

    override fun initRule(config: WrasseRuleConfig): WBufferedNodeRule {
        val ruleId = id
        return object : WBufferedNodeRule {
            override val id = ruleId
            override val config = config
            override val targetTypes = setOf(WNodeType.DESTRUCTURING_DECLARATION)

            override fun exitNode(ctx: WContext, children: ChildBuffer, reporter: WReporter) {
                var count = 0
                for (i in 0 until children.size) {
                    if (children.type(i) == WNodeType.DESTRUCTURING_DECLARATION_ENTRY) count++
                }
                val message = DestructuringTooManyEntriesDecision.decide(count) ?: return
                reporter.report(ruleId, message, ctx.startOffset, ctx.endOffset, this)
            }
        }
    }
}
