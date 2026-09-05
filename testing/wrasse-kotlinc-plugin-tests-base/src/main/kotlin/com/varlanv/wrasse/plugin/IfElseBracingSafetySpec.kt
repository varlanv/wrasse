package com.varlanv.wrasse.plugin

import com.varlanv.wrasse.lang.WPatchApplier
import com.varlanv.wrasse.testing.BaseSpec
import com.varlanv.wrasse.testing.harness.TestSource
import com.varlanv.wrasse.testing.harness.WrasseTestHarness
import com.varlanv.wrasse.testing.useTempDir
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import java.nio.file.Files

/**
 * Dedicated real-compile safety net for `if-else-bracing`'s two grammar-riskiest shapes: an
 * `else if` chain (never `else { if ... }`, [com.varlanv.wrasse.rules.IfElseBracingRule]'s own
 * KDoc) and a "dangling else" nested bare `if` (the `else` must keep binding to the *inner* `if`
 * after bracing, never appear to shift toward the outer one). The generic fixture harness already
 * runs D19's idempotence cycle over every fixture; this spec adds an independent, explicit
 * assertion on top for exactly these two shapes, mirroring [EmptyClassBodySafetySpec] /
 * [NoUnitReturnSafetySpec].
 */
open class IfElseBracingSafetySpec : BaseSpec({

    val wrasseConfig = """{"rules":{"if-else-bracing":{"level":"error"}}}"""

    should("brace every branch of an unbraced multi-line else-if chain without ever wrapping the else-if itself") {
        val source = TestSource(
            "sample/Sample.kt",
            """
            package sample

            fun classify(positive: Boolean, negative: Boolean): String {
                if (positive)
                    return "positive"
                else if (negative)
                    return "negative"
                else
                    return "zero"
            }
            """
                .trimIndent(),
        )

        useTempDir { workDir ->
            useTempDir { fixOutputDir ->
                val harness = WrasseTestHarness(wrasseConfig = wrasseConfig, fixOutputDir = fixOutputDir)
                val round1 = harness.compile(listOf(source), workDir)
                round1.wrasseDiagnostics shouldHaveSize 3

                val patchFile = fixOutputDir.resolve("patch").resolve("wrasse-fixes.txt")
                if (Files.exists(patchFile)) {
                    WPatchApplier.apply(fixOutputDir)
                }

                val patchedContent = Files.readString(harness.sourcePath(workDir, source))
                patchedContent shouldBe """
                    package sample

                    fun classify(positive: Boolean, negative: Boolean): String {
                        if (positive) {
                            return "positive"
                        } else if (negative) {
                            return "negative"
                        } else {
                            return "zero"
                        }
                    }
                    """
                    .trimIndent()
                patchedContent.contains("else { if") shouldBe false
                patchedContent.contains("else {if") shouldBe false

                val round2 = harness.compile(listOf(TestSource(source.path, patchedContent)), workDir)
                IdempotenceCycle.assertPatchedFileCompiles(round2.diagnostics)
                round2.wrasseDiagnostics shouldHaveSize 0
            }
        }
    }

    should("keep the else bound to the inner if of a dangling-else nested shape after bracing") {
        val source = TestSource(
            "sample/Sample.kt",
            """
            package sample

            fun classify(outer: Boolean, inner: Boolean): String {
                if (outer)
                    if (inner)
                        return "both"
                    else
                        return "outer-only"
                return "neither"
            }
            """
                .trimIndent(),
        )

        useTempDir { workDir ->
            useTempDir { fixOutputDir ->
                val harness = WrasseTestHarness(wrasseConfig = wrasseConfig, fixOutputDir = fixOutputDir)
                val round1 = harness.compile(listOf(source), workDir)
                round1.wrasseDiagnostics shouldHaveSize 3

                val patchFile = fixOutputDir.resolve("patch").resolve("wrasse-fixes.txt")
                if (Files.exists(patchFile)) {
                    WPatchApplier.apply(fixOutputDir)
                }

                val patchedContent = Files.readString(harness.sourcePath(workDir, source))
                patchedContent shouldBe """
                    package sample

                    fun classify(outer: Boolean, inner: Boolean): String {
                        if (outer)
                            if (inner) {
                                return "both"
                            } else {
                                return "outer-only"
                            }
                        return "neither"
                    }
                    """
                    .trimIndent()

                val round2 = harness.compile(listOf(TestSource(source.path, patchedContent)), workDir)
                IdempotenceCycle.assertPatchedFileCompiles(round2.diagnostics)
                round2.wrasseDiagnostics shouldHaveSize 1
            }
        }
    }
})
