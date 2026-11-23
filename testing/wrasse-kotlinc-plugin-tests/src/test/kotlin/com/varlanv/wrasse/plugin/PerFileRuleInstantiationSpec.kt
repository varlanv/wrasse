package com.varlanv.wrasse.plugin

import com.varlanv.wrasse.testing.BaseSpec
import com.varlanv.wrasse.testing.harness.TestSource
import com.varlanv.wrasse.testing.harness.WrasseTestHarness
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe

class PerFileRuleInstantiationSpec : BaseSpec({

    should("resolve one file's afterFile-deferred pending state without affecting the next file") {
        val config = """{"rules":{"no-semicolons":{"level":"error"}}}"""
        val harness = WrasseTestHarness(wrasseConfig = config)
        val pendingAtEof = TestSource("first/Pending.kt", "val x = 1;")
        val clean = TestSource("second/Clean.kt", "val y = 2\n")

        val result = harness.compile(listOf(pendingAtEof, clean))

        result.wrasseDiagnostics shouldHaveSize 1
        val diagnostic = result.wrasseDiagnostics[0]
        diagnostic.message shouldBe "wrasse: no-semicolons: Unnecessary semicolon"
        (diagnostic.location?.path?.contains("first") ?: false) shouldBe true
    }
})
