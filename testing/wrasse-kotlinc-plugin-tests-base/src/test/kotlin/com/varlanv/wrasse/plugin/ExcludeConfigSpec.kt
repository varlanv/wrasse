package com.varlanv.wrasse.plugin

import com.varlanv.wrasse.testing.BaseSpec
import com.varlanv.wrasse.testing.harness.TestSource
import com.varlanv.wrasse.testing.harness.WrasseTestHarness
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe

class ExcludeConfigSpec :
    BaseSpec(
        {

            should("skip a globally-excluded file while a sibling file still reports") {
                val config = """{"exclude":["**/excluded/**"],"rules":{"no-semicolons":{"level":"error"}}}"""
                val harness = WrasseTestHarness(wrasseConfig = config)
                val excludedSource = TestSource("excluded/Foo.kt", "val x = 1;\n")
                val includedSource = TestSource("included/Bar.kt", "val y = 2;\n")

                val result = harness.compile(listOf(excludedSource, includedSource))

                result.wrasseDiagnostics shouldHaveSize 1
                (result.wrasseDiagnostics[0].location?.path?.contains("included") ?: false) shouldBe true
            }

            should("skip a per-rule-excluded file while a sibling file still reports") {
                val config = """{"rules":{"no-semicolons":{"level":"error","exclude":["**/legacy/**"]}}}"""
                val harness = WrasseTestHarness(wrasseConfig = config)
                val legacySource = TestSource("legacy/Old.kt", "val x = 1;\n")
                val modernSource = TestSource("modern/New.kt", "val y = 2;\n")

                val result = harness.compile(listOf(legacySource, modernSource))

                result.wrasseDiagnostics shouldHaveSize 1
                (result.wrasseDiagnostics[0].location?.path?.contains("modern") ?: false) shouldBe true
            }

            should("not exclude anything when no exclude glob is configured") {
                val config = """{"rules":{"no-semicolons":{"level":"error"}}}"""
                val harness = WrasseTestHarness(wrasseConfig = config)
                val first = TestSource("a/First.kt", "val x = 1;\n")
                val second = TestSource("b/Second.kt", "val y = 2;\n")

                val result = harness.compile(listOf(first, second))

                result.wrasseDiagnostics shouldHaveSize 2
            }
        },
    )
