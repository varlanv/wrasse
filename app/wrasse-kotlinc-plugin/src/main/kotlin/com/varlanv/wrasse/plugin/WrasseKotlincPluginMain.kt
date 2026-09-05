package com.varlanv.wrasse.plugin

import com.varlanv.wrasse.lang.ConfigValue
import com.varlanv.wrasse.lang.ConfigValueJsonc
import com.varlanv.wrasse.lang.FileWalkUp
import com.varlanv.wrasse.model.WConfig
import com.varlanv.wrasse.model.WRuleOptionSpec
import com.varlanv.wrasse.model.WRuleSet
import com.varlanv.wrasse.model.WUninitializedRule
import com.varlanv.wrasse.model.WUninitializedRuleGroup
import com.varlanv.wrasse.model.WrasseRuleConfig
import com.varlanv.wrasse.rules.AlsoCouldBeApplyRule
import com.varlanv.wrasse.rules.BackingPropertyNamingRule
import com.varlanv.wrasse.rules.BooleanExpressionsRule
import com.varlanv.wrasse.rules.ClassMetricsEngine
import com.varlanv.wrasse.rules.ClassNamingRule
import com.varlanv.wrasse.rules.CollapseIfRule
import com.varlanv.wrasse.rules.CommentOverPrivateDeclarationRule
import com.varlanv.wrasse.rules.CommentPositionEngine
import com.varlanv.wrasse.rules.ComplexConditionRule
import com.varlanv.wrasse.rules.ConstructorParameterNamingRule
import com.varlanv.wrasse.rules.CustomLabelRule
import com.varlanv.wrasse.rules.DebugPrintRule
import com.varlanv.wrasse.rules.DestructuringTooManyEntriesRule
import com.varlanv.wrasse.rules.DoubleNegativeRule
import com.varlanv.wrasse.rules.EmptyBlockEngine
import com.varlanv.wrasse.rules.EmptyCatchBlockRule
import com.varlanv.wrasse.rules.EmptyDefaultConstructorRule
import com.varlanv.wrasse.rules.EmptyFunctionBlockRule
import com.varlanv.wrasse.rules.EmptyKotlinFileRule
import com.varlanv.wrasse.rules.EmptyWhenBlockRule
import com.varlanv.wrasse.rules.EnumEntryNamingRule
import com.varlanv.wrasse.rules.EqualsNullCallRule
import com.varlanv.wrasse.rules.ExceptionRaisedInUnexpectedLocationRule
import com.varlanv.wrasse.rules.ExplicitItLambdaMultipleParametersRule
import com.varlanv.wrasse.rules.ExplicitItLambdaParameterRule
import com.varlanv.wrasse.rules.ExtensionFunctionsSameNameRule
import com.varlanv.wrasse.rules.FileNamingRule
import com.varlanv.wrasse.rules.FileSizeRule
import com.varlanv.wrasse.rules.ForbiddenCallsRule
import com.varlanv.wrasse.rules.ForbiddenCommentRule
import com.varlanv.wrasse.rules.ForbiddenExpressionBodyFunctionsRule
import com.varlanv.wrasse.rules.FunctionExpressionBodyRule
import com.varlanv.wrasse.rules.FunctionMetricsEngine
import com.varlanv.wrasse.rules.FunctionNameLengthEngine
import com.varlanv.wrasse.rules.FunctionNamingRule
import com.varlanv.wrasse.rules.FunctionOnlyReturningConstantRule
import com.varlanv.wrasse.rules.FunctionParameterNamingRule
import com.varlanv.wrasse.rules.GetterSetterFieldsRule
import com.varlanv.wrasse.rules.GlobalCoroutineUsageRule
import com.varlanv.wrasse.rules.IfElseBracingRule
import com.varlanv.wrasse.rules.ImportEngine
import com.varlanv.wrasse.rules.InstanceOfCheckForExceptionRule
import com.varlanv.wrasse.rules.InvalidRangeRule
import com.varlanv.wrasse.rules.KdocDeprecatedTagRule
import com.varlanv.wrasse.rules.KdocEngine
import com.varlanv.wrasse.rules.KdocReferencesNonPublicPropertyRule
import com.varlanv.wrasse.rules.LambdaParameterNamingRule
import com.varlanv.wrasse.rules.LambdaReturnRule
import com.varlanv.wrasse.rules.LongNumericalValuesRule
import com.varlanv.wrasse.rules.LongParameterListRule
import com.varlanv.wrasse.rules.LoopWithTooManyJumpStatementsRule
import com.varlanv.wrasse.rules.MagicNumberRule
import com.varlanv.wrasse.rules.MayBeConstantRule
import com.varlanv.wrasse.rules.MissingPackageDeclarationRule
import com.varlanv.wrasse.rules.MixedArgumentsRule
import com.varlanv.wrasse.rules.MixedConditionOperatorsRule
import com.varlanv.wrasse.rules.ModifierEngine
import com.varlanv.wrasse.rules.NestedClassesVisibilityRule
import com.varlanv.wrasse.rules.NoConsecutiveCommentsRule
import com.varlanv.wrasse.rules.NamedArgumentsRule
import com.varlanv.wrasse.rules.NoEmptyClassBodyRule
import com.varlanv.wrasse.rules.NoEmptyParensBeforeTrailingLambdaRule
import com.varlanv.wrasse.rules.NoSemicolonsRule
import com.varlanv.wrasse.rules.NoSingleLineBlockCommentRule
import com.varlanv.wrasse.rules.NoUnitReturnRule
import com.varlanv.wrasse.rules.NotImplementedDeclarationRule
import com.varlanv.wrasse.rules.PackageNamingRule
import com.varlanv.wrasse.rules.PrintStackTraceRule
import com.varlanv.wrasse.rules.PropertyNamingRule
import com.varlanv.wrasse.rules.RangeConventionalRule
import com.varlanv.wrasse.rules.RedundantConstructorKeywordRule
import com.varlanv.wrasse.rules.RedundantToStringInTemplateRule
import com.varlanv.wrasse.rules.RethrowCaughtExceptionRule
import com.varlanv.wrasse.rules.SafeCastRule
import com.varlanv.wrasse.rules.StringConcatenationRule
import com.varlanv.wrasse.rules.StringShouldBeRawStringRule
import com.varlanv.wrasse.rules.SwallowedExceptionRule
import com.varlanv.wrasse.rules.SyncInAsyncRule
import com.varlanv.wrasse.rules.ThrowingExceptionInMainRule
import com.varlanv.wrasse.rules.TooGenericExceptionCaughtRule
import com.varlanv.wrasse.rules.TooGenericExceptionThrownRule
import com.varlanv.wrasse.rules.TrailingNewlineRule
import com.varlanv.wrasse.rules.TrimMultilineRawStringRule
import com.varlanv.wrasse.rules.TrivialAccessorsRule
import com.varlanv.wrasse.rules.UnconditionalJumpStatementInLoopRule
import com.varlanv.wrasse.rules.UnnecessaryBacktickRule
import com.varlanv.wrasse.rules.UnnecessaryInheritanceRule
import com.varlanv.wrasse.rules.UnnecessaryPartOfBinaryExpressionRule
import com.varlanv.wrasse.rules.UnusedParameterRule
import com.varlanv.wrasse.rules.UnusedPrivateClassRule
import com.varlanv.wrasse.rules.UseLetRule
import com.varlanv.wrasse.rules.UselessPostfixExpressionRule
import com.varlanv.wrasse.rules.VariableNameMaxLengthRule
import com.varlanv.wrasse.rules.WhenEntryBracingRule
import com.varlanv.wrasse.rules.WhenMustHaveElseRule
import java.nio.file.Path
import org.jetbrains.kotlin.backend.common.push

