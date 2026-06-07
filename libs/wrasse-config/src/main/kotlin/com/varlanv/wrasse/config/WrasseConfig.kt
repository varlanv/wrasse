package com.varlanv.wrasse.config

import com.varlanv.wrasse.lang.ConfigValue
import java.nio.file.FileSystems
import java.nio.file.PathMatcher

class WrasseConfig(
    val exclude: List<PathMatcher>,
    val rules: WrasseRulesConfig,
) {
    companion object {

        fun from(configValue: ConfigValue): Result<WrasseConfig> =
            when (configValue) {
                is ConfigValue.Obj -> TODO()
                else -> Result.failure(Exception("Expected object on root level"))
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
