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
 * An `if`/`else` whose condition is exactly `<identifier> is T` (or `!is T`) and whose branches
 * are exactly the identifier on one side and `null` on the other is reported (see
 * [SafeCastDecision]) at the whole `if` expression's span.
 */
class SafeCastRule : WUninitializedRule {
    override val id: String = "safe-cast"

    override fun initRule(config: WrasseRuleConfig): WBufferedNodeRule {
        val ruleId = id
        return object : WBufferedNodeRule {
            override val id = ruleId
            override val config = config
            override val targetTypes = setOf(WNodeType.IF, WNodeType.IS_EXPRESSION)

            private val pendingIfs = mutableListOf<PendingIf>()

            override fun enterNode(ctx: WContext, reporter: WReporter): Boolean {
                if (ctx.type == WNodeType.IF) pendingIfs.add(PendingIf())
                return true
            }

            override fun exitNode(ctx: WContext, children: ChildBuffer, reporter: WReporter) {
                when (ctx.type) {
                    WNodeType.IS_EXPRESSION -> recordIsExpression(ctx, children)
                    WNodeType.IF -> finalizeIf(ctx, children, reporter)
                    else -> {}
                }
            }

            private fun recordIsExpression(ctx: WContext, children: ChildBuffer) {
                if (ctx.ancestors.peekType() != WNodeType.CONDITION) return
                if (ctx.ancestors.size < 2 || ctx.ancestors.typeAt(ctx.ancestors.size - 2) != WNodeType.IF) return
                val pending = pendingIfs.lastOrNull() ?: return

                val sig = significantIndices(children)
                if (sig.size < 2) return
                val leftIdx = sig[0]
                val opIdx = sig[1]
                if (children.type(leftIdx) != WNodeType.REFERENCE_EXPRESSION) return
                if (children.type(opIdx) != WNodeType.OPERATION_REFERENCE) return

                pending.identifier = children.textSpan(leftIdx, ctx.sourceText).toString()
                pending.negated = children.textSpan(opIdx, ctx.sourceText).toString().startsWith("!")
                pending.hasIsCondition = true
            }

            private fun finalizeIf(ctx: WContext, children: ChildBuffer, reporter: WReporter) {
                val pending = pendingIfs.removeAt(pendingIfs.size - 1)
                val identifier = pending.identifier
                if (!pending.hasIsCondition || identifier == null) return

                val thenIdx = children.firstChildOfType(WNodeType.THEN)
                val elseIdx = children.firstChildOfType(WNodeType.ELSE)
                if (thenIdx < 0 || elseIdx < 0) return

                val thenText = singleStatementText(children.textSpan(thenIdx, ctx.sourceText).toString())
                val elseText = singleStatementText(children.textSpan(elseIdx, ctx.sourceText).toString())

                val message = SafeCastDecision.decide(identifier, pending.negated, thenText, elseText) ?: return
                reporter.report(ruleId, message, ctx.startOffset, ctx.endOffset, this)
            }

            private fun singleStatementText(text: String): String {
                val trimmed = text.trim()
                if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
                    return trimmed.substring(1, trimmed.length - 1).trim()
                }
                return trimmed
            }

            private fun significantIndices(children: ChildBuffer): List<Int> {
                val result = ArrayList<Int>(children.size)
                for (i in 0 until children.size) if (!children.type(i).isWhitespaceOrComment) result.add(i)
                return result
            }
        }
    }

    private class PendingIf {
        var identifier: String? = null
        var negated: Boolean = false
        var hasIsCondition: Boolean = false
    }
}
