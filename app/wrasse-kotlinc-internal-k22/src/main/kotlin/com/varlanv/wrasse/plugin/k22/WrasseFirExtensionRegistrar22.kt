package com.varlanv.wrasse.plugin.k22

import com.varlanv.wrasse.config.WrasseSeverity
import com.varlanv.wrasse.plugin.WrassePlugin
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.extensions.FirExtensionRegistrar

@OptIn(ExperimentalCompilerApi::class)
class WrasseFirExtensionRegistrar22(
    private val plugin: WrassePlugin,
) : FirExtensionRegistrar() {
    override fun ExtensionRegistrarContext.configurePlugin() {
        val diagnostic = when (plugin.severity) {
            WrasseSeverity.ERROR -> WrasseErrors22Container.WRASSE_ERROR
            WrasseSeverity.WARNING -> WrasseErrors22Container.WRASSE_WARNING
        }
        +{ session: FirSession -> WrasseFirChecker22(session, plugin, diagnostic) }
        registerDiagnosticContainers(WrasseErrors22Container)
    }
}
