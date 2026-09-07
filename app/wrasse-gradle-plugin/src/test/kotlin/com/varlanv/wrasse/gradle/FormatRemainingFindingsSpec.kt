package com.varlanv.wrasse.gradle

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import java.nio.file.Files

class FormatRemainingFindingsSpec : ShouldSpec({

    val config = """{"rules":{"no-semicolons":{"level":"error"},"magic-number":{"level":"warn"}}}"""
    val source = "package sample\n\nfun main() {\n    println(42);\n}\n"

    should("show the non-fixable finding on the very first format run, and again right after on lint") {
        val playground = Playground.create("format-remaining")
        try {
            playground.module("app", mapOf("sample/Sample.kt" to source))
            playground.settings()
            playground.config(config)
            val sourceUri = playground.sourceUri("app", "sample/Sample.kt")
            val finding = "w: $sourceUri:4:13 wrasse: magic-number: This expression contains a magic number; " +
                "consider defining it as a well-named constant"

            val first = playground.run("wrasseFormat")
            first.output shouldContain "Fixed: "
            first.output.countOf(finding) shouldBe 1
            Files.readString(playground.dir.resolve("app/src/main/kotlin/sample/Sample.kt")) shouldBe
                "package sample\n\nfun main() {\n    println(42)\n}\n"

            val lint = playground.run("wrasseLint")
            lint.output.countOf(finding) shouldBe 1
        } finally {
            playground.delete()
        }
    }
})
