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
 * Reports a `when` expression with zero entries. A comment or KDoc anywhere between the braces
 * exempts it, matching this project's own uniform empty-block convention even though the upstream
 * rule this id derives from applies no such exemption to itself — narrower, never a new false
 * positive relative to it.
 */
class EmptyWhenBlockRule : WUninitializedRule {
    override val id: String = "empty-when-block"

    override fun initRule(config: WrasseRuleConfig): WBufferedNodeRule {
        val ruleId = id
        return object : WBufferedNodeRule {
            override val id = ruleId
            override val config = config
            override val targetTypes = setOf(WNodeType.WHEN)

            override fun exitNode(ctx: WContext, children: ChildBuffer, reporter: WReporter) {
                if (children.hasChildOfType(WNodeType.WHEN_ENTRY)) return
                if (hasComment(children)) return
                reporter
                    .report(ruleId, "Empty when block detected. This when expression has no entries", ctx.startOffset, ctx.endOffset, this)
            }

            private fun hasComment(children: ChildBuffer): Boolean {
                for (i in 0 until children.size) {
                    val type = children.type(i)
                    if (type.isWhitespaceOrComment && type != WNodeType.WHITE_SPACE) return true
                }
                return false
            }
        }
    }
}
