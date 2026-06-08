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
        val plugin = wrasseMain(sourceRoots, severity).getOrThrow()

        val cl = this::class.java.classLoader
        when {
            classExists(cl, "org.jetbrains.kotlin.extensions.ExtensionPointDescriptor") -> {
                K22Registrar.register(this, plugin)
            }
            classExists(cl, "org.jetbrains.kotlin.diagnostics.KtDiagnosticsContainer") -> {
                delegateToRegistrar("com.varlanv.wrasse.plugin.k22.WrasseCompilerPluginRegistrar22", configuration)
            }
            else -> {
                delegateToRegistrar("com.varlanv.wrasse.plugin.k20.WrasseCompilerPluginRegistrar20", configuration)
            }
        }
    }

    private fun ExtensionStorage.delegateToRegistrar(className: String, configuration: CompilerConfiguration) {
        val registrar = Class.forName(className)
            .getDeclaredConstructor()
            .newInstance() as CompilerPluginRegistrar
        with(registrar) { this@delegateToRegistrar.registerExtensions(configuration) }
    }

    private fun classExists(cl: ClassLoader, name: String): Boolean {
        return try {
            cl.loadClass(name)
            true
        } catch (_: ClassNotFoundException) {
            false
        }
    }
}
