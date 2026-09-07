package sample

import com.varlanv.wrasse.lang.ConfigValueJsonc
import com.varlanv.wrasse.testing.BaseSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import java.nio.file.Path

class WConfigSpec : BaseSpec({

    fun singleLevelResolver(json: String): ExtendsResolver = ExtendsResolver {
        ConfigValueJsonc.parse(json).map {
            ExtendsResolution(it, ExtendsResolver { Result.failure(Exception("no further extends configured")) })
        }
    }

    fun buildConfig(
        json: String,
        configDir: Path? = null,
        warnOnly: Boolean = false,
    ): WConfig {
        val value = ConfigValueJsonc.parse(json).getOrThrow()
        return WConfig
            .from(configValue = value, ruleIds = setOf("no-semicolons"), warnOnly = warnOnly, configDir = configDir)
            .getOrThrow()
    }

    fun buildConfigWithExtends(childJson: String, baseJson: String): WConfig {
        val childValue = ConfigValueJsonc.parse(childJson).getOrThrow()
        return WConfig
            .from(
                configValue = childValue,
                ruleIds = setOf("no-semicolons"),
                warnOnly = false,
                resolveExtends = singleLevelResolver(baseJson),
            )
            .getOrThrow()
    }

    should("derive wrapNestedCallArguments from named-arguments being on with wrap left at its default") {
        val specs = mapOf(
            "named-arguments" to
                listOf(WRuleOptionSpec.Optional("wrap", WRuleOptionType.BOOLEAN, "", WRuleOptionValue.Bool(true))),
        )

        fun style(
            json: String,
        ) = WConfig
            .from(
                configValue = ConfigValueJsonc.parse(json).getOrThrow(),
                ruleIds = setOf("named-arguments", "no-semicolons"),
                warnOnly = false,
                ruleOptionSpecs = specs,
            )
            .getOrThrow()
            .format
            .style
        style("""{"format":{},"rules":{"named-arguments":{"level":"error"}}}""").wrapNestedCallArguments shouldBe true
        style("""{"format":{},"rules":{"named-arguments":{"level":"error","wrap":false}}}""")
            .wrapNestedCallArguments shouldBe false
        style("""{"format":{},"rules":{"named-arguments":{"level":"off","wrap":true}}}""")
            .wrapNestedCallArguments shouldBe false
        style("""{"format":{},"rules":{"no-semicolons":{"level":"error"}}}""").wrapNestedCallArguments shouldBe false
    }

    should("retain the configDir it was built with") {
        val dir = Path.of("/some/project/dir")
        val config = buildConfig("""{"rules":{"no-semicolons":{"level":"error"}}}""", configDir = dir)
        config.configDir shouldBe dir
    }

    should("default configDir to null when not provided") {
        val config = buildConfig("""{"rules":{"no-semicolons":{"level":"error"}}}""")
        config.configDir shouldBe null
    }

    should("compile global exclude globs that match nested paths") {
        val config = buildConfig("""{"exclude":["**/generated/**"],"rules":{"no-semicolons":{"level":"error"}}}""")
        config.exclude shouldHaveSize 1
        config.exclude[0].matches(Path.of("sub/generated/Foo.kt")) shouldBe true
        config.exclude[0].matches(Path.of("sub/other/Foo.kt")) shouldBe false
    }

    should("compile per-rule exclude globs independently from the global list") {
        val config = buildConfig("""{"rules":{"no-semicolons":{"level":"error","exclude":["legacy/**"]}}}""")
        val ruleConfig = config.rulesConfigs.idToConfig.getValue("no-semicolons")
        ruleConfig.exclude shouldHaveSize 1
        ruleConfig.exclude[0].matches(Path.of("legacy/Old.kt")) shouldBe true
        ruleConfig.exclude[0].matches(Path.of("modern/New.kt")) shouldBe false
        config.exclude shouldHaveSize 0
    }

    should("default format to disabled with the documented style defaults when the key is absent") {
        val config = buildConfig("""{"rules":{"no-semicolons":{"level":"error"}}}""")
        config.format.enabled shouldBe false
        config.format.style.indentWidth shouldBe 4
        config.format.style.maxLineLength shouldBe 120
        config.format.style.trailingCommas shouldBe true
        config.format.style.importLayout shouldBe ImportLayout.ASCII
        config.format.style.multilineSignatureThreshold shouldBe 3
        config.format.ruleConfig.level shouldBe RuleLevel.OFF
        config.format.ruleConfig.effectiveLevel shouldBe RuleLevel.OFF
    }

    should("default format enabled to true when the format object is present without its own 'enabled'") {
        val config = buildConfig("""{"format":{"indentWidth":2},"rules":{}}""")
        config.format.enabled shouldBe true
        config.format.style.indentWidth shouldBe 2
        config.format.ruleConfig.effectiveLevel shouldBe RuleLevel.ERROR
    }

    should("honor an explicit format.enabled false alongside custom style parameters") {
        val config = buildConfig(
            """{"format":{"enabled":false,"maxLineLength":100,"trailingCommas":false,"multilineSignatureThreshold":2},"rules":{}}""",
        )
        config.format.enabled shouldBe false
        config.format.style.maxLineLength shouldBe 100
        config.format.style.trailingCommas shouldBe false
        config.format.style.multilineSignatureThreshold shouldBe 2
        config.format.ruleConfig.level shouldBe RuleLevel.OFF
    }

    should("downgrade format's effective level to warn under the global warnOnly flag") {
        val config = buildConfig("""{"format":{"enabled":true},"rules":{}}""", warnOnly = true)
        config.format.ruleConfig.level shouldBe RuleLevel.ERROR
        config.format.ruleConfig.effectiveLevel shouldBe RuleLevel.WARN
    }

    val optionSpecs = mapOf(
        "no-semicolons" to
            listOf(
                WRuleOptionSpec.Optional("allow-inline", WRuleOptionType.BOOLEAN, "", WRuleOptionValue.Bool(false)),
                WRuleOptionSpec.Optional("max-width", WRuleOptionType.INTEGER, "", null, minimum = 1, maximum = 100),
                WRuleOptionSpec.Required("prefixes", WRuleOptionType.STRING_LIST, ""),
                WRuleOptionSpec.Optional("label", WRuleOptionType.STRING, "", WRuleOptionValue.Str("default")),
            ),
        "forbidden-calls" to listOf(WRuleOptionSpec.Required("calls", WRuleOptionType.STRING_LIST_MAP, "")),
    )

    fun buildWithOptions(
        json: String,
        baseJson: String? = null,
    ): Result<WConfig> = WConfig.from(
        configValue = ConfigValueJsonc.parse(json).getOrThrow(),
        ruleIds = setOf("no-semicolons"),
        warnOnly = false,
        resolveExtends = baseJson?.let { base -> singleLevelResolver(base) },
        ruleOptionSpecs = optionSpecs,
    )

    should("parse declared options, applying defaults and leaving a default-less optional absent") {
        val config = buildWithOptions("""{"rules":{"no-semicolons":{"level":"error","prefixes":["a","b"]}}}""")
            .getOrThrow()
        val options = config.rulesConfigs.idToConfig.getValue("no-semicolons").options
        options.boolean("allow-inline") shouldBe false
        options.integerOrNull("max-width") shouldBe null
        options.stringList("prefixes") shouldBe listOf("a", "b")
        options.string("label") shouldBe "default"
    }

    should("let an explicit value override an option's default") {
        val json = """{"rules":{"no-semicolons":{"level":"error","prefixes":[],"allow-inline":true,"max-width":80,"label":"x"}}}"""
        val options = buildWithOptions(json).getOrThrow().rulesConfigs.idToConfig.getValue("no-semicolons").options
        options.boolean("allow-inline") shouldBe true
        options.integer("max-width") shouldBe 80L
        options.stringList("prefixes") shouldBe emptyList()
        options.string("label") shouldBe "x"
    }

    should("parse a map-of-string-arrays option and reject a map holding anything else") {
        val ok = WConfig
            .from(
                configValue = ConfigValueJsonc
                    .parse("""{"rules":{"forbidden-calls":{"level":"error","calls":{"a.b":["**/X.kt"],"c.*":[]}}}}""")
                    .getOrThrow(),
                ruleIds = setOf("forbidden-calls"),
                warnOnly = false,
                ruleOptionSpecs = optionSpecs,
            )
            .getOrThrow()
        ok.rulesConfigs.idToConfig.getValue("forbidden-calls").options.stringListMap("calls") shouldBe
            mapOf("a.b" to listOf("**/X.kt"), "c.*" to emptyList())
        val bad = WConfig.from(
            configValue = ConfigValueJsonc
                .parse("""{"rules":{"forbidden-calls":{"level":"error","calls":{"a.b":"X.kt"}}}}""")
                .getOrThrow(),
            ruleIds = setOf("forbidden-calls"),
            warnOnly = false,
            ruleOptionSpecs = optionSpecs,
        )
        bad.exceptionOrNull()?.message shouldBe
            "Option 'calls' for rule 'forbidden-calls' must be a map of string arrays, got object"
    }

    should("refuse function-expression-body and forbidden-expression-body-functions on together") {
        val result = WConfig.from(
            configValue = ConfigValueJsonc
                .parse(
                    """{"rules":{"function-expression-body":{"level":"error"},"forbidden-expression-body-functions":{"level":"warn"}}}""",
                )
                .getOrThrow(),
            ruleIds = setOf("function-expression-body", "forbidden-expression-body-functions"),
            warnOnly = false,
        )
        result.exceptionOrNull()?.message shouldBe
            "Rules 'function-expression-body' and 'forbidden-expression-body-functions' cannot both be on"
    }

    should("fail with the full message when a required option is missing") {
        val result = buildWithOptions("""{"rules":{"no-semicolons":{"level":"error"}}}""")
        result.exceptionOrNull()?.message shouldBe "Missing required option 'prefixes' for rule 'no-semicolons'"
    }

    should("fail with the full message on an option the rule does not declare") {
        val result = buildWithOptions("""{"rules":{"no-semicolons":{"level":"error","prefixes":[],"bogus":1}}}""")
        result.exceptionOrNull()?.message shouldBe
            "Unknown option 'bogus' for rule 'no-semicolons'; expected one of [allow-inline, max-width, prefixes, label]"
    }

    should("fail with the full message on an option for a rule that declares none") {
        val value = ConfigValueJsonc.parse("""{"rules":{"no-semicolons":{"level":"error","bogus":1}}}""").getOrThrow()
        val result = WConfig.from(configValue = value, ruleIds = setOf("no-semicolons"), warnOnly = false)
        result.exceptionOrNull()?.message shouldBe
            "Unknown option 'bogus' for rule 'no-semicolons'; rule accepts no options"
    }

    should("fail with the full message on an option of the wrong type") {
        val result = buildWithOptions(
            """{"rules":{"no-semicolons":{"level":"error","prefixes":[],"allow-inline":"yes"}}}""",
        )
        result.exceptionOrNull()?.message shouldBe
            "Option 'allow-inline' for rule 'no-semicolons' must be a boolean, got string"
    }

    should("fail with the full message on an integer option below its minimum") {
        val result = buildWithOptions("""{"rules":{"no-semicolons":{"level":"error","prefixes":[],"max-width":0}}}""")
        result.exceptionOrNull()?.message shouldBe
            "Option 'max-width' for rule 'no-semicolons' must be at least 1, got 0"
    }

    should("fail with the full message on an integer option above its maximum") {
        val result = buildWithOptions(
            """{"rules":{"no-semicolons":{"level":"error","prefixes":[],"max-width":2147483648}}}""",
        )
        result.exceptionOrNull()?.message shouldBe
            "Option 'max-width' for rule 'no-semicolons' must be at most 100, got 2147483648"
    }

    should("skip option validation for a rule that is off") {
        val result = buildWithOptions("""{"rules":{"no-semicolons":{"level":"off","bogus":1}}}""")
        result.getOrThrow().rulesConfigs.idToConfig shouldBe emptyMap()
    }

    should("merge options across extends, child key by key over base") {
        val child = """{"extends":"base.json","rules":{"no-semicolons":{"allow-inline":true}}}"""
        val base = """{"rules":{"no-semicolons":{"level":"error","prefixes":["p"],"max-width":10}}}"""
        val options = buildWithOptions(
            child,
            base,
        ).getOrThrow().rulesConfigs.idToConfig.getValue("no-semicolons").options
        options.boolean("allow-inline") shouldBe true
        options.stringList("prefixes") shouldBe listOf("p")
        options.integer("max-width") shouldBe 10L
    }

    should("fail with the full message on an unknown importLayout value") {
        val value = ConfigValueJsonc.parse("""{"format":{"importLayout":"idea"},"rules":{}}""").getOrThrow()
        val result = WConfig.from(configValue = value, ruleIds = setOf("no-semicolons"), warnOnly = false)
        result.exceptionOrNull()?.message shouldBe "Invalid importLayout 'idea' for 'format'; expected 'ascii'"
    }

    should("fail with the full message when 'format' is not an object") {
        val value = ConfigValueJsonc.parse("""{"format":"on","rules":{}}""").getOrThrow()
        val result = WConfig.from(configValue = value, ruleIds = setOf("no-semicolons"), warnOnly = false)
        result.exceptionOrNull()?.message shouldBe "'format' must be an object, got string"
    }

    should("fail with the full message when a format style field has the wrong type") {
        val value = ConfigValueJsonc.parse("""{"format":{"indentWidth":"four"},"rules":{}}""").getOrThrow()
        val result = WConfig.from(configValue = value, ruleIds = setOf("no-semicolons"), warnOnly = false)
        result.exceptionOrNull()?.message shouldBe "Property 'indentWidth' for 'format' must be a number, got string"
    }

    should("let a child's format block fully override the base's, extends-style") {
        val config = buildConfigWithExtends(
            childJson = """{"extends":"base.json","format":{"enabled":true,"indentWidth":2}}""",
            baseJson = """{"format":{"enabled":false,"indentWidth":8,"maxLineLength":80}}""",
        )
        config.format.enabled shouldBe true
        config.format.style.indentWidth shouldBe 2
        config.format.style.maxLineLength shouldBe 120
    }

    should("inherit the base's format block when the child does not set one") {
        val config = buildConfigWithExtends(
            childJson = """{"extends":"base.json","rules":{}}""",
            baseJson = """{"format":{"enabled":true,"indentWidth":8}}""",
        )
        config.format.enabled shouldBe true
        config.format.style.indentWidth shouldBe 8
    }

    should("resolve a second-level extends against the resolver the first level returned, not the leaf's") {
        var resolverB: ExtendsResolver? = null
        val resolverA = ExtendsResolver { relativePath ->
            if (relativePath == "level1") {
                ConfigValueJsonc
                    .parse("""{"extends":"level2","rules":{"no-semicolons":{"level":"warn"}}}""")
                    .map { ExtendsResolution(it, resolverB!!) }
            } else {
                Result.failure(Exception("resolverA cannot resolve $relativePath"))
            }
        }
        resolverB = ExtendsResolver { relativePath ->
            if (relativePath == "level2") {
                ConfigValueJsonc
                    .parse("""{"rules":{"no-semicolons":{"level":"error"},"magic-number":{"level":"warn"}}}""")
                    .map { ExtendsResolution(it, resolverB!!) }
            } else {
                Result.failure(Exception("resolverB cannot resolve $relativePath"))
            }
        }
        val childValue = ConfigValueJsonc.parse("""{"extends":"level1","rules":{}}""").getOrThrow()

        val config = WConfig
            .from(
                configValue = childValue,
                ruleIds = setOf("no-semicolons", "magic-number"),
                warnOnly = false,
                resolveExtends = resolverA,
            )
            .getOrThrow()

        config.rulesConfigs.idToConfig.getValue("no-semicolons").level shouldBe RuleLevel.WARN
        config.rulesConfigs.idToConfig.getValue("magic-number").level shouldBe RuleLevel.WARN
    }
})

// expect-clean
// fixture-option: trailing-newline
