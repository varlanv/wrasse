package com.varlanv.wrasse.plugin

import com.varlanv.wrasse.lang.FileApplyResult
import com.varlanv.wrasse.lang.WPatchApplier
import com.varlanv.wrasse.testing.BaseSpec
import com.varlanv.wrasse.testing.harness.TestSource
import com.varlanv.wrasse.testing.harness.WrasseTestHarness
import com.varlanv.wrasse.testing.useTempDir
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import java.nio.file.Files

class CrlfPatchConsistencySpec : BaseSpec({
    should("skip applying a fix to a CRLF file rather than splice at shifted offsets") {
        useTempDir { workDir ->
            useTempDir { fixOutputDir ->
                val config = """{"rules":{"no-semicolons":{"level":"error"}}}"""
                val harness = WrasseTestHarness(wrasseConfig = config, fixOutputDir = fixOutputDir)
                val source = TestSource("sample/test.kt", "package sample\r\n\r\nval x = 1;\r\n")

                val result = harness.compile(listOf(source), workDir)

                result.wrasseDiagnostics shouldHaveSize 1
                val sourceFile = harness.sourcePath(workDir, source)
                val originalContent = Files.readString(sourceFile)

                val applyResult = WPatchApplier.apply(fixOutputDir)

                applyResult.files shouldHaveSize 1
                applyResult.files[0].shouldBeInstanceOf<FileApplyResult.Skipped>()
                val skipped = applyResult.files[0] as FileApplyResult.Skipped
                skipped.reason shouldBe
                    "source line endings differ from what the compiler analyzed (CRLF vs LF); re-run the build to refresh the patch"
                Files.readString(sourceFile) shouldBe originalContent
            }
        }
    }
})
