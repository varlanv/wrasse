package com.varlanv.wrasse.gradle

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import java.nio.file.Files

class MultiplatformSpec : ShouldSpec({

    should("check a multiplatform jvm compilation, keep it passing under warnOnly and replay its finding") {
        val playground = Playground.create("multiplatform")
        try {
            playground.rawModule(
                "app",
                """
                plugins {
                    kotlin("multiplatform") version "$KOTLIN_VERSION"
                    id("com.varlanv.wrasse")
                }

                kotlin {
                    jvm()
                }
                """.trimIndent(),
            )
            val sourceRoot = "src/jvmMain/kotlin"
            playground.source("app", "sample/Sample.kt", SEMICOLON_SOURCE, sourceRoot)
            playground.settings()
            playground.config(WARN_CONFIG)
            val finding = "w: ${playground.sourceUri("app", "sample/Sample.kt", sourceRoot)}:3:16 " +
                "wrasse: no-semicolons: Unnecessary semicolon"

            val compile = playground.run("compileKotlinJvm")
            compile.output shouldContain "BUILD SUCCESSFUL"
            compile.output.countOf(finding) shouldBe 1

            val lint = playground.run("wrasseLint")
            lint.output.countOf(finding) shouldBe 1
            Files.isDirectory(playground.dir.resolve("app/build/wrasse/kotlinJvm/patch")) shouldBe true
        } finally {
            playground.delete()
        }
    }
})
