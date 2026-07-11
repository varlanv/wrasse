package com.varlanv.wrasse.plugin.k20

import com.varlanv.wrasse.plugin.WrassePlugin
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.analysis.checkers.declaration.DeclarationCheckers
import org.jetbrains.kotlin.fir.analysis.checkers.expression.ExpressionCheckers
import org.jetbrains.kotlin.fir.analysis.extensions.FirAdditionalCheckersExtension

class WrasseFirChecker20(
    session: FirSession,
    plugin: WrassePlugin,
) : FirAdditionalCheckersExtension(session) {

    override val declarationCheckers = object : DeclarationCheckers() {
        override val fileCheckers = setOf(FirSyntacticChecker20(plugin))
    }
    override val expressionCheckers = object : ExpressionCheckers() {
        override val functionCallCheckers = setOf(FirRestrictedApiChecker20(plugin))
    }
}
