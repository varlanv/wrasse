package com.varlanv.wrasse.gradle

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain

class FreeCompilerArgsSpec : ShouldSpec({

    should("keep reporting findings and keep the consumer's own free compiler arg when the build script overwrites freeCompilerArgs after applying wrasse") {
        val playground = Playground.create("free-compiler-args")
        try {
            playground.module(
                "app",
                mapOf("sample/Sample.kt" to SEMICOLON_SOURCE),
                afterExtension = """
                tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
                    compilerOptions.freeCompilerArgs.set(listOf("-Xcontext-parameters"))
                }
                """.trimIndent(),
            )
            playground.settings()
            playground.config(WARN_CONFIG)
            val sourceUri = playground.sourceUri("app", "sample/Sample.kt")

            val result = playground.run("wrasseLint", "--debug")

            result.output shouldContain "w: $sourceUri:3:16 wrasse: no-semicolons: Unnecessary semicolon"
            val compilerArgsLine = result.output.lineSequence().first { it.contains("Kotlin compiler args:") }
            compilerArgsLine shouldContain "-Xcontext-parameters"
            compilerArgsLine shouldContain "plugin:com.varlanv.wrasse:fixOutputDir="
        } finally {
            playground.delete()
        }
    }

    should("keep replaying the finding through wrasseLint on a later, up-to-date run") {
        val playground = Playground.create("free-compiler-args-replay")
        try {
            playground.module(
                "app",
                mapOf("sample/Sample.kt" to SEMICOLON_SOURCE),
                afterExtension = """
                tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
                    compilerOptions.freeCompilerArgs.set(listOf("-Xcontext-parameters"))
                }
                """.trimIndent(),
            )
            playground.settings()
            playground.config(WARN_CONFIG)
            val sourceUri = playground.sourceUri("app", "sample/Sample.kt")
            val finding = "w: $sourceUri:3:16 wrasse: no-semicolons: Unnecessary semicolon"

            playground.run("wrasseLint")
            val second = playground.run("wrasseLint")

            second.output shouldContain "Task :app:compileKotlin UP-TO-DATE"
            second.output.countOf(finding) shouldBe 1
        } finally {
            playground.delete()
        }
    }
})
