package com.varlanv.wrasse.rules

import com.varlanv.wrasse.model.LayoutDirectiveKind
import com.varlanv.wrasse.model.RuleLevel
import com.varlanv.wrasse.model.WContext
import com.varlanv.wrasse.model.WLeafRule
import com.varlanv.wrasse.model.WNodeType
import com.varlanv.wrasse.model.WReporter
import com.varlanv.wrasse.model.WrasseRuleConfig

/**
 * Framework-owned rule that records every `// wrasse matrix:` and `// wrasse columns:` directive
 * into [WContext.layoutDirectives]. A directive is an end-of-line comment whose text starts with a
 * [LayoutDirectiveKind.prefix] and which has only indentation before it on its line; anything else
 * is an ordinary comment. The size is validated later, by the printer.
 */
class LayoutDirectiveCollectorRule : WLeafRule {
    override val id = "layout-directive-collector"
    override val config = WrasseRuleConfig(level = RuleLevel.OFF, exclude = emptyList(), effectiveLevel = RuleLevel.OFF)
    override val targetTypes = setOf(WNodeType.EOL_COMMENT)

    override fun visitLeaf(ctx: WContext, reporter: WReporter) {
        val text = ctx.leafString() ?: return
        val kind = LayoutDirectiveKind.ofText(text) ?: return
        val lineStart = lineStartIfOnlyIndentBefore(ctx.sourceText, ctx.startOffset) ?: return
        ctx.layoutDirectives.add(kind, text, ctx.startOffset, ctx.endOffset, lineStart, ctx.sourceText)
    }
}
