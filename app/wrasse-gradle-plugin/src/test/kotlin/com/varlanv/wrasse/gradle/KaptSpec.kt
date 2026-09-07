package com.varlanv.wrasse.gradle

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import java.nio.file.Files

class KaptSpec : ShouldSpec({

    should("print a finding once and keep the compile cacheable when kapt adds a stub task") {
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

            val lint = playground.run("wrasseLint", "--info")

            lint.output shouldContain "Task :app:kaptGenerateStubsKotlin"
            lint.output.countOf(finding) shouldBe 1
            lint.output shouldContain "Build cache key for task ':app:compileKotlin'"
            lint.output shouldNotContain "Caching disabled for task ':app:compileKotlin'"
            Files.exists(playground.dir.resolve("app/build/wrasse/kaptGenerateStubsKotlin")) shouldBe false
        } finally {
            playground.delete()
        }
    }
})
