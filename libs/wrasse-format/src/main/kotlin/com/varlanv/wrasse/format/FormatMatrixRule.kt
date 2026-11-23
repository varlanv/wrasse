package com.varlanv.wrasse.format

import com.varlanv.wrasse.model.WContext
import com.varlanv.wrasse.model.WFileRule
import com.varlanv.wrasse.model.WReporter
import com.varlanv.wrasse.model.WrasseRuleConfig

/**
 * Framework-owned rule that reports every `// wrasse matrix:<N>` directive the printer rejected,
 * at the directive comment, with [config] taken from the `format` setting. Records no edit.
 * A directive lying inside a `// wrasse fmt:off` region, or whose line below starts in one, is
 * ignored.
 */
class FormatMatrixRule(override val config: WrasseRuleConfig) : WFileRule {
    override val id: String = "format-matrix"

    override fun visit(ctx: WContext, reporter: WReporter) {
        for (directive in ctx.matrixDirectives.all()) {
            if (directive.isInside(ctx.formatRegions)) continue
            val message = MatrixMessages.problemOf(directive) ?: continue
            reporter.report(id, message, directive.commentStart, directive.commentEnd, this)
        }
    }
}
