package com.varlanv.wrasse.plugin.internal

import com.varlanv.wrasse.plugin.WrassePlugin
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.extensions.FirExtensionRegistrar

@OptIn(ExperimentalCompilerApi::class)
class WrasseFirExtensionRegistrar(private val plugin: WrassePlugin) : FirExtensionRegistrar() {
    override fun ExtensionRegistrarContext.configurePlugin() {
        +{ session: FirSession -> WrasseFirChecker(session, plugin) }
        registerDiagnosticContainers(WrasseErrors)
    }
}
