package com.varlanv.wrasse.plugin.k20

import com.varlanv.wrasse.plugin.KEY_DUMP_RESOLVED_USAGE
import com.varlanv.wrasse.plugin.KEY_ENABLED
import com.varlanv.wrasse.plugin.KEY_FIX
import com.varlanv.wrasse.plugin.KEY_FIX_OUTPUT_DIR
import com.varlanv.wrasse.plugin.KEY_WARN_ONLY
import com.varlanv.wrasse.plugin.wrasseMain
import org.jetbrains.kotlin.cli.jvm.config.javaSourceRoots
import org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.fir.extensions.FirExtensionRegistrarAdapter
import java.nio.file.Paths

@OptIn(ExperimentalCompilerApi::class)
class WrasseCompilerPluginRegistrar20 : CompilerPluginRegistrar() {
    override val supportsK2: Boolean = true

    override fun ExtensionStorage.registerExtensions(configuration: CompilerConfiguration) {
        val enabled = configuration[KEY_ENABLED, true]
        if (!enabled) {
            return
        }

        val warnOnly = configuration[KEY_WARN_ONLY, false]
        val fixEnabled = configuration[KEY_FIX, false]
        val fixOutputDir = configuration[KEY_FIX_OUTPUT_DIR]?.let { Paths.get(it) }
        val dumpResolvedUsage = configuration[KEY_DUMP_RESOLVED_USAGE, false]
        val sourceRoots = configuration.javaSourceRoots.map { Paths.get(it) }
        val plugin = wrasseMain(
            sourceRoots,
            warnOnly = warnOnly,
            fixEnabled = fixEnabled,
            fixOutputDir = fixOutputDir,
            dumpResolvedUsage = dumpResolvedUsage,
        ).getOrThrow()

        FirExtensionRegistrarAdapter.registerExtension(WrasseFirExtensionRegistrar20(plugin))
    }
}
