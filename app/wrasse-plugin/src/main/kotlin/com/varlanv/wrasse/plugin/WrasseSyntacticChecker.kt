package com.varlanv.wrasse.plugin

import com.varlanv.wrasse.adapter.LightTreeAdapter
import com.varlanv.wrasse.config.WrasseConfig
import com.varlanv.wrasse.config.WrasseRuleToggle
import com.varlanv.wrasse.config.WrasseRulesConfig
import com.varlanv.wrasse.config.WrasseSeverity
import com.varlanv.wrasse.model.WRule
import com.varlanv.wrasse.model.WViolation
import com.varlanv.wrasse.rules.NoSemicolonsRule
import org.jetbrains.kotlin.KtLightSourceElement
import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.diagnostics.reportOn
import org.jetbrains.kotlin.fir.analysis.checkers.MppCheckerKind
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.analysis.checkers.declaration.FirFileChecker
import org.jetbrains.kotlin.fir.declarations.FirFile

object WrasseSyntacticChecker : FirFileChecker(MppCheckerKind.Common) {

    private val rules: List<WRule> = listOf(
        NoSemicolonsRule(),
    )

    private val config = WrasseConfig(
        exclude = emptyList(),
        rules = WrasseRulesConfig(
            noSemicolons = WrasseRuleToggle(
                enabled = true,
                severity = WrasseSeverity.WARNING,
                exclude = listOf(WrasseConfig.pathMatcher("**/generated/**").getOrThrow()/*todo*/),
            ),
        ),
    )

    context(context: CheckerContext, reporter: DiagnosticReporter)
    override fun check(declaration: FirFile) {
        val source = declaration.source ?: return
        if (source !is KtLightSourceElement) return

        val wFile = LightTreeAdapter.adapt(source, declaration.name)

        for (rule in rules) {
            for (violation in rule.check(wFile, config)) {
                val line = wFile.sourceText.subSequence(0, violation.node.startOffset).count { it == '\n' } + 1
                val col = violation.node.column + 1
                val diagnostic = when (violation.severity) {
                    WrasseSeverity.ERROR -> WrasseErrors.RESTRICTED_API
                    WrasseSeverity.WARNING -> WrasseErrors.WRASSE_WARNING
                }
                reporter.reportOn(
                    declaration.source,
                    diagnostic,
                    "${violation.ruleId}: ${violation.message} ($line:$col)",
                )
            }
        }
    }
}
