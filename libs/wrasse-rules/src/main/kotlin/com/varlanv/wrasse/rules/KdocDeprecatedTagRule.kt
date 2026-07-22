package com.varlanv.wrasse.rules

import com.varlanv.wrasse.model.WContext
import com.varlanv.wrasse.model.WLeafRule
import com.varlanv.wrasse.model.WNodeType
import com.varlanv.wrasse.model.WReporter
import com.varlanv.wrasse.model.WUninitializedRule
import com.varlanv.wrasse.model.WrasseRuleConfig

/**
 * A KDoc containing an `@deprecated` block tag is reported (see [KdocDeprecatedTagDecision]) at
 * the KDoc's own span — matched directly over its raw text, since `@deprecated` cannot occur
 * inside the comment's own delimiters.
 */
class KdocDeprecatedTagRule : WUninitializedRule {
    override val id: String = "kdoc-deprecated-tag"

    override fun initRule(config: WrasseRuleConfig): WLeafRule {
        val ruleId = id
        return object : WLeafRule {
            override val id = ruleId
            override val config = config
            override val targetTypes = setOf(WNodeType.KDOC)

            override fun visitLeaf(ctx: WContext, reporter: WReporter) {
                val text = ctx.leafText ?: return
                if (!KdocDeprecatedTagDecision.hasDeprecatedTag(text)) return
                reporter.report(ruleId, KdocDeprecatedTagDecision.MESSAGE, ctx.startOffset, ctx.endOffset, this)
            }
        }
    }
}
