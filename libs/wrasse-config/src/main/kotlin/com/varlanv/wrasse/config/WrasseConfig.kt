package com.varlanv.wrasse.config

import com.varlanv.wrasse.lang.ConfigValue
import com.varlanv.wrasse.lang.Property
import com.varlanv.wrasse.lang.SafeProperties
import java.nio.file.FileSystems
import java.nio.file.PathMatcher

class WrasseConfig(
    val exclude: List<PathMatcher>,
    val rules: WrasseRulesConfig,
) {
    companion object {

        fun from(configValue: ConfigValue): Result<WrasseConfig> = when (configValue) {
            is ConfigValue.Obj -> {
                val rootProps = configValue.value
                val exclude = rootProps.require(key = "exclude", ConfigValue.StrArr::class.java).getOrElse {
                    return Result.failure(it)
                }
                val excludeGlobs =
                    exclude.value.map { glob -> pathMatcher(glob = glob).getOrElse { ex -> return Result.failure(ex) } }
                val rulesConfig = WrasseRulesConfig(
                    noSemicolons = commonRuleToggle(
                        key = "no-semicolons",
                        props = rootProps
                    ).getOrElse { ex -> return Result.failure(ex) }
                )
                return Result.success(
                    WrasseConfig(
                        exclude = excludeGlobs,
                        rules = rulesConfig
                    )
                )
            }

            else -> Result.failure(Exception("Expected object on root level"))
        }

        private fun commonRuleToggle(key: String, props: SafeProperties): Result<WrasseRuleToggle> {
            val prop = props.get(
                key,
                ConfigValue.Obj::class.java
            )
            when (prop) {
                is Property.Missing -> return Result.failure(Exception("Missing"))
                is Property.TypeMismatch -> return Result.failure(Exception(""))
                is Property.Val<ConfigValue.Obj> -> {
                    val enabled = prop.value.value.require("enabled", ConfigValue.Str::class.java)
                        .getOrElse { ex -> return Result.failure(ex) }
                    val exclude = prop.value.value.require("exclude", ConfigValue.StrArr::class.java)
                        .getOrElse { ex -> return Result.failure(ex) }
                    val severity = prop.value.value.require("severity", ConfigValue.Str::class.java)
                        .getOrElse { ex -> return Result.failure(ex) }
                    val severityEnum = runCatching { WrasseSeverity.valueOf(severity.value) }.getOrElse { ex ->
                        return Result.failure(ex)
                    }
                    val excludeGlobs = exclude.value
                        .map { glob -> pathMatcher(glob).getOrElse { ex -> return Result.failure(ex) } }
                    return Result.success(
                        WrasseRuleToggle(
                            enabled = enabled.value == "true",
                            severity = severityEnum,
                            exclude = excludeGlobs
                        )
                    )
                }
            }
        }

        fun pathMatcher(glob: String): Result<PathMatcher> = runCatching {
            FileSystems.getDefault().getPathMatcher("glob:$glob")
        }
    }
}

class WrasseRulesConfig(
    val noSemicolons: WrasseRuleToggle,
)

class WrasseRuleToggle(
    val enabled: Boolean,
    val severity: WrasseSeverity,
    val exclude: List<PathMatcher>,
)

enum class WrasseSeverity {
    ERROR,
    WARNING,
}
