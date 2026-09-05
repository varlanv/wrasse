package com.varlanv.wrasse.rules

import com.varlanv.wrasse.model.WContext
import com.varlanv.wrasse.model.WNodeType
import com.varlanv.wrasse.model.WReporter
import com.varlanv.wrasse.model.WStreamRule
import com.varlanv.wrasse.model.WUninitializedRule
import com.varlanv.wrasse.model.WrasseRuleConfig

/**
 * A comment leaf (KDoc/block/EOL) whose nearest preceding non-whitespace leaf is also a comment
 * is reported (see [NoConsecutiveCommentsDecision]) at the later comment's own span. The
 * "nearest preceding non-whitespace leaf" is tracked across the whole file as a running
 * last-significant-leaf type, the same shape [NoSemicolonsRule] already establishes; the
 * immediately preceding leaf's own text (`ctx.prevLeafText`), read only when that leaf is
 * whitespace, supplies the blank-line count.
 */
class NoConsecutiveCommentsRule : WUninitializedRule {
    override val id: String = "no-consecutive-comments"

    override fun initRule(config: WrasseRuleConfig): WStreamRule {
        val ruleId = id
        return object : WStreamRule {
            override val id = ruleId
            override val config = config

            private var lastSignificantLeafType: WNodeType? = null

            override fun visitLeaf(ctx: WContext, reporter: WReporter) {
                if (ctx.type == WNodeType.WHITE_SPACE) return

                if (isCommentType(ctx.type)) {
                    val previous = lastSignificantLeafType
                    if (previous != null && isCommentType(previous)) {
                        val separatedByBlankLine =
                            ctx.prevLeafType == WNodeType.WHITE_SPACE && countNewlines(ctx.prevLeafText) > 1
                        val message = NoConsecutiveCommentsDecision.decide(previous, ctx.type, separatedByBlankLine)
                        if (message != null) reporter.report(ruleId, message, ctx.startOffset, ctx.endOffset, this)
                    }
                }

                lastSignificantLeafType = ctx.type
            }

            private fun isCommentType(type: WNodeType): Boolean =
                type == WNodeType.EOL_COMMENT || type == WNodeType.BLOCK_COMMENT || type == WNodeType.KDOC

            private fun countNewlines(text: CharSequence?): Int {
                if (text == null) return 0
                var count = 0
                for (c in text) if (c == '\n') count++
                return count
            }
        }
    }
}
