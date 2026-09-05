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
 * Dedicated real-compile safety net for `no-empty-class-body`'s anonymous-object-expression
 * exemption — the one shape where an empty body is syntactically mandatory, since kotlinc's
 * grammar requires a body on an object literal. Matching both upstream ktlint
 * (`!isPartOf(OBJECT_LITERAL)`) and detekt's `EmptyClassBlock` (`isObjectLiteral()`), wrasse never
 * reports this shape at all — this spec locks that across every supported Kotlin minor via a real
 * compile, and confirms the file is left untouched regardless.
 */
open class EmptyClassBodySafetySpec : BaseSpec({

    val wrasseConfig = """{"rules":{"no-empty-class-body":{"level":"error"}}}"""

    should("never report an anonymous object expression's empty body, and leave it untouched") {
        val source = TestSource(
            "sample/Sample.kt",
            """
                package sample

                val anon = object {}
                """
                .trimIndent(),
        )

        useTempDir { workDir ->
            useTempDir { fixOutputDir ->
                val harness = WrasseTestHarness(wrasseConfig = wrasseConfig, fixOutputDir = fixOutputDir)
                val round1 = harness.compile(listOf(source), workDir)
                round1.wrasseDiagnostics shouldHaveSize 0

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
