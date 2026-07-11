package com.varlanv.wrasse.model

import com.varlanv.wrasse.lang.ConfigValue
import com.varlanv.wrasse.lang.Property
import com.varlanv.wrasse.lang.SafeProperties
import java.nio.file.FileSystems
import java.nio.file.PathMatcher

class WConfig(
    val exclude: List<PathMatcher>,
    val rulesConfigs: WrasseRulesConfig,
) {
    companion object {

        fun from(configValue: ConfigValue, ruleIds: Set<String>, warnOnly: Boolean): Result<WConfig> {
            if (configValue !is ConfigValue.Obj) {
                return Result.failure(Exception("Expected object at root, got ${configValue.typeName()}"))
            }
            val root = configValue.value

            val exclude = root.require("exclude", ConfigValue.StrArr::class.java)
                .fold({ it.value.map { glob -> pathMatcher(glob) } }, { return Result.failure(it) })

            val rulesObj = root.require("rules", ConfigValue.Obj::class.java)
                .fold({ it.value }, { return Result.failure(it) })

            val ruleIdToConfig = mutableMapOf<String, WrasseRuleConfig>()
            for (ruleId in ruleIds) {
                val ruleConfig = parseRuleConfig(rulesProps = rulesObj, key = ruleId, warnOnly = warnOnly)
                    .getOrElse { return Result.failure(it) }
                if (ruleConfig.level != RuleLevel.OFF) {
                    ruleIdToConfig[ruleId] = ruleConfig
                }
            }
            return Result.success(
                WConfig(
                    exclude = exclude,
                    rulesConfigs = WrasseRulesConfig(
                        idToConfig = ruleIdToConfig
                    ),
                )
            )
        }

        private fun parseRuleConfig(
            rulesProps: SafeProperties,
            key: String,
            warnOnly: Boolean
        ): Result<WrasseRuleConfig> {
            val ruleObj = rulesProps.require(key, ConfigValue.Obj::class.java)
                .fold({ it.value }, { return Result.failure(it) })

            val level = parseLevel(ruleObj, key)
                .getOrElse { return Result.failure(it) }

            val exclude = ruleObj.require("exclude", ConfigValue.StrArr::class.java)
                .fold({ it.value.map { glob -> pathMatcher(glob) } }, { return Result.failure(it) })

            var configuredLevel = level
            if (warnOnly && configuredLevel == RuleLevel.ERROR) {
                configuredLevel = RuleLevel.WARN
            }

            return Result.success(
                WrasseRuleConfig(
                    level = level,
                    exclude = exclude,
                    effectiveLevel = configuredLevel
                )
            )
        }

        private fun parseLevel(props: SafeProperties, ruleKey: String): Result<RuleLevel> {
            val prop = props.get("level", ConfigValue.Str::class.java)
            return when (prop) {
                is Property.Val -> when (prop.value.value) {
                    "off" -> Result.success(RuleLevel.OFF)
                    "warn" -> Result.success(RuleLevel.WARN)
                    "error" -> Result.success(RuleLevel.ERROR)
                    else -> Result.failure(
                        Exception("Invalid level '${prop.value.value}' for rule '$ruleKey'; expected 'off', 'warn', or 'error'")
                    )
                }

                is Property.Missing -> Result.failure(
                    Exception("Missing required property 'level' for rule '$ruleKey'")
                )

                is Property.TypeMismatch -> Result.failure(
                    Exception("Property 'level' for rule '$ruleKey' must be a string, got ${prop.actual.typeName()}")
                )
            }
        }

        private fun pathMatcher(glob: String): PathMatcher =
            FileSystems.getDefault().getPathMatcher("glob:$glob")
    }
}

class WrasseRulesConfig(
    val idToConfig: Map<String, WrasseRuleConfig>,
)

class WrasseRuleConfig(
    val level: RuleLevel,
    val exclude: List<PathMatcher>,
    val effectiveLevel: RuleLevel
)

enum class RuleLevel {
    OFF,
    WARN,
    ERROR,
}
