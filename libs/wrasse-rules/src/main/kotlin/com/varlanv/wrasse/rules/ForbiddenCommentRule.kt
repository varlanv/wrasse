package com.varlanv.wrasse.rules

import com.varlanv.wrasse.model.WContext
import com.varlanv.wrasse.model.WLeafRule
import com.varlanv.wrasse.model.WNodeType
import com.varlanv.wrasse.model.WReporter
import com.varlanv.wrasse.model.WUninitializedRule
import com.varlanv.wrasse.model.WrasseRuleConfig

private val TARGET_TYPES = setOf(WNodeType.EOL_COMMENT, WNodeType.BLOCK_COMMENT, WNodeType.KDOC)

/**
 * A line comment, block comment, or KDoc containing `TODO:`, `FIXME:`, or `STOPSHIP:` is reported
 * (see [ForbiddenCommentDecision]). Matching runs directly over the comment's own raw source text,
 * opening/closing delimiters included, since none of the three markers can occur inside a comment
 * delimiter itself — never fixed, deleting or rewriting a comment's content is never a mechanical
 * decision.
 */
class ForbiddenCommentRule : WUninitializedRule {
    override val id: String = "forbidden-comment"

    override fun initRule(config: WrasseRuleConfig): WLeafRule {
        val ruleId = id
        return object : WLeafRule {
            override val id = ruleId
            override val config = config
            override val targetTypes = TARGET_TYPES

            override fun visitLeaf(ctx: WContext, reporter: WReporter) {
                val text = ctx.leafText ?: return
                val message = ForbiddenCommentDecision.decide(text) ?: return
                reporter.report(ruleId, message, ctx.startOffset, ctx.endOffset, this)
            }
        }
    }
}
