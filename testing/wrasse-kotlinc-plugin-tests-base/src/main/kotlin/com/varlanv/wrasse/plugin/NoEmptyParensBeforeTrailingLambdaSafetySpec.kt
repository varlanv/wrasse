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
 * Real-compile safety net for `no-empty-parens-before-trailing-lambda`'s newline-gap bail: wrasse
 * never autofixes either newline-gap shape (a plain line break or a blank line before the trailing
 * lambda), since deleting the empty `()` there would break the compile — see
 * [com.varlanv.wrasse.rules.NoEmptyParensBeforeTrailingLambdaRule]'s KDoc for the invariant. This
 * spec locks both shapes, mirroring [NoUnitReturnSafetySpec].
 */
open class NoEmptyParensBeforeTrailingLambdaSafetySpec : BaseSpec({

    val wrasseConfig = """{"rules":{"no-empty-parens-before-trailing-lambda":{"level":"error"}}}"""

    should("leave empty parentheses followed by a newline then a trailing lambda untouched and still compiling") {
        val source = TestSource(
            "sample/Sample.kt",
            """
                package sample

                fun greet(f: () -> Unit) {}

                fun main() {
                    greet()
                    { }
                }
                """
                .trimIndent(),
        )

        useTempDir { workDir ->
            useTempDir { fixOutputDir ->
                val harness = WrasseTestHarness(wrasseConfig = wrasseConfig, fixOutputDir = fixOutputDir)
                val round1 = harness.compile(listOf(source), workDir)
                round1.wrasseDiagnostics shouldHaveSize 1

                val patchFile = fixOutputDir.resolve("wrasse-fixes.txt")
                if (Files.exists(patchFile)) {
                    WPatchApplier.apply(fixOutputDir)
                }

                val patchedContent = Files.readString(harness.sourcePath(workDir, source))
                patchedContent shouldBe source.content

                val round2 = harness.compile(listOf(TestSource(source.path, patchedContent)), workDir)
                IdempotenceCycle.assertPatchedFileCompiles(round2.diagnostics)
            }
        }
    }

    should("leave empty parentheses followed by a blank line then a trailing lambda untouched and still compiling") {
        val source = TestSource(
            "sample/Sample.kt",
            """
                package sample

                fun greet(f: () -> Unit) {}

                fun main() {
                    greet()

                    { }
                }
                """
                .trimIndent(),
        )

        useTempDir { workDir ->
            useTempDir { fixOutputDir ->
                val harness = WrasseTestHarness(wrasseConfig = wrasseConfig, fixOutputDir = fixOutputDir)
                val round1 = harness.compile(listOf(source), workDir)
                round1.wrasseDiagnostics shouldHaveSize 1

                val patchFile = fixOutputDir.resolve("wrasse-fixes.txt")
                if (Files.exists(patchFile)) {
                    WPatchApplier.apply(fixOutputDir)
                }

                val patchedContent = Files.readString(harness.sourcePath(workDir, source))
                patchedContent shouldBe source.content

                val round2 = harness.compile(listOf(TestSource(source.path, patchedContent)), workDir)
                IdempotenceCycle.assertPatchedFileCompiles(round2.diagnostics)
            }
        }
    }
})
