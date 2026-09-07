package com.varlanv.wrasse.plugin

import com.varlanv.wrasse.testing.BaseSpec
import com.varlanv.wrasse.testing.harness.TestSource
import com.varlanv.wrasse.testing.harness.WrasseTestHarness
import com.varlanv.wrasse.testing.useTempDir
import io.kotest.matchers.shouldBe
import java.nio.file.Files
import org.jetbrains.kotlin.cli.common.ExitCode
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity

/**
 * A fix patch that cannot be read or written never costs the user their build nor their
 * diagnostics: the file's rule reports still print, one warning names the failed journal, and
 * kotlinc finishes normally.
 */
open class PatchStoreFailureIsolationSpec : BaseSpec({

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

    should("report the file's diagnostics plus one warning when the journal path is a directory") {
        useTempDir { workDir ->
            useTempDir { fixOutputDir ->
                val journal = fixOutputDir.resolve("patch").resolve("wrasse-fixes.txt")
                Files.createDirectories(journal)
                val harness = WrasseTestHarness(
                    wrasseConfig = wrasseConfig,
                    warnOnly = true,
                    fixOutputDir = fixOutputDir,
                )
                val result = harness.compile(listOf(source), workDir)

                result.exitCode shouldBe ExitCode.OK
                result.wrasseDiagnostics.map { it.severity to it.message } shouldBe
                    listOf(
                        CompilerMessageSeverity.WARNING to
                            "wrasse: wrasse could not update the fix patch $journal " +
                                "(IOException: Is a directory); this file's diagnostics are reported but will not be autofixed",
                        CompilerMessageSeverity.WARNING to
                            "wrasse: magic-number: This expression contains a magic number; consider defining it as a well-named constant",
                        CompilerMessageSeverity.WARNING to "wrasse: no-semicolons: Unnecessary semicolon",
                    )
                Files.isDirectory(journal) shouldBe true
            }
        }
    }
})
