package com.varlanv.wrasse.plugin

import com.varlanv.wrasse.lang.ConfigValue
import com.varlanv.wrasse.lang.ConfigValueJsonc
import com.varlanv.wrasse.lang.FileWalkUp
import com.varlanv.wrasse.model.SplitRules
import com.varlanv.wrasse.model.WConfig
import com.varlanv.wrasse.model.WRule
import com.varlanv.wrasse.model.WUninitializedRule
import com.varlanv.wrasse.rules.NoSemicolonsRule
import com.varlanv.wrasse.rules.NoWildcardImportsRule
import com.varlanv.wrasse.rules.TrailingNewlineVisitor
import org.jetbrains.kotlin.backend.common.push
import java.nio.file.Path

private val configFileNames = setOf("wrasse.jsonc", "wrasse.json")

fun wrasseMain(sourceRoots: List<Path>, warnOnly: Boolean = false): Result<WrassePlugin> {
    val uninitializedRules = sequenceOf(NoSemicolonsRule(), NoWildcardImportsRule(), TrailingNewlineVisitor())
        .associateBy { it.id }
    val config =
        loadConfig(
            sourceRoots = sourceRoots,
            uninitializedRules = uninitializedRules,
            warnOnly = warnOnly
        ).getOrElse { return Result.failure(it) }
    val rules = mutableListOf<WRule>()
    for ((ruleId, ruleConfig) in config.rulesConfigs.idToConfig) {
        val uninitRule = uninitializedRules[ruleId] ?: continue
        val rule = uninitRule.initRule(ruleConfig)
        rules.push(rule)
    }
    return Result.success(
        WrassePlugin(
            rules = SplitRules(rules),
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
