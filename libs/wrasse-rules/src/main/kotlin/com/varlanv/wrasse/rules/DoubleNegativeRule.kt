package com.varlanv.wrasse.rules

import com.varlanv.wrasse.model.WContext
import com.varlanv.wrasse.model.WNodeRule
import com.varlanv.wrasse.model.WNodeType
import com.varlanv.wrasse.model.WReporter
import com.varlanv.wrasse.model.WUninitializedRule
import com.varlanv.wrasse.model.WrasseRuleConfig

/** See [DoubleNegativeDecision]. Reports once, at the outermost `!` of a chain. */
class DoubleNegativeRule : WUninitializedRule {
    override val id: String = "double-negative"

    override fun initRule(config: WrasseRuleConfig): WNodeRule {
        val ruleId = id
        return object : WNodeRule {
            override val id = ruleId
            override val config = config
            override val targetTypes = setOf(WNodeType.PREFIX_EXPRESSION)

            override fun enterNode(ctx: WContext, reporter: WReporter): Boolean {
                if (ctx.sourceText[ctx.startOffset] != '!') return false
                if (isNestedInsideExclamationPrefix(ctx)) return false

                val chainLength = DoubleNegativeDecision.exclamationChainLength(
                    ctx.sourceText,
                    ctx.startOffset,
                    ctx.endOffset,
                )
                val message = DoubleNegativeDecision.decide(chainLength) ?: return false
                reporter.report(ruleId, message, ctx.startOffset, ctx.endOffset, this)
                return false
            }

            private fun isNestedInsideExclamationPrefix(ctx: WContext): Boolean {
                val ancestors = ctx.ancestors
                if (ancestors.isEmpty || ancestors.peekType() != WNodeType.PREFIX_EXPRESSION) return false
                return ctx.sourceText[ancestors.peekStartOffset()] == '!'
            }
        }
    }
}
