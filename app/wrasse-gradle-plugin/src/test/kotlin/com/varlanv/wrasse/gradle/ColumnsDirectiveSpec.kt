@file:Suppress("BlockingMethodInNonBlockingContext")

package com.varlanv.wrasse.gradle

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import java.nio.file.Files

class ColumnsDirectiveSpec : ShouldSpec({

    val config = """{"format":{"enabled":true}}"""
    val rejectedSource = "package sample\n\n// wrasse columns:0\nval xs = arrayOf(1, 2, 3, 4)\n"
    val rejection = "size 0 in '// wrasse columns:0' is below the minimum of 1: a row needs at least 1 element"
    val acceptedSource = "package sample\n\n// wrasse columns:3\nval xs = arrayOf(1, 2, 3, 4)\n"
    val acceptedLayout = "package sample\n\n// wrasse columns:3\nval xs = arrayOf(\n    1, 2, 3,\n    4,\n)\n"
    val clashSource = "package sample\n\n// wrasse matrix:2\n// wrasse columns:2\nval xs = arrayOf(1, 2, 3, 4)\n"
    val clash = "'// wrasse matrix:2' (line 3) and '// wrasse columns:2' (line 4) both target the call below; " +
        "keep only one"

    fun findingLines(
        output: String,
    ): List<String> = output.lines().filter { "format-columns" in it || "format-matrix" in it }

    should("only warn about a rejected columns directive while compiling, and not touch the source") {
        val playground = Playground.create("columns-compile")
        try {
            playground.module("app", mapOf("sample/Sample.kt" to rejectedSource))
            playground.settings()
            playground.config(config)
            val location = playground.sourceUri("app", "sample/Sample.kt")

            val compiled = playground.run("compileKotlin")

            findingLines(compiled.output) shouldBe listOf("w: $location:3:1 wrasse: format-columns: $rejection")
            compiled.output shouldContain "BUILD SUCCESSFUL"
            Files.readString(playground.dir.resolve("app/src/main/kotlin/sample/Sample.kt")) shouldBe rejectedSource
        } finally {
            playground.delete()
        }
    }

    should("fail wrasseLint with the rejected columns directive as an error") {
        val playground = Playground.create("columns-lint")
        try {
            playground.module("app", mapOf("sample/Sample.kt" to rejectedSource))
            playground.settings()
            playground.config(config)
            val location = playground.sourceUri("app", "sample/Sample.kt")

            val linted = playground.runAndFail("wrasseLint")

            findingLines(linted.output).filter { it.startsWith("e: ") } shouldBe
                listOf("e: $location:3:1 wrasse: format-columns: $rejection")
            linted.output shouldContain "wrasse found 1 error-level violation(s) in :app"
        } finally {
            playground.delete()
        }
    }

    should("fail wrasseFormat with the rejected columns directive and leave the source as it was") {
        val playground = Playground.create("columns-format-rejected")
        try {
            playground.module("app", mapOf("sample/Sample.kt" to rejectedSource))
            playground.settings()
            playground.config(config)
            val location = playground.sourceUri("app", "sample/Sample.kt")

            val formatted = playground.runAndFail("wrasseFormat")

            findingLines(formatted.output) shouldBe listOf("e: $location:3:1 wrasse: format-columns: $rejection")
            formatted.output shouldContain "wrasse could not format: 1 format directive(s) were rejected"
            Files.readString(playground.dir.resolve("app/src/main/kotlin/sample/Sample.kt")) shouldBe rejectedSource
        } finally {
            playground.delete()
        }
    }

    should("lay a valid columns call out on wrasseFormat and be a no-op the second time") {
        val playground = Playground.create("columns-format")
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

    should("compile a source holding a valid columns directive without any finding") {
        val playground = Playground.create("columns-compile-valid")
        try {
            playground.module("app", mapOf("sample/Sample.kt" to acceptedLayout))
            playground.settings()
            playground.config(config)

            val compiled = playground.run("compileKotlin")

            findingLines(compiled.output) shouldBe emptyList()
            compiled.output shouldContain "BUILD SUCCESSFUL"
        } finally {
            playground.delete()
        }
    }

    should("format the valid columns call of a file and still fail for its rejected one") {
        val playground = Playground.create("columns-format-mixed")
        try {
            val mixed = "$acceptedSource\n// wrasse columns:0\nval ys = arrayOf(1, 2, 3, 4)\n"
            playground.module("app", mapOf("sample/Sample.kt" to mixed))
            playground.settings()
            playground.config(config)
            val sample = playground.dir.resolve("app/src/main/kotlin/sample/Sample.kt")
            val location = playground.sourceUri("app", "sample/Sample.kt")

            val formatted = playground.runAndFail("wrasseFormat")

            Files.readString(sample) shouldBe "$acceptedLayout\n// wrasse columns:0\nval ys = arrayOf(1, 2, 3, 4)\n"
            findingLines(formatted.output) shouldBe listOf("e: $location:9:1 wrasse: format-columns: $rejection")
            formatted.output shouldContain "wrasse could not format: 1 format directive(s) were rejected"
        } finally {
            playground.delete()
        }
    }

    should("lay a columns call of size one out as one element per row and be a no-op the second time") {
        val playground = Playground.create("columns-one-format")
        try {
            val oneSource = "package sample\n\n// wrasse columns:1\nval xs = arrayOf(1, 22, 3)\n"
            val oneLayout = "package sample\n\n// wrasse columns:1\nval xs = arrayOf(\n    1,\n    22,\n    3,\n)\n"
            playground.module("app", mapOf("sample/Sample.kt" to oneSource))
            playground.settings()
            playground.config(config)
            val sample = playground.dir.resolve("app/src/main/kotlin/sample/Sample.kt")

            playground.run("wrasseFormat").output shouldContain "Fixed: "
            Files.readString(sample) shouldBe oneLayout

            val second = playground.run("wrasseFormat")
            second.output shouldNotContain "Fixed: "
            Files.readString(sample) shouldBe oneLayout
            playground.run("wrasseLint").output shouldContain "BUILD SUCCESSFUL"
        } finally {
            playground.delete()
        }
    }

    should("lay a columns call of size one out while still rejecting a matrix directive of size one") {
        val playground = Playground.create("columns-one-and-matrix-one")
        try {
            val both = "package sample\n\n// wrasse columns:1\nval xs = arrayOf(1, 2)\n\n" +
                "// wrasse matrix:1\nval ys = arrayOf(1, 2)\n"
            val laidOut = "package sample\n\n// wrasse columns:1\nval xs = arrayOf(\n    1,\n    2,\n)\n\n" +
                "// wrasse matrix:1\nval ys = arrayOf(1, 2)\n"
            playground.module("app", mapOf("sample/Sample.kt" to both))
            playground.settings()
            playground.config(config)
            val sample = playground.dir.resolve("app/src/main/kotlin/sample/Sample.kt")
            val location = playground.sourceUri("app", "sample/Sample.kt")

            val formatted = playground.runAndFail("wrasseFormat")

            Files.readString(sample) shouldBe laidOut
            findingLines(formatted.output) shouldBe
                listOf(
                    "e: $location:9:1 wrasse: format-matrix: size 1 in '// wrasse matrix:1' is below the minimum " +
                        "of 2: a row needs at least 2 elements",
                )
            formatted.output shouldContain "wrasse could not format: 1 format directive(s) were rejected"
        } finally {
            playground.delete()
        }
    }

    should("skip an annotation line between the directive and the call while formatting") {
        val playground = Playground.create("columns-format-annotation")
        try {
            val annotated = "package sample\n\n// wrasse columns:3\n@JvmField\nval xs = arrayOf(1, 2, 3, 4)\n"
            val laidOut = "package sample\n\n// wrasse columns:3\n@JvmField\nval xs = arrayOf(\n    1, 2, 3,\n    4,\n)\n"
            playground.module("app", mapOf("sample/Sample.kt" to annotated))
            playground.settings()
            playground.config(config)
            val sample = playground.dir.resolve("app/src/main/kotlin/sample/Sample.kt")

            playground.run("wrasseFormat").output shouldContain "Fixed: "

            Files.readString(sample) shouldBe laidOut
            playground.run("wrasseLint").output shouldContain "BUILD SUCCESSFUL"
        } finally {
            playground.delete()
        }
    }

    should("only warn about a clash of directives while compiling, under the rule id of each directive") {
        val playground = Playground.create("columns-clash-compile")
        try {
            playground.module("app", mapOf("sample/Sample.kt" to clashSource))
            playground.settings()
            playground.config(config)
            val location = playground.sourceUri("app", "sample/Sample.kt")

            val compiled = playground.run("compileKotlin")

            findingLines(compiled.output) shouldBe
                listOf(
                    "w: $location:3:1 wrasse: format-matrix: $clash",
                    "w: $location:4:1 wrasse: format-columns: $clash",
                )
            compiled.output shouldContain "BUILD SUCCESSFUL"
            Files.readString(playground.dir.resolve("app/src/main/kotlin/sample/Sample.kt")) shouldBe clashSource
        } finally {
            playground.delete()
        }
    }

    should("fail wrasseLint with both directives of a clash as errors") {
        val playground = Playground.create("columns-clash-lint")
        try {
            playground.module("app", mapOf("sample/Sample.kt" to clashSource))
            playground.settings()
            playground.config(config)
            val location = playground.sourceUri("app", "sample/Sample.kt")

            val linted = playground.runAndFail("wrasseLint")

            findingLines(linted.output).filter { it.startsWith("e: ") } shouldBe
                listOf(
                    "e: $location:3:1 wrasse: format-matrix: $clash",
                    "e: $location:4:1 wrasse: format-columns: $clash",
                )
            linted.output shouldContain "wrasse found 2 error-level violation(s) in :app"
        } finally {
            playground.delete()
        }
    }

    should("fail wrasseFormat for a clash, count both directives and leave the source as it was") {
        val playground = Playground.create("columns-clash-format")
        try {
            playground.module("app", mapOf("sample/Sample.kt" to clashSource))
            playground.settings()
            playground.config(config)
            val location = playground.sourceUri("app", "sample/Sample.kt")

            val formatted = playground.runAndFail("wrasseFormat")

            findingLines(formatted.output) shouldBe
                listOf(
                    "e: $location:3:1 wrasse: format-matrix: $clash",
                    "e: $location:4:1 wrasse: format-columns: $clash",
                )
            formatted.output shouldContain "wrasse could not format: 2 format directive(s) were rejected"
            Files.readString(playground.dir.resolve("app/src/main/kotlin/sample/Sample.kt")) shouldBe clashSource
        } finally {
            playground.delete()
        }
    }

    should("count a rejected matrix directive and a rejected columns directive together") {
        val playground = Playground.create("columns-and-matrix-format-rejected")
        try {
            val both = "package sample\n\n// wrasse matrix:3\nval xs = arrayOf(1, 2, 3, 4)\n\n" +
                "// wrasse columns:0\nval ys = arrayOf(1, 2, 3, 4)\n"
            playground.module("app", mapOf("sample/Sample.kt" to both))
            playground.settings()
            playground.config(config)
            val location = playground.sourceUri("app", "sample/Sample.kt")

            val formatted = playground.runAndFail("wrasseFormat")

            findingLines(formatted.output) shouldBe
                listOf(
                    "e: $location:3:1 wrasse: format-matrix: '// wrasse matrix:3' cannot split the 4 elements of " +
                        "the call below into rows of 3: 4 is not divisible by 3",
                    "e: $location:6:1 wrasse: format-columns: $rejection",
                )
            formatted.output shouldContain "wrasse could not format: 2 format directive(s) were rejected"
            Files.readString(playground.dir.resolve("app/src/main/kotlin/sample/Sample.kt")) shouldBe both
        } finally {
            playground.delete()
        }
    }

    should("ignore every columns directive while format is disabled") {
        val playground = Playground.create("columns-format-disabled")
        try {
            playground.module("app", mapOf("sample/Sample.kt" to rejectedSource))
            playground.settings()
            playground.config("""{"format":{"enabled":false},"rules":{"no-semicolons":{"level":"error"}}}""")
            val sample = playground.dir.resolve("app/src/main/kotlin/sample/Sample.kt")

            playground.run("wrasseLint").output shouldNotContain "format-columns"
            playground.run("wrasseFormat").output shouldNotContain "format-columns"
            Files.readString(sample) shouldBe rejectedSource
        } finally {
            playground.delete()
        }
    }
})
