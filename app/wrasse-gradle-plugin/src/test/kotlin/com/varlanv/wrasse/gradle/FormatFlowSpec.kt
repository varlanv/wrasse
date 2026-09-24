package com.varlanv.wrasse.gradle

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import java.nio.file.Files

class FormatFlowSpec : ShouldSpec({

    val config = """{"rules":{"no-semicolons":{"level":"error"},"magic-number":{"level":"warn"}}}"""
    val source = "package sample\n\nfun main() {\n    println(42);\n}\n"

    should("rewrite the source quietly and be a no-op the second time") {
        val playground = Playground.create("format")
        try {
            playground.module("app", mapOf("sample/Sample.kt" to source))
            playground.settings()
            playground.config(config)

            val first = playground.run("wrasseFormat")
            first.output shouldNotContain "no-semicolons"
            first.output shouldContain "Fixed: "
            Files.readString(playground.dir.resolve("app/src/main/kotlin/sample/Sample.kt")) shouldBe
                "package sample\n\nfun main() {\n    println(42)\n}\n"
            Files.exists(playground.dir.resolve("app/build/wrasse/main/format-request")) shouldBe false

            val second = playground.run("wrasseFormat")
            second.output shouldNotContain "Fixed: "
            second.output shouldNotContain "wrasse: magic-number"
            second.output shouldContain "BUILD SUCCESSFUL"
        } finally {
            playground.delete()
        }
    }

    should("leave the sources untouched when the compile fails, and fix them once it compiles") {
        val playground = Playground.create("format-failing-compile")
        try {
            playground.module(
                "app",
                mapOf(
                    "sample/Sample.kt" to SEMICOLON_SOURCE,
                    "sample/Bad.kt" to "package sample\n\nval bad: Int = \"text\"\n",
                ),
            )
            playground.settings()
            playground.config(ERROR_CONFIG)
            val sample = playground.dir.resolve("app/src/main/kotlin/sample/Sample.kt")

            val failed = playground.runAndFail("wrasseFormat")
            failed.output shouldContain "> Task :app:compileKotlin FAILED"
            failed.output shouldNotContain "> Task :app:wrasseApply"
            Files.readString(playground.dir.resolve("app/build/wrasse/main/patch/wrasse-fixes.txt")) shouldContain "Sample.kt"
            Files.readString(sample) shouldBe SEMICOLON_SOURCE

            playground.source("app", "sample/Bad.kt", "package sample\n\nval bad: Int = 1\n")
            val fixed = playground.run("wrasseFormat")
            fixed.output shouldContain "Fixed: "
            Files.readString(sample) shouldBe CLEAN_SOURCE
        } finally {
            playground.delete()
        }
    }

    should("format successfully and leave error-level lint findings for wrasseLint") {
        val playground = Playground.create("format-remaining-errors")
        try {
            val errorConfig = """{"rules":{"no-semicolons":{"level":"error"},"magic-number":{"level":"error"}}}"""
            playground.module("app", mapOf("sample/Sample.kt" to source))
            playground.settings()
            playground.config(errorConfig)
            val message = "wrasse found 1 error-level violation(s) in :app"
            val fixedFile = playground.dir.resolve("app/src/main/kotlin/sample/Sample.kt")

            val first = playground.run("wrasseFormat")
            first.output shouldNotContain message
            first.output shouldNotContain "wrasse: magic-number"
            Files.readString(fixedFile) shouldBe "package sample\n\nfun main() {\n    println(42)\n}\n"

            val second = playground.run("wrasseFormat")
            second.output shouldNotContain message
            second.output shouldNotContain "wrasse: magic-number"
            Files.readString(fixedFile) shouldBe "package sample\n\nfun main() {\n    println(42)\n}\n"

            val lint = playground.runAndFail("wrasseLint")
            lint.output shouldContain message
        } finally {
            playground.delete()
        }
    }

    should("apply nothing and leave no request behind when the compile was up to date") {
        val playground = Playground.create("format-uptodate")
        try {
            playground.module("app", mapOf("sample/Sample.kt" to CLEAN_SOURCE))
            playground.settings()
            playground.config(ERROR_CONFIG)

            playground.run("wrasseFormat")
            val again = playground.run("wrasseFormat")
            again.output shouldContain "Task :app:compileKotlin UP-TO-DATE"
            Files.exists(playground.dir.resolve("app/build/wrasse/main/format-request")) shouldBe false
            Files.readString(playground.dir.resolve("app/src/main/kotlin/sample/Sample.kt")) shouldBe CLEAN_SOURCE
        } finally {
            playground.delete()
        }
    }
})
