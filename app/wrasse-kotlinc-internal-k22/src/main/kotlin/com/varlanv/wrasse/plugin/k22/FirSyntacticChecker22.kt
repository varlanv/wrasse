package com.varlanv.wrasse.plugin.k22

import com.varlanv.wrasse.plugin.WrassePlugin
import org.jetbrains.kotlin.KtLightSourceElement
import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.diagnostics.KtDiagnosticFactory1
import org.jetbrains.kotlin.diagnostics.reportOn
import org.jetbrains.kotlin.fir.analysis.checkers.MppCheckerKind
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.analysis.checkers.declaration.FirFileChecker
import org.jetbrains.kotlin.fir.declarations.FirFile

class FirSyntacticChecker22(
    private val plugin: WrassePlugin,
    private val diagnostic: KtDiagnosticFactory1<String>,
) : FirFileChecker(MppCheckerKind.Common) {

    context(context: CheckerContext, reporter: DiagnosticReporter)
    override fun check(declaration: FirFile) {
        val source = declaration.source as? KtLightSourceElement ?: return

        for (violation in plugin.checkFile(source, declaration.name)) {
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
