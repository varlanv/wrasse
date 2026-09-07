package com.varlanv.wrasse.plugin.internal

import com.varlanv.wrasse.plugin.KEY_DUMP_RESOLVED_USAGE
import com.varlanv.wrasse.plugin.KEY_DUMP_RESOLVED_USAGE_STR
import com.varlanv.wrasse.plugin.KEY_ENABLED
import com.varlanv.wrasse.plugin.KEY_ENABLED_STR
import com.varlanv.wrasse.plugin.KEY_EXCLUDED_ROOT
import com.varlanv.wrasse.plugin.KEY_EXCLUDED_ROOT_STR
import com.varlanv.wrasse.plugin.KEY_FIX_OUTPUT_DIR
import com.varlanv.wrasse.plugin.KEY_FIX_OUTPUT_DIR_STR
import com.varlanv.wrasse.plugin.KEY_PROJECT_DIR
import com.varlanv.wrasse.plugin.KEY_PROJECT_DIR_STR
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
        CliOption(
            KEY_ENABLED_STR,
            "<true|false>",
            "Whether the plugin is enabled; the last occurrence wins",
            required = false,
            allowMultipleOccurrences = true,
        ),
        CliOption(
            KEY_WARN_ONLY_STR,
            "<true|false>",
            "Report violations as warnings instead of errors",
            required = false,
        ),
        CliOption(
            KEY_FIX_OUTPUT_DIR_STR,
            "<path>",
            "Directory for autofix patch output; when set, the patch is always emitted",
            required = false,
        ),
        CliOption(
            KEY_DUMP_RESOLVED_USAGE_STR,
            "<true|false>",
            "Dump the resolved-usage facade as a diagnostic per file",
            required = false,
        ),
        CliOption(
            KEY_EXCLUDED_ROOT_STR,
            "<path>",
            "Absolute directory whose files wrasse skips entirely",
            required = false,
            allowMultipleOccurrences = true,
        ),
        CliOption(
            KEY_PROJECT_DIR_STR,
            "<path>",
            "Project directory the diagnostics report stores file paths relative to",
            required = false,
        ),
    )

    override fun processOption(
        option: AbstractCliOption,
        value: String,
        configuration: CompilerConfiguration,
    ) {
        when (option.optionName) {
            KEY_ENABLED_STR -> configuration.put(KEY_ENABLED, value.toBoolean())
            KEY_WARN_ONLY_STR -> configuration.put(KEY_WARN_ONLY, value.toBoolean())
            KEY_FIX_OUTPUT_DIR_STR -> configuration.put(KEY_FIX_OUTPUT_DIR, value)
            KEY_DUMP_RESOLVED_USAGE_STR -> configuration.put(KEY_DUMP_RESOLVED_USAGE, value.toBoolean())
            KEY_EXCLUDED_ROOT_STR -> configuration.add(KEY_EXCLUDED_ROOT, value)
            KEY_PROJECT_DIR_STR -> configuration.put(KEY_PROJECT_DIR, value)
        }
    }
}
