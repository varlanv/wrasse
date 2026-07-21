package com.varlanv.wrasse.rules

import com.varlanv.wrasse.model.ChildBuffer
import com.varlanv.wrasse.model.WBufferedNodeRule
import com.varlanv.wrasse.model.WContext
import com.varlanv.wrasse.model.WNodeType
import com.varlanv.wrasse.model.WReporter
import com.varlanv.wrasse.model.WUninitializedRule
import com.varlanv.wrasse.model.WrasseRuleConfig

/**
 * A lambda declaring more than one parameter, one of them named `it`, is reported (see
 * [ExplicitItLambdaMultipleParametersDecision]) — never fixed, since choosing a meaningful
 * replacement name is an authored decision.
 */
class ExplicitItLambdaMultipleParametersRule : WUninitializedRule {
    override val id: String = "explicit-it-lambda-multiple-parameters"

    override fun initRule(config: WrasseRuleConfig): WBufferedNodeRule {
        val ruleId = id
        return object : WBufferedNodeRule {
            override val id = ruleId
            override val config = config
            override val targetTypes = setOf(WNodeType.FUNCTION_LITERAL, WNodeType.VALUE_PARAMETER)

            private val pendingLiterals = mutableListOf<PendingLiteral>()

            override fun enterNode(ctx: WContext, reporter: WReporter): Boolean {
                if (ctx.type == WNodeType.FUNCTION_LITERAL) {
                    pendingLiterals.add(PendingLiteral())
                }
                return true
            }

            override fun exitNode(ctx: WContext, children: ChildBuffer, reporter: WReporter) {
                when (ctx.type) {
                    WNodeType.VALUE_PARAMETER -> recordParam(ctx, children)
                    WNodeType.FUNCTION_LITERAL -> finalizeLiteral(ctx, reporter)
                    else -> {}
                }
            }

            private fun recordParam(ctx: WContext, children: ChildBuffer) {
                if (ctx.ancestors.peekType() != WNodeType.VALUE_PARAMETER_LIST) return
                if (ctx.ancestors.size < 2 || ctx.ancestors.typeAt(ctx.ancestors.size - 2) != WNodeType.FUNCTION_LITERAL) {
                    return
                }
                val pending = pendingLiterals.lastOrNull() ?: return
                pending.paramCount++
                val identifierIdx = children.firstChildOfType(WNodeType.IDENTIFIER)
                if (identifierIdx < 0) return
                if (IdentifierCasing.unquote(children.textSpan(identifierIdx, ctx.sourceText)) == "it") {
                    pending.hasItParam = true
                }
            }

            private fun finalizeLiteral(ctx: WContext, reporter: WReporter) {
                val pending = pendingLiterals.removeAt(pendingLiterals.size - 1)
                val message = ExplicitItLambdaMultipleParametersDecision.decide(pending.paramCount, pending.hasItParam) ?: return
                reporter.report(ruleId, message, ctx.startOffset, ctx.endOffset, this)
            }
        }
    }

    private class PendingLiteral {
        var paramCount = 0
        var hasItParam = false
    }
}
