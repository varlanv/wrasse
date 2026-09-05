package com.varlanv.wrasse.rules

import com.varlanv.wrasse.model.WContext
import com.varlanv.wrasse.model.WNodeRule
import com.varlanv.wrasse.model.WNodeType
import com.varlanv.wrasse.model.WReporter
import com.varlanv.wrasse.model.WUninitializedRule
import com.varlanv.wrasse.model.WrasseRuleConfig

private val TARGET_TYPES = setOf(WNodeType.CATCH, WNodeType.VALUE_PARAMETER_LIST, WNodeType.DOT_QUALIFIED_EXPRESSION)

/**
 * `Thread.dumpStack()` (reported at the whole call) and a caught exception's own
 * `<param>.printStackTrace()` (reported at the receiver's own name) are both reported (see
 * [PrintStackTraceDecision]). Both shapes are matched as literal whole-span text on
 * `DOT_QUALIFIED_EXPRESSION` — no descendant node inspection — so a stray internal whitespace
 * variant (`Thread . dumpStack ()`) is never a candidate, an accepted narrowing shared by every
 * whole-span-text rule in this batch.
 */
class PrintStackTraceRule : WUninitializedRule {
    override val id: String = "print-stack-trace"

    override fun initRule(config: WrasseRuleConfig): WNodeRule {
        val ruleId = id
        return object : WNodeRule {
            override val id = ruleId
            override val config = config
            override val targetTypes = TARGET_TYPES

            private val pendingCatchNames = mutableListOf<String?>()

            override fun enterNode(ctx: WContext, reporter: WReporter): Boolean {
                when (ctx.type) {
                    WNodeType.CATCH -> {
                        pendingCatchNames.add(null)
                        return true
                    }

                    WNodeType.VALUE_PARAMETER_LIST -> {
                        if (ctx.ancestors.peekType() == WNodeType.CATCH && pendingCatchNames.isNotEmpty()) {
                            val facts = CatchParameterText.parse(
                                ctx.sourceText.subSequence(ctx.startOffset, ctx.endOffset),
                            )
                            pendingCatchNames[pendingCatchNames.size - 1] = facts?.name
                        }
                        return false
                    }

                    WNodeType.DOT_QUALIFIED_EXPRESSION -> {
                        checkQualifiedExpression(ctx, reporter)
                        return false
                    }

                    else -> return false
                }
            }

            override fun exitNode(ctx: WContext, reporter: WReporter) {
                if (ctx.type == WNodeType.CATCH) pendingCatchNames.removeAt(pendingCatchNames.size - 1)
            }

            private fun checkQualifiedExpression(ctx: WContext, reporter: WReporter) {
                val text = ctx.sourceText.subSequence(ctx.startOffset, ctx.endOffset).toString()
                if (text == "Thread.dumpStack()") {
                    reporter.report(ruleId, PrintStackTraceDecision.MESSAGE, ctx.startOffset, ctx.endOffset, this)
                    return
                }
                val name = pendingCatchNames.lastOrNull() ?: return
                if (text == "$name.printStackTrace()") {
                    reporter.report(
                        ruleId,
                        PrintStackTraceDecision.MESSAGE,
                        ctx.startOffset,
                        ctx.startOffset + name.length,
                        this,
                    )
                }
            }
        }
    }
}
