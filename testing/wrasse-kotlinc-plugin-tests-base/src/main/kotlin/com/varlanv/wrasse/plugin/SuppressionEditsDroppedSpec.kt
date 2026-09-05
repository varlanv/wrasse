package com.varlanv.wrasse.plugin

import com.varlanv.wrasse.lang.WPatchReader
import com.varlanv.wrasse.testing.BaseSpec
import com.varlanv.wrasse.testing.harness.TestSource
import com.varlanv.wrasse.testing.harness.WrasseTestHarness
import com.varlanv.wrasse.testing.useTempDir
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import java.nio.file.Files

/**
 * Verifies suppressed-violation self-cleaning: a suppressed fixable violation neither reports nor
 * emits an edit — the suppression gate lives in `WReporter.report`, before any
 * [com.varlanv.wrasse.model.EditPlan.add] call. Drives two real compiles of the same file path
 * against the same `fixOutputDir`: round 1 (unsuppressed) emits a patch entry; round 2, with the
 * violation now suppressed, must leave that file with zero edits via merge-on-write — not merely
 * absent from round 1's own output. Neither `WrasseFixtureSpec` (one compile per fixture) nor
 * `IdempotenceCycle` (apply-and-recompile on the same content) expresses this two-round,
 * edited-between-rounds sequence, hence a dedicated spec.
 */
open class SuppressionEditsDroppedSpec : BaseSpec({

    val wrasseConfig = """{"rules":{"no-unused-imports":{"level":"error"}}}"""

    should("drop a previously-emitted patch entry once the violation is suppressed and the file recompiles") {
        useTempDir { workDir ->
            useTempDir { fixOutputDir ->
                val harness = WrasseTestHarness(wrasseConfig = wrasseConfig, fixOutputDir = fixOutputDir)
                val unsuppressed = TestSource(
                    "sample/test.kt",
                    """
                        package sample

                        import kotlin.text.Regex

                        val x = 1
                        """
                        .trimIndent(),
                )

                val round1 = harness.compile(listOf(unsuppressed), workDir)
                round1.wrasseDiagnostics shouldHaveSize 1

                val patchFile = fixOutputDir.resolve("wrasse-fixes.txt")
                Files.exists(patchFile) shouldBe true
                val round1Entries = WPatchReader.read(Files.readString(patchFile))
                val round1Entry = round1Entries.firstOrNull { it.filePath.endsWith("test.kt") }
                (round1Entry != null && round1Entry.edits.isNotEmpty()) shouldBe true

                val suppressed = TestSource(
                    "sample/test.kt",
                    """
                    @file:Suppress("no-unused-imports")

                    package sample

                    import kotlin.text.Regex

                    val x = 1
                    """
                        .trimIndent(),
                )

                val round2 = harness.compile(listOf(suppressed), workDir)
                round2.wrasseDiagnostics.shouldBeEmpty()

                val round2Entries = WPatchReader.read(Files.readString(patchFile))
                val round2Entry = round2Entries.firstOrNull { it.filePath.endsWith("test.kt") }
                (round2Entry == null || round2Entry.edits.isEmpty()) shouldBe true
            }
        }
    }
})
