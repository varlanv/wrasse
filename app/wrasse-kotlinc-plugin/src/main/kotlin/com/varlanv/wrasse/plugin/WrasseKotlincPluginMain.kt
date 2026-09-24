package com.varlanv.wrasse.plugin

import com.varlanv.koper.lang.collection.calculateHashMapCapacity
import com.varlanv.wrasse.lang.*
import com.varlanv.wrasse.model.*
import com.varlanv.wrasse.rules.*
import org.jetbrains.kotlin.backend.common.push
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import java.nio.file.Path

internal object Static {


    internal val configFileNames = setOf("wrasse.jsonc", "wrasse.json")

    /** Every single-id rule wrasse ships. See [registeredRuleGroups] for fused multi-id engines. */
    internal val registeredRules: List<WUninitializedRule> = listOf(
        AlsoCouldBeApplyRule(),
        BackingPropertyNamingRule(),
        BooleanExpressionsRule(),
        ClassNamingRule(),
        CollapseIfRule(),
        CommentOverPrivateDeclarationRule(),
        ComplexConditionRule(),
        ConstructorParameterNamingRule(),
        CustomLabelRule(),
        DebugPrintRule(),
        DestructuringTooManyEntriesRule(),
        DoubleNegativeRule(),
        EmptyCatchBlockRule(),
        EmptyDefaultConstructorRule(),
        EmptyFunctionBlockRule(),
        EmptyKotlinFileRule(),
        EmptyWhenBlockRule(),
        EnumEntryNamingRule(),
        EqualsNullCallRule(),
        ExceptionRaisedInUnexpectedLocationRule(),
        ExplicitItLambdaMultipleParametersRule(),
        ExplicitItLambdaParameterRule(),
        ExtensionFunctionsSameNameRule(),
        FileSizeRule(),
        FileNamingRule(),
        ForbiddenCallsRule(),
        ForbiddenCommentRule(),
        ForbiddenExpressionBodyFunctionsRule(),
        FunctionExpressionBodyRule(),
        FunctionNamingRule(),
        FunctionOnlyReturningConstantRule(),
        FunctionParameterNamingRule(),
        GetterSetterFieldsRule(),
        GlobalCoroutineUsageRule(),
        IfElseBracingRule(),
        InstanceOfCheckForExceptionRule(),
        InvalidRangeRule(),
        KdocDeprecatedTagRule(),
        KdocReferencesNonPublicPropertyRule(),
        LambdaParameterNamingRule(),
        LambdaReturnRule(),
        LongNumericalValuesRule(),
        LongParameterListRule(),
        LoopWithTooManyJumpStatementsRule(),
        MagicNumberRule(),
        MayBeConstantRule(),
        MissingPackageDeclarationRule(),
        MixedConditionOperatorsRule(),
        NamedArgumentsRule(),
        NestedClassesVisibilityRule(),
        NoConsecutiveCommentsRule(),
        NoEmptyClassBodyRule(),
        NoEmptyParensBeforeTrailingLambdaRule(),
        NoSemicolonsRule(),
        NoSingleLineBlockCommentRule(),
        NoUnitReturnRule(),
        NotImplementedDeclarationRule(),
        PackageNamingRule(),
        PrintStackTraceRule(),
        PropertyNamingRule(),
        RangeConventionalRule(),
        RedundantConstructorKeywordRule(),
        RedundantToStringInTemplateRule(),
        RethrowCaughtExceptionRule(),
        SafeCastRule(),
        StringConcatenationRule(),
        StringShouldBeRawStringRule(),
        SwallowedExceptionRule(),
        SyncInAsyncRule(),
        ThrowingExceptionInMainRule(),
        TooGenericExceptionCaughtRule(),
        TooGenericExceptionThrownRule(),
        TrailingNewlineRule(),
        TrimMultilineRawStringRule(),
        TrivialAccessorsRule(),
        UnconditionalJumpStatementInLoopRule(),
        UnnecessaryBacktickRule(),
        UnnecessaryInheritanceRule(),
        UnnecessaryPartOfBinaryExpressionRule(),
        UnusedParameterRule(),
        UnusedPrivateClassRule(),
        UseLetRule(),
        UselessPostfixExpressionRule(),
        VariableNameMaxLengthRule(),
        WhenEntryBracingRule(),
        WhenMustHaveElseRule(),
    )

    /**
     * Every fused multi-id engine wrasse ships: [ImportEngine], backing
     * `no-unused-imports`/`no-wildcard-imports`/`import-ordering`/`no-unnecessary-fqn` behind one
     * decision-maker; [ModifierEngine], backing `modifier-order`/`redundant-visibility-modifier`;
     * [FunctionNameLengthEngine], backing `function-name-max-length`/`function-name-min-length`;
     * [FunctionMetricsEngine], backing `return-count`/`throws-count`/`nested-block-depth`/
     * `cyclomatic-complexity`/`long-method`; [ClassMetricsEngine], backing
     * `too-many-functions`/`large-class`; [KdocEngine], backing `undocumented-public-class`/
     * `undocumented-public-function`/`undocumented-public-property`/`kdoc-tag-mismatch`; and
     * [EmptyBlockEngine], backing `empty-if-block`/`empty-else-block`/`empty-for-block`/
     * `empty-while-block`/`empty-do-while-block`/`empty-finally-block`/`empty-try-block`/
     * `empty-init-block`/`empty-secondary-constructor`; and [CommentPositionEngine], backing
     * `kdoc-placement`/`type-argument-comment`/`type-parameter-comment`/`value-argument-comment`/
     * `value-parameter-comment`. Composition is internal to each engine, so unlike [registeredRules]
     * this list carries no registration-order constraint.
     */
    internal val registeredRuleGroups: List<WUninitializedRuleGroup> = listOf(
        ImportEngine(),
        ModifierEngine(),
        FunctionNameLengthEngine(),
        FunctionMetricsEngine(),
        ClassMetricsEngine(),
        KdocEngine(),
        EmptyBlockEngine(),
        CommentPositionEngine(),
    )
    internal val uninitializedRules = registeredRules.associateBy { it.id }
    internal val allRuleIds = uninitializedRules.keys + registeredRuleGroups.flatMap { it.ids }

