package com.varlanv.wrasse.plugin.internal

import com.varlanv.wrasse.config.WrasseSeverity
import com.varlanv.wrasse.plugin.WrassePlugin
import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.diagnostics.reportOn
import org.jetbrains.kotlin.fir.analysis.checkers.MppCheckerKind
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.analysis.checkers.expression.FirFunctionCallChecker
import org.jetbrains.kotlin.fir.expressions.FirFunctionCall
import org.jetbrains.kotlin.fir.references.FirResolvedNamedReference
import org.jetbrains.kotlin.fir.symbols.impl.FirNamedFunctionSymbol

class FirRestrictedApiChecker(
    private val plugin: WrassePlugin,
) : FirFunctionCallChecker(MppCheckerKind.Common) {

    context(context: CheckerContext, reporter: DiagnosticReporter)
    override fun check(expression: FirFunctionCall) {
        val ref = expression.calleeReference as? FirResolvedNamedReference ?: return
        val symbol = ref.resolvedSymbol as? FirNamedFunctionSymbol ?: return
        val callableId = symbol.callableId

        val message = plugin.checkCall(callableId.packageName.asString(), callableId.callableName.asString())
            ?: return

        val diagnostic = when (plugin.severity) {
            WrasseSeverity.ERROR -> WrasseErrors.WRASSE_ERROR
            WrasseSeverity.WARNING -> WrasseErrors.WRASSE_WARNING
        }
        reporter.reportOn(expression.source, diagnostic, message)
    }
}
