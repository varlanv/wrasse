package com.varlanv.wrasse.rules

import com.varlanv.wrasse.model.ChildBuffer
import com.varlanv.wrasse.model.WBufferedNodeRule
import com.varlanv.wrasse.model.WContext
import com.varlanv.wrasse.model.WNodeType
import com.varlanv.wrasse.model.WReporter
import com.varlanv.wrasse.model.WUninitializedRule
import com.varlanv.wrasse.model.WrasseRuleConfig

/**
 * Reports a parenthesized argument list that mixes named and positional arguments. Report-only:
 * `named-arguments` with `all-calls: true` is the fix. A trailing lambda is outside the list and
 * never counts. See [MixedArgumentsDecision].
 */
class MixedArgumentsRule : WUninitializedRule {
    override val id: String = "no-mixed-named-positional-arguments"

    override fun initRule(config: WrasseRuleConfig): WBufferedNodeRule {
        val ruleId = id
        return object : WBufferedNodeRule {
            override val id = ruleId
            override val config = config
            override val targetTypes = setOf(WNodeType.VALUE_ARGUMENT_LIST)

            override fun exitNode(
                ctx: WContext,
                children: ChildBuffer,
                reporter: WReporter,
            ) {
                var named = 0
                var positional = 0
                for (i in 0 until children.size) {
                    if (children.type(i) != WNodeType.VALUE_ARGUMENT) continue
                    if (MixedArgumentsDecision.isNamedArgument(ctx.sourceText, children.startOffset(i), children.endOffset(i))) {
                        named++
                    } else {
                        positional++
                    }
                }
                if (MixedArgumentsDecision.mixesNamedAndPositional(named, positional)) {
                    reporter.report(ruleId, MESSAGE, ctx.startOffset, ctx.endOffset, this)
                }
            }
        }
    }

    private companion object {
        const val MESSAGE = "Named and positional arguments must not be mixed in one call"
    }
}
