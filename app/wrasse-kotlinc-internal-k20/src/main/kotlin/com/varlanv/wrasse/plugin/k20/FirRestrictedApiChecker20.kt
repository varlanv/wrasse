package com.varlanv.wrasse.plugin.k20

import com.varlanv.wrasse.plugin.WrassePlugin
import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.diagnostics.reportOn
import org.jetbrains.kotlin.fir.analysis.checkers.MppCheckerKind
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.analysis.checkers.expression.FirFunctionCallChecker
import org.jetbrains.kotlin.fir.expressions.FirFunctionCall
import org.jetbrains.kotlin.fir.references.FirResolvedNamedReference
import org.jetbrains.kotlin.fir.symbols.impl.FirNamedFunctionSymbol

class FirRestrictedApiChecker20(private val plugin: WrassePlugin) : FirFunctionCallChecker(MppCheckerKind.Common) {
    override fun check(
        expression: FirFunctionCall,
        context: CheckerContext,
        reporter: DiagnosticReporter,
    ) {
        val ref = expression.calleeReference as? FirResolvedNamedReference ?: return
        val symbol = ref.resolvedSymbol as? FirNamedFunctionSymbol ?: return
        val callableId = symbol.callableId

        val message = plugin.checkCall(callableId.packageName.asString(), callableId.callableName.asString()) ?: return

        reporter.reportOn(expression.source, WrasseErrors20.WRASSE_ERROR, message, context)
    }
}
