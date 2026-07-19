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
 * Dedicated real-compile safety net for `no-empty-parens-before-trailing-lambda`'s newline-gap
 * bail (design.md §13 B.2) — found empirically while probing upstream ktlint's own real behavior
 * (not from its test suite, which covers only three shapes): deleting the empty `()` when a
 * newline sits between it and the trailing lambda strips the only token that told the parser
 * "this is still a call" — reproduced with a real `K2JVMCompiler` run, where the naively-fixed
 * form failed to recompile with `error: Function invocation 'forEach(...)' expected`. wrasse never
 * autofixes either newline-gap shape (a plain line break or a blank line before the lambda) for
 * exactly this reason — see [com.varlanv.wrasse.rules.NoEmptyParensBeforeTrailingLambdaRule]'s
 * KDoc — but this spec locks both shapes where getting it wrong would have broken the compile,
 * mirroring [NoUnitReturnSafetySpec].
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
            """.trimIndent(),
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
            """.trimIndent(),
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
