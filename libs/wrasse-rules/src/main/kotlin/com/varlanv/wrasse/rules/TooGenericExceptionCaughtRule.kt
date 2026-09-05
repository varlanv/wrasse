package com.varlanv.wrasse.rules

import com.varlanv.wrasse.model.WContext
import com.varlanv.wrasse.model.WNodeRule
import com.varlanv.wrasse.model.WNodeType
import com.varlanv.wrasse.model.WReporter
import com.varlanv.wrasse.model.WUninitializedRule
import com.varlanv.wrasse.model.WrasseRuleConfig

/**
 * A `catch` clause declaring one of a fixed set of overly-generic exception types is reported
 * (see [TooGenericExceptionCaughtDecision]) at the catch parameter's own name, unless the
 * parameter's own name matches [AllowedExceptionName] (an explicit "yes, I mean to ignore this"
 * signal). The catch parameter's own text is read directly off `VALUE_PARAMETER_LIST`'s span, so
 * no child buffering is needed.
 */
class TooGenericExceptionCaughtRule : WUninitializedRule {
    override val id: String = "too-generic-exception-caught"

    override fun initRule(config: WrasseRuleConfig): WNodeRule {
        val ruleId = id
        return object : WNodeRule {
            override val id = ruleId
            override val config = config
            override val targetTypes = setOf(WNodeType.VALUE_PARAMETER_LIST)

            override fun enterNode(ctx: WContext, reporter: WReporter): Boolean {
                if (ctx.ancestors.peekType() != WNodeType.CATCH) return false
                val facts = CatchParameterText.parse(
                    ctx.sourceText.subSequence(ctx.startOffset, ctx.endOffset),
                ) ?: return false
                val message = TooGenericExceptionCaughtDecision.decide(facts.typeText, facts.name) ?: return false
                reporter.report(
                    ruleId,
                    message,
                    ctx.startOffset + facts.nameStart,
                    ctx.startOffset + facts.nameEnd,
                    this,
                )
                return false
            }
        }
    }
}
