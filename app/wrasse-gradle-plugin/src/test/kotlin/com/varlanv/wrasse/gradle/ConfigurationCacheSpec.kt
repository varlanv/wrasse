package com.varlanv.wrasse.gradle

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import java.nio.file.Files

class ConfigurationCacheSpec : ShouldSpec({

    should("store and reuse the configuration cache across lint and format runs") {
        val playground = Playground.create("config-cache")
        try {
            playground.module("app", mapOf("sample/Sample.kt" to SEMICOLON_SOURCE))
            playground.module("lib", mapOf("sample/Lib.kt" to "package sample\n\nval lib = 1;\n"))
            playground.settings("org.gradle.configuration-cache=true\norg.gradle.configuration-cache.problems=fail\n")
            playground.config(WARN_CONFIG)
            val finding = "w: ${playground.sourceUri("app", "sample/Sample.kt")}:3:16 wrasse: no-semicolons: Unnecessary semicolon"

            val first = playground.run("wrasseLint")
            first.output shouldContain "Configuration cache entry stored."
            first.output.countOf(finding) shouldBe 1

            val second = playground.run("wrasseLint")
            second.output shouldContain "Reusing configuration cache."
            second.output.countOf(finding) shouldBe 1

            val format = playground.run("wrasseFormat")
            format.output shouldContain "Configuration cache entry stored."
            format.output shouldNotContain "no-semicolons"
            Files.readString(playground.dir.resolve("app/src/main/kotlin/sample/Sample.kt")) shouldBe CLEAN_SOURCE
            Files.readString(playground.dir.resolve("lib/src/main/kotlin/sample/Lib.kt")) shouldBe "package sample\n\nval lib = 1\n"

            val formatAgain = playground.run("wrasseFormat")
            formatAgain.output shouldContain "Reusing configuration cache."
            formatAgain.output shouldContain "BUILD SUCCESSFUL"
            formatAgain.output shouldNotContain "Fixed: "
            Files.readString(playground.dir.resolve("app/src/main/kotlin/sample/Sample.kt")) shouldBe CLEAN_SOURCE
        } finally {
            playground.delete()
        }
    }

    should("fail on error-level findings from a reused configuration cache entry without configuration cache problems") {
        val playground = Playground.create("config-cache-errors")
        try {
            playground.module("app", mapOf("sample/Sample.kt" to SEMICOLON_SOURCE))
            playground.settings("org.gradle.configuration-cache=true\norg.gradle.configuration-cache.problems=fail\n")
            playground.config(ERROR_CONFIG)
            val finding = "e: ${playground.sourceUri("app", "sample/Sample.kt")}:3:16 wrasse: no-semicolons: Unnecessary semicolon"

            val first = playground.runAndFail("wrasseLint")
            first.output shouldContain "Configuration cache entry stored."
            first.output shouldContain "wrasse found 1 error-level violation(s) in :app"
            first.output.countOf(finding) shouldBe 1

            val second = playground.runAndFail("wrasseLint")
            second.output shouldContain "Reusing configuration cache."
            second.output.countOf(finding) shouldBe 1
            second.output shouldContain "wrasse found 1 error-level violation(s) in :app"
        } finally {
            playground.delete()
        }
    }
})
