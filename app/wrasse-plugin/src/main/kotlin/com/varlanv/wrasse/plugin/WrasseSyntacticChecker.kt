package com.varlanv.wrasse.plugin

import com.varlanv.wrasse.adapter.LightTreeAdapter
import com.varlanv.wrasse.model.WFile
import com.varlanv.wrasse.model.WNodeType
import org.jetbrains.kotlin.KtLightSourceElement
import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.diagnostics.reportOn
import org.jetbrains.kotlin.fir.analysis.checkers.MppCheckerKind
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.analysis.checkers.declaration.FirFileChecker
import org.jetbrains.kotlin.fir.declarations.FirFile

object WrasseSyntacticChecker : FirFileChecker(MppCheckerKind.Common) {

    context(context: CheckerContext, reporter: DiagnosticReporter)
    override fun check(declaration: FirFile) {
        val source = declaration.source ?: return
        if (source !is KtLightSourceElement) return

        val filePath = declaration.name
        val wFile = LightTreeAdapter.adapt(source, filePath)

        checkNoSemicolons(wFile, declaration)
    }

    context(context: CheckerContext, reporter: DiagnosticReporter)
    private fun checkNoSemicolons(wFile: WFile, firFile: FirFile) {
        wFile.root.descendants()
            .filter { it.type == WNodeType.SEMICOLON }
            .filter { !it.isInsideNodeOfType(WNodeType.FOR) }
            .filter { !it.isInsideNodeOfType(WNodeType.ENUM_ENTRY) }
            .forEach { node ->
                reporter.reportOn(
                    firFile.source,
                    WrasseErrors.WRASSE_WARNING,
                    "Unnecessary semicolon at column ${node.column}",
                )
            }
    }
}
