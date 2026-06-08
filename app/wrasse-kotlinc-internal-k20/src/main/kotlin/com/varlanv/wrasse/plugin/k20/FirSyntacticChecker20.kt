package com.varlanv.wrasse.plugin.k20

import com.varlanv.wrasse.plugin.WrassePlugin
import org.jetbrains.kotlin.KtLightSourceElement
import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.diagnostics.KtDiagnosticFactory1
import org.jetbrains.kotlin.diagnostics.reportOn
import org.jetbrains.kotlin.fir.analysis.checkers.MppCheckerKind
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.analysis.checkers.declaration.FirFileChecker
import org.jetbrains.kotlin.fir.declarations.FirFile

class FirSyntacticChecker20(
    private val plugin: WrassePlugin,
    private val diagnostic: KtDiagnosticFactory1<String>,
) : FirFileChecker(MppCheckerKind.Common) {

    override fun check(declaration: FirFile, context: CheckerContext, reporter: DiagnosticReporter) {
        val source = declaration.source as? KtLightSourceElement ?: return

        val violations = plugin.checkFile(source, declaration.name)
        for (violation in violations) {
            val violationSource = KtLightSourceElement(
                source.lighterASTNode,
                violation.startOffset,
                violation.endOffset,
                source.treeStructure,
            )
            reporter.reportOn(violationSource, diagnostic, violation.message, context)
        }
    }
}
