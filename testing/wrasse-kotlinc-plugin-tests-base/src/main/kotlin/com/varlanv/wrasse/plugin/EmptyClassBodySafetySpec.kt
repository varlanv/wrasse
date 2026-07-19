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
 * Dedicated real-compile safety net for `no-empty-class-body`'s anonymous-object-expression bail
 * (design.md §13 B.2) — the one shape where deleting an empty body is a syntax error, not merely a
 * restyle, since kotlinc's grammar mandates a body on an object literal.
 *
 * [IdempotenceCycle]'s general `assertNoNewCompileErrors` guard is now wired into every fixture's
 * cycle and already catches a regression here (proven by sabotaging the bail and watching
 * `no-empty-class-body/object-literal-bail-error` fail with exactly the syntax error this spec
 * guards against). This spec is the explicit, fixture-independent lock named for this one bail,
 * mirroring [FqnImportInsertionSafetySpec]: the real bail means report without an edit, so the
 * file is byte-identical after "applying" whatever patch exists, and still compiles.
 */
open class EmptyClassBodySafetySpec : BaseSpec({

    val wrasseConfig = """{"rules":{"no-empty-class-body":{"level":"error"}}}"""

    should("leave an anonymous object expression's empty body untouched and still compiling") {
        val source = TestSource(
            "sample/Sample.kt",
            """
            package sample

            val anon = object {}
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
