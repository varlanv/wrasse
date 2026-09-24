package com.varlanv.wrasse.gradle

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotContain
import java.nio.file.Files

class FormatRemainingFindingsSpec : ShouldSpec({

    val config = """{"rules":{"no-semicolons":{"level":"error"},"magic-number":{"level":"warn"}}}"""
    val source = "package sample\n\nfun main() {\n    println(42);\n}\n"

    should("keep non-fixable findings quiet during format and show them on lint") {
        val playground = Playground.create("format-remaining")
        try {
            playground.module("app", mapOf("sample/Sample.kt" to source))
            playground.settings()
            playground.config(config)
            val sourceUri = playground.sourceUri("app", "sample/Sample.kt")
            val finding = "w: $sourceUri:4:13 wrasse: magic-number: This expression contains a magic number; " +
                "consider defining it as a well-named constant"
            val fixedPath = playground.dir.resolve("app/src/main/kotlin/sample/Sample.kt").toString()

            val first = playground.run("wrasseFormat")
            first.output.countOf("Fixed: $fixedPath (1 edits)") shouldBe 1
            first.output shouldNotContain finding
            Files.readString(playground.dir.resolve("app/src/main/kotlin/sample/Sample.kt")) shouldBe
                "package sample\n\nfun main() {\n    println(42)\n}\n"

            val lint = playground.run("wrasseLint")
            lint.output.countOf(finding) shouldBe 1
        } finally {
            playground.delete()
        }
    }

    should("keep non-fixable warnings quiet when the printer rewrote the whole file") {
        val playground = Playground.create("format-remaining-whole-file")
        try {
            val formatConfig =
                """{"rules":{"magic-number":{"level":"warn"},"no-semicolons":{"level":"error"}},"format":{"enabled":true}}"""
            val badlyIndented = "package sample\n\nfun main() {\n    println(42);\n        println(\"hi\")\n}\n"
            playground.module("app", mapOf("sample/Sample.kt" to badlyIndented))
            playground.settings()
            playground.config(formatConfig)
            val sourceUri = playground.sourceUri("app", "sample/Sample.kt")
            val exactFinding = "w: $sourceUri:4:13 wrasse: magic-number: This expression contains a magic number; " +
                "consider defining it as a well-named constant"
            val fixedFile = playground.dir.resolve("app/src/main/kotlin/sample/Sample.kt")

            val first = playground.run("wrasseFormat")
            first.output.countOf("Fixed: $fixedFile (1 edits)") shouldBe 1
            first.output shouldNotContain "wrasse: magic-number"
            first.output shouldNotContain "no-semicolons"
            Files.readString(fixedFile) shouldBe "package sample\n\nfun main() {\n    println(42)\n    println(\"hi\")\n}\n"

            val lint = playground.run("wrasseLint")
            lint.output.countOf(exactFinding) shouldBe 1
            lint.output shouldNotContain "no-semicolons"
        } finally {
            playground.delete()
        }
    }
})
