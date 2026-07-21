package com.varlanv.wrasse.model

import com.varlanv.wrasse.lang.ConfigValueJsonc
import com.varlanv.wrasse.testing.BaseSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import java.nio.file.Path

class WConfigSpec :
    BaseSpec(
        {

            fun buildConfig(json: String, configDir: Path? = null, warnOnly: Boolean = false): WConfig {
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
                        resolveExtends = { ConfigValueJsonc.parse(baseJson) },
                    )
                    .getOrThrow()
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
                config.format.style.maxLineLength shouldBe 140
                config.format.style.trailingCommas shouldBe true
                config.format.style.importLayout shouldBe ImportLayout.ASCII
                config.format.style.multilineSignatureThreshold shouldBe null
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
                config.format.style.maxLineLength shouldBe 140
            }

            should("inherit the base's format block when the child does not set one") {
                val config = buildConfigWithExtends(
                    childJson = """{"extends":"base.json","rules":{}}""",
                    baseJson = """{"format":{"enabled":true,"indentWidth":8}}""",
                )
                config.format.enabled shouldBe true
                config.format.style.indentWidth shouldBe 8
            }
        },
    )
