package com.varlanv.wrasse.compiler

import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.fir.extensions.FirExtensionRegistrarAdapter

@OptIn(ExperimentalCompilerApi::class)
class WrasseCompilerPluginRegistrar : CompilerPluginRegistrar() {
    override val supportsK2: Boolean = true
    override val pluginId: String = "com.varlanv.wrasse"

    override fun ExtensionStorage.registerExtensions(configuration: CompilerConfiguration) {
        val enabled = configuration.get(KEY_ENABLED, true)
        if (!enabled) return

        FirExtensionRegistrarAdapter.registerExtension(WrasseFirExtensionRegistrar())
        IrGenerationExtension.registerExtension(WrasseIrGenerationExtension())
    }
}
