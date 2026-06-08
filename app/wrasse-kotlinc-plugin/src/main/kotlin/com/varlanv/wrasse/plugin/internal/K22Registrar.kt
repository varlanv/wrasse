package com.varlanv.wrasse.plugin.internal

import com.varlanv.wrasse.plugin.WrassePlugin
import org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar.ExtensionStorage
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.jetbrains.kotlin.fir.extensions.FirExtensionRegistrarAdapter

@OptIn(ExperimentalCompilerApi::class)
internal object K22Registrar {
    fun register(storage: ExtensionStorage, plugin: WrassePlugin) {
        with(storage) {
            FirExtensionRegistrarAdapter.registerExtension(WrasseFirExtensionRegistrar(plugin))
        }
    }
}
