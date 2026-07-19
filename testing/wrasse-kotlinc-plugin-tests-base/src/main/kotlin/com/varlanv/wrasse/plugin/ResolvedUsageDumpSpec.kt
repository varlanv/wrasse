package com.varlanv.wrasse.plugin

import com.varlanv.wrasse.testing.BaseSpec
import com.varlanv.wrasse.testing.harness.TestSource
import com.varlanv.wrasse.testing.harness.WrasseTestHarness
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe

open class ResolvedUsageDumpSpec : BaseSpec({

    val emptyRulesConfig = """{"rules":{}}"""

    should("dump classifiers, callables and errors=false for a file exercising the full usage surface") {
        val source = TestSource(
            "sample/Sample.kt",
            """
            package sample

            import kotlin.math.abs
            import kotlin.math.abs as kabs
            import kotlin.math.absoluteValue
            import kotlin.math.cbrt
            import kotlin.text.Regex

            fun sample(pattern: Regex): Int {
                val n = abs(-1)
                val k = kabs(-2)
                val a = (-3).absoluteValue
                val sum = n + k
                val pair = Pair(sum, a)
                val (x, y) = pair
                val ref: (Int) -> Int = ::abs
                val cbrtRef: (Double) -> Double = ::cbrt
                return x + y + ref(0) + cbrtRef(0.0).toInt()
            }
            """.trimIndent(),
        )
        val harness = WrasseTestHarness(wrasseConfig = emptyRulesConfig, dumpResolvedUsage = true)

        val result = harness.compile(listOf(source))

        result.wrasseDiagnostics shouldHaveSize 1
        result.wrasseDiagnostics[0].message shouldBe "wrasse: resolved-usage: classifiers=[kotlin.Double, kotlin.Function1, kotlin.Int, kotlin.Pair, kotlin.text.Regex] callables=[kotlin.Double/toInt, kotlin.Function1/invoke, kotlin.Int/plus, kotlin.Pair/Pair, kotlin.Pair/component1, kotlin.Pair/component2, kotlin.math/abs, kotlin.math/absoluteValue, kotlin.math/cbrt] errors=false"
    }

    should("dump errors=true for a file with an unresolved reference") {
        val source = TestSource(
            "sample/Sample.kt",
            """
            package sample

            fun sample() {
                unresolvedFunction()
            }
            """.trimIndent(),
        )
        val harness = WrasseTestHarness(wrasseConfig = emptyRulesConfig, dumpResolvedUsage = true)

        val result = harness.compile(listOf(source))

        result.wrasseDiagnostics shouldHaveSize 1
        result.wrasseDiagnostics[0].message shouldBe "wrasse: resolved-usage: classifiers=[kotlin.Unit] callables=[] errors=true"
    }

    should("dump empty-ish sets for a file with no references beyond its own declarations") {
        val source = TestSource(
            "sample/Sample.kt",
            """
            package sample

            class Empty
            """.trimIndent(),
        )
        val harness = WrasseTestHarness(wrasseConfig = emptyRulesConfig, dumpResolvedUsage = true)

        val result = harness.compile(listOf(source))

        result.wrasseDiagnostics shouldHaveSize 1
        result.wrasseDiagnostics[0].message shouldBe "wrasse: resolved-usage: classifiers=[kotlin.Any, sample.Empty] callables=[kotlin.Any/Any] errors=false"
    }

    should("dump cleanly for a trailing-newline-terminated file with no references beyond its own declarations") {
        val source = TestSource(
            "sample/Sample.kt",
            """
            package sample

            class Empty
            """.trimIndent() + "\n",
        )
        val harness = WrasseTestHarness(wrasseConfig = emptyRulesConfig, dumpResolvedUsage = true)

        val result = harness.compile(listOf(source))

        result.wrasseDiagnostics shouldHaveSize 1
        result.wrasseDiagnostics[0].location?.line shouldBe 1
        result.wrasseDiagnostics[0].location?.column shouldBe 1
        result.wrasseDiagnostics[0].message shouldBe "wrasse: resolved-usage: classifiers=[kotlin.Any, sample.Empty] callables=[kotlin.Any/Any] errors=false"
    }

    should("collect nothing when dumpResolvedUsage is off and no rule requires resolution") {
        val source = TestSource(
            "sample/Sample.kt",
            """
            package sample

            fun sample(): Int = 1
            """.trimIndent(),
        )
        val harness = WrasseTestHarness(wrasseConfig = emptyRulesConfig)

        val result = harness.compile(listOf(source))

        result.wrasseDiagnostics.shouldBeEmpty()
    }
})