    internal val ruleOptionSpecs: Map<String, List<WRuleOptionSpec>> = HashMap<String, List<WRuleOptionSpec>>(calculateHashMapCapacity(128)).apply {
        for ((ruleId, rule) in uninitializedRules) put(ruleId, rule.options)
        for (group in registeredRuleGroups) for (ruleId in group.ids) put(ruleId, group.optionSpecs[ruleId] ?: emptyList())
    }

}

fun wrasseMain(
    sourceRoots: List<Path>,
    warnOnly: Boolean = false,
    fixOutputDir: Path? = null,
    dumpResolvedUsage: Boolean = false,
    explicitApiActive: Boolean = false,
    excludedRoots: List<Path> = emptyList(),
    projectDir: Path? = null,
    messageCollector: MessageCollector = MessageCollector.NONE,
): WrassePlugin {
    val config = loadConfig(
        sourceRoots = sourceRoots,
        ruleIds = Static.allRuleIds,
        warnOnly = warnOnly,
        explicitApiActive = explicitApiActive,
        ruleOptionSpecs = Static.ruleOptionSpecs,
    )
    val activeRules = mutableListOf<Pair<WUninitializedRule, WrasseRuleConfig>>()
    for ((ruleId, ruleConfig) in config.rulesConfigs.idToConfig) {
        val uninitRule = Static.uninitializedRules[ruleId] ?: continue
        activeRules.push(uninitRule to ruleConfig)
    }
    val activeGroups = mutableListOf<Pair<WUninitializedRuleGroup, Map<String, WrasseRuleConfig>>>()
    for (group in Static.registeredRuleGroups) {
        val configs = group.ids
            .mapNotNull { ruleId -> config.rulesConfigs.idToConfig[ruleId]?.let { ruleId to it } }
            .toMap()
        if (configs.isNotEmpty()) activeGroups.push(group to configs)
    }
    val request = if (fixOutputDir != null) FormatRequest.consume(fixOutputDir) else RunRequest.NONE
    return WrassePlugin(
        ruleSet = WRuleSet(activeRules, activeGroups),
        fixOutputDir = fixOutputDir,
        globalExclude = config.exclude,
        excludedRoots = excludedRoots,
        configDir = config.configDir,
        dumpResolvedUsage = dumpResolvedUsage,
        formatConfig = config.format,
        formatRun = request.formatting,
        quiet = request.quiet,
        perf = WPerf.create(active = request.debugPerformance),
        messageCollector = messageCollector,
        projectDir = projectDir,
    )
}

private fun loadConfig(
    sourceRoots: List<Path>,
    ruleIds: Set<String>,
    warnOnly: Boolean,
    explicitApiActive: Boolean,
    ruleOptionSpecs: Map<String, List<WRuleOptionSpec>>,
): WConfig {
    for (root in sourceRoots) {
        val startDir = if (root.toFile().isFile) root.parent ?: continue else root
        val configPath = try {
            FileWalkUp.find(startDir) { it in Static.configFileNames }
        } catch (e: Exception) {
            throw IllegalStateException("wrasse: error searching for config from $root: ${e.message}", e)
        } ?: continue
        val configDir = configPath.parent
        val text = configPath.toFile().readText()
        val configVal = try {
            ConfigValueJsonc.parse(input = text)
        } catch (e: Exception) {
            throw IllegalStateException(
                "wrasse: failed to parse $configPath: ${e.message}",
                e
            )
        }
        val resolveExtends = resolveExtendsFrom(configDir)
        return try {
            WConfig.from(
                configValue = configVal,
                ruleIds = ruleIds,
                warnOnly = warnOnly,
                configDir = configDir,
                resolveExtends = resolveExtends,
                explicitApiActive = explicitApiActive,
                ruleOptionSpecs = ruleOptionSpecs,
            )
        } catch (e: Exception) {
            throw IllegalStateException("wrasse: invalid config in $configPath: ${e.message}", e)
        }
    }
    error("wrasse: config file not found. Searched upward from source roots: $sourceRoots for: $Static.configFileNames")
}

/** Resolves each `"extends"` link's path against the directory of the file that named it, not the leaf config's. */
private fun resolveExtendsFrom(baseDir: Path): ExtendsResolver = ExtendsResolver { relativePath ->
    val resolved = baseDir.resolve(relativePath).normalize()
    if (!resolved.toFile().isFile) {
        error("wrasse: extended config not found: $resolved")
    } else {
        val text = resolved.toFile().readText()
        try {
            val res = ConfigValueJsonc
                .parse(input = text)
            ExtendsResolution(res, resolveExtendsFrom(resolved.parent))
        } catch (e: Exception) {
            throw IllegalStateException("wrasse: failed to parse $resolved: ${e.message}", e)
        }
    }
}
