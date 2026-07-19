package com.varlanv.wrasse.plugin

import com.varlanv.wrasse.lang.ConfigValue
import com.varlanv.wrasse.lang.ConfigValueJsonc
import com.varlanv.wrasse.lang.FileWalkUp
import com.varlanv.wrasse.model.WConfig
import com.varlanv.wrasse.model.WRuleSet
import com.varlanv.wrasse.model.WUninitializedRule
import com.varlanv.wrasse.model.WrasseRuleConfig
import com.varlanv.wrasse.rules.ImportOrderingRule
import com.varlanv.wrasse.rules.NoSemicolonsRule
import com.varlanv.wrasse.rules.NoUnusedImportsRule
import com.varlanv.wrasse.rules.NoWildcardImportsRule
import com.varlanv.wrasse.rules.TrailingNewlineRule
import java.nio.file.Path
import org.jetbrains.kotlin.backend.common.push

private val configFileNames = setOf("wrasse.jsonc", "wrasse.json")

/**
 * Every rule wrasse ships, in registration order. This order is [WRuleSet.dispatchForFile]'s
 * `activeRules` order, which is [StreamDispatch][com.varlanv.wrasse.model.StreamDispatch]'s
 * `allRules` order, which is the order `afterFile` is called on every rule for a given file (see
 * `LightTreeStreamAdapter.walk`) — so this list's order is load-bearing for any rule composing
 * across `afterFile`-deferred edits, not just cosmetic. [ImportOrderingRule] must stay after
 * [NoUnusedImportsRule] and [NoWildcardImportsRule]: it composes their `afterFile`-collected
 * edits via `EditPlan.takeEditsIn`, which only sees edits already collected by the time it runs.
 */
internal fun registeredRules(): List<WUninitializedRule> =
    listOf(NoSemicolonsRule(), NoWildcardImportsRule(), TrailingNewlineRule(), NoUnusedImportsRule(), ImportOrderingRule())

fun wrasseMain(
    sourceRoots: List<Path>,
    warnOnly: Boolean = false,
    fixEnabled: Boolean = false,
    fixOutputDir: Path? = null,
    dumpResolvedUsage: Boolean = false,
): Result<WrassePlugin> {
    val uninitializedRules = registeredRules().associateBy { it.id }
    val config =
        loadConfig(
            sourceRoots = sourceRoots,
            uninitializedRules = uninitializedRules,
            warnOnly = warnOnly
        ).getOrElse { return Result.failure(it) }
    val activeRules = mutableListOf<Pair<WUninitializedRule, WrasseRuleConfig>>()
    for ((ruleId, ruleConfig) in config.rulesConfigs.idToConfig) {
        val uninitRule = uninitializedRules[ruleId] ?: continue
        activeRules.push(uninitRule to ruleConfig)
    }
    return Result.success(
        WrassePlugin(
            ruleSet = WRuleSet(activeRules),
            fixEnabled = fixEnabled,
            fixOutputDir = fixOutputDir,
            globalExclude = config.exclude,
            configDir = config.configDir,
            dumpResolvedUsage = dumpResolvedUsage,
        )
    )
}


private fun loadConfig(
    sourceRoots: List<Path>,
    uninitializedRules: Map<String, WUninitializedRule>,
    warnOnly: Boolean
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
            ruleIds = uninitializedRules.keys,
            warnOnly = warnOnly,
            configDir = configDir,
            resolveExtends = resolveExtends,
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
