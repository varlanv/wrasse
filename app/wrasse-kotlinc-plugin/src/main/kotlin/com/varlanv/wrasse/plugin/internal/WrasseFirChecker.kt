package com.varlanv.wrasse.plugin.internal

import com.varlanv.wrasse.plugin.WrassePlugin
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.analysis.checkers.declaration.DeclarationCheckers
import org.jetbrains.kotlin.fir.analysis.checkers.expression.ExpressionCheckers
import org.jetbrains.kotlin.fir.analysis.extensions.FirAdditionalCheckersExtension

class WrasseFirChecker(
    session: FirSession,
    plugin: WrassePlugin,
) : FirAdditionalCheckersExtension(session) {

    override val declarationCheckers = object : DeclarationCheckers() {
        override val fileCheckers = setOf(FirSyntacticChecker(plugin))
    }
    override val expressionCheckers = object : ExpressionCheckers() {
        override val functionCallCheckers = setOf(FirRestrictedApiChecker(plugin))
    }
}
