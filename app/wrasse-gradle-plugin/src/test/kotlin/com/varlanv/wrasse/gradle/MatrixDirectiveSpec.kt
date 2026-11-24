@file:Suppress("BlockingMethodInNonBlockingContext")
package com.varlanv.wrasse.gradle

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import java.nio.file.Files

class MatrixDirectiveSpec : ShouldSpec({

    val config = """{"format":{"enabled":true}}"""
    val rejectedSource = "package sample\n\n// wrasse matrix:3\nval xs = arrayOf(1, 2, 3, 4)\n"
    val rejection =
        "'// wrasse matrix:3' cannot split the 4 elements of the call below into rows of 3: 4 is not divisible by 3"
    val acceptedSource = "package sample\n\n// wrasse matrix:2\nval xs = arrayOf(1, 2, 3, 4)\n"
    val acceptedLayout = "package sample\n\n// wrasse matrix:2\nval xs = arrayOf(\n    1, 2,\n    3, 4,\n)\n"

    fun findingLines(output: String): List<String> = output.lines().filter { "format-matrix" in it }

    should("only warn about a rejected directive while compiling, and not touch the source") {
        val playground = Playground.create("matrix-compile")
        try {
            playground.module("app", mapOf("sample/Sample.kt" to rejectedSource))
            playground.settings()
            playground.config(config)
            val location = playground.sourceUri("app", "sample/Sample.kt")

            val compiled = playground.run("compileKotlin")

            findingLines(compiled.output) shouldBe listOf("w: $location:3:1 wrasse: format-matrix: $rejection")
            compiled.output shouldContain "BUILD SUCCESSFUL"
            Files.readString(playground.dir.resolve("app/src/main/kotlin/sample/Sample.kt")) shouldBe rejectedSource
        } finally {
            playground.delete()
        }
    }

    should("fail wrasseLint with the rejected directive as an error") {
        val playground = Playground.create("matrix-lint")
        try {
            playground.module("app", mapOf("sample/Sample.kt" to rejectedSource))
            playground.settings()
            playground.config(config)
            val location = playground.sourceUri("app", "sample/Sample.kt")

            val linted = playground.runAndFail("wrasseLint")

            findingLines(linted.output).filter { it.startsWith("e: ") } shouldBe
                listOf("e: $location:3:1 wrasse: format-matrix: $rejection")
            linted.output shouldContain "wrasse found 1 error-level violation(s) in :app"
        } finally {
            playground.delete()
        }
    }

    should("fail wrasseFormat with the rejected directive and leave the source as it was") {
        val playground = Playground.create("matrix-format-rejected")
        try {
            playground.module("app", mapOf("sample/Sample.kt" to rejectedSource))
            playground.settings()
            playground.config(config)
            val location = playground.sourceUri("app", "sample/Sample.kt")

            val formatted = playground.runAndFail("wrasseFormat")

            findingLines(formatted.output) shouldBe listOf("e: $location:3:1 wrasse: format-matrix: $rejection")
            formatted.output shouldContain "wrasse could not format: 1 format directive(s) were rejected"
            Files.readString(playground.dir.resolve("app/src/main/kotlin/sample/Sample.kt")) shouldBe rejectedSource
        } finally {
            playground.delete()
        }
    }

    should("lay a valid matrix out on wrasseFormat and be a no-op the second time") {
        val playground = Playground.create("matrix-format")
        try {
            playground.module("app", mapOf("sample/Sample.kt" to acceptedSource))
            playground.settings()
            playground.config(config)
            val sample = playground.dir.resolve("app/src/main/kotlin/sample/Sample.kt")

            val first = playground.run("wrasseFormat")
            first.output shouldContain "Fixed: "
            Files.readString(sample) shouldBe acceptedLayout

            val second = playground.run("wrasseFormat")
            second.output shouldNotContain "Fixed: "
            Files.readString(sample) shouldBe acceptedLayout
            playground.run("wrasseLint").output shouldContain "BUILD SUCCESSFUL"
        } finally {
            playground.delete()
        }
    }

    should("format the valid matrix of a file and still fail for its rejected one") {
        val playground = Playground.create("matrix-format-mixed")
        try {
            val mixed = "$acceptedSource\n// wrasse matrix:3\nval ys = arrayOf(1, 2, 3, 4)\n"
            playground.module("app", mapOf("sample/Sample.kt" to mixed))
            playground.settings()
            playground.config(config)
            val sample = playground.dir.resolve("app/src/main/kotlin/sample/Sample.kt")
            val location = playground.sourceUri("app", "sample/Sample.kt")

            val formatted = playground.runAndFail("wrasseFormat")

            Files.readString(sample) shouldBe "$acceptedLayout\n// wrasse matrix:3\nval ys = arrayOf(1, 2, 3, 4)\n"
            findingLines(formatted.output) shouldBe listOf("e: $location:9:1 wrasse: format-matrix: $rejection")
            formatted.output shouldContain "wrasse could not format: 1 format directive(s) were rejected"
        } finally {
            playground.delete()
        }
    }

    should("ignore every directive while format is disabled") {
        val playground = Playground.create("matrix-format-disabled")
        try {
            playground.module("app", mapOf("sample/Sample.kt" to rejectedSource))
            playground.settings()
            playground.config("""{"format":{"enabled":false},"rules":{"no-semicolons":{"level":"error"}}}""")
            val sample = playground.dir.resolve("app/src/main/kotlin/sample/Sample.kt")

            playground.run("wrasseLint").output shouldNotContain "format-matrix"
            playground.run("wrasseFormat").output shouldNotContain "format-matrix"
            Files.readString(sample) shouldBe rejectedSource
        } finally {
            playground.delete()
        }
    }
})
