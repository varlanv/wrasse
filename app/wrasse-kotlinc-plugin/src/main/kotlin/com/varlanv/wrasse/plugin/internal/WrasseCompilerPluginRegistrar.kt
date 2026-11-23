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
import org.jetbrains.kotlin.config.MessageCollectorAccess
import org.jetbrains.kotlin.config.languageVersionSettings
import org.jetbrains.kotlin.fir.extensions.FirExtensionRegistrarAdapter

@OptIn(ExperimentalCompilerApi::class, MessageCollectorAccess::class)
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
        val explicitApiActive = configuration.languageVersionSettings.getFlag(AnalysisFlags.explicitApiMode) !=
            ExplicitApiMode.DISABLED
        val sourceRoots = configuration.javaSourceRoots.map { Paths.get(it) }
        if (sourceRoots.isEmpty()) {
            return
        }

        val plugin = wrasseMain(
            sourceRoots = sourceRoots,
            warnOnly = warnOnly,
            fixOutputDir = fixOutputDir,
            dumpResolvedUsage = dumpResolvedUsage,
            explicitApiActive = explicitApiActive,
            excludedRoots = excludedRoots,
            projectDir = projectDir,
            messageCollector = messageCollector,
        )

        FirExtensionRegistrarAdapter.registerExtension(WrasseFirExtensionRegistrar(plugin))
    }
}
