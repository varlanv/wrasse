package com.varlanv.wrasse.rules

import com.varlanv.wrasse.model.WContext
import com.varlanv.wrasse.model.WNodeRule
import com.varlanv.wrasse.model.WNodeType
import com.varlanv.wrasse.model.WReporter
import com.varlanv.wrasse.model.WUninitializedRule
import com.varlanv.wrasse.model.WrasseRuleConfig

/**
 * An `is`/`!is` check or an unsafe `as` cast against the innermost enclosing catch parameter,
 * found anywhere within that catch's own body, is reported (see
 * [InstanceOfCheckForExceptionDecision]) at the check's own span. Both shapes are recognized as a
 * literal text prefix (`"$name is "`, `"$name !is "`, `"$name as "`) over the node's own span —
 * `as?` never matches the unsafe-cast prefix (the `?` sits where the check requires whitespace),
 * so the safe-cast form is naturally excluded without extra logic.
 */
class InstanceOfCheckForExceptionRule : WUninitializedRule {
    override val id: String = "instance-of-check-for-exception"

    override fun initRule(config: WrasseRuleConfig): WNodeRule {
        val ruleId = id
        return object : WNodeRule {
            override val id = ruleId
            override val config = config
            override val targetTypes = setOf(
                WNodeType.CATCH,
                WNodeType.VALUE_PARAMETER_LIST,
                WNodeType.IS_EXPRESSION,
                WNodeType.AS_EXPRESSION,
            )

            private val pendingCatchNames = mutableListOf<String?>()

            override fun enterNode(ctx: WContext, reporter: WReporter): Boolean {
                when (ctx.type) {
                    WNodeType.CATCH -> {
                        pendingCatchNames.add(null)
                        return true
                    }

                    WNodeType.VALUE_PARAMETER_LIST -> {
                        if (ctx.ancestors.peekType() == WNodeType.CATCH && pendingCatchNames.isNotEmpty()) {
                            val facts = CatchParameterText.parse(ctx.sourceText.subSequence(ctx.startOffset, ctx.endOffset))
                            pendingCatchNames[pendingCatchNames.size - 1] = facts?.name
                        }
                        return false
                    }

                    WNodeType.IS_EXPRESSION, WNodeType.AS_EXPRESSION -> {
                        checkExpression(ctx, reporter)
                        return false
                    }

                    else -> return false
                }
            }

            override fun exitNode(ctx: WContext, reporter: WReporter) {
                if (ctx.type == WNodeType.CATCH) pendingCatchNames.removeAt(pendingCatchNames.size - 1)
            }

            private fun checkExpression(ctx: WContext, reporter: WReporter) {
                val name = pendingCatchNames.lastOrNull() ?: return
                val text = ctx.sourceText.subSequence(ctx.startOffset, ctx.endOffset).toString()
                val checkedType = when (ctx.type) {
                        WNodeType.IS_EXPRESSION ->
                            when {
                                text.startsWith("$name is ") -> text.removePrefix("$name is ")
                                text.startsWith("$name !is ") -> text.removePrefix("$name !is ")
                                else -> null
                            }

                        WNodeType.AS_EXPRESSION -> if (text.startsWith("$name as ")) text.removePrefix("$name as ") else null
                        else -> null
                    }
                    ?: return

                val message = InstanceOfCheckForExceptionDecision.decide(checkedType.trim()) ?: return
                reporter.report(ruleId, message, ctx.startOffset, ctx.endOffset, this)
            }
        }
    }
}
