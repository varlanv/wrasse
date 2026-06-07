package com.varlanv.wrasse.plugin.internal

import com.varlanv.wrasse.config.WrasseSeverity
import com.varlanv.wrasse.plugin.WrassePlugin
import org.jetbrains.kotlin.KtLightSourceElement
import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.diagnostics.reportOn
import org.jetbrains.kotlin.fir.analysis.checkers.MppCheckerKind
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.analysis.checkers.declaration.FirFileChecker
import org.jetbrains.kotlin.fir.declarations.FirFile

class FirSyntacticChecker(
    private val plugin: WrassePlugin,
) : FirFileChecker(MppCheckerKind.Common) {

    context(context: CheckerContext, reporter: DiagnosticReporter)
    override fun check(declaration: FirFile) {
        val source = declaration.source as? KtLightSourceElement ?: return

        val violations = plugin.checkFile(source, declaration.name)

        for (violation in violations) {
            val diagnostic = when (violation.severity) {
                WrasseSeverity.ERROR -> WrasseErrors.RESTRICTED_API
                WrasseSeverity.WARNING -> WrasseErrors.WRASSE_WARNING
            }
            val violationSource = KtLightSourceElement(
                source.lighterASTNode,
                violation.startOffset,
                violation.endOffset,
                source.treeStructure,
            )
            reporter.reportOn(violationSource, diagnostic, violation.message)
        }
    }
}
