package com.varlanv.wrasse.rules

import com.varlanv.wrasse.model.FormatDirective
import com.varlanv.wrasse.model.RuleLevel
import com.varlanv.wrasse.model.WContext
import com.varlanv.wrasse.model.WLeafRule
import com.varlanv.wrasse.model.WNodeType
import com.varlanv.wrasse.model.WReporter
import com.varlanv.wrasse.model.WrasseRuleConfig

/**
 * Framework-owned, always-on rule that records every `// wrasse fmt:off` / `// wrasse fmt:on`
 * directive into [WContext.formatRegions]. A directive is an end-of-line comment whose text is
 * exactly [FormatDirective.text] and which has only indentation before it on its line; anything
 * else is an ordinary comment.
 */
class FormatDirectiveCollectorRule : WLeafRule {
    override val id = "format-directive-collector"
    override val config = WrasseRuleConfig(level = RuleLevel.OFF, exclude = emptyList(), effectiveLevel = RuleLevel.OFF)
    override val targetTypes = setOf(WNodeType.EOL_COMMENT)

    override fun visitLeaf(ctx: WContext, reporter: WReporter) {
        val directive = FormatDirective.parse(ctx.leafText ?: return) ?: return
        val lineStart = lineStartIfOnlyIndentBefore(ctx.sourceText, ctx.startOffset) ?: return
        ctx.formatRegions.add(directive, lineStart, ctx.endOffset)
    }
}
