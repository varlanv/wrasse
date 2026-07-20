package com.varlanv.wrasse.plugin.internal

import com.varlanv.wrasse.model.RuleLevel
import com.varlanv.wrasse.model.WContext
import com.varlanv.wrasse.model.WLeafRule
import com.varlanv.wrasse.model.WNodeType
import com.varlanv.wrasse.model.WReporter
import com.varlanv.wrasse.model.WRuleSet
import com.varlanv.wrasse.model.WUninitializedRule
import com.varlanv.wrasse.model.WrasseRuleConfig
import com.varlanv.wrasse.plugin.WrassePlugin
import com.varlanv.wrasse.rules.NoSemicolonsRule
import java.nio.file.Paths
import org.jetbrains.kotlin.compiler.plugin.AbstractCliOption
import org.jetbrains.kotlin.compiler.plugin.CliOption
import org.jetbrains.kotlin.compiler.plugin.CommandLineProcessor
import org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.config.CompilerConfigurationKey

const val THROWING_TEST_RULE_CRASH_MARKER = "wrasseCrashTestMarker"
const val THROWING_TEST_RULE_CRASH_MESSAGE = "boom from throwing test rule"

const val THROWING_TEST_PLUGIN_ID = "com.varlanv.wrasse.test.throwing"
const val FIX_OUTPUT_DIR_OPTION = "fixOutputDir"
const val WITH_EDITING_RULE_OPTION = "withEditingRule"
private val KEY_FIX_OUTPUT_DIR = CompilerConfigurationKey<String>(FIX_OUTPUT_DIR_OPTION)
private val KEY_WITH_EDITING_RULE = CompilerConfigurationKey<Boolean>(WITH_EDITING_RULE_OPTION)

/**
 * Real-compile probe support for [InternalFailureIsolationSpec]: a rule that throws on a magic
 * identifier, wired into a live `K2JVMCompiler` run via [ThrowingRuleTestRegistrar] rather than
 * any production hook — [WrassePlugin] and the internal FIR-registrar glue it rides
 * ([K22Registrar], [WrasseFirExtensionRegistrar], [FirSyntacticChecker]) already accept an
 * arbitrary [WRuleSet], so this is test-only wiring on top of an existing seam.
 */
class ThrowingTestRule : WUninitializedRule {
    override val id: String = "throwing-test-rule"

    override fun initRule(config: WrasseRuleConfig): WLeafRule =
        object : WLeafRule {
            override val id: String = "throwing-test-rule"
            override val config: WrasseRuleConfig = config
            override val targetTypes: Set<WNodeType> = setOf(WNodeType.IDENTIFIER)

            override fun visitLeaf(ctx: WContext, reporter: WReporter) {
                if (ctx.leafText?.toString() == THROWING_TEST_RULE_CRASH_MARKER) {
                    throw IllegalStateException(THROWING_TEST_RULE_CRASH_MESSAGE)
                }
            }
        }
}

/**
 * A second, independent `CompilerPluginRegistrar` entry point loaded only from the test
 * classpath (see [InternalFailureIsolationSpec]'s own `META-INF/services` files under
 * `src/test/resources`). Configured through its own plugin options — parsed into this exact
 * compile's own [CompilerConfiguration] by [ThrowingRuleTestCommandLineProcessor], the same
 * per-call-scoped mechanism the production registrar uses — rather than `wrasse.json`, since it
 * never goes through [com.varlanv.wrasse.plugin.wrasseMain]. The production registrar rides
 * along on the same compile but is neutralized via its own `enabled=false` plugin option.
 */
@OptIn(ExperimentalCompilerApi::class)
class ThrowingRuleTestRegistrar : CompilerPluginRegistrar() {
    override val supportsK2: Boolean = true
    override val pluginId: String = THROWING_TEST_PLUGIN_ID

    override fun ExtensionStorage.registerExtensions(configuration: CompilerConfiguration) {
        val fixOutputDir = configuration[KEY_FIX_OUTPUT_DIR]?.let(Paths::get)
        val withEditingRule = configuration[KEY_WITH_EDITING_RULE, false]

        val activeRules = mutableListOf<Pair<WUninitializedRule, WrasseRuleConfig>>()
        if (withEditingRule) {
            activeRules.add(NoSemicolonsRule() to warnConfig())
        }
        activeRules.add(ThrowingTestRule() to warnConfig())

        val plugin = WrassePlugin(
            ruleSet = WRuleSet(activeRules),
            fixOutputDir = fixOutputDir,
        )
        K22Registrar.register(this, plugin)
    }

    /**
     * Both rules run at `warn`: the crash's own synthetic [com.varlanv.wrasse.model.ViolationReport]
     * is always `RuleLevel.WARN` regardless of the crashing rule's configured level (see
     * `WrassePlugin.internalFailureReports`), so mixing an `error`-level rule into the same
     * multi-file compile as the crash would mean two different [com.varlanv.wrasse.model.RuleLevel]s
     * riding the same custom `WrasseErrors` diagnostic container across different files — an
     * unrelated `KtDiagnosticFactoryToRendererMap`/severity-grouping interaction (confirmed by
     * direct compiler experimentation) drops the lower-severity one in that shape. Keeping both at
     * the same level sidesteps that entirely, so the test stays about wrasse's own crash-isolation
     * behavior.
     */
    private fun warnConfig(): WrasseRuleConfig =
        WrasseRuleConfig(level = RuleLevel.WARN, exclude = emptyList(), effectiveLevel = RuleLevel.WARN)
}

/** Routes [ThrowingRuleTestRegistrar]'s own plugin options into this compile's [CompilerConfiguration]. */
@OptIn(ExperimentalCompilerApi::class)
class ThrowingRuleTestCommandLineProcessor : CommandLineProcessor {
    override val pluginId: String = THROWING_TEST_PLUGIN_ID

    override val pluginOptions: Collection<AbstractCliOption> = listOf(
        CliOption(FIX_OUTPUT_DIR_OPTION, "<path>", "Fix output directory", required = false),
        CliOption(WITH_EDITING_RULE_OPTION, "<true|false>", "Whether to also enable no-semicolons", required = false),
    )

    override fun processOption(option: AbstractCliOption, value: String, configuration: CompilerConfiguration) {
        when (option.optionName) {
            FIX_OUTPUT_DIR_OPTION -> configuration.put(KEY_FIX_OUTPUT_DIR, value)
            WITH_EDITING_RULE_OPTION -> configuration.put(KEY_WITH_EDITING_RULE, value.toBoolean())
        }
    }
}
