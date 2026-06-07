package com.varlanv.wrasse.compiler

import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.jetbrains.kotlin.fir.extensions.FirExtensionRegistrar

@OptIn(ExperimentalCompilerApi::class)
class WrasseFirExtensionRegistrar : FirExtensionRegistrar() {
    override fun ExtensionRegistrarContext.configurePlugin() {
        +::WrasseFirChecker
    }
}
