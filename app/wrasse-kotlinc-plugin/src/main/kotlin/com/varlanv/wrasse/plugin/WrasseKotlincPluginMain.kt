package com.varlanv.wrasse.plugin

import com.varlanv.wrasse.lang.ConfigValue
import com.varlanv.wrasse.lang.ConfigValueJsonc
import com.varlanv.wrasse.lang.FileWalkUp
import com.varlanv.wrasse.model.WConfig
import com.varlanv.wrasse.model.WRuleSet
import com.varlanv.wrasse.model.WUninitializedRule
import com.varlanv.wrasse.model.WUninitializedRuleGroup
import com.varlanv.wrasse.model.WrasseRuleConfig
import com.varlanv.wrasse.rules.IfElseBracingRule
import com.varlanv.wrasse.rules.ImportEngine
import com.varlanv.wrasse.rules.ModifierOrderRule
import com.varlanv.wrasse.rules.NoEmptyClassBodyRule
import com.varlanv.wrasse.rules.NoEmptyParensBeforeTrailingLambdaRule
import com.varlanv.wrasse.rules.NoSemicolonsRule
import com.varlanv.wrasse.rules.NoUnitReturnRule
import com.varlanv.wrasse.rules.RedundantVisibilityModifierRule
import com.varlanv.wrasse.rules.TrailingNewlineRule
import com.varlanv.wrasse.rules.WhenEntryBracingRule
import java.nio.file.Path
import org.jetbrains.kotlin.backend.common.push

private val configFileNames = setOf("wrasse.jsonc", "wrasse.json")

/** Every single-id rule wrasse ships. See [registeredRuleGroups] for fused multi-id engines. */
internal fun registeredRules(): List<WUninitializedRule> =
    listOf(
        IfElseBracingRule(),
        ModifierOrderRule(),
        NoEmptyClassBodyRule(),
        NoEmptyParensBeforeTrailingLambdaRule(),
        NoSemicolonsRule(),
        NoUnitReturnRule(),
        RedundantVisibilityModifierRule(),
        TrailingNewlineRule(),
        WhenEntryBracingRule(),
    )

/**
 * Every fused multi-id engine wrasse ships — currently just [ImportEngine], backing
 * `no-unused-imports`/`no-wildcard-imports`/`import-ordering` behind one decision-maker.
 * Composition is internal to the engine, so unlike [registeredRules] this list carries no
 * registration-order constraint.
 */
internal fun registeredRuleGroups(): List<WUninitializedRuleGroup> = listOf(ImportEngine())

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
    val config =
        loadConfig(
            sourceRoots = sourceRoots,
            ruleIds = allRuleIds,
            warnOnly = warnOnly,
            explicitApiActive = explicitApiActive,
        ).getOrElse { return Result.failure(it) }
    val activeRules = mutableListOf<Pair<WUninitializedRule, WrasseRuleConfig>>()
    for ((ruleId, ruleConfig) in config.rulesConfigs.idToConfig) {
        val uninitRule = uninitializedRules[ruleId] ?: continue
        activeRules.push(uninitRule to ruleConfig)
    }
    val activeGroups = mutableListOf<Pair<WUninitializedRuleGroup, Map<String, WrasseRuleConfig>>>()
    for (group in groups) {
        val configs = group.ids.mapNotNull { ruleId -> config.rulesConfigs.idToConfig[ruleId]?.let { ruleId to it } }.toMap()
        if (configs.isNotEmpty()) activeGroups.push(group to configs)
    }
    return Result.success(
        WrassePlugin(
            ruleSet = WRuleSet(activeRules, activeGroups),
            fixOutputDir = fixOutputDir,
            globalExclude = config.exclude,
            configDir = config.configDir,
            dumpResolvedUsage = dumpResolvedUsage,
        )
    )
}


private fun loadConfig(
    sourceRoots: List<Path>,
    ruleIds: Set<String>,
    warnOnly: Boolean,
    explicitApiActive: Boolean,
): Result<WConfig> {
    for (root in sourceRoots) {
        val startDir = if (root.toFile().isFile) root.parent ?: continue else root
        val configPath = FileWalkUp.find(startDir) { it in configFileNames }
            .getOrElse {
                return Result.failure(
                    Exception(
                        "wrasse: error searching for config from $root: ${it.message}",
                        it
                    )
                )
            }
            ?: continue
        val configDir = configPath.parent
        val text = configPath.toFile().readText()
        val configValue = ConfigValueJsonc.parse(input = text)
            .getOrElse { return Result.failure(Exception("wrasse: failed to parse $configPath: ${it.message}", it)) }
        val resolveExtends = resolveExtendsFrom(configDir)
        return WConfig.from(
            configValue = configValue,
            ruleIds = ruleIds,
            warnOnly = warnOnly,
            configDir = configDir,
            resolveExtends = resolveExtends,
            explicitApiActive = explicitApiActive,
        )
            .getOrElse { return Result.failure(Exception("wrasse: invalid config in $configPath: ${it.message}", it)) }
            .let { Result.success(it) }
    }
    throw Exception("wrasse: config file not found. Searched upward from source roots: $sourceRoots for: $configFileNames")
}

private fun resolveExtendsFrom(baseDir: Path): (String) -> Result<ConfigValue> = { relativePath ->
    val resolved = baseDir.resolve(relativePath).normalize()
    if (!resolved.toFile().isFile) {
        Result.failure(Exception("wrasse: extended config not found: $resolved"))
    } else {
        val text = resolved.toFile().readText()
        ConfigValueJsonc.parse(input = text)
            .fold(
                { Result.success(it) },
                { Result.failure(Exception("wrasse: failed to parse $resolved: ${it.message}", it)) }
            )
    }
}
