package com.varlanv.wrasse.plugin

import com.varlanv.wrasse.testing.BaseSpec
import com.varlanv.wrasse.testing.harness.TestSource
import com.varlanv.wrasse.testing.harness.WrasseTestHarness
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe

class LayoutDirectiveCrlfSpec : BaseSpec({

    val config = """{"format":{"enabled":true}}"""
    val noCall = "has no call to lay out: no call with a parenthesized argument list starts on the first line " +
        "below it that is neither an annotation-only line nor another layout directive (a function, constructor or " +
        "class declaration is not a call)"

    fun compile(vararg lines: String) = WrasseTestHarness(wrasseConfig = config)
        .compile(listOf(TestSource("sample/test.kt", lines.joinToString("") { "$it\r\n" })))
        .wrasseDiagnostics
        .map { "${it.location?.line}:${it.location?.column} ${it.message}" }

    should("find the call below a columns directive in a CRLF file and report a short last row as clean") {
        compile(
            "package sample",
            "",
            "// wrasse columns:3",
            "val xs = intArrayOf(",
            "    1, 22, 333,",
            "    4,",
            ")",
        ).shouldBeEmpty()
    }

    should("report a CRLF file whose columns call is not laid out yet") {
        compile(
            "package sample",
            "",
            "// wrasse columns:3",
            "val xs = intArrayOf(1, 22, 333, 4)",
        ) shouldBe listOf("1:1 wrasse: format: File is not wrasse-formatted")
    }

    should("report an indented rejected columns directive of a CRLF file at its own line and column") {
        compile(
            "package sample",
            "",
            "fun main() {",
            "    // wrasse columns:1",
            "    val xs = intArrayOf(1, 2, 3, 4)",
            "}",
        ) shouldBe listOf(
            "4:5 wrasse: format-columns: size 1 in '// wrasse columns:1' is below the minimum of 2: a row needs " +
                "at least 2 elements",
        )
    }

    should("skip annotation lines of a CRLF file between a directive and its call") {
        compile(
            "package sample",
            "",
            "// wrasse columns:3",
            "@Suppress(\"unused\")",
            "@JvmField",
            "val xs = intArrayOf(",
            "    1, 22, 333,",
            "    4,",
            ")",
        ).shouldBeEmpty()
    }

    should("see no call below a directive followed by an annotation and a blank CRLF line") {
        compile(
            "package sample",
            "",
            "// wrasse columns:2",
            "@JvmField",
            "",
            "val xs = intArrayOf(1, 2, 3, 4)",
        ) shouldBe listOf("3:1 wrasse: format-columns: '// wrasse columns:2' $noCall")
    }

    should("report a clash of stacked directives of a CRLF file on each directive under its own rule id") {
        val clash = "'// wrasse matrix:2' (line 3) and '// wrasse columns:2' (line 4) both target the call below; " +
            "keep only one"

        compile(
            "package sample",
            "",
            "// wrasse matrix:2",
            "// wrasse columns:2",
            "val xs = intArrayOf(1, 2, 3, 4)",
        ) shouldBe listOf("3:1 wrasse: format-matrix: $clash", "4:1 wrasse: format-columns: $clash")
    }

    should("report a clash across an annotation line of a CRLF file") {
        val clash = "'// wrasse columns:2' (line 3) and '// wrasse matrix:2' (line 5) both target the call below; " +
            "keep only one"

        compile(
            "package sample",
            "",
            "// wrasse columns:2",
            "@JvmField",
            "// wrasse matrix:2",
            "internal val xs = intArrayOf(1, 2)",
        ) shouldBe listOf("3:1 wrasse: format-columns: $clash", "5:1 wrasse: format-matrix: $clash")
    }
})
