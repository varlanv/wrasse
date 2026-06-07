package com.varlanv.wrasse.plugin

import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.diagnostics.reportOn
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.analysis.checkers.MppCheckerKind
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.analysis.checkers.declaration.DeclarationCheckers
import org.jetbrains.kotlin.fir.analysis.checkers.expression.ExpressionCheckers
import org.jetbrains.kotlin.fir.analysis.checkers.expression.FirFunctionCallChecker
import org.jetbrains.kotlin.fir.analysis.extensions.FirAdditionalCheckersExtension
import org.jetbrains.kotlin.fir.expressions.FirFunctionCall
import org.jetbrains.kotlin.fir.references.FirResolvedNamedReference
import org.jetbrains.kotlin.fir.symbols.impl.FirNamedFunctionSymbol
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name

class WrasseFirChecker(session: FirSession) : FirAdditionalCheckersExtension(session) {
    override val declarationCheckers = object : DeclarationCheckers() {
        override val fileCheckers = setOf(WrasseSyntacticChecker)
    }
    override val expressionCheckers = object : ExpressionCheckers() {
        override val functionCallCheckers = setOf(RestrictedApiChecker)
    }
}

object RestrictedApiChecker : FirFunctionCallChecker(MppCheckerKind.Common) {

    private val FORBIDDEN = setOf(
        CallableId(FqName("kotlin.io"), Name.identifier("println")),
        CallableId(FqName("kotlin.io"), Name.identifier("print")),
    )

    context(context: CheckerContext, reporter: DiagnosticReporter)
    override fun check(expression: FirFunctionCall) {
        val ref = expression.calleeReference as? FirResolvedNamedReference ?: return
        val symbol = ref.resolvedSymbol as? FirNamedFunctionSymbol ?: return
        val callableId = symbol.callableId

        if (callableId in FORBIDDEN) {
            reporter.reportOn(
                expression.source,
                WrasseErrors.WRASSE_WARNING,
                "Use a logger instead of ${callableId.asSingleFqName()}",
            )
        }
    }
}
