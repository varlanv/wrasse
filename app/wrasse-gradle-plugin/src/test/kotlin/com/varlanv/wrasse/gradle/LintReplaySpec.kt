package com.varlanv.wrasse.gradle

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import java.nio.file.Files

class LintReplaySpec : ShouldSpec({

    should("print each finding exactly once, replay it while the compile is up to date or restored from the cache, and stop once the source is fixed") {
        val playground = Playground.create("lint-replay")
        try {
            playground.module("app", mapOf("sample/Sample.kt" to SEMICOLON_SOURCE))
            playground.settings("org.gradle.caching=true\n")
            playground.config(ERROR_CONFIG)
            val finding = "e: ${playground.sourceUri("app", "sample/Sample.kt")}:3:16 wrasse: no-semicolons: Unnecessary semicolon"

            val first = playground.runAndFail("wrasseLint")
            first.output.countOf(finding) shouldBe 1
            first.output shouldContain "wrasse found 1 error-level violation(s) in :app"
            first.output shouldContain "Task :app:compileKotlin\n"

            val second = playground.runAndFail("wrasseLint")
            second.output shouldContain "Task :app:compileKotlin UP-TO-DATE"
            second.output.countOf(finding) shouldBe 1

            playground.run("clean")
            val third = playground.runAndFail("wrasseLint")
            third.output shouldContain "Task :app:compileKotlin FROM-CACHE"
            third.output.countOf(finding) shouldBe 1

            playground.source("app", "sample/Sample.kt", CLEAN_SOURCE)
            val fourth = playground.run("wrasseLint")
            fourth.output shouldNotContain "no-semicolons"
            fourth.output shouldContain "BUILD SUCCESSFUL"
            Files.exists(playground.dir.resolve("app/build/wrasse/main/format-request")) shouldBe false
        } finally {
            playground.delete()
        }
    }

    should("treat warn-level findings as passing and still print them") {
        val playground = Playground.create("lint-warn")
        try {
            playground.module("app", mapOf("sample/Sample.kt" to SEMICOLON_SOURCE))
            playground.settings()
            playground.config(WARN_CONFIG)

            val result = playground.run("wrasseLint")
            result.output.countOf("w: ${playground.sourceUri("app", "sample/Sample.kt")}:3:16 wrasse: no-semicolons: Unnecessary semicolon") shouldBe 1
        } finally {
            playground.delete()
        }
    }

    should("let a plain compile print findings itself and fail it only when warnOnly is off") {
        val playground = Playground.create("plain-compile")
        try {
            playground.module("app", mapOf("sample/Sample.kt" to SEMICOLON_SOURCE), extension = "warnOnly.set(false)")
            playground.settings()
            playground.config(ERROR_CONFIG)

            val failed = playground.runAndFail("compileKotlin")
            failed.output shouldContain "e: ${playground.sourceUri("app", "sample/Sample.kt")}:3:16 wrasse: no-semicolons: Unnecessary semicolon"

            Files.writeString(
                playground.dir.resolve("app/build.gradle.kts"),
                Files.readString(playground.dir.resolve("app/build.gradle.kts")).replace("warnOnly.set(false)", "warnOnly.set(true)"),
            )
            val passed = playground.run("compileKotlin")
            passed.output shouldContain "w: ${playground.sourceUri("app", "sample/Sample.kt")}:3:16 wrasse: no-semicolons: Unnecessary semicolon"
        } finally {
            playground.delete()
        }
    }
})
