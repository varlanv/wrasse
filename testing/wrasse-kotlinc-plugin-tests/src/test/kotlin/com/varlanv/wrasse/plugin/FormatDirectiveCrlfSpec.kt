package com.varlanv.wrasse.plugin

import com.varlanv.wrasse.testing.BaseSpec
import com.varlanv.wrasse.testing.harness.TestSource
import com.varlanv.wrasse.testing.harness.WrasseTestHarness
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe

class FormatDirectiveCrlfSpec : BaseSpec({

    val config = """{"format":{"enabled":true},"rules":{"no-semicolons":{"level":"error"}}}"""

    fun compile(vararg lines: String) = WrasseTestHarness(wrasseConfig = config)
        .compile(listOf(TestSource("sample/test.kt", lines.joinToString("") { "$it\r\n" })))
        .wrasseDiagnostics
        .map { "${it.location?.line}:${it.location?.column} ${it.message}" }

    should("report a CRLF file whose only difference lies inside a region as clean") {
        compile(
            "package sample",
            "",
            "fun main() {",
            "    val a = 1",
            "    // wrasse fmt:off",
            "    val   b    =    2   ",
            "",
            "        val c   =   3",
            "    // wrasse fmt:on",
            "    val d = 4",
            "}",
        ).shouldBeEmpty()
    }

    should("report a CRLF file with a difference outside the region once") {
        compile(
            "package sample",
            "",
            "fun main() {",
            "    val a   =   1",
            "    // wrasse fmt:off",
            "    val   b    =    2   ",
            "    // wrasse fmt:on",
            "    val d = 4",
            "}",
        ) shouldBe listOf("1:1 wrasse: format: File is not wrasse-formatted")
    }

    should("report a semicolon inside a CRLF region without formatting around it") {
        compile(
            "package sample",
            "",
            "fun main() {",
            "    val a = 1",
            "    // wrasse fmt:off",
            "    val b = 2;",
            "    // wrasse fmt:on",
            "    val d = 4",
            "}",
        ) shouldBe listOf("6:14 wrasse: no-semicolons: Unnecessary semicolon")
    }
})
