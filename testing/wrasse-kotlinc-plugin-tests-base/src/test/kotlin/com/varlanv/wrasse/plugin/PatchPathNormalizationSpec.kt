package com.varlanv.wrasse.plugin

import com.varlanv.wrasse.testing.BaseSpec
import com.varlanv.wrasse.testing.harness.TestSource
import com.varlanv.wrasse.testing.harness.WrasseTestHarness
import com.varlanv.wrasse.testing.useTempDir
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import java.nio.file.Files
import java.nio.file.Path

class PatchPathNormalizationSpec : BaseSpec({
    should("write an absolute, normalized path into the patch even when the compiler saw a non-normalized one") {
        useTempDir { workDir ->
            useTempDir { fixOutputDir ->
                val config = """{"rules":{"no-semicolons":{"level":"error"}}}"""
                val harness = WrasseTestHarness(wrasseConfig = config, fixOutputDir = fixOutputDir)
                val source = TestSource("sample/../sample/test.kt", "val x = 1;\n")

                harness.compile(listOf(source), workDir)

                val patchFile = fixOutputDir.resolve("wrasse-fixes.txt")
                Files.exists(patchFile) shouldBe true
                val patchContent = Files.readString(patchFile)
                val fileLine = patchContent.lines().first { it.startsWith("file:") }

                fileLine shouldNotContain ".."
                fileLine shouldContain "sample/test.kt"
                val writtenPath = Path.of(fileLine.substring("file:".length))
                writtenPath.isAbsolute shouldBe true
                writtenPath shouldBe writtenPath.normalize()
            }
        }
    }
})
