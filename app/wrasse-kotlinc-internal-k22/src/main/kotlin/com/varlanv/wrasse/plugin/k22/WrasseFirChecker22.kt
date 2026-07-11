package com.varlanv.wrasse.plugin.k22

import com.varlanv.wrasse.plugin.WrassePlugin
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.analysis.checkers.declaration.DeclarationCheckers
import org.jetbrains.kotlin.fir.analysis.checkers.expression.ExpressionCheckers
import org.jetbrains.kotlin.fir.analysis.extensions.FirAdditionalCheckersExtension

class WrasseFirChecker22(
    session: FirSession,
    plugin: WrassePlugin,
) : FirAdditionalCheckersExtension(session) {

    override val declarationCheckers = object : DeclarationCheckers() {
        override val fileCheckers = setOf(FirSyntacticChecker22(plugin))
    }
    override val expressionCheckers = object : ExpressionCheckers() {
        override val functionCallCheckers = setOf(FirRestrictedApiChecker22(plugin))
    }
}
