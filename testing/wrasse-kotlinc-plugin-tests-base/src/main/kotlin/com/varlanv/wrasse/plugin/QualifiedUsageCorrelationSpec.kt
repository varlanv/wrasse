package com.varlanv.wrasse.plugin

import com.varlanv.wrasse.testing.BaseSpec
import com.varlanv.wrasse.testing.harness.TestSource
import com.varlanv.wrasse.testing.harness.WrasseTestHarness
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe

open class QualifiedUsageCorrelationSpec : BaseSpec({

    val emptyRulesConfig = """{"rules":{}}"""

    should(
        "correlate FIR-resolved qualifier/type-ref spans with the LightTree offsets the walk itself sees, and record nothing for desugared hazard constructs",
    ) {
        val aux = TestSource(
            "sample/aux/Aux.kt",
            """
                package sample.aux

                annotation class A

                object C {
                    fun staticLike(): Int = 1

                    class Nested
                }

                enum class Color {
                    RED,
                    GREEN,
                }
                """
                .trimIndent(),
        )
        val source = TestSource(
            "sample/Sample.kt",
            """
            package sample

            @sample.aux.A
            class Sample {
                val c: sample.aux.C = sample.aux.C
                val nested: sample.aux.C.Nested = sample.aux.C.Nested()
                val color: sample.aux.Color = sample.aux.Color.RED
                val staticCall: Int = sample.aux.C.staticLike()

                fun hazards(): Int {
                    for (i in 1..3) {
                        println(i)
                    }
                    val (a, b) = Pair(1, 2)
                    val s = "text ${'$'}a ${'$'}b"
                    val cond = if (a > 0) 1 else 2
                    println(s)
                    return cond
                }
            }
            """
                .trimIndent(),
        )
        val harness = WrasseTestHarness(wrasseConfig = emptyRulesConfig, dumpResolvedUsage = true)

        val result = harness.compile(listOf(source, aux))

        result.wrasseDiagnostics shouldHaveSize 2
        val sampleDiagnostic = result.wrasseDiagnostics.single { it.location?.path?.endsWith("Sample.kt") == true }
        sampleDiagnostic.message shouldBe
            "wrasse: resolved-usage: classifiers=[kotlin.Any, kotlin.Int, kotlin.Pair, kotlin.String, kotlin.collections.IntIterator, sample.Sample, sample.aux.A, sample.aux.C, sample.aux.C.Nested, sample.aux.Color] callables=[_synthetic/WHEN_CALL, kotlin.Any/Any, kotlin.Int/compareTo, kotlin.Int/rangeTo, kotlin.Pair/Pair, kotlin.Pair/component1, kotlin.Pair/component2, kotlin.collections.IntIterator/hasNext, kotlin.collections.IntIterator/next, kotlin.io/println, kotlin.ranges.IntProgression/iterator, sample.aux.A/A, sample.aux.C.Nested/Nested, sample.aux.C/staticLike, sample.aux.Color/RED] imports=[] qualified=[17..29:TYPE_REF:sample.aux.A, 56..68:TYPE_REF:sample.aux.C, 71..83:QUALIFIER:sample.aux.C, 100..119:TYPE_REF:sample.aux.C.Nested, 122..134:QUALIFIER:sample.aux.C, 159..175:TYPE_REF:sample.aux.Color, 178..194:QUALIFIER:sample.aux.Color, 219..222:TYPE_REF:kotlin.Int, 225..237:QUALIFIER:sample.aux.C, 271..274:TYPE_REF:kotlin.Int] calls=[315..325:kotlin.io/println:stable:[323..324=message], 357..367:kotlin.Pair/Pair:stable:[362..363=first,365..366=second], 444..454:kotlin.io/println:stable:[452..453=message]] errors=false"
    }

    should(
        "record a typealias-abbreviated type ref's span against the alias's own classId, not its expansion (D.2 facade fix)",
    ) {
        val aux = TestSource(
            "sample/aux/Widget.kt",
            """
                package sample.aux

                class Widget

                typealias WidgetAlias = Widget
                """
                .trimIndent(),
        )
        val source = TestSource(
            "sample/AliasSample.kt",
            """
                package sample

                class AliasSample {
                    val w: sample.aux.WidgetAlias = sample.aux.Widget()
                }
                """
                .trimIndent(),
        )
        val harness = WrasseTestHarness(wrasseConfig = emptyRulesConfig, dumpResolvedUsage = true)

        val result = harness.compile(listOf(source, aux))

        result.wrasseDiagnostics shouldHaveSize 2
        val sampleDiagnostic = result.wrasseDiagnostics.single { it.location?.path?.endsWith("AliasSample.kt") == true }
        sampleDiagnostic.message shouldBe
            "wrasse: resolved-usage: classifiers=[kotlin.Any, sample.AliasSample, sample.aux.Widget, sample.aux.WidgetAlias] callables=[kotlin.Any/Any, sample.aux.Widget/Widget] imports=[] qualified=[47..69:TYPE_REF:sample.aux.WidgetAlias] calls=[] errors=false"
    }
})
