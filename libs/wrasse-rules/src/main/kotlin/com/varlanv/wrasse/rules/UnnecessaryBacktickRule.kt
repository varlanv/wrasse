package com.varlanv.wrasse.rules

import com.varlanv.wrasse.lang.WEdit
import com.varlanv.wrasse.model.WContext
import com.varlanv.wrasse.model.WLeafRule
import com.varlanv.wrasse.model.WNodeType
import com.varlanv.wrasse.model.WReporter
import com.varlanv.wrasse.model.WUninitializedRule
import com.varlanv.wrasse.model.WrasseRuleConfig

/**
 * A backtick-quoted identifier whose escaping is provably unnecessary loses its backticks; see
 * [UnnecessaryBacktickDecision] for the exact necessity check.
 *
 * Reported but never autofixed when the identifier sits inside a string template's short-form
 * entry (`"$\`name\`"`): the identifier's own text there is directly adjacent to whatever literal
 * text follows it in the same string, with no delimiter of its own once the backticks are gone,
 * so removal could silently extend the reference into that following text. A long-form entry
 * (`"${\`name\`}"`) has its own explicit closing brace and is never affected.
 */
class UnnecessaryBacktickRule : WUninitializedRule {
    override val id: String = "unnecessary-backticks"
    override val canAutofix: Boolean = true

    override fun initRule(config: WrasseRuleConfig): WLeafRule {
        val ruleId = id
        return object : WLeafRule {
            override val id = ruleId
            override val config = config
            override val targetTypes = setOf(WNodeType.IDENTIFIER)

            override fun visitLeaf(ctx: WContext, reporter: WReporter) {
                val text = ctx.leafText ?: return
                val unquoted = UnnecessaryBacktickDecision.unquote(text) ?: return
                val canFix = !ctx.hasAncestor(WNodeType.SHORT_STRING_TEMPLATE_ENTRY)
                val edits =
                    if (canFix) listOf(WEdit(ctx.startOffset, ctx.endOffset, unquoted.toString())) else emptyList()
                reporter.report(
                    ruleId,
                    "Backticks are unnecessary",
                    ctx.startOffset,
                    ctx.endOffset,
                    this,
                    edits = edits,
                )
            }
        }
    }
}
