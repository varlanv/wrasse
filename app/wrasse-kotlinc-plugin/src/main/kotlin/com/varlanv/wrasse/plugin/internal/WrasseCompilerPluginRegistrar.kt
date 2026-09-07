package com.varlanv.wrasse.plugin.internal

import com.varlanv.wrasse.plugin.KEY_DUMP_RESOLVED_USAGE
import com.varlanv.wrasse.plugin.KEY_ENABLED
import com.varlanv.wrasse.plugin.KEY_EXCLUDED_ROOT
import com.varlanv.wrasse.plugin.KEY_FIX_OUTPUT_DIR
import com.varlanv.wrasse.plugin.KEY_PROJECT_DIR
import com.varlanv.wrasse.plugin.KEY_WARN_ONLY
import com.varlanv.wrasse.plugin.PLUGIN_ID
import com.varlanv.wrasse.plugin.WrassePlugin
import com.varlanv.wrasse.plugin.wrasseMain
import java.nio.file.Paths
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.cli.jvm.config.javaSourceRoots
import org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.jetbrains.kotlin.config.AnalysisFlags
import org.jetbrains.kotlin.config.CommonConfigurationKeys
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.config.ExplicitApiMode
import org.jetbrains.kotlin.config.languageVersionSettings

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
        val fixOutputDir = configuration[KEY_FIX_OUTPUT_DIR]?.let { Paths.get(it) }
        val dumpResolvedUsage = configuration[KEY_DUMP_RESOLVED_USAGE, false]
        val excludedRoots = configuration.getList(KEY_EXCLUDED_ROOT).map { Paths.get(it).toAbsolutePath().normalize() }
        val projectDir = configuration[KEY_PROJECT_DIR]?.let { Paths.get(it).toAbsolutePath().normalize() }
        val messageCollector = configuration[CommonConfigurationKeys.MESSAGE_COLLECTOR_KEY] ?: MessageCollector.NONE
        val explicitApiActive = configuration.languageVersionSettings.getFlag(
            AnalysisFlags.explicitApiMode,
        ) != ExplicitApiMode.DISABLED
        val sourceRoots = configuration.javaSourceRoots.map { Paths.get(it) }
        val plugin = wrasseMain(
            sourceRoots = sourceRoots,
            warnOnly = warnOnly,
            fixOutputDir = fixOutputDir,
            dumpResolvedUsage = dumpResolvedUsage,
            explicitApiActive = explicitApiActive,
            excludedRoots = excludedRoots,
            projectDir = projectDir,
            messageCollector = messageCollector,
        ).getOrThrow()

        val cl = this::class.java.classLoader
        when {
            classExists(cl, "org.jetbrains.kotlin.extensions.ExtensionPointDescriptor") -> {
                K22Registrar.register(this, plugin)
            }

            classExists(cl, "org.jetbrains.kotlin.diagnostics.KtDiagnosticsContainer") -> {
                delegateToRegistrar(
                    "com.varlanv.wrasse.plugin.k22.WrasseCompilerPluginRegistrar22",
                    configuration,
                    plugin,
                )
            }

            else -> {
                delegateToRegistrar(
                    "com.varlanv.wrasse.plugin.k20.WrasseCompilerPluginRegistrar20",
                    configuration,
                    plugin,
                )
            }
        }
    }

    private fun ExtensionStorage.delegateToRegistrar(
        className: String,
        configuration: CompilerConfiguration,
        plugin: WrassePlugin,
    ) {
        val registrar =
            Class
                .forName(className)
                .getDeclaredConstructor(WrassePlugin::class.java)
                .newInstance(plugin) as CompilerPluginRegistrar
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
