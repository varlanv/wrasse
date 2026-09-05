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
 * A `&&`/`||` [WNodeType.BINARY_EXPRESSION] anywhere inside an `if`/`while`/`do-while` own
 * `CONDITION` — matching the upstream rule this derives from's own scope exactly — whose own two
 * direct operands are directly simplifiable (see [BooleanExpressionsDecision]) is reported at its
 * own span. Every `PREFIX_EXPRESSION`'s own `!`-operator and base text are recorded at its own
 * exit, keyed by offset, so a sibling operand can be tested as that prefix's own direct complement
 * by raw text — one level below the binary expression's own two operands, not recursed through any
 * further chain.
 */
class BooleanExpressionsRule : WUninitializedRule {
    override val id: String = "boolean-expressions"

    override fun initRule(config: WrasseRuleConfig): WBufferedNodeRule {
        val ruleId = id
        return object : WBufferedNodeRule {
            override val id = ruleId
            override val config = config
            override val targetTypes = setOf(WNodeType.BINARY_EXPRESSION, WNodeType.PREFIX_EXPRESSION)

            private val negationFacts = mutableMapOf<Long, String>()

            override fun enterNode(ctx: WContext, reporter: WReporter): Boolean = ctx.hasAncestor(WNodeType.CONDITION)

            override fun exitNode(
                ctx: WContext,
                children: ChildBuffer,
                reporter: WReporter,
            ) {
                when (ctx.type) {
                    WNodeType.PREFIX_EXPRESSION -> finalizePrefix(ctx, children)
                    WNodeType.BINARY_EXPRESSION -> finalizeBinary(ctx, children, reporter)
                    else -> {}
                }
            }

            private fun finalizePrefix(ctx: WContext, children: ChildBuffer) {
                val significant = ArrayList<Int>(children.size)
                for (i in 0 until children.size) if (!children.type(i).isWhitespaceOrComment) significant.add(i)
                if (significant.size != 2) return
                val (opIdx, operandIdx) = significant[0] to significant[1]
                if (!children.textSpan(opIdx, ctx.sourceText).contentEquals("!")) return
                negationFacts[key(
                    ctx.startOffset,
                    ctx.endOffset,
                )] = children.textSpan(operandIdx, ctx.sourceText).toString()
            }

            private fun finalizeBinary(
                ctx: WContext,
                children: ChildBuffer,
                reporter: WReporter,
            ) {
                val significant = ArrayList<Int>(children.size)
                for (i in 0 until children.size) if (!children.type(i).isWhitespaceOrComment) significant.add(i)
                if (significant.size != 3) return
                val (leftIdx, opIdx, rightIdx) = Triple(significant[0], significant[1], significant[2])

                val opText = children.textSpan(opIdx, ctx.sourceText)
                val isAndOr = children.type(opIdx) ==
                    WNodeType.OPERATION_REFERENCE &&
                    (opText.contentEquals("&&") || opText.contentEquals("||"))

                val leftText = children.textSpan(leftIdx, ctx.sourceText)
                val rightText = children.textSpan(rightIdx, ctx.sourceText)
                val isLiteralAbsorption = BooleanExpressionsDecision.isLiteralAbsorption(
                    children.type(leftIdx),
                    leftText,
                    children.type(rightIdx),
                    rightText,
                )

                val leftNegatesRight = negationOf(children, leftIdx)?.contentEquals(rightText) == true
                val rightNegatesLeft = negationOf(children, rightIdx)?.contentEquals(leftText) == true

                val message = BooleanExpressionsDecision.decide(
                    isAndOr,
                    isLiteralAbsorption,
                    leftNegatesRight || rightNegatesLeft,
                ) ?: return
                reporter.report(ruleId, message, ctx.startOffset, ctx.endOffset, this)
            }

            private fun negationOf(children: ChildBuffer, idx: Int): String? {
                if (children.type(idx) != WNodeType.PREFIX_EXPRESSION) return null
                return negationFacts[key(children.startOffset(idx), children.endOffset(idx))]
            }

            private fun key(start: Int, end: Int): Long = (start.toLong() shl 32) or (end.toLong() and 0xFF_FFF_FFFL)
        }
    }
}
