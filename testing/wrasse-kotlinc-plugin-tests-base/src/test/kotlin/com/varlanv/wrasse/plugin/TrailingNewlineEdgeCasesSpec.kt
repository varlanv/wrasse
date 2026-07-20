package com.varlanv.wrasse.plugin

import com.varlanv.wrasse.testing.BaseSpec
import com.varlanv.wrasse.testing.harness.TestSource
import com.varlanv.wrasse.testing.harness.WrasseTestHarness
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe

class TrailingNewlineEdgeCasesSpec : BaseSpec({
    val config = """{"rules":{"trailing-newline":{"level":"error"}}}"""

    should("report nothing for a truly empty file") {
        val harness = WrasseTestHarness(wrasseConfig = config)
        val result = harness.compile(listOf(TestSource("sample/test.kt", "")))
        result.wrasseDiagnostics.shouldBeEmpty()
    }

    should("report an in-range span for a whitespace-only file with no trailing newline") {
        val harness = WrasseTestHarness(wrasseConfig = config)
        val result = harness.compile(listOf(TestSource("sample/test.kt", "   ")))
        result.wrasseDiagnostics shouldHaveSize 1
        val diagnostic = result.wrasseDiagnostics[0]
        diagnostic.location?.line shouldBe 1
        diagnostic.location?.column shouldBe 1
        diagnostic.message shouldBe "wrasse: trailing-newline: File must end with a newline"
    }

    should("report nothing for a file ending with multiple trailing newlines") {
        val harness = WrasseTestHarness(wrasseConfig = config)
        val result = harness.compile(listOf(TestSource("sample/test.kt", "val x = 1\n\n\n")))
        result.wrasseDiagnostics.shouldBeEmpty()
    }
})
