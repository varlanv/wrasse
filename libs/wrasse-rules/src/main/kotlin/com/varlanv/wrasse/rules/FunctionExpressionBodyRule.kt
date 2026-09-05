package com.varlanv.wrasse.rules

import com.varlanv.wrasse.model.ChildBuffer
import com.varlanv.wrasse.model.ChildLeafHandler
import com.varlanv.wrasse.model.WBufferedNodeRule
import com.varlanv.wrasse.model.WContext
import com.varlanv.wrasse.model.WNodeType
import com.varlanv.wrasse.model.WReporter
import com.varlanv.wrasse.model.WUninitializedRule
import com.varlanv.wrasse.model.WrasseRuleConfig

private val TARGET_TYPES = setOf(WNodeType.BLOCK)

/**
 * A function's own `BLOCK` body whose only content, ignoring braces and whitespace, is a single
 * `return`/`throw` statement is reported (see [FunctionExpressionBodyDecision]) at the block's own
 * span. Only a `BLOCK` whose immediate parent is `FUN` is ever inspected (a lambda's or `when`
 * branch's own block is never a candidate). Report-only.
 */
class FunctionExpressionBodyRule : WUninitializedRule {
    override val id: String = "function-expression-body"

    override fun initRule(config: WrasseRuleConfig): WBufferedNodeRule {
        val ruleId = id
        return object : WBufferedNodeRule, ChildLeafHandler {
            override val id = ruleId
            override val config = config
            override val targetTypes = TARGET_TYPES

            private var returnKeywordCount = 0

            override fun enterNode(ctx: WContext, reporter: WReporter): Boolean {
                if (ctx.ancestors.peekType() != WNodeType.FUN) return false
                returnKeywordCount = 0
                return true
            }

            override fun onChildLeaf(ctx: WContext, reporter: WReporter) {
                if (ctx.type == WNodeType.KW_RETURN) returnKeywordCount++
            }

            override fun exitNode(
                ctx: WContext,
                children: ChildBuffer,
                reporter: WReporter,
            ) {
                var soleType: WNodeType? = null
                var significantCount = 0
                for (i in 0 until children.size) {
                    val type = children.type(i)
                    if (type == WNodeType.LBRACE || type == WNodeType.RBRACE || type == WNodeType.WHITE_SPACE) continue
                    significantCount++
                    soleType = type
                }
                if (significantCount != 1) return

                val message = FunctionExpressionBodyDecision.decide(soleType, returnKeywordCount) ?: return
                reporter.report(ruleId, message, ctx.startOffset, ctx.endOffset, this)
            }
        }
    }
}
