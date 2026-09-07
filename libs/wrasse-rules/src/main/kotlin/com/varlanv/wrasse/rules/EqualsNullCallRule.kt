package com.varlanv.wrasse.rules

import com.varlanv.wrasse.model.ChildBuffer
import com.varlanv.wrasse.model.WBufferedNodeRule
import com.varlanv.wrasse.model.WContext
import com.varlanv.wrasse.model.WNodeType
import com.varlanv.wrasse.model.WReporter
import com.varlanv.wrasse.model.WUninitializedRule
import com.varlanv.wrasse.model.WrasseRuleConfig

private val TARGET_TYPES = setOf(WNodeType.CALL_EXPRESSION)

/**
 * A call whose callee is spelled `equals` with a single `null` argument (`x.equals(null)` or bare
 * `equals(null)`) is reported (see [EqualsNullCallDecision]) at the call expression's own span.
 * Purely textual on both the callee name and the single argument's own trimmed text — matches any
 * call literally named `equals`, resolved or not, exactly like the upstream rule this derives from.
 */
class EqualsNullCallRule : WUninitializedRule {
    override val id: String = "equals-null-call"

    override fun initRule(config: WrasseRuleConfig): WBufferedNodeRule {
        val ruleId = id
        return object : WBufferedNodeRule {
            override val id = ruleId
            override val config = config
            override val targetTypes = TARGET_TYPES

            override fun exitNode(
                ctx: WContext,
                children: ChildBuffer,
                reporter: WReporter,
            ) {
                val calleeIdx = children.firstChildOfType(WNodeType.REFERENCE_EXPRESSION)
                if (calleeIdx < 0) return
                val argsIdx = children.firstChildOfType(WNodeType.VALUE_ARGUMENT_LIST)
                val argumentText = if (argsIdx < 0) null else singleArgumentText(children, argsIdx, ctx.sourceText)
                val message = EqualsNullCallDecision.decide(children.textSpan(calleeIdx, ctx.sourceText), argumentText)
                    ?: return
                reporter.report(ruleId, message, ctx.startOffset, ctx.endOffset, this)
            }

            private fun singleArgumentText(
                children: ChildBuffer,
                argsIdx: Int,
                sourceText: CharSequence,
            ): String? {
                val text = children.textSpan(argsIdx, sourceText).toString().trim()
                if (!text.startsWith("(") || !text.endsWith(")")) return null
                return text.substring(1, text.length - 1).trim()
            }
        }
    }
}
