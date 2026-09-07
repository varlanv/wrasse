package com.varlanv.wrasse.plugin.internal

import com.varlanv.wrasse.model.RuleLevel
import com.varlanv.wrasse.model.WUninitializedRule
import com.varlanv.wrasse.model.WrasseRuleConfig
import com.varlanv.wrasse.model.WRuleSet
import com.varlanv.wrasse.plugin.WrassePlugin
import com.varlanv.wrasse.rules.LongNumericalValuesRule
import com.varlanv.wrasse.rules.RangeConventionalRule
import java.nio.file.Paths
import org.jetbrains.kotlin.compiler.plugin.AbstractCliOption
import org.jetbrains.kotlin.compiler.plugin.CliOption
import org.jetbrains.kotlin.compiler.plugin.CommandLineProcessor
import org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.config.CompilerConfigurationKey

const val OVERLAPPING_EDITS_TEST_PLUGIN_ID = "com.varlanv.wrasse.test.overlap"
const val OVERLAPPING_EDITS_FIX_OUTPUT_DIR_OPTION = "fixOutputDir"
private val KEY_OVERLAPPING_EDITS_FIX_OUTPUT_DIR = CompilerConfigurationKey<String>(OVERLAPPING_EDITS_FIX_OUTPUT_DIR_OPTION)

/**
 * Real-compile probe support for [OverlappingEditsIsolationSpec]: wires the real
 * [RangeConventionalRule] and [LongNumericalValuesRule] into a live `K2JVMCompiler` run so a
 * source shape whose edits genuinely overlap (`0.rangeTo(1000000)`) is checked through
 * [WrassePlugin] end to end, rather than through the lower-level [com.varlanv.wrasse.model.EditPlan]
 * unit tests alone.
 */
@OptIn(ExperimentalCompilerApi::class)
class OverlappingEditsTestRegistrar : CompilerPluginRegistrar() {
    override val supportsK2: Boolean = true
    override val pluginId: String = OVERLAPPING_EDITS_TEST_PLUGIN_ID

    override fun ExtensionStorage.registerExtensions(configuration: CompilerConfiguration) {
        val fixOutputDir = configuration[KEY_OVERLAPPING_EDITS_FIX_OUTPUT_DIR]?.let(Paths::get)
        val activeRules = listOf(
            RangeConventionalRule() to errorConfig(),
            LongNumericalValuesRule() to errorConfig(),
        )
        val plugin = WrassePlugin(ruleSet = WRuleSet(activeRules), fixOutputDir = fixOutputDir)
        K22Registrar.register(this, plugin)
    }

    private fun errorConfig(): WrasseRuleConfig = WrasseRuleConfig(
        level = RuleLevel.ERROR,
        exclude = emptyList(),
        effectiveLevel = RuleLevel.ERROR,
    )
}

/** Routes [OverlappingEditsTestRegistrar]'s own plugin options into this compile's [CompilerConfiguration]. */
@OptIn(ExperimentalCompilerApi::class)
class OverlappingEditsTestCommandLineProcessor : CommandLineProcessor {
    override val pluginId: String = OVERLAPPING_EDITS_TEST_PLUGIN_ID

    override val pluginOptions: Collection<AbstractCliOption> = listOf(
        CliOption(OVERLAPPING_EDITS_FIX_OUTPUT_DIR_OPTION, "<path>", "Fix output directory", required = false),
    )

    override fun processOption(
        option: AbstractCliOption,
        value: String,
        configuration: CompilerConfiguration,
    ) {
        if (option.optionName == OVERLAPPING_EDITS_FIX_OUTPUT_DIR_OPTION) {
            configuration.put(KEY_OVERLAPPING_EDITS_FIX_OUTPUT_DIR, value)
        }
    }
}
