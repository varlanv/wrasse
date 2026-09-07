package com.varlanv.wrasse.gradle

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain

class PluginOrderSpec : ShouldSpec({

    should("report a finding when wrasse is applied before the Kotlin plugin") {
        val playground = Playground.create("wrasse-first")
        try {
            playground.rawModule(
                "app",
                """
                plugins {
                    id("com.varlanv.wrasse")
                    kotlin("jvm") version "$KOTLIN_VERSION"
                }
                """.trimIndent(),
            )
            playground.source("app", "sample/Sample.kt", SEMICOLON_SOURCE)
            playground.settings()
            playground.config(WARN_CONFIG)
            val finding = "w: ${playground.sourceUri("app", "sample/Sample.kt")}:3:16 " +
                "wrasse: no-semicolons: Unnecessary semicolon"

            val lint = playground.run("wrasseLint")

            lint.output.countOf(finding) shouldBe 1
        } finally {
            playground.delete()
        }
    }

    should("report a finding when wrasse is applied from the consumer's own afterEvaluate") {
        val playground = Playground.create("wrasse-after-evaluate")
        try {
            playground.rawModule(
                "app",
                """
                plugins {
                    kotlin("jvm") version "$KOTLIN_VERSION"
                    id("com.varlanv.wrasse") apply false
                }

                project.afterEvaluate {
                    apply(plugin = "com.varlanv.wrasse")
                }
                """.trimIndent(),
            )
            playground.source("app", "sample/Sample.kt", SEMICOLON_SOURCE)
            playground.settings()
            playground.config(WARN_CONFIG)
            val finding = "w: ${playground.sourceUri("app", "sample/Sample.kt")}:3:16 " +
                "wrasse: no-semicolons: Unnecessary semicolon"

            val compile = playground.run("compileKotlin")
            compile.output shouldContain "BUILD SUCCESSFUL"
            compile.output.countOf(finding) shouldBe 1

            val lint = playground.run("wrasseLint")
            lint.output.countOf(finding) shouldBe 1
        } finally {
            playground.delete()
        }
    }
})
