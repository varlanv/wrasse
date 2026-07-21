package com.varlanv.wrasse.plugin.k20

import com.varlanv.wrasse.plugin.WrassePlugin
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.jetbrains.kotlin.diagnostics.rendering.RootDiagnosticRendererFactory
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.extensions.FirExtensionRegistrar

@OptIn(ExperimentalCompilerApi::class)
class WrasseFirExtensionRegistrar20(private val plugin: WrassePlugin) : FirExtensionRegistrar() {
    override fun ExtensionRegistrarContext.configurePlugin() {
        +{ session: FirSession -> WrasseFirChecker20(session, plugin) }
        RootDiagnosticRendererFactory.registerFactory(WrasseErrors20.Renderers)
    }
}
