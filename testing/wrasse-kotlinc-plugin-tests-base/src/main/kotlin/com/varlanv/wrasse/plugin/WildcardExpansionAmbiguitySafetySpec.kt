package com.varlanv.wrasse.plugin

import com.varlanv.wrasse.lang.WPatchApplier
import com.varlanv.wrasse.lang.WPatchReader
import com.varlanv.wrasse.testing.BaseSpec
import com.varlanv.wrasse.testing.harness.TestSource
import com.varlanv.wrasse.testing.harness.WrasseTestHarness
import com.varlanv.wrasse.testing.useTempDir
import io.kotest.matchers.collections.shouldHaveSize
import java.nio.file.Files

/**
 * Dedicated real-compile safety net for the two ambiguity shapes `WildcardExpansionDecision`'s
 * simple-name collision bail exists to prevent (design.md §8): the resolved-usage facade cannot
 * distinguish "resolved because this star brought the name into scope" from "resolved via full
 * qualification, needing no import at all", so a naive attribution can emit an edit that either
 * breaks compilation outright (conflicting import) or silently changes what a bare name resolves
 * to (a flip with no diagnostic pointing back at the fix). Kept separate from the fixture-driven
 * `IdempotenceCycle` cycle every other fixture goes through: `IdempotenceCycle.assertPatchedFileCompiles`
 * is deliberately not wired into that shared path (several existing fixtures compile with
 * `noJdk = true` and trip unrelated classpath diagnostics of their own), so this spec drives the
 * compile → fix → reapply → recompile cycle directly and applies that stronger check only here,
 * where a false positive from an unrelated pre-existing fixture can't drown it out. When the bail
 * fires correctly, no edit is emitted at all — no patch file, nothing further to check.
 */
open class WildcardExpansionAmbiguitySafetySpec : BaseSpec({

    val wrasseConfig = """{"rules":{"no-wildcard-imports":{"level":"error"}}}"""

    should("never emit a star-expansion edit that breaks compilation via a conflicting import") {
        val aux1 = TestSource(
            "sample/auxc1/Aux1.kt",
            """
            package sample.auxc1

            class Item {
                companion object {
                    fun make(): Int = 1
                }
            }
            """.trimIndent(),
        )
        val aux2 = TestSource(
            "sample/auxc2/Aux2.kt",
            """
            package sample.auxc2

            class Item
            """.trimIndent(),
        )
        val source = TestSource(
            "sample/Sample.kt",
            """
            package sample

            import sample.auxc2.Item
            import sample.auxc1.*

            val q = Item()
            val p = sample.auxc1.Item.make()
            """.trimIndent(),
        )

        useTempDir { workDir ->
            useTempDir { fixOutputDir ->
                val harness = WrasseTestHarness(wrasseConfig = wrasseConfig, fixOutputDir = fixOutputDir)
                val round1 = harness.compile(listOf(source, aux1, aux2), workDir)
                round1.wrasseDiagnostics shouldHaveSize 1

                val patchFile = fixOutputDir.resolve("wrasse-fixes.txt")
                if (Files.exists(patchFile)) {
                    val edits = WPatchReader.read(Files.readString(patchFile)).flatMap { it.edits }
                    if (edits.isNotEmpty()) {
                        WPatchApplier.apply(fixOutputDir)
                        val patchedContent = Files.readString(harness.sourcePath(workDir, source))
                        val round2 = harness.compile(
                            listOf(TestSource(source.path, patchedContent), aux1, aux2),
                            workDir,
                        )
                        IdempotenceCycle.assertPatchedFileCompiles(round2.diagnostics)
                    }
                }
            }
        }
    }

    should("never emit a star-expansion edit that silently flips what a qualified-colliding bare name resolves to") {
        val aux = TestSource(
            "sample/auxflip/AuxFlip.kt",
            """
            package sample.auxflip

            class List
            """.trimIndent(),
        )
        val source = TestSource(
            "sample/Sample.kt",
            """
            package sample

            import sample.auxflip.*

            val bareList: List<Int> = listOf(1, 2, 3)
            val qualified = sample.auxflip.List()
            """.trimIndent(),
        )

        useTempDir { workDir ->
            useTempDir { fixOutputDir ->
                val harness = WrasseTestHarness(wrasseConfig = wrasseConfig, fixOutputDir = fixOutputDir)
                val round1 = harness.compile(listOf(source, aux), workDir)
                round1.wrasseDiagnostics shouldHaveSize 1

                val patchFile = fixOutputDir.resolve("wrasse-fixes.txt")
                if (Files.exists(patchFile)) {
                    val edits = WPatchReader.read(Files.readString(patchFile)).flatMap { it.edits }
                    if (edits.isNotEmpty()) {
                        WPatchApplier.apply(fixOutputDir)
                        val patchedContent = Files.readString(harness.sourcePath(workDir, source))
                        val round2 = harness.compile(
                            listOf(TestSource(source.path, patchedContent), aux),
                            workDir,
                        )
                        IdempotenceCycle.assertPatchedFileCompiles(round2.diagnostics)
                    }
                }
            }
        }
    }
})
