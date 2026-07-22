package com.varlanv.wrasse.rules

import com.varlanv.wrasse.model.ChildBuffer
import com.varlanv.wrasse.model.WBufferedNodeRule
import com.varlanv.wrasse.model.WContext
import com.varlanv.wrasse.model.WNodeType
import com.varlanv.wrasse.model.WReporter
import com.varlanv.wrasse.model.WUninitializedRule
import com.varlanv.wrasse.model.WrasseRuleConfig

/**
 * Reports a file whose only content, once its own `package` declaration is disregarded, is
 * whitespace: no imports, no declarations, not even a comment.
 */
class EmptyKotlinFileRule : WUninitializedRule {
    override val id: String = "empty-kotlin-file"

    override fun initRule(config: WrasseRuleConfig): WBufferedNodeRule {
        val ruleId = id
        return object : WBufferedNodeRule {
            override val id = ruleId
            override val config = config
            override val targetTypes = setOf(WNodeType.FILE)

            override fun exitNode(ctx: WContext, children: ChildBuffer, reporter: WReporter) {
                for (i in 0 until children.size) {
                    if (children.type(i) == WNodeType.PACKAGE_DIRECTIVE) continue
                    if (children.textSpan(i, ctx.sourceText).isNotBlank()) return
                }
                reporter.report(ruleId, "Empty Kotlin file detected. This file can be removed", 0, 0, this)
            }
        }
    }
}
