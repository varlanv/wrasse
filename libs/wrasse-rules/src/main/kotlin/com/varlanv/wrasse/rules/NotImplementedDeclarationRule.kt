package com.varlanv.wrasse.rules

import com.varlanv.wrasse.model.WContext
import com.varlanv.wrasse.model.WNodeRule
import com.varlanv.wrasse.model.WNodeType
import com.varlanv.wrasse.model.WReporter
import com.varlanv.wrasse.model.WUninitializedRule
import com.varlanv.wrasse.model.WrasseRuleConfig

private val TARGET_TYPES = setOf(WNodeType.THROW, WNodeType.CALL_EXPRESSION)

/**
 * `throw NotImplementedError(...)` (reported at the whole `throw`) and a `TODO(...)` call with
 * zero or one arguments (reported at the call) are both stub markers that should never reach
 * production (see [NotImplementedDeclarationDecision]). Both checks read the node's own raw text
 * directly (via [CallShapeText]) rather than descendant node inspection, so no child buffering is
 * needed.
 */
class NotImplementedDeclarationRule : WUninitializedRule {
    override val id: String = "not-implemented-declaration"

    override fun initRule(config: WrasseRuleConfig): WNodeRule {
        val ruleId = id
        return object : WNodeRule {
            override val id = ruleId
            override val config = config
            override val targetTypes = TARGET_TYPES

            override fun enterNode(ctx: WContext, reporter: WReporter): Boolean {
                when (ctx.type) {
                    WNodeType.THROW -> checkThrow(ctx, reporter)
                    WNodeType.CALL_EXPRESSION -> checkCall(ctx, reporter)
                    else -> {}
                }
                return false
            }

            private fun checkThrow(ctx: WContext, reporter: WReporter) {
                val body = ctx.sourceText
                    .subSequence(ctx.startOffset, ctx.endOffset)
                    .toString()
                    .trim()
                    .removePrefix("throw")
                    .trim()
                val facts = CallShapeText.parse(body) ?: return
                val message = NotImplementedDeclarationDecision.decideThrow(facts.simpleName) ?: return
                reporter.report(ruleId, message, ctx.startOffset, ctx.endOffset, this)
            }

            private fun checkCall(ctx: WContext, reporter: WReporter) {
                val text = ctx.sourceText.subSequence(ctx.startOffset, ctx.endOffset).toString()
                val facts = CallShapeText.parse(text) ?: return
                val message = NotImplementedDeclarationDecision.decideTodoCall(
                    facts.simpleName,
                    facts.argumentCount,
                ) ?: return
                reporter.report(ruleId, message, ctx.startOffset, ctx.endOffset, this)
            }
        }
    }
}
