package com.varlanv.wrasse.plugin.internal

import com.varlanv.wrasse.config.WrasseSeverity
import com.varlanv.wrasse.plugin.KEY_ENABLED
import com.varlanv.wrasse.plugin.KEY_WARN_ONLY
import com.varlanv.wrasse.plugin.PLUGIN_ID
import com.varlanv.wrasse.plugin.wrasseMain
import org.jetbrains.kotlin.cli.jvm.config.javaSourceRoots
import org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.fir.extensions.FirExtensionRegistrarAdapter
import java.nio.file.Paths

@OptIn(ExperimentalCompilerApi::class)
class WrasseCompilerPluginRegistrar : CompilerPluginRegistrar() {
    override val supportsK2: Boolean = true
    override val pluginId: String = PLUGIN_ID

    override fun ExtensionStorage.registerExtensions(configuration: CompilerConfiguration) {
        val enabled = configuration[KEY_ENABLED, true]
        if (!enabled) {
            return
        }

        val warnOnly = configuration[KEY_WARN_ONLY, false]
        val severity = if (warnOnly) WrasseSeverity.WARNING else WrasseSeverity.ERROR
        val sourceRoots = configuration.javaSourceRoots.map { Paths.get(it) }
        // Give up in case "main" could not be initialized. This can be potentially changed if
        // kotlinc exposes api for more graceful failure than just throwing
        val plugin = wrasseMain(sourceRoots, severity).getOrThrow()

        FirExtensionRegistrarAdapter.registerExtension(
            WrasseFirExtensionRegistrar(plugin)
        )
    }
}
