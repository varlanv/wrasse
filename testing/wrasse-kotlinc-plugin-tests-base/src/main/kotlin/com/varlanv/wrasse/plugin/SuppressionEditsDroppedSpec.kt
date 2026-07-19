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
 * D22 self-cleaning interaction (design.md §22, matrix item 5): a suppressed fixable violation
 * must neither report nor emit an edit at all — the suppression collector's gate lives directly
 * in `WReporter.report`, before any [com.varlanv.wrasse.model.EditPlan.add] call, so a suppressed
 * report's edits are never added to the plan in the first place (see `WrassePlugin.checkFile`).
 * This drives the genuine two-compile sequence D22's merge-on-write depends on: round 1 (no
 * suppression) emits a real patch entry for the file; round 2, same `fixOutputDir`, same file path,
 * now annotated `@file:Suppress("no-unused-imports")`, must report nothing and — via merge-on-write
 * — leave that file with zero edits after the recompile, not merely absent from round 1's own
 * output. Kept as a dedicated spec (the `WildcardExpansionAmbiguitySafetySpec` pattern) rather than
 * a plain fixture: `WrasseFixtureSpec` drives exactly one compile per fixture, plus `IdempotenceCycle`'s
 * own apply-and-recompile cycle on the *same* content — neither expresses "recompile the file after
 * editing its source between rounds", which is what self-cleaning actually depends on.
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
                    """.trimIndent(),
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
                    """.trimIndent(),
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
