package com.varlanv.wrasse.rules

import com.varlanv.wrasse.model.WContext
import com.varlanv.wrasse.model.WNodeRule
import com.varlanv.wrasse.model.WNodeType
import com.varlanv.wrasse.model.WReporter
import com.varlanv.wrasse.model.WUninitializedRule
import com.varlanv.wrasse.model.WrasseRuleConfig

/**
 * A `throw` constructing one of a fixed set of overly-generic exception types is reported (see
 * [TooGenericExceptionThrownDecision]) at the whole `throw` expression's own span. The thrown
 * expression's own callee is read directly off the `THROW` node's own text (stripping the
 * `throw` keyword, then the call-shape up to its first `(`) rather than descendant node
 * inspection, so no child buffering is needed; a non-call thrown expression (`throw e`) is never
 * a candidate.
 */
class TooGenericExceptionThrownRule : WUninitializedRule {
    override val id: String = "too-generic-exception-thrown"

    override fun initRule(config: WrasseRuleConfig): WNodeRule {
        val ruleId = id
        return object : WNodeRule {
            override val id = ruleId
            override val config = config
            override val targetTypes = setOf(WNodeType.THROW)

            override fun enterNode(ctx: WContext, reporter: WReporter): Boolean {
                val body = ctx.sourceText.subSequence(ctx.startOffset, ctx.endOffset).toString().trim().removePrefix("throw").trim()
                val facts = CallShapeText.parse(body) ?: return false
                val message = TooGenericExceptionThrownDecision.decide(facts.simpleName) ?: return false
                reporter.report(ruleId, message, ctx.startOffset, ctx.endOffset, this)
                return false
            }
        }
    }
}
