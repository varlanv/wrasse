package com.varlanv.wrasse.plugin

import com.varlanv.wrasse.testing.BaseSpec
import com.varlanv.wrasse.testing.harness.TestSource
import com.varlanv.wrasse.testing.harness.WrasseTestHarness
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe

open class ResolvedUsageDumpSpec :
    BaseSpec(
        {

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
                        """
                        .trimIndent(),
                )
                val harness = WrasseTestHarness(wrasseConfig = emptyRulesConfig, dumpResolvedUsage = true)

                val result = harness.compile(listOf(source))

                result.wrasseDiagnostics shouldHaveSize 1
                result.wrasseDiagnostics[0].message shouldBe
                    "wrasse: resolved-usage: classifiers=[kotlin.Double, kotlin.Function1, kotlin.Int, kotlin.Pair, kotlin.text.Regex] callables=[kotlin.Double/toInt, kotlin.Function1/invoke, kotlin.Int/plus, kotlin.Pair/Pair, kotlin.Pair/component1, kotlin.Pair/component2, kotlin.math/abs, kotlin.math/absoluteValue, kotlin.math/cbrt] imports=[kotlin.math.abs, kotlin.math.abs, kotlin.math.absoluteValue, kotlin.math.cbrt, kotlin.text.Regex] qualified=[173..178:TYPE_REF:kotlin.text.Regex, 181..184:TYPE_REF:kotlin.Int, 342..354:TYPE_REF:kotlin.Function1, 380..398:TYPE_REF:kotlin.Function1] errors=false"
            }

            should("dump errors=true for a file with an unresolved reference") {
                val source = TestSource(
                    "sample/Sample.kt",
                    """
                        package sample

                        fun sample() {
                            unresolvedFunction()
                        }
                        """
                        .trimIndent(),
                )
                val harness = WrasseTestHarness(wrasseConfig = emptyRulesConfig, dumpResolvedUsage = true)

                val result = harness.compile(listOf(source))

                result.wrasseDiagnostics shouldHaveSize 1
                result.wrasseDiagnostics[0].message shouldBe
                    "wrasse: resolved-usage: classifiers=[kotlin.Unit] callables=[] imports=[] qualified=[] errors=true"
            }

            should("dump empty-ish sets for a file with no references beyond its own declarations") {
                val source = TestSource(
                    "sample/Sample.kt",
                    """
                        package sample

                        class Empty
                        """
                        .trimIndent(),
                )
                val harness = WrasseTestHarness(wrasseConfig = emptyRulesConfig, dumpResolvedUsage = true)

                val result = harness.compile(listOf(source))

                result.wrasseDiagnostics shouldHaveSize 1
                result.wrasseDiagnostics[0].message shouldBe
                    "wrasse: resolved-usage: classifiers=[kotlin.Any, sample.Empty] callables=[kotlin.Any/Any] imports=[] qualified=[] errors=false"
            }

            should("dump cleanly for a trailing-newline-terminated file with no references beyond its own declarations") {
                val source = TestSource(
                    "sample/Sample.kt",
                    """
                            package sample

                            class Empty
                            """
                            .trimIndent() +
                        "\n",
                )
                val harness = WrasseTestHarness(wrasseConfig = emptyRulesConfig, dumpResolvedUsage = true)

                val result = harness.compile(listOf(source))

                result.wrasseDiagnostics shouldHaveSize 1
                result.wrasseDiagnostics[0].location?.line shouldBe 1
                result.wrasseDiagnostics[0].location?.column shouldBe 1
                result.wrasseDiagnostics[0].message shouldBe
                    "wrasse: resolved-usage: classifiers=[kotlin.Any, sample.Empty] callables=[kotlin.Any/Any] imports=[] qualified=[] errors=false"
            }

            should("dump both the abbreviated (typealias) classifier and its expansion for a supertype-position usage") {
                val aux = TestSource(
                    "sample/aux/Aux.kt",
                    """
                        package sample.aux

                        open class Base

                        typealias BaseAlias = Base
                        """
                        .trimIndent(),
                )
                val source = TestSource(
                    "sample/Sample.kt",
                    """
                        package sample

                        import sample.aux.BaseAlias

                        class Impl : BaseAlias()
                        """
                        .trimIndent(),
                )
                val harness = WrasseTestHarness(wrasseConfig = emptyRulesConfig, dumpResolvedUsage = true)

                val result = harness.compile(listOf(source, aux))

                result.wrasseDiagnostics shouldHaveSize 2
                val sampleDiagnostic = result.wrasseDiagnostics.single { it.message.contains("BaseAlias") }
                sampleDiagnostic.message shouldBe
                    "wrasse: resolved-usage: classifiers=[sample.Impl, sample.aux.Base, sample.aux.BaseAlias] callables=[sample.aux.Base/Base] imports=[sample.aux.BaseAlias] qualified=[58..67:TYPE_REF:sample.aux.BaseAlias] errors=false"
            }

            should("distinguish a package-star from a member-star import by its resolved parent class") {
                val aux = TestSource(
                    "sample/aux/Aux.kt",
                    """
                        package sample.aux

                        enum class Status {
                            ACTIVE,
                            INACTIVE,
                        }

                        class Widget
                        """
                        .trimIndent(),
                )
                val source = TestSource(
                    "sample/Sample.kt",
                    """
                        package sample

                        import sample.aux.*
                        import sample.aux.Status.*

                        val w = Widget()
                        val s = ACTIVE
                        """
                        .trimIndent(),
                )
                val harness = WrasseTestHarness(wrasseConfig = emptyRulesConfig, dumpResolvedUsage = true)

                val result = harness.compile(listOf(source, aux))

                result.wrasseDiagnostics shouldHaveSize 2
                val sampleDiagnostic = result.wrasseDiagnostics.single { it.message.contains("ACTIVE") }
                sampleDiagnostic.message shouldBe
                    "wrasse: resolved-usage: classifiers=[sample.aux.Status, sample.aux.Widget] callables=[sample.aux.Status/ACTIVE, sample.aux.Widget/Widget] imports=[sample.aux.*, sample.aux.Status.*(parent=sample.aux.Status)] qualified=[] errors=false"
            }

            should("collect nothing when dumpResolvedUsage is off and no rule requires resolution") {
                val source = TestSource(
                    "sample/Sample.kt",
                    """
                        package sample

                        fun sample(): Int = 1
                        """
                        .trimIndent(),
                )
                val harness = WrasseTestHarness(wrasseConfig = emptyRulesConfig)

                val result = harness.compile(listOf(source))

                result.wrasseDiagnostics.shouldBeEmpty()
            }
        },
    )
