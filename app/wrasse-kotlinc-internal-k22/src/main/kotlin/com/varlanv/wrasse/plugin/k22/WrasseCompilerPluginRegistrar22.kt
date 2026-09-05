package com.varlanv.wrasse.plugin.k22

import com.varlanv.wrasse.plugin.KEY_DUMP_RESOLVED_USAGE
import com.varlanv.wrasse.plugin.KEY_ENABLED
import com.varlanv.wrasse.plugin.KEY_FIX_OUTPUT_DIR
import com.varlanv.wrasse.plugin.KEY_WARN_ONLY
import com.varlanv.wrasse.plugin.WrassePlugin
import com.varlanv.wrasse.plugin.wrasseMain
import java.nio.file.Paths
import org.jetbrains.kotlin.cli.jvm.config.javaSourceRoots
import org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.jetbrains.kotlin.config.AnalysisFlags
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.config.ExplicitApiMode
import org.jetbrains.kotlin.config.languageVersionSettings
import org.jetbrains.kotlin.fir.extensions.FirExtensionRegistrarAdapter

@OptIn(ExperimentalCompilerApi::class)
class WrasseCompilerPluginRegistrar22(private val prebuilt: WrassePlugin? = null) : CompilerPluginRegistrar() {
    override val supportsK2: Boolean = true

    override fun ExtensionStorage.registerExtensions(configuration: CompilerConfiguration) {
        val enabled = configuration[KEY_ENABLED, true]
        if (!enabled) return

        val warnOnly = configuration[KEY_WARN_ONLY, false]
        val fixOutputDir = configuration[KEY_FIX_OUTPUT_DIR]?.let { Paths.get(it) }
        val dumpResolvedUsage = configuration[KEY_DUMP_RESOLVED_USAGE, false]
        val explicitApiActive = configuration.languageVersionSettings.getFlag(
            AnalysisFlags.explicitApiMode,
        ) != ExplicitApiMode.DISABLED
        val sourceRoots = configuration.javaSourceRoots.map { Paths.get(it) }
        val plugin = prebuilt
            ?: wrasseMain(
                sourceRoots,
                warnOnly = warnOnly,
                fixOutputDir = fixOutputDir,
                dumpResolvedUsage = dumpResolvedUsage,
                explicitApiActive = explicitApiActive,
            ).getOrThrow()

        FirExtensionRegistrarAdapter.registerExtension(WrasseFirExtensionRegistrar22(plugin))
    }
}
