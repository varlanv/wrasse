package com.varlanv.wrasse.gradle

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import java.nio.file.Files

class ParallelMultiModuleSpec : ShouldSpec({

    should("leave every module untouched when another module cannot compile") {
        val playground = Playground.create("format-atomic")
        try {
            playground.module("good", mapOf("sample/Good.kt" to SEMICOLON_SOURCE))
            playground.module(
                "bad",
                mapOf("sample/Bad.kt" to "package sample\n\nval bad: Int = \"text\"\n"),
                afterExtension = """tasks.named("compileKotlin") { mustRunAfter(":good:wrasseFormatApply") }""",
            )
            playground.settings("org.gradle.parallel=true\n")
            playground.config(ERROR_CONFIG)

            val failed = playground.runAndFail("wrasseFormat", "--parallel", "--continue")
            failed.output shouldContain "> Task :bad:compileKotlin FAILED"
            Files.readString(playground.dir.resolve("good/src/main/kotlin/sample/Good.kt")) shouldBe SEMICOLON_SOURCE
        } finally {
            playground.delete()
        }
    }

    should("report the findings of every module in one parallel lint run and fail once per module") {
        val playground = Playground.create("parallel")
        try {
            for (name in listOf("one", "two", "three")) {
                playground.module(name, mapOf("sample/$name.kt" to "package sample\n\nval $name = 1;\n"))
            }
            playground.settings("org.gradle.parallel=true\n")
            playground.config(ERROR_CONFIG)

            val result = playground.runAndFail("wrasseLint", "--continue")
            for (name in listOf("one", "two", "three")) {
                result.output.countOf("e: ${playground.sourceUri(name, "sample/$name.kt")}:3:${"val $name = 1".length + 1} wrasse: no-semicolons: Unnecessary semicolon") shouldBe 1
                result.output shouldContain "wrasse found 1 error-level violation(s) in :$name"
            }
        } finally {
            playground.delete()
        }
    }
})
