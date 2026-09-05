package com.varlanv.wrasse.plugin.k20

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

class FirSyntacticChecker20(private val plugin: WrassePlugin) : FirFileChecker(MppCheckerKind.Common) {
    override fun check(
        declaration: FirFile,
        context: CheckerContext,
        reporter: DiagnosticReporter,
    ) {
        val source = declaration.source as? KtLightSourceElement ?: return
        val sourceFilePath = declaration.sourceFile?.path ?: declaration.name

        for (violation in plugin.checkFile(
            source,
            declaration.name,
            sourceFilePath,
        ) { collectQualifiedUsages, collectCallSites ->
            ResolvedUsageCollector.collect(declaration, collectQualifiedUsages, collectCallSites)
        }) {
            val diagnostic =
                when (violation.level) {
                    RuleLevel.ERROR -> WrasseErrors20.WRASSE_ERROR
                    RuleLevel.WARN -> WrasseErrors20.WRASSE_WARNING
                    RuleLevel.OFF -> continue
                }
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
