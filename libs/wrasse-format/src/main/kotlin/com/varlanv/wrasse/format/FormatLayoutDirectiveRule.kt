package com.varlanv.wrasse.format

import com.varlanv.wrasse.model.LayoutDirectiveKind
import com.varlanv.wrasse.model.WContext
import com.varlanv.wrasse.model.WFileRule
import com.varlanv.wrasse.model.WReporter
import com.varlanv.wrasse.model.WrasseRuleConfig

/**
 * Framework-owned rule that reports every [kind] layout directive the printer rejected, at the
 * directive comment, under [LayoutDirectiveKind.ruleId], with [config] taken from the `format`
 * setting. Records no edit. A directive lying inside a `// wrasse fmt:off` region, or whose target
 * line starts in one, is ignored.
 */
class FormatLayoutDirectiveRule(
    private val kind: LayoutDirectiveKind,
    override val config: WrasseRuleConfig,
) : WFileRule {
    override val id: String = kind.ruleId

    override fun visit(ctx: WContext, reporter: WReporter) {
        for (directive in ctx.layoutDirectives.all()) {
            if (directive.kind != kind) continue
            if (ctx.layoutDirectives.isInsideRegion(directive, ctx.sourceText, ctx.formatRegions)) continue
            val message = LayoutDirectiveMessages.problemOf(directive) ?: continue
            reporter.report(id, message, directive.commentStart, directive.commentEnd, this)
        }
    }
}
