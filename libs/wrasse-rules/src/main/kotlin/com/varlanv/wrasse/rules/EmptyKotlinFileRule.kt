package com.varlanv.wrasse.rules

import com.varlanv.wrasse.model.ChildBuffer
import com.varlanv.wrasse.model.WBufferedNodeRule
import com.varlanv.wrasse.model.WContext
import com.varlanv.wrasse.model.WNodeType
import com.varlanv.wrasse.model.WReporter
import com.varlanv.wrasse.model.WUninitializedRule
import com.varlanv.wrasse.model.WrasseRuleConfig

private val TARGET_TYPES = setOf(WNodeType.FILE)

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
            override val targetTypes = TARGET_TYPES

            override fun exitNode(
                ctx: WContext,
                children: ChildBuffer,
                reporter: WReporter,
            ) {
                if (!EmptyKotlinFileCheck.isEmpty(ctx, children)) return
                reporter.report(ruleId, "Empty Kotlin file detected. This file can be removed", 0, 0, this)
            }
        }
    }
}
