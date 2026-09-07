package com.varlanv.wrasse.gradle

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import java.nio.file.Files

class RequestCleanupSpec : ShouldSpec({

    should("delete a request no compile consumed, so the next plain compile still fails on the finding") {
        val playground = Playground.create("request-cleanup")
        try {
            playground.module(
                "app",
                mapOf("sample/Sample.kt" to SEMICOLON_SOURCE),
                extension = "warnOnly.set(false)",
            )
            playground.settings()
            playground.config(ERROR_CONFIG)
            val finding = "e: ${playground.sourceUri("app", "sample/Sample.kt")}:3:16 " +
                "wrasse: no-semicolons: Unnecessary semicolon"

            val request = playground.run("wrasseLintRequest")
            request.output shouldContain "BUILD SUCCESSFUL"
            Files.exists(playground.dir.resolve("app/build/wrasse/main/format-request")) shouldBe false

            val compile = playground.runAndFail("compileKotlin")
            compile.output.countOf(finding) shouldBe 1
        } finally {
            playground.delete()
        }
    }

    should("write the format request after the lint request when both run in one invocation") {
        val playground = Playground.create("request-order")
        try {
            playground.module("app", mapOf("sample/Sample.kt" to SEMICOLON_SOURCE))
            playground.settings()
            playground.config(WARN_CONFIG)

            val result = playground.run("wrasseLint", "wrasseFormat")

            val lintIndex = result.output.indexOf("> Task :app:wrasseLintRequest")
            val formatIndex = result.output.indexOf("> Task :app:wrasseFormatRequest")
            lintIndex shouldBeGreaterThan -1
            formatIndex shouldBeGreaterThan lintIndex
            Files.readString(playground.dir.resolve("app/src/main/kotlin/sample/Sample.kt")) shouldBe CLEAN_SOURCE
        } finally {
            playground.delete()
        }
    }
})
