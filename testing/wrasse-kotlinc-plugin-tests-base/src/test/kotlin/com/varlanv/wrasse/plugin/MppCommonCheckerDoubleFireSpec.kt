package com.varlanv.wrasse.plugin

import com.varlanv.wrasse.lang.WPatchReader
import com.varlanv.wrasse.testing.BaseSpec
import com.varlanv.wrasse.testing.harness.TestSource
import com.varlanv.wrasse.testing.harness.WrasseTestHarness
import com.varlanv.wrasse.testing.useTempDir
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import java.nio.file.Files

class MppCommonCheckerDoubleFireSpec : BaseSpec({

    val config = """{"rules":{"no-semicolons":{"level":"error"}}}"""
    val commonSource = TestSource(
        "common/Common.kt",
        "package sample\n\nexpect fun platformName(): String\n\nval x = 1;\n",
    )
    val platformSource = TestSource(
        "platform/Platform.kt",
        "package sample\n\nactual fun platformName(): String = \"jvm\"\n",
    )

    should("report the common source's violation exactly once in a legacy -Xcommon-sources MPP compile") {
        val harness = WrasseTestHarness(
            wrasseConfig = config,
            multiPlatformCommonSources = setOf(commonSource.path),
        )

        val result = harness.compile(listOf(commonSource, platformSource))

        result.wrasseDiagnostics shouldHaveSize 1
        val diagnostic = result.wrasseDiagnostics[0]
        diagnostic.message shouldBe "wrasse: no-semicolons: Unnecessary semicolon"
        (diagnostic.location?.path?.contains("Common.kt") ?: false) shouldBe true
    }

    should("append the common source's fix edits to the patch exactly once in a legacy -Xcommon-sources MPP compile") {
        useTempDir { workDir ->
            useTempDir { fixOutputDir ->
                val harness = WrasseTestHarness(
                    wrasseConfig = config,
                    fixOutputDir = fixOutputDir,
                    multiPlatformCommonSources = setOf(commonSource.path),
                )

                harness.compile(listOf(commonSource, platformSource), workDir)

                val patchFile = fixOutputDir.resolve("wrasse-fixes.txt")
                Files.exists(patchFile) shouldBe true
                val patchContent = Files.readString(patchFile)
                val commonFilePath = harness.sourcePath(workDir, commonSource).toString()

                val fileEditsForCommon = WPatchReader.read(patchContent).filter { it.filePath == commonFilePath }
                fileEditsForCommon shouldHaveSize 1
                fileEditsForCommon[0].edits shouldHaveSize 1

                val fileLineOccurrences = patchContent.lines().count { it == "file:$commonFilePath" }
                fileLineOccurrences shouldBe 1
            }
        }
    }
})
