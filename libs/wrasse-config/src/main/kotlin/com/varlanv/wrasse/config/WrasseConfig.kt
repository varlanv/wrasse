package com.varlanv.wrasse.config

import java.nio.file.FileSystems
import java.nio.file.PathMatcher

class WrasseConfig(
    val exclude: List<PathMatcher>,
    val rules: WrasseRulesConfig,
) {
    companion object {
        fun pathMatcher(glob: String): PathMatcher =
            FileSystems.getDefault().getPathMatcher("glob:$glob")
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
