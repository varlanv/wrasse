package com.varlanv.wrasse.plugin

import com.varlanv.wrasse.lang.FormatRequest
import com.varlanv.wrasse.lang.WPatchReader
import com.varlanv.wrasse.testing.BaseSpec
import com.varlanv.wrasse.testing.harness.TestSource
import com.varlanv.wrasse.testing.harness.WrasseTestHarness
import com.varlanv.wrasse.testing.useTempDir
import io.kotest.matchers.shouldBe
import java.nio.file.Files

/**
 * A format run (a fresh `format-request` in the compilation's `fixOutputDir`) keeps every
 * diagnostic that carries edits quiet — they are about to be applied — while diagnostics without
 * edits still print; the patch is emitted regardless and the request file is consumed.
 */
open class FormatRunSuppressionSpec : BaseSpec({

    val wrasseConfig = """{"rules":{"no-semicolons":{"level":"error"},"magic-number":{"level":"error"}}}"""
    val source = TestSource(
        "sample/Sample.kt",
        """
            package sample

            fun main() {
                println(42);
            }
            """
            .trimIndent(),
    )
    val semicolonMessage = "wrasse: no-semicolons: Unnecessary semicolon"
    val magicNumberMessage =
        "wrasse: magic-number: This expression contains a magic number; consider defining it as a well-named constant"

    should("keep autofixable diagnostics quiet during a format run, still emit their edits, and consume the request") {
        useTempDir { workDir ->
            useTempDir { fixOutputDir ->
                FormatRequest.write(fixOutputDir)
                val harness = WrasseTestHarness(wrasseConfig = wrasseConfig, fixOutputDir = fixOutputDir)
                val result = harness.compile(listOf(source), workDir)

                result.wrasseDiagnostics.map { it.message } shouldBe listOf(magicNumberMessage)
                Files.exists(fixOutputDir.resolve(FormatRequest.FILE_NAME)) shouldBe false
                val edits = WPatchReader
                    .read(Files.readString(fixOutputDir.resolve("patch").resolve("wrasse-fixes.txt")))
                    .flatMap { it.edits }
                edits.size shouldBe 1
            }
        }
    }

    should("report everything when the request is stale") {
        useTempDir { workDir ->
            useTempDir { fixOutputDir ->
                FormatRequest.write(
                    fixOutputDir,
                    now = System.currentTimeMillis() - FormatRequest.MAX_AGE_MILLIS - 1_000,
                )
                val harness = WrasseTestHarness(wrasseConfig = wrasseConfig, fixOutputDir = fixOutputDir)
                val result = harness.compile(listOf(source), workDir)

                result.wrasseDiagnostics.map { it.message } shouldBe listOf(magicNumberMessage, semicolonMessage)
                Files.exists(fixOutputDir.resolve(FormatRequest.FILE_NAME)) shouldBe false
            }
        }
    }

    should("report everything when there is no request") {
        useTempDir { workDir ->
            useTempDir { fixOutputDir ->
                val harness = WrasseTestHarness(wrasseConfig = wrasseConfig, fixOutputDir = fixOutputDir)
                val result = harness.compile(listOf(source), workDir)

                result.wrasseDiagnostics.map { it.message } shouldBe listOf(magicNumberMessage, semicolonMessage)
            }
        }
    }
})
