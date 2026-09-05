package com.varlanv.wrasse.rules

import com.varlanv.wrasse.model.ChildBuffer
import com.varlanv.wrasse.model.WBufferedNodeRule
import com.varlanv.wrasse.model.WContext
import com.varlanv.wrasse.model.WNodeStack
import com.varlanv.wrasse.model.WNodeType
import com.varlanv.wrasse.model.WReporter
import com.varlanv.wrasse.model.WUninitializedRule
import com.varlanv.wrasse.model.WrasseRuleConfig

/**
 * See [LambdaParameterNamingDecision]. A plain lambda parameter is checked directly on its own
 * `VALUE_PARAMETER` exit; a destructured one (`{ (a, b) -> ... }`) has no `IDENTIFIER` child of
 * its own `VALUE_PARAMETER` at all — each `DESTRUCTURING_DECLARATION_ENTRY` is checked
 * independently instead, since that is where the destructured component's own name actually lives.
 */
class LambdaParameterNamingRule : WUninitializedRule {
    override val id: String = "lambda-parameter-naming"

    override fun initRule(config: WrasseRuleConfig): WBufferedNodeRule {
        val ruleId = id
        return object : WBufferedNodeRule {
            override val id = ruleId
            override val config = config
            override val targetTypes = setOf(WNodeType.VALUE_PARAMETER, WNodeType.DESTRUCTURING_DECLARATION_ENTRY)

            override fun exitNode(
                ctx: WContext,
                children: ChildBuffer,
                reporter: WReporter,
            ) {
                when (ctx.type) {
                    WNodeType.VALUE_PARAMETER -> checkPlainParameter(ctx, children, reporter)
                    WNodeType.DESTRUCTURING_DECLARATION_ENTRY -> checkDestructuredEntry(ctx, children, reporter)
                    else -> {}
                }
            }

            private fun checkPlainParameter(
                ctx: WContext,
                children: ChildBuffer,
                reporter: WReporter,
            ) {
                if (!isOwnedByLambda(ctx.ancestors)) return
                val idIdx = children.firstChildOfType(WNodeType.IDENTIFIER)
                if (idIdx < 0) return
                report(
                    children.textSpan(idIdx, ctx.sourceText),
                    children.startOffset(idIdx),
                    children.endOffset(idIdx),
                    reporter,
                )
            }

            private fun checkDestructuredEntry(
                ctx: WContext,
                children: ChildBuffer,
                reporter: WReporter,
            ) {
                val ancestors = ctx.ancestors
                if (ancestors.size < 4) return
                if (ancestors.peekType() != WNodeType.DESTRUCTURING_DECLARATION) return
                if (ancestors.typeAt(ancestors.size - 2) != WNodeType.VALUE_PARAMETER) return
                if (ancestors.typeAt(ancestors.size - 3) != WNodeType.VALUE_PARAMETER_LIST) return
                if (ancestors.typeAt(ancestors.size - 4) != WNodeType.FUNCTION_LITERAL) return
                val idIdx = children.firstChildOfType(WNodeType.IDENTIFIER)
                if (idIdx < 0) return
                report(
                    children.textSpan(idIdx, ctx.sourceText),
                    children.startOffset(idIdx),
                    children.endOffset(idIdx),
                    reporter,
                )
            }

            private fun isOwnedByLambda(ancestors: WNodeStack): Boolean {
                if (ancestors.peekType() != WNodeType.VALUE_PARAMETER_LIST || ancestors.size < 2) return false
                return ancestors.typeAt(ancestors.size - 2) == WNodeType.FUNCTION_LITERAL
            }

            private fun report(
                name: CharSequence,
                start: Int,
                end: Int,
                reporter: WReporter,
            ) {
                val message = LambdaParameterNamingDecision.decide(name) ?: return
                reporter.report(ruleId, message, start, end, this)
            }
        }
    }
}
