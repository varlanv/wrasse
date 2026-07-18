package com.varlanv.wrasse.model

import com.varlanv.wrasse.lang.ConfigValueJsonc
import com.varlanv.wrasse.testing.BaseSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import java.nio.file.Path

class WConfigSpec : BaseSpec({

    fun buildConfig(json: String, configDir: Path? = null): WConfig {
        val value = ConfigValueJsonc.parse(json).getOrThrow()
        return WConfig.from(
            configValue = value,
            ruleIds = setOf("no-semicolons"),
            warnOnly = false,
            configDir = configDir,
        ).getOrThrow()
    }

    should("retain the configDir it was built with") {
        val dir = Path.of("/some/project/dir")
        val config = buildConfig(
            """{"rules":{"no-semicolons":{"level":"error"}}}""",
            configDir = dir,
        )
        config.configDir shouldBe dir
    }

    should("default configDir to null when not provided") {
        val config = buildConfig("""{"rules":{"no-semicolons":{"level":"error"}}}""")
        config.configDir shouldBe null
    }

    should("compile global exclude globs that match nested paths") {
        val config = buildConfig(
            """{"exclude":["**/generated/**"],"rules":{"no-semicolons":{"level":"error"}}}"""
        )
        config.exclude shouldHaveSize 1
        config.exclude[0].matches(Path.of("sub/generated/Foo.kt")) shouldBe true
        config.exclude[0].matches(Path.of("sub/other/Foo.kt")) shouldBe false
    }

    should("compile per-rule exclude globs independently from the global list") {
        val config = buildConfig(
            """{"rules":{"no-semicolons":{"level":"error","exclude":["legacy/**"]}}}"""
        )
        val ruleConfig = config.rulesConfigs.idToConfig.getValue("no-semicolons")
        ruleConfig.exclude shouldHaveSize 1
        ruleConfig.exclude[0].matches(Path.of("legacy/Old.kt")) shouldBe true
        ruleConfig.exclude[0].matches(Path.of("modern/New.kt")) shouldBe false
        config.exclude shouldHaveSize 0
    }
})
