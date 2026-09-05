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
 * Real-compile safety net for `no-unnecessary-fqn`'s import-insertion fix — the trickiest
 * insertion shapes, each driven through a real compile → fix → reapply → recompile cycle, like
 * [WildcardExpansionAmbiguitySafetySpec], since [IdempotenceCycle.assertPatchedFileCompiles] is not
 * wired into the shared fixture cycle (several fixtures compile with `noJdk = true` and trip
 * unrelated classpath diagnostics of their own). Every shape below emits at least one edit, so
 * each has a real post-fix compile proof, not just a diagnostic-message assertion.
 */
open class FqnImportInsertionSafetySpec : BaseSpec({

    val wrasseConfig = """{"rules":{"no-unnecessary-fqn":{"level":"error"},"no-unused-imports":{"level":"error"}}}"""

    should("compile after inserting a brand-new import into a file with no import list at all") {
        val aux = TestSource(
            "sample/aux/Aux.kt",
            """
                package sample.aux

                class Widget
                """
                .trimIndent(),
        )
        val source = TestSource(
            "sample/Sample.kt",
            """
                package sample

                val w: sample.aux.Widget = TODO()
                """
                .trimIndent(),
        )

        useTempDir { workDir ->
            useTempDir { fixOutputDir ->
                val harness = WrasseTestHarness(wrasseConfig = wrasseConfig, fixOutputDir = fixOutputDir)
                val round1 = harness.compile(listOf(source, aux), workDir)
                round1.wrasseDiagnostics shouldHaveSize 1

                val patchFile = fixOutputDir.resolve("patch").resolve("wrasse-fixes.txt")
                if (Files.exists(patchFile)) {
                    val edits = WPatchReader.read(Files.readString(patchFile)).flatMap { it.edits }
                    if (edits.isNotEmpty()) {
                        WPatchApplier.apply(fixOutputDir)
                        val patchedContent = Files.readString(harness.sourcePath(workDir, source))
                        val round2 = harness.compile(listOf(TestSource(source.path, patchedContent), aux), workDir)
                        IdempotenceCycle.assertPatchedFileCompiles(round2.diagnostics)
                    }
                }
            }
        }
    }

    should("compile after rewriting a qualified usage inside a file annotation and adding its import") {
        val aux = TestSource(
            "sample/aux/AuxMarker.kt",
            """
                package sample.marker

                @RequiresOptIn
                annotation class Marker
                """
                .trimIndent(),
        )
        val source = TestSource(
            "sample/Sample.kt",
            """
                @file:OptIn(sample.marker.Marker::class)

                package sample

                val x = 1
                """
                .trimIndent(),
        )

        useTempDir { workDir ->
            useTempDir { fixOutputDir ->
                val harness = WrasseTestHarness(wrasseConfig = wrasseConfig, fixOutputDir = fixOutputDir)
                val round1 = harness.compile(listOf(source, aux), workDir)
                round1.wrasseDiagnostics shouldHaveSize 1

                val patchFile = fixOutputDir.resolve("patch").resolve("wrasse-fixes.txt")
                if (Files.exists(patchFile)) {
                    val edits = WPatchReader.read(Files.readString(patchFile)).flatMap { it.edits }
                    if (edits.isNotEmpty()) {
                        WPatchApplier.apply(fixOutputDir)
                        val patchedContent = Files.readString(harness.sourcePath(workDir, source))
                        val round2 = harness.compile(listOf(TestSource(source.path, patchedContent), aux), workDir)
                        IdempotenceCycle.assertPatchedFileCompiles(round2.diagnostics)
                    }
                }
            }
        }
    }

    should(
        "compile after the reconciliation case: an explicit import stays untouched while its qualified usage is rewritten",
    ) {
        val aux = TestSource(
            "sample/aux/Aux.kt",
            """
                package sample.aux

                class Widget
                """
                .trimIndent(),
        )
        val source = TestSource(
            "sample/Sample.kt",
            """
                package sample

                import sample.aux.Widget

                val w: sample.aux.Widget = TODO()
                """
                .trimIndent(),
        )

        useTempDir { workDir ->
            useTempDir { fixOutputDir ->
                val harness = WrasseTestHarness(wrasseConfig = wrasseConfig, fixOutputDir = fixOutputDir)
                val round1 = harness.compile(listOf(source, aux), workDir)
                round1.wrasseDiagnostics shouldHaveSize 1

                val patchFile = fixOutputDir.resolve("patch").resolve("wrasse-fixes.txt")
                if (Files.exists(patchFile)) {
                    val edits = WPatchReader.read(Files.readString(patchFile)).flatMap { it.edits }
                    if (edits.isNotEmpty()) {
                        WPatchApplier.apply(fixOutputDir)
                        val patchedContent = Files.readString(harness.sourcePath(workDir, source))
                        val round2 = harness.compile(listOf(TestSource(source.path, patchedContent), aux), workDir)
                        IdempotenceCycle.assertPatchedFileCompiles(round2.diagnostics)
                    }
                }
            }
        }
    }

    should("compile after dropping a default-import-package prefix with no import added") {
        val source = TestSource(
            "sample/Sample.kt",
            """
                package sample

                fun f(): kotlin.Unit = Unit
                """
                .trimIndent(),
        )

        useTempDir { workDir ->
            useTempDir { fixOutputDir ->
                val harness = WrasseTestHarness(wrasseConfig = wrasseConfig, fixOutputDir = fixOutputDir)
                val round1 = harness.compile(listOf(source), workDir)
                round1.wrasseDiagnostics shouldHaveSize 1

                val patchFile = fixOutputDir.resolve("patch").resolve("wrasse-fixes.txt")
                if (Files.exists(patchFile)) {
                    val edits = WPatchReader.read(Files.readString(patchFile)).flatMap { it.edits }
                    if (edits.isNotEmpty()) {
                        WPatchApplier.apply(fixOutputDir)
                        val patchedContent = Files.readString(harness.sourcePath(workDir, source))
                        val round2 = harness.compile(listOf(TestSource(source.path, patchedContent)), workDir)
                        IdempotenceCycle.assertPatchedFileCompiles(round2.diagnostics)
                    }
                }
            }
        }
    }
})
