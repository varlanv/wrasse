package com.varlanv.wrasse.config

import com.varlanv.wrasse.lang.ConfigValue
import com.varlanv.wrasse.lang.SafeProperties
import java.nio.file.FileSystems
import java.nio.file.PathMatcher

class WrasseConfig(
    val exclude: List<PathMatcher>,
    val rulesConfigs: WrasseRulesConfig,
) {
    companion object {

        fun from(configValue: ConfigValue): Result<WrasseConfig> {
            if (configValue !is ConfigValue.Obj) {
                return Result.failure(Exception("Expected object at root, got ${configValue.typeName()}"))
            }
            val root = configValue.value

            val exclude = root.require("exclude", ConfigValue.StrArr::class.java)
                .fold({ it.value.map { glob -> pathMatcher(glob) } }, { return Result.failure(it) })

            val rulesObj = root.require("rules", ConfigValue.Obj::class.java)
                .fold({ it.value }, { return Result.failure(it) })

            val noSemicolons = parseRuleToggle(rulesObj, "no-semicolons")
                .getOrElse { return Result.failure(it) }
            val noWildcardImports = parseRuleToggle(rulesObj, "no-wildcard-imports")
                .getOrElse { return Result.failure(it) }
            val trailingNewline = parseRuleToggle(rulesObj, "trailing-newline")
                .getOrElse { return Result.failure(it) }

            return Result.success(
                WrasseConfig(
                    exclude = exclude,
                    rulesConfigs = WrasseRulesConfig(
                        noSemicolons = noSemicolons,
                        noWildcardImports = noWildcardImports,
                        trailingNewline = trailingNewline,
                    ),
                )
            )
        }

        private fun parseRuleToggle(rulesProps: SafeProperties, key: String): Result<WrasseRuleToggle> {
            val ruleObj = rulesProps.require(key, ConfigValue.Obj::class.java)
                .fold({ it.value }, { return Result.failure(it) })

            val enabled = ruleObj.require("enabled", ConfigValue.Bool::class.java)
                .fold({ it.value }, { return Result.failure(it) })

            val exclude = ruleObj.require("exclude", ConfigValue.StrArr::class.java)
                .fold({ it.value.map { glob -> pathMatcher(glob) } }, { return Result.failure(it) })

            return Result.success(
                WrasseRuleToggle(
                    enabled = enabled,
                    exclude = exclude,
                )
            )
        }

        private fun pathMatcher(glob: String): PathMatcher =
            FileSystems.getDefault().getPathMatcher("glob:$glob")
    }
}

class WrasseRulesConfig(
    val noSemicolons: WrasseRuleToggle,
    val noWildcardImports: WrasseRuleToggle,
    val trailingNewline: WrasseRuleToggle,
)

class WrasseRuleToggle(
    val enabled: Boolean,
    val exclude: List<PathMatcher>,
)

enum class WrasseSeverity {
    ERROR,
    WARNING,
}
