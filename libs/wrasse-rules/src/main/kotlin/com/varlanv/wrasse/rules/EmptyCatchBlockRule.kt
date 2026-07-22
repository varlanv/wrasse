package com.varlanv.wrasse.rules

import com.varlanv.wrasse.model.ChildBuffer
import com.varlanv.wrasse.model.WBufferedNodeRule
import com.varlanv.wrasse.model.WContext
import com.varlanv.wrasse.model.WNodeType
import com.varlanv.wrasse.model.WReporter
import com.varlanv.wrasse.model.WUninitializedRule
import com.varlanv.wrasse.model.WrasseRuleConfig

/**
 * A catch body containing nothing but whitespace is reported at the body's own span (see
 * [AllowedExceptionName] for the catch-parameter-name exemption every catch-shaped rule in this
 * batch shares). Any comment or KDoc inside makes the body non-empty and never reported, matching
 * `no-empty-class-body`'s own convention for the same shape.
 */
class EmptyCatchBlockRule : WUninitializedRule {
    override val id: String = "empty-catch-block"

    override fun initRule(config: WrasseRuleConfig): WBufferedNodeRule {
        val ruleId = id
        return object : WBufferedNodeRule {
            override val id = ruleId
            override val config = config
            override val targetTypes = setOf(WNodeType.CATCH, WNodeType.VALUE_PARAMETER_LIST, WNodeType.BLOCK)

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

                    WNodeType.BLOCK -> return ctx.ancestors.peekType() == WNodeType.CATCH
                    else -> return false
                }
            }

            override fun exitNode(ctx: WContext, children: ChildBuffer, reporter: WReporter) {
                when (ctx.type) {
                    WNodeType.CATCH -> pendingCatchNames.removeAt(pendingCatchNames.size - 1)
                    WNodeType.BLOCK -> checkBody(ctx, children, reporter)
                    else -> {}
                }
            }

            private fun checkBody(ctx: WContext, children: ChildBuffer, reporter: WReporter) {
                if (!EmptyBlockCheck.isEmpty(children)) return
                val name = pendingCatchNames.lastOrNull() ?: return
                if (AllowedExceptionName.isAllowed(name)) return
                reporter
                    .report(
                        ruleId,
                        "Empty catch block detected. Empty catch blocks indicate that an exception is ignored and not handled.",
                        ctx.startOffset,
                        ctx.endOffset,
                        this,
                    )
            }
        }
    }
}
