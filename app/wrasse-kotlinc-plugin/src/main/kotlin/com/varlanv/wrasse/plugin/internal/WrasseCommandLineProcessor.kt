package com.varlanv.wrasse.plugin.internal

import com.varlanv.wrasse.plugin.KEY_ENABLED
import com.varlanv.wrasse.plugin.KEY_ENABLED_STR
import com.varlanv.wrasse.plugin.KEY_WARN_ONLY
import com.varlanv.wrasse.plugin.KEY_WARN_ONLY_STR
import com.varlanv.wrasse.plugin.PLUGIN_ID
import org.jetbrains.kotlin.compiler.plugin.AbstractCliOption
import org.jetbrains.kotlin.compiler.plugin.CliOption
import org.jetbrains.kotlin.compiler.plugin.CommandLineProcessor
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.jetbrains.kotlin.config.CompilerConfiguration

@OptIn(ExperimentalCompilerApi::class)
class WrasseCommandLineProcessor : CommandLineProcessor {
    override val pluginId: String = PLUGIN_ID

    override val pluginOptions: Collection<AbstractCliOption> = listOf(
        CliOption(KEY_ENABLED_STR, "<true|false>", "Whether the plugin is enabled", required = false),
        CliOption(KEY_WARN_ONLY_STR, "<true|false>", "Report violations as warnings instead of errors", required = false),
    )

    override fun processOption(option: AbstractCliOption, value: String, configuration: CompilerConfiguration) {
        when (option.optionName) {
            KEY_ENABLED_STR -> configuration.put(KEY_ENABLED, value.toBoolean())
            KEY_WARN_ONLY_STR -> configuration.put(KEY_WARN_ONLY, value.toBoolean())
        }
    }
}
