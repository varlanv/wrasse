package com.varlanv.wrasse.gradle

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import java.nio.file.Files

class KaptSpec : ShouldSpec({

    should("print a finding once, keep the compile cacheable, and disable wrasse for the kapt stub task's own args") {
        val playground = Playground.create("kapt")
        try {
            playground.rawModule(
                "app",
                """
                plugins {
                    kotlin("jvm") version "$KOTLIN_VERSION"
                    kotlin("kapt") version "$KOTLIN_VERSION"
                    id("com.varlanv.wrasse")
                }
                """.trimIndent(),
            )
            playground.source("app", "sample/Sample.kt", SEMICOLON_SOURCE)
            playground.settings("org.gradle.caching=true\n")
            playground.config(WARN_CONFIG)
            val finding = "w: ${playground.sourceUri("app", "sample/Sample.kt")}:3:16 " +
                "wrasse: no-semicolons: Unnecessary semicolon"

            val lint = playground.run("wrasseLint", "--debug")

            lint.output shouldContain "Task :app:kaptGenerateStubsKotlin"
            lint.output.countOf(finding) shouldBe 1
            lint.output shouldContain "Build cache key for task ':app:compileKotlin'"
            lint.output shouldNotContain "Caching disabled for task ':app:compileKotlin'"
            Files.exists(playground.dir.resolve("app/build/wrasse/kaptGenerateStubsKotlin")) shouldBe false
            val stubArgLines = lint.output.lineSequence()
                .filter { it.contains("Kotlin compiler args:") && it.contains("plugin:com.varlanv.wrasse:enabled=false") }
                .toList()
            stubArgLines shouldHaveSize 1
            stubArgLines[0] shouldContain "kapt3"
        } finally {
            playground.delete()
        }
    }
})
