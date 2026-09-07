package com.varlanv.wrasse.gradle

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain

class InvalidationSpec : ShouldSpec({

    should("recompile when wrasse.json changes and report with the new level") {
        val playground = Playground.create("config-change")
        try {
            playground.module("app", mapOf("sample/Sample.kt" to SEMICOLON_SOURCE))
            playground.settings()
            playground.config(WARN_CONFIG)
            val sourceUri = playground.sourceUri("app", "sample/Sample.kt")

            playground.run("wrasseLint").output shouldContain "w: $sourceUri:3:16 wrasse: no-semicolons"

            playground.config(ERROR_CONFIG)
            val afterChange = playground.runAndFail("wrasseLint")
            afterChange.output shouldNotContain "Task :app:compileKotlin UP-TO-DATE"
            afterChange.output shouldContain "e: $sourceUri:3:16 wrasse: no-semicolons"
        } finally {
            playground.delete()
        }
    }

    should("keep only the findings of files an incremental compile still sees as violating") {
        val playground = Playground.create("incremental")
        try {
            playground.module(
                "app",
                mapOf(
                    "sample/A.kt" to "package sample\n\nval a = 1;\n",
                    "sample/B.kt" to "package sample\n\nval b = 2;\n",
                ),
            )
            playground.settings()
            playground.config(ERROR_CONFIG)
            val aUri = playground.sourceUri("app", "sample/A.kt")
            val bUri = playground.sourceUri("app", "sample/B.kt")

            val first = playground.runAndFail("wrasseLint")
            first.output.countOf("e: $aUri:3:10 wrasse: no-semicolons") shouldBe 1
            first.output.countOf("e: $bUri:3:10 wrasse: no-semicolons") shouldBe 1

            playground.source("app", "sample/A.kt", "package sample\n\nval a = 1\n")
            val second = playground.runAndFail("wrasseLint")
            second.output shouldNotContain "A.kt:3:10"
            second.output.countOf("e: $bUri:3:10 wrasse: no-semicolons") shouldBe 1
            second.output shouldContain "wrasse found 1 error-level violation(s) in :app"
        } finally {
            playground.delete()
        }
    }

    should("do nothing when the extension disables wrasse") {
        val playground = Playground.create("disabled")
        try {
            playground.module("app", mapOf("sample/Sample.kt" to SEMICOLON_SOURCE), extension = "enabled.set(false)")
            playground.settings()
            playground.config(ERROR_CONFIG)

            val result = playground.run("wrasseLint")
            result.output shouldNotContain "no-semicolons"
            result.output shouldContain "BUILD SUCCESSFUL"
        } finally {
            playground.delete()
        }
    }
})
