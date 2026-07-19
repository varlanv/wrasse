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
 * Dedicated real-compile safety net for `no-unit-return`'s EOL-comment bail (design.md §13 B.2) —
 * found empirically while probing upstream ktlint's own real behavior (not from its test suite):
 * deleting the whitespace between an EOL comment and the `Unit` type reference pulls the newline
 * out from under that comment, merging whatever follows (here, the function's `{`) onto the
 * comment's own line and corrupting the file. wrasse never autofixes any comment-adjacent shape
 * (block comment, KDoc, or EOL comment) for exactly this reason — see
 * [com.varlanv.wrasse.rules.NoUnitReturnRule]'s KDoc — but this spec locks the one shape where
 * getting it wrong would have broken the compile, mirroring [EmptyClassBodySafetySpec].
 */
open class NoUnitReturnSafetySpec : BaseSpec({

    val wrasseConfig = """{"rules":{"no-unit-return":{"level":"error"}}}"""

    should("leave a Unit return type followed by an EOL comment untouched and still compiling") {
        val source = TestSource(
            "sample/Sample.kt",
            """
            package sample

            fun foo(): // trailing comment
                Unit {}
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
