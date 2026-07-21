package com.varlanv.wrasse.rules

import com.varlanv.wrasse.model.ChildBuffer
import com.varlanv.wrasse.model.WBufferedNodeRule
import com.varlanv.wrasse.model.WContext
import com.varlanv.wrasse.model.WNodeType
import com.varlanv.wrasse.model.WReporter
import com.varlanv.wrasse.model.WUninitializedRule
import com.varlanv.wrasse.model.WrasseRuleConfig
import com.varlanv.wrasse.model.isWhitespaceOrComment

/**
 * A lambda declaring its own single value parameter explicitly as `it` (`list.map { it -> it.x }`)
 * has that declaration removed, leaving Kotlin's implicit `it` to name the same slot.
 *
 * Matched only when the lambda's own `VALUE_PARAMETER_LIST` has exactly one `VALUE_PARAMETER`
 * whose name is the literal identifier `it` (a backtick-quoted `` `it` `` never matches — see
 * [ExplicitItLambdaParameterDecision] KDoc's own-scope note); a multi-parameter lambda where one
 * parameter happens to be named `it` is out of scope entirely (a different redundancy shape, not
 * this rule's). Never autofixed when that parameter carries an explicit type reference, or when a
 * comment sits anywhere between the lambda's own `{` and its `->` — see
 * [ExplicitItLambdaParameterDecision] for why.
 */
class ExplicitItLambdaParameterRule : WUninitializedRule {
    override val id: String = "explicit-it-lambda-parameter"
    override val canAutofix: Boolean = true

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
                    WNodeType.FUNCTION_LITERAL -> finalizeLiteral(ctx, children, reporter)
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
                if (identifierIdx < 0 || !children.textSpan(identifierIdx, ctx.sourceText).contentEquals("it")) return
                pending.itParamSeen = true
                pending.itParamHasType = children.hasChildOfType(WNodeType.COLON)
            }

            private fun finalizeLiteral(ctx: WContext, children: ChildBuffer, reporter: WReporter) {
                val pending = pendingLiterals.removeAt(pendingLiterals.size - 1)
                if (pending.paramCount != 1 || !pending.itParamSeen) return

                val lbraceIdx = children.firstChildOfType(WNodeType.LBRACE)
                val vpListIdx = children.firstChildOfType(WNodeType.VALUE_PARAMETER_LIST)
                val arrowIdx = children.firstChildOfType(WNodeType.ARROW)
                if (lbraceIdx < 0 || vpListIdx < 0 || arrowIdx < 0) return

                val hasComment = hasCommentBetween(children, lbraceIdx + 1, arrowIdx)
                val verdict =
                ExplicitItLambdaParameterDecision
                    .decide(
                        vpListStart = children.startOffset(vpListIdx),
                        lbraceEnd = children.endOffset(lbraceIdx),
                        arrowEnd = children.endOffset(arrowIdx),
                        hasType = pending.itParamHasType,
                        hasComment = hasComment,
                    )
                reporter.report(ruleId, verdict.message, verdict.reportStart, verdict.reportEnd, this, edits = verdict.edits)
            }

            private fun hasCommentBetween(children: ChildBuffer, from: Int, until: Int): Boolean {
                for (i in from until until) {
                    val type = children.type(i)
                    if (type.isWhitespaceOrComment && type != WNodeType.WHITE_SPACE) return true
                }
                return false
            }
        }
    }

    private class PendingLiteral {
        var paramCount = 0
        var itParamSeen = false
        var itParamHasType = false
    }
}
