package com.varlanv.wrasse.plugin

import org.jetbrains.kotlin.compiler.plugin.AbstractCliOption
import org.jetbrains.kotlin.compiler.plugin.CliOption
import org.jetbrains.kotlin.compiler.plugin.CommandLineProcessor
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.config.CompilerConfigurationKey

val KEY_ENABLED = CompilerConfigurationKey<Boolean>("enabled")

@OptIn(ExperimentalCompilerApi::class)
class WrasseCommandLineProcessor : CommandLineProcessor {
    override val pluginId: String = "com.varlanv.wrasse"

    override val pluginOptions: Collection<AbstractCliOption> = listOf(
        CliOption("enabled", "<true|false>", "Whether the plugin is enabled", required = false),
    )

    override fun processOption(option: AbstractCliOption, value: String, configuration: CompilerConfiguration) {
        when (option.optionName) {
            "enabled" -> configuration.put(KEY_ENABLED, value.toBoolean())
        }
    }
}
