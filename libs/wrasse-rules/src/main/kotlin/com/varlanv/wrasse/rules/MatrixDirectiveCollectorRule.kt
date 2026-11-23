package com.varlanv.wrasse.rules

import com.varlanv.wrasse.model.MatrixDirective
import com.varlanv.wrasse.model.RuleLevel
import com.varlanv.wrasse.model.WContext
import com.varlanv.wrasse.model.WLeafRule
import com.varlanv.wrasse.model.WNodeType
import com.varlanv.wrasse.model.WReporter
import com.varlanv.wrasse.model.WrasseRuleConfig

/**
 * Framework-owned rule that records every `// wrasse matrix:` directive into
 * [WContext.matrixDirectives]. A directive is an end-of-line comment whose text starts with
 * [MatrixDirective.PREFIX] and which has only indentation before it on its line; anything else is
 * an ordinary comment. The size is validated later, by the printer.
 */
class MatrixDirectiveCollectorRule : WLeafRule {
    override val id = "matrix-directive-collector"
    override val config = WrasseRuleConfig(level = RuleLevel.OFF, exclude = emptyList(), effectiveLevel = RuleLevel.OFF)
    override val targetTypes = setOf(WNodeType.EOL_COMMENT)

    override fun visitLeaf(ctx: WContext, reporter: WReporter) {
        val text = ctx.leafString() ?: return
        if (!MatrixDirective.isDirectiveText(text)) return
        val lineStart = lineStartIfOnlyIndentBefore(ctx.sourceText, ctx.startOffset) ?: return
        ctx.matrixDirectives.add(text, ctx.startOffset, ctx.endOffset, lineStart, ctx.sourceText)
    }
}
