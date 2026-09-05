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
 * An `if` whose condition is exactly `<expr> != null`/`null != <expr>` with an `else` branch that
 * reduces to `null`, or `<expr> == null`/`null == <expr>` with a `then` branch that reduces to
 * `null`, is reported (see [UseLetDecision]) at the whole `if` expression's span. The non-null
 * side of the condition is never inspected — mirroring the upstream rule this derives from, which
 * only checks the operator and that one operand is literally `null`.
 */
class UseLetRule : WUninitializedRule {
    override val id: String = "use-let"

    override fun initRule(config: WrasseRuleConfig): WBufferedNodeRule {
        val ruleId = id
        return object : WBufferedNodeRule {
            override val id = ruleId
            override val config = config
            override val targetTypes = setOf(WNodeType.IF, WNodeType.BINARY_EXPRESSION)

            private val pendingIfs = mutableListOf<PendingIf>()

            override fun enterNode(ctx: WContext, reporter: WReporter): Boolean {
                if (ctx.type == WNodeType.IF) pendingIfs.add(PendingIf())
                return true
            }

            override fun exitNode(
                ctx: WContext,
                children: ChildBuffer,
                reporter: WReporter,
            ) {
                when (ctx.type) {
                    WNodeType.BINARY_EXPRESSION -> recordCondition(ctx, children)
                    WNodeType.IF -> finalizeIf(ctx, children, reporter)
                    else -> {}
                }
            }

            private fun recordCondition(ctx: WContext, children: ChildBuffer) {
                if (ctx.ancestors.peekType() != WNodeType.CONDITION) return
                if (ctx.ancestors.size < 2 || ctx.ancestors.typeAt(ctx.ancestors.size - 2) != WNodeType.IF) return
                val pending = pendingIfs.lastOrNull() ?: return

                val sig = significantIndices(children)
                if (sig.size != 3) return
                val leftIdx = sig[0]
                val opIdx = sig[1]
                val rightIdx = sig[2]
                if (children.type(opIdx) != WNodeType.OPERATION_REFERENCE) return
                val opText = children.textSpan(opIdx, ctx.sourceText)
                val isEq = opText.contentEquals("==")
                val isNotEq = opText.contentEquals("!=")
                if (!isEq && !isNotEq) return

                val leftIsNull = children.textSpan(leftIdx, ctx.sourceText).toString().trim() == "null"
                val rightIsNull = children.textSpan(rightIdx, ctx.sourceText).toString().trim() == "null"
                if (!leftIsNull && !rightIsNull) return

                pending.isNullCheck = isEq
                pending.isNonNullCheck = isNotEq
            }

            private fun finalizeIf(
                ctx: WContext,
                children: ChildBuffer,
                reporter: WReporter,
            ) {
                val pending = pendingIfs.removeAt(pendingIfs.size - 1)
                if (!pending.isNullCheck && !pending.isNonNullCheck) return

                val thenIdx = children.firstChildOfType(WNodeType.THEN)
                val elseIdx = children.firstChildOfType(WNodeType.ELSE)
                if (thenIdx < 0 || elseIdx < 0) return

                val branchIdx = if (pending.isNullCheck) thenIdx else elseIdx
                val branchText = singleStatementText(children.textSpan(branchIdx, ctx.sourceText).toString())

                val message = UseLetDecision.decide(branchText) ?: return
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
        var isNullCheck: Boolean = false
        var isNonNullCheck: Boolean = false
    }
}
