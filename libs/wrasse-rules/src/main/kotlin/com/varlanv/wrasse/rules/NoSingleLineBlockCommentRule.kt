package com.varlanv.wrasse.rules

import com.varlanv.wrasse.lang.WEdit
import com.varlanv.wrasse.model.WContext
import com.varlanv.wrasse.model.WLeafRule
import com.varlanv.wrasse.model.WNodeType
import com.varlanv.wrasse.model.WReporter
import com.varlanv.wrasse.model.WUninitializedRule
import com.varlanv.wrasse.model.WrasseRuleConfig

private val TARGET_TYPES = setOf(WNodeType.BLOCK_COMMENT)

/**
 * A single-line `/* ... */` block comment with nothing but same-line whitespace after it (end of
 * file or a newline follows, skipping only spaces/tabs) is reported (see
 * [NoSingleLineBlockCommentDecision]) at its own span, matching the upstream rule this derives
 * from exactly. Autofixed by replacing the comment's own span with [NoSingleLineBlockCommentDecision.replacement]
 * whenever that is non-null; an all-whitespace `/* */` is reported without an edit.
 */
class NoSingleLineBlockCommentRule : WUninitializedRule {
    override val id: String = "no-single-line-block-comment"
    override val canAutofix: Boolean = true

    override fun initRule(config: WrasseRuleConfig): WLeafRule {
        val ruleId = id
        return object : WLeafRule {
            override val id = ruleId
            override val config = config
            override val targetTypes = TARGET_TYPES

            override fun visitLeaf(ctx: WContext, reporter: WReporter) {
                val text = ctx.leafText ?: return
                val message = NoSingleLineBlockCommentDecision.decide(text, followedByCodeOnSameLine(ctx)) ?: return
                val replacement = NoSingleLineBlockCommentDecision.replacement(text)
                val edits = if (replacement == null) {
                    emptyList()
                } else {
                    listOf(WEdit(ctx.startOffset, ctx.endOffset, replacement))
                }
                reporter.report(ruleId, message, ctx.startOffset, ctx.endOffset, this, edits = edits)
            }

            private fun followedByCodeOnSameLine(ctx: WContext): Boolean {
                val source = ctx.sourceText
                var i = ctx.endOffset
                while (i < source.length && (source[i] == ' ' || source[i] == '\t')) i++
                if (i >= source.length) return false
                return source[i] != '\n' && source[i] != '\r'
            }
        }
    }
}
