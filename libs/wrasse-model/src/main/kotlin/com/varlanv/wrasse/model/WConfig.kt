package com.varlanv.wrasse.model

import com.varlanv.wrasse.lang.ConfigValue
import com.varlanv.wrasse.lang.Property
import com.varlanv.wrasse.lang.SafeProperties
import java.nio.file.FileSystems
import java.nio.file.Path
import java.nio.file.PathMatcher

class WConfig(
    val exclude: List<PathMatcher>,
    val rulesConfigs: WrasseRulesConfig,
    val configDir: Path?,
) {
    companion object {

        private const val MAX_EXTENDS_DEPTH = 10

        fun from(
            configValue: ConfigValue,
            ruleIds: Set<String>,
            warnOnly: Boolean,
            configDir: Path? = null,
            resolveExtends: ((String) -> Result<ConfigValue>)? = null,
        ): Result<WConfig> {
            val raw = resolveRaw(configValue, resolveExtends, depth = 0)
                .getOrElse { return Result.failure(it) }
            return buildConfig(raw, ruleIds, warnOnly, configDir)
        }

        private fun resolveRaw(
            configValue: ConfigValue,
            resolveExtends: ((String) -> Result<ConfigValue>)?,
            depth: Int,
        ): Result<RawConfig> {
            if (depth > MAX_EXTENDS_DEPTH) {
                return Result.failure(Exception("'extends' chain exceeds $MAX_EXTENDS_DEPTH levels (circular reference?)"))
            }
            if (configValue !is ConfigValue.Obj) {
                return Result.failure(Exception("Expected object at root, got ${configValue.typeName()}"))
            }
            val root = configValue.value

            val base = when (val extendsProp = root.get("extends", ConfigValue.Str::class.java)) {
                is Property.Val -> {
                    if (resolveExtends == null) {
                        return Result.failure(Exception("'extends' is not supported in this context"))
                    }
                    val baseValue = resolveExtends(extendsProp.value.value)
                        .getOrElse { return Result.failure(it) }
                    resolveRaw(baseValue, resolveExtends, depth + 1)
                        .getOrElse { return Result.failure(it) }
                }
                is Property.TypeMismatch -> {
                    return Result.failure(Exception("'extends' must be a string, got ${extendsProp.actual.typeName()}"))
                }
                is Property.Missing -> null
            }

            var excludeSet = false
            val exclude = when (val prop = root.get("exclude", ConfigValue.StrArr::class.java)) {
                is Property.Val -> { excludeSet = true; prop.value.value }
                is Property.Missing -> emptyList()
                is Property.TypeMismatch -> return Result.failure(
                    Exception("'exclude' must be a string array, got ${prop.actual.typeName()}")
                )
            }

            val rules = mutableMapOf<String, RawRuleConfig>()
            when (val rulesProp = root.get("rules", ConfigValue.Obj::class.java)) {
                is Property.Val -> {
                    val rulesObj = rulesProp.value.value
                    for (ruleId in rulesObj.keys()) {
                        val rawRule = parseRawRuleConfig(rulesObj, ruleId)
                            .getOrElse { return Result.failure(it) }
                        rules[ruleId] = rawRule
                    }
                }
                is Property.Missing -> {}
                is Property.TypeMismatch -> return Result.failure(
                    Exception("'rules' must be an object, got ${rulesProp.actual.typeName()}")
                )
            }

            return if (base != null) {
                Result.success(mergeRaw(base, RawConfig(exclude, excludeSet, rules)))
            } else {
                Result.success(RawConfig(exclude, excludeSet, rules))
            }
        }

        private fun mergeRaw(base: RawConfig, child: RawConfig): RawConfig {
            val exclude = if (child.excludeSet) child.exclude else base.exclude
            val rules = LinkedHashMap<String, RawRuleConfig>(base.rules)
            for ((id, childRule) in child.rules) {
                val baseRule = rules[id]
                if (baseRule != null) {
                    rules[id] = RawRuleConfig(
                        level = childRule.level ?: baseRule.level,
                        exclude = if (childRule.excludeSet) childRule.exclude else baseRule.exclude,
                        excludeSet = childRule.excludeSet || baseRule.excludeSet,
                    )
                } else {
                    rules[id] = childRule
                }
            }
            return RawConfig(exclude, excludeSet = true, rules)
        }

        private fun buildConfig(
            raw: RawConfig,
            ruleIds: Set<String>,
            warnOnly: Boolean,
            configDir: Path?,
        ): Result<WConfig> {
            val globalExclude = raw.exclude.map { pathMatcher(it) }
            val ruleIdToConfig = mutableMapOf<String, WrasseRuleConfig>()

            for (ruleId in ruleIds) {
                val rawRule = raw.rules[ruleId] ?: continue
                val level = rawRule.level ?: RuleLevel.OFF
                if (level == RuleLevel.OFF) continue
                val effectiveLevel = if (warnOnly && level == RuleLevel.ERROR) RuleLevel.WARN else level
                ruleIdToConfig[ruleId] = WrasseRuleConfig(
                    level = level,
                    exclude = rawRule.exclude.map { pathMatcher(it) },
                    effectiveLevel = effectiveLevel,
                )
            }

            return Result.success(
                WConfig(
                    exclude = globalExclude,
                    rulesConfigs = WrasseRulesConfig(idToConfig = ruleIdToConfig),
                    configDir = configDir,
                )
            )
        }

        private fun parseRawRuleConfig(rulesProps: SafeProperties, key: String): Result<RawRuleConfig> {
            val ruleObj = when (val prop = rulesProps.get(key, ConfigValue.Obj::class.java)) {
                is Property.Val -> prop.value.value
                is Property.Missing -> return Result.success(RawRuleConfig(level = null, exclude = emptyList(), excludeSet = false))
                is Property.TypeMismatch -> return Result.failure(
                    Exception("Rule '$key' must be an object, got ${prop.actual.typeName()}")
                )
            }

            val level = when (val prop = ruleObj.get("level", ConfigValue.Str::class.java)) {
                is Property.Val -> when (prop.value.value) {
                    "off" -> RuleLevel.OFF
                    "warn" -> RuleLevel.WARN
                    "error" -> RuleLevel.ERROR
                    else -> return Result.failure(
                        Exception("Invalid level '${prop.value.value}' for rule '$key'; expected 'off', 'warn', or 'error'")
                    )
                }
                is Property.Missing -> null
                is Property.TypeMismatch -> return Result.failure(
                    Exception("Property 'level' for rule '$key' must be a string, got ${prop.actual.typeName()}")
                )
            }

            var excludeSet = false
            val exclude = when (val prop = ruleObj.get("exclude", ConfigValue.StrArr::class.java)) {
                is Property.Val -> { excludeSet = true; prop.value.value }
                is Property.Missing -> emptyList()
                is Property.TypeMismatch -> return Result.failure(
                    Exception("Property 'exclude' for rule '$key' must be a string array, got ${prop.actual.typeName()}")
                )
            }

            return Result.success(RawRuleConfig(level = level, exclude = exclude, excludeSet = excludeSet))
        }

        private fun pathMatcher(glob: String): PathMatcher =
            FileSystems.getDefault().getPathMatcher("glob:$glob")
    }
}

private class RawConfig(
    val exclude: List<String>,
    val excludeSet: Boolean,
    val rules: Map<String, RawRuleConfig>,
)

private class RawRuleConfig(
    val level: RuleLevel?,
    val exclude: List<String>,
    val excludeSet: Boolean,
)

class WrasseRulesConfig(
    val idToConfig: Map<String, WrasseRuleConfig>,
)

class WrasseRuleConfig(
    val level: RuleLevel,
    val exclude: List<PathMatcher>,
    val effectiveLevel: RuleLevel,
)

enum class RuleLevel {
    OFF,
    WARN,
    ERROR,
}
