package com.varlanv.wrasse.plugin

import com.varlanv.wrasse.testing.BaseSpec
import com.varlanv.wrasse.testing.harness.TestSource
import com.varlanv.wrasse.testing.harness.WrasseTestHarness
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe

class MatrixDirectiveCrlfSpec : BaseSpec({

    val config = """{"format":{"enabled":true}}"""

    fun compile(vararg lines: String) = WrasseTestHarness(wrasseConfig = config)
        .compile(listOf(TestSource("sample/test.kt", lines.joinToString("") { "$it\r\n" })))
        .wrasseDiagnostics
        .map { "${it.location?.line}:${it.location?.column} ${it.message}" }

    should("find the call below a directive in a CRLF file and report a laid-out matrix as clean") {
        compile(
            "package sample",
            "",
            "// wrasse matrix:2",
            "val xs = intArrayOf(",
            "    1,   22,",
            "    333, 4,",
            ")",
        ).shouldBeEmpty()
    }

    should("report a CRLF file whose matrix is not laid out yet") {
        compile(
            "package sample",
            "",
            "// wrasse matrix:2",
            "val xs = intArrayOf(1, 22, 333, 4)",
        ) shouldBe listOf("1:1 wrasse: format: File is not wrasse-formatted")
    }

    should("report an indented rejected directive of a CRLF file at its own line and column") {
        compile(
            "package sample",
            "",
            "fun main() {",
            "    // wrasse matrix:3",
            "    val xs = intArrayOf(1, 2, 3, 4)",
            "}",
        ) shouldBe listOf(
            "4:5 wrasse: format-matrix: '// wrasse matrix:3' cannot split the 4 elements of the call below into " +
                "rows of 3: 4 is not divisible by 3",
        )
    }

    should("see no call below a directive followed by a blank CRLF line") {
        compile(
            "package sample",
            "",
            "// wrasse matrix:2",
            "",
            "val xs = intArrayOf(1, 2, 3, 4)",
        ) shouldBe listOf(
            "3:1 wrasse: format-matrix: '// wrasse matrix:2' has no call to lay out: no call with a parenthesized " +
                "argument list starts on the line directly below it (a function, constructor or class declaration " +
                "is not a call)",
        )
    }
})
