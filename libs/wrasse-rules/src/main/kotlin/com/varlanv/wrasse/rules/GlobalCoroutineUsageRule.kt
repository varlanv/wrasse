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
 * A dot-qualified expression whose receiver is spelled `GlobalScope` and whose selector call
 * begins with `launch` or `async` is reported (see [GlobalCoroutineUsageDecision]) at the whole
 * expression's own span. Purely textual on both the receiver and the selector's own leading
 * callee name — matches any receiver literally named `GlobalScope`, resolved or not, exactly like
 * the upstream rule this derives from.
 */
class GlobalCoroutineUsageRule : WUninitializedRule {
    override val id: String = "global-coroutine-usage"

    override fun initRule(config: WrasseRuleConfig): WBufferedNodeRule {
        val ruleId = id
        return object : WBufferedNodeRule {
            override val id = ruleId
            override val config = config
            override val targetTypes = setOf(WNodeType.DOT_QUALIFIED_EXPRESSION)

            override fun exitNode(
                ctx: WContext,
                children: ChildBuffer,
                reporter: WReporter,
            ) {
                val receiverIdx = (0 until
                    children.size).firstOrNull { !children.type(it).isWhitespaceOrComment } ?: return
                val dotIdx = children.firstChildOfType(WNodeType.DOT)
                if (dotIdx < 0) return
                val selectorIdx = (dotIdx + 1 until
                    children.size).firstOrNull { !children.type(it).isWhitespaceOrComment } ?: return

                val receiverText = children.textSpan(receiverIdx, ctx.sourceText)
                val selectorText = children.textSpan(selectorIdx, ctx.sourceText)
                val calleeText =
                    when {
                        WordBoundaryScan.startsWithWord(selectorText, "launch") -> "launch"
                        WordBoundaryScan.startsWithWord(selectorText, "async") -> "async"
                        else -> null
                    }

                val message = GlobalCoroutineUsageDecision.decide(receiverText, calleeText) ?: return
                reporter.report(ruleId, message, ctx.startOffset, ctx.endOffset, this)
            }
        }
    }
}
