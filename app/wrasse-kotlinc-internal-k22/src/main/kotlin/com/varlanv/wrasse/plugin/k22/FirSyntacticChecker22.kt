package com.varlanv.wrasse.plugin.k22

import com.varlanv.wrasse.model.RuleLevel
import com.varlanv.wrasse.plugin.WrassePlugin
import com.varlanv.wrasse.plugin.internal.ResolvedUsageCollector
import org.jetbrains.kotlin.KtLightSourceElement
import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.diagnostics.reportOn
import org.jetbrains.kotlin.fir.analysis.checkers.MppCheckerKind
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.analysis.checkers.declaration.FirFileChecker
import org.jetbrains.kotlin.fir.declarations.FirFile

class FirSyntacticChecker22(
    private val plugin: WrassePlugin,
) : FirFileChecker(MppCheckerKind.Common) {

    context(context: CheckerContext, reporter: DiagnosticReporter)
    override fun check(declaration: FirFile) {
        val source = declaration.source as? KtLightSourceElement ?: return
        val sourceFilePath = declaration.sourceFile?.path ?: declaration.name

        for (violation in plugin.checkFile(source, declaration.name, sourceFilePath) { ResolvedUsageCollector.collect(declaration) }) {
            val diagnostic = when (violation.level) {
                RuleLevel.ERROR -> WrasseErrors22Container.WRASSE_ERROR
                RuleLevel.WARN -> WrasseErrors22Container.WRASSE_WARNING
                RuleLevel.OFF -> continue
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
