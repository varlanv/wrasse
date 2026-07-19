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
 * Real-compile safety net for `WildcardExpansionDecision`'s simple-name collision bail (design.md
 * §8): a naive attribution could otherwise emit an edit that breaks compilation via a conflicting
 * import, or silently changes what a bare name resolves to. Drives compile → fix → reapply →
 * recompile directly and applies [IdempotenceCycle.assertPatchedFileCompiles]'s stronger check —
 * not the shared [IdempotenceCycle.runIfFixEmitted] path, which skips that check to tolerate other
 * fixtures' unrelated `noJdk = true` classpath diagnostics.
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
