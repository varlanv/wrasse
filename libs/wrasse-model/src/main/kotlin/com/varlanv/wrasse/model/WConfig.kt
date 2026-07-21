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
    val format: WFormatConfig,
) {
    companion object {

        private const val MAX_EXTENDS_DEPTH = 10

        fun from(
            configValue: ConfigValue,
            ruleIds: Set<String>,
            warnOnly: Boolean,
            configDir: Path? = null,
            resolveExtends: ((String) -> Result<ConfigValue>)? = null,
            explicitApiActive: Boolean = false,
        ): Result<WConfig> {
            val raw = resolveRaw(configValue, resolveExtends, depth = 0)
                .getOrElse { return Result.failure(it) }
            return buildConfig(raw, ruleIds, warnOnly, configDir, explicitApiActive)
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
                is Property.Missing -> {
                    null
                }
            }

            var excludeSet = false
            val exclude = when (val prop = root.get("exclude", ConfigValue.StrArr::class.java)) {
                is Property.Val -> { excludeSet = true; prop.value.value }
                is Property.Missing -> {
                    emptyList()
                }
                is Property.TypeMismatch -> {
                    return Result.failure(
                        Exception("'exclude' must be a string array, got ${prop.actual.typeName()}")
                    )
                }
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
                is Property.TypeMismatch -> {
                    return Result.failure(
                        Exception("'rules' must be an object, got ${rulesProp.actual.typeName()}")
                    )
                }
            }

            var formatSet = false
            val format = when (val formatProp = root.get("format", ConfigValue.Obj::class.java)) {
                is Property.Val -> {
                    formatSet = true
                    parseRawFormatConfig(formatProp.value.value).getOrElse { return Result.failure(it) }
                }
                is Property.Missing -> null
                is Property.TypeMismatch -> {
                    return Result.failure(
                        Exception("'format' must be an object, got ${formatProp.actual.typeName()}")
                    )
                }
            }

            return if (base != null) {
                Result.success(mergeRaw(base, RawConfig(exclude, excludeSet, rules, format, formatSet)))
            } else {
                Result.success(RawConfig(exclude, excludeSet, rules, format, formatSet))
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
            val format = if (child.formatSet) child.format else base.format
            return RawConfig(exclude, excludeSet = true, rules, format, formatSet = true)
        }

        private fun buildConfig(
            raw: RawConfig,
            ruleIds: Set<String>,
            warnOnly: Boolean,
            configDir: Path?,
            explicitApiActive: Boolean,
        ): Result<WConfig> {
            val globalExclude = raw.exclude.map { pathMatcher(it) }
            val format = buildFormatConfig(raw.format, warnOnly, explicitApiActive)
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
                    explicitApiActive = explicitApiActive,
                    formatEnabled = format.enabled,
                )
            }

            return Result.success(
                WConfig(
                    exclude = globalExclude,
                    rulesConfigs = WrasseRulesConfig(idToConfig = ruleIdToConfig),
                    configDir = configDir,
                    format = format,
                )
            )
        }

        private fun buildFormatConfig(
            raw: RawFormatConfig?,
            warnOnly: Boolean,
            explicitApiActive: Boolean,
        ): WFormatConfig {
            val enabled = raw?.enabled ?: false
            val level = if (enabled) RuleLevel.ERROR else RuleLevel.OFF
            val effectiveLevel = if (warnOnly && level == RuleLevel.ERROR) RuleLevel.WARN else level
            return WFormatConfig(
                enabled = enabled,
                style = FormatStyle(
                    indentWidth = raw?.indentWidth ?: 4,
                    maxLineLength = raw?.maxLineLength ?: 140,
                    trailingCommas = raw?.trailingCommas ?: true,
                    importLayout = raw?.importLayout ?: ImportLayout.ASCII,
                    multilineSignatureThreshold = raw?.multilineSignatureThreshold,
                ),
                ruleConfig = WrasseRuleConfig(
                    level = level,
                    exclude = emptyList(),
                    effectiveLevel = effectiveLevel,
                    explicitApiActive = explicitApiActive,
                    formatEnabled = enabled,
                ),
            )
        }

        private fun parseRawFormatConfig(formatObj: SafeProperties): Result<RawFormatConfig> {
            val enabled = when (val prop = formatObj.get("enabled", ConfigValue.Bool::class.java)) {
                is Property.Val -> prop.value.value
                is Property.Missing -> true
                is Property.TypeMismatch -> {
                    return Result.failure(
                        Exception("Property 'enabled' for 'format' must be a boolean, got ${prop.actual.typeName()}")
                    )
                }
            }

            val indentWidth = when (val prop = formatObj.get("indentWidth", ConfigValue.Num::class.java)) {
                is Property.Val -> prop.value.value.toInt()
                is Property.Missing -> null
                is Property.TypeMismatch -> {
                    return Result.failure(
                        Exception("Property 'indentWidth' for 'format' must be a number, got ${prop.actual.typeName()}")
                    )
                }
            }

            val maxLineLength = when (val prop = formatObj.get("maxLineLength", ConfigValue.Num::class.java)) {
                is Property.Val -> prop.value.value.toInt()
                is Property.Missing -> null
                is Property.TypeMismatch -> {
                    return Result.failure(
                        Exception("Property 'maxLineLength' for 'format' must be a number, got ${prop.actual.typeName()}")
                    )
                }
            }

            val trailingCommas = when (val prop = formatObj.get("trailingCommas", ConfigValue.Bool::class.java)) {
                is Property.Val -> prop.value.value
                is Property.Missing -> null
                is Property.TypeMismatch -> {
                    return Result.failure(
                        Exception("Property 'trailingCommas' for 'format' must be a boolean, got ${prop.actual.typeName()}")
                    )
                }
            }

            val importLayout = when (val prop = formatObj.get("importLayout", ConfigValue.Str::class.java)) {
                is Property.Val -> when (prop.value.value) {
                    "ascii" -> ImportLayout.ASCII
                    else -> return Result.failure(
                        Exception("Invalid importLayout '${prop.value.value}' for 'format'; expected 'ascii'")
                    )
                }
                is Property.Missing -> null
                is Property.TypeMismatch -> {
                    return Result.failure(
                        Exception("Property 'importLayout' for 'format' must be a string, got ${prop.actual.typeName()}")
                    )
                }
            }

            val multilineSignatureThreshold = when (
                val prop = formatObj.get("multilineSignatureThreshold", ConfigValue.Num::class.java)
            ) {
                is Property.Val -> prop.value.value.toInt()
                is Property.Missing -> null
                is Property.TypeMismatch -> {
                    return Result.failure(
                        Exception(
                            "Property 'multilineSignatureThreshold' for 'format' must be a number, got " +
                                prop.actual.typeName()
                        )
                    )
                }
            }

            return Result.success(
                RawFormatConfig(
                    enabled = enabled,
                    indentWidth = indentWidth,
                    maxLineLength = maxLineLength,
                    trailingCommas = trailingCommas,
                    importLayout = importLayout,
                    multilineSignatureThreshold = multilineSignatureThreshold,
                )
            )
        }

        private fun parseRawRuleConfig(rulesProps: SafeProperties, key: String): Result<RawRuleConfig> {
            val ruleObj = when (val prop = rulesProps.get(key, ConfigValue.Obj::class.java)) {
                is Property.Val -> prop.value.value
                is Property.Missing -> return Result.success(RawRuleConfig(level = null, exclude = emptyList(), excludeSet = false))
                is Property.TypeMismatch -> {
                    return Result.failure(
                        Exception("Rule '$key' must be an object, got ${prop.actual.typeName()}")
                    )
                }
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
                is Property.TypeMismatch -> {
                    return Result.failure(
                        Exception("Property 'level' for rule '$key' must be a string, got ${prop.actual.typeName()}")
                    )
                }
            }

            var excludeSet = false
            val exclude = when (val prop = ruleObj.get("exclude", ConfigValue.StrArr::class.java)) {
                is Property.Val -> { excludeSet = true; prop.value.value }
                is Property.Missing -> {
                    emptyList()
                }
                is Property.TypeMismatch -> {
                    return Result.failure(
                        Exception("Property 'exclude' for rule '$key' must be a string array, got ${prop.actual.typeName()}")
                    )
                }
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
    val format: RawFormatConfig?,
    val formatSet: Boolean,
)

private class RawRuleConfig(
    val level: RuleLevel?,
    val exclude: List<String>,
    val excludeSet: Boolean,
)

private class RawFormatConfig(
    val enabled: Boolean,
    val indentWidth: Int?,
    val maxLineLength: Int?,
    val trailingCommas: Boolean?,
    val importLayout: ImportLayout?,
    val multilineSignatureThreshold: Int?,
)

class WrasseRulesConfig(
    val idToConfig: Map<String, WrasseRuleConfig>,
)

class WrasseRuleConfig(
    val level: RuleLevel,
    val exclude: List<PathMatcher>,
    val effectiveLevel: RuleLevel,
    /**
     * True when the current compile runs under Kotlin's explicit API mode (`-Xexplicit-api=strict`
     * or `-Xexplicit-api=warning`). Compile-wide, not user-configurable via `wrasse.json` — set
     * uniformly on every rule's config from `WrasseCompilerPluginRegistrar`'s own read of
     * `CompilerConfiguration.languageVersionSettings`. Only
     * [com.varlanv.wrasse.rules.ModifierEngine] consults it for `redundant-visibility-modifier`: an
     * explicit `public` is a required declaration under that mode, not redundant, so the id
     * self-disables entirely rather than risk breaking an explicit-API build.
     */
    val explicitApiActive: Boolean = false,
    /**
     * True when `format` is enabled for this compile (D23's pattern applied to a second
     * cross-cutting fact: set uniformly on every rule's config from [WConfig.buildConfig], never
     * gated behind a rule's own config key). Only `if-else-bracing`/`when-entry-bracing`
     * ([com.varlanv.wrasse.rules.IfElseBracingRule], [com.varlanv.wrasse.rules.WhenEntryBracingRule])
     * consult it: with the printer active, they stop computing indentation themselves and emit
     * minimal, unindented brace edits for the printer to lay out (§5.3), lifting their own
     * multiline-body bail in the same mode.
     */
    val formatEnabled: Boolean = false,
)

enum class RuleLevel {
    OFF,
    WARN,
    ERROR,
}