private val configFileNames = setOf("wrasse.jsonc", "wrasse.json")

/** Every single-id rule wrasse ships. See [registeredRuleGroups] for fused multi-id engines. */
internal fun registeredRules(): List<WUninitializedRule> = listOf(
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
    MixedArgumentsRule(),
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
internal fun registeredRuleGroups(): List<WUninitializedRuleGroup> = listOf(
    ImportEngine(),
    ModifierEngine(),
    FunctionNameLengthEngine(),
    FunctionMetricsEngine(),
    ClassMetricsEngine(),
    KdocEngine(),
    EmptyBlockEngine(),
    CommentPositionEngine(),
)

fun wrasseMain(
    sourceRoots: List<Path>,
    warnOnly: Boolean = false,
    fixOutputDir: Path? = null,
    dumpResolvedUsage: Boolean = false,
    explicitApiActive: Boolean = false,
): Result<WrassePlugin> {
    val uninitializedRules = registeredRules().associateBy { it.id }
    val groups = registeredRuleGroups()
    val allRuleIds = uninitializedRules.keys + groups.flatMap { it.ids }
    val ruleOptionSpecs = HashMap<String, List<WRuleOptionSpec>>()
    for ((ruleId, rule) in uninitializedRules) ruleOptionSpecs[ruleId] = rule.options
    for (group in groups) for (ruleId in group.ids) ruleOptionSpecs[ruleId] = group.optionSpecs[ruleId] ?: emptyList()
    val config = loadConfig(
        sourceRoots = sourceRoots,
        ruleIds = allRuleIds,
        warnOnly = warnOnly,
        explicitApiActive = explicitApiActive,
        ruleOptionSpecs = ruleOptionSpecs,
    ).getOrElse { return Result.failure(it) }
    val activeRules = mutableListOf<Pair<WUninitializedRule, WrasseRuleConfig>>()
    for ((ruleId, ruleConfig) in config.rulesConfigs.idToConfig) {
        val uninitRule = uninitializedRules[ruleId] ?: continue
        activeRules.push(uninitRule to ruleConfig)
    }
    val activeGroups = mutableListOf<Pair<WUninitializedRuleGroup, Map<String, WrasseRuleConfig>>>()
    for (group in groups) {
        val configs = group.ids
            .mapNotNull { ruleId -> config.rulesConfigs.idToConfig[ruleId]?.let { ruleId to it } }
            .toMap()
        if (configs.isNotEmpty()) activeGroups.push(group to configs)
    }
    return Result.success(
        WrassePlugin(
            ruleSet = WRuleSet(activeRules, activeGroups),
            fixOutputDir = fixOutputDir,
            globalExclude = config.exclude,
            configDir = config.configDir,
            dumpResolvedUsage = dumpResolvedUsage,
            formatConfig = config.format,
        ),
    )
}

private fun loadConfig(
    sourceRoots: List<Path>,
    ruleIds: Set<String>,
    warnOnly: Boolean,
    explicitApiActive: Boolean,
    ruleOptionSpecs: Map<String, List<WRuleOptionSpec>>,
): Result<WConfig> {
    for (root in sourceRoots) {
        val startDir = if (root.toFile().isFile) root.parent ?: continue else root
        val configPath = FileWalkUp.find(startDir) { it in configFileNames }.getOrElse {
            return Result.failure(Exception("wrasse: error searching for config from $root: ${it.message}", it))
        } ?: continue
        val configDir = configPath.parent
        val text = configPath.toFile().readText()
        val configValue = ConfigValueJsonc
            .parse(input = text)
            .getOrElse { return Result.failure(Exception("wrasse: failed to parse $configPath: ${it.message}", it)) }
        val resolveExtends = resolveExtendsFrom(configDir)
        return WConfig
            .from(
                configValue = configValue,
                ruleIds = ruleIds,
                warnOnly = warnOnly,
                configDir = configDir,
                resolveExtends = resolveExtends,
                explicitApiActive = explicitApiActive,
                ruleOptionSpecs = ruleOptionSpecs,
            )
            .getOrElse { return Result.failure(Exception("wrasse: invalid config in $configPath: ${it.message}", it)) }
            .let { Result.success(it) }
    }
    throw Exception(
        "wrasse: config file not found. Searched upward from source roots: $sourceRoots for: $configFileNames",
    )
}

private fun resolveExtendsFrom(baseDir: Path): (String) -> Result<ConfigValue> = { relativePath ->
    val resolved = baseDir.resolve(relativePath).normalize()
    if (!resolved.toFile().isFile) {
        Result.failure(Exception("wrasse: extended config not found: $resolved"))
    } else {
        val text = resolved.toFile().readText()
        ConfigValueJsonc
            .parse(input = text)
            .fold(
                { Result.success(it) },
                { Result.failure(Exception("wrasse: failed to parse $resolved: ${it.message}", it)) },
            )
    }
}
