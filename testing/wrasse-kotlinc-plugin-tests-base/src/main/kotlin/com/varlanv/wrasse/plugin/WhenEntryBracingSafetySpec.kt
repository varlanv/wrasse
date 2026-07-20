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
 * Dedicated real-compile safety net for `when-entry-bracing`'s grammar-riskiest shape: a bare
 * `when`-entry whose body is itself a bare `if`/`else` — the key cross-rule risk the assignment
 * brief calls out, since [com.varlanv.wrasse.rules.IfElseBracingRule] sees the exact same region.
 * `when-entry-bracing` bails on that entry (its own content spans multiple lines) while
 * `if-else-bracing` fixes the inner `if` independently; this asserts the combined `wrasseFix` pass
 * still converges to compiling, idempotent output and the `if`/`else` keeps binding correctly. The
 * generic fixture harness already runs D19's idempotence cycle over every fixture (see the
 * `when-if-bracing-combined` fixture directory); this spec adds an independent, explicit real-compile
 * assertion on top, mirroring [IfElseBracingSafetySpec].
 */
open class WhenEntryBracingSafetySpec : BaseSpec({

    val wrasseConfig = """{"rules":{"when-entry-bracing":{"level":"error"}}}"""

    should("brace every bare entry once some sibling is already braced and some entry has a multiline body") {
        val source = TestSource(
            "sample/Sample.kt",
            """
            package sample

            fun classify(x: Int): String {
                return when (x) {
                    1 -> {
                        "one"
                    }
                    2 ->
                        "two"
                    else -> "other"
                }
            }
            """.trimIndent(),
        )

        useTempDir { workDir ->
            useTempDir { fixOutputDir ->
                val harness = WrasseTestHarness(wrasseConfig = wrasseConfig, fixOutputDir = fixOutputDir)
                val round1 = harness.compile(listOf(source), workDir)
                round1.wrasseDiagnostics shouldHaveSize 2

                val patchFile = fixOutputDir.resolve("wrasse-fixes.txt")
                if (Files.exists(patchFile)) {
                    WPatchApplier.apply(fixOutputDir)
                }

                val patchedContent = Files.readString(harness.sourcePath(workDir, source))
                patchedContent shouldBe
                    """
                    package sample

                    fun classify(x: Int): String {
                        return when (x) {
                            1 -> {
                                "one"
                            }
                            2 -> {
                                "two"
                            }
                            else -> {
                                "other"
                            }
                        }
                    }
                    """.trimIndent()

                val round2 = harness.compile(listOf(TestSource(source.path, patchedContent)), workDir)
                IdempotenceCycle.assertPatchedFileCompiles(round2.diagnostics)
                round2.wrasseDiagnostics shouldHaveSize 0
            }
        }
    }

    should("bail on a when-entry whose body is a bare if while if-else-bracing fixes it independently, and converge") {
        val wrasseConfigCombined =
            """{"rules":{"when-entry-bracing":{"level":"error"},"if-else-bracing":{"level":"error"}}}"""
        val source = TestSource(
            "sample/Sample.kt",
            """
            package sample

            fun classify(x: Int, big: Boolean): String {
                return when (x) {
                    1 ->
                        if (big)
                            "big"
                        else
                            "small"
                    2 -> {
                        println("computing")
                        "two"
                    }
                    else -> "other"
                }
            }
            """.trimIndent(),
        )

        useTempDir { workDir ->
            useTempDir { fixOutputDir ->
                val harness = WrasseTestHarness(wrasseConfig = wrasseConfigCombined, fixOutputDir = fixOutputDir)
                val round1 = harness.compile(listOf(source), workDir)
                round1.wrasseDiagnostics shouldHaveSize 4

                val patchFile = fixOutputDir.resolve("wrasse-fixes.txt")
                if (Files.exists(patchFile)) {
                    WPatchApplier.apply(fixOutputDir)
                }

                val patchedContent = Files.readString(harness.sourcePath(workDir, source))
                patchedContent shouldBe
                    """
                    package sample

                    fun classify(x: Int, big: Boolean): String {
                        return when (x) {
                            1 ->
                                if (big) {
                                    "big"
                                } else {
                                    "small"
                                }
                            2 -> {
                                println("computing")
                                "two"
                            }
                            else -> {
                                "other"
                            }
                        }
                    }
                    """.trimIndent()

                val round2 = harness.compile(listOf(TestSource(source.path, patchedContent)), workDir)
                IdempotenceCycle.assertPatchedFileCompiles(round2.diagnostics)
                round2.wrasseDiagnostics shouldHaveSize 1
            }
        }
    }
})
