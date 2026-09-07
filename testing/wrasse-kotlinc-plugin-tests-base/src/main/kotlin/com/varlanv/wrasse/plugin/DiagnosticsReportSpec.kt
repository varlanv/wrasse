package com.varlanv.wrasse.plugin

import com.varlanv.wrasse.lang.FormatRequest
import com.varlanv.wrasse.lang.WReportReader
import com.varlanv.wrasse.lang.replayReports
import com.varlanv.wrasse.testing.BaseSpec
import com.varlanv.wrasse.testing.harness.TestSource
import com.varlanv.wrasse.testing.harness.WrasseTestHarness
import com.varlanv.wrasse.testing.useTempDir
import io.kotest.matchers.shouldBe
import java.nio.file.Files

/**
 * Every diagnostic a compile reports is also recorded in `patch/wrasse-report.txt` with the
 * rule's configured level, autofixable ones included even during a format run, so a build tool
 * can replay them for a compile that does not run again.
 */
open class DiagnosticsReportSpec : BaseSpec({

    val wrasseConfig = """{"rules":{"no-semicolons":{"level":"error"},"magic-number":{"level":"warn"}}}"""
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

    should("record both diagnostics with their configured levels even when warn-only demotes the compile") {
        useTempDir { workDir ->
            useTempDir { fixOutputDir ->
                val harness = WrasseTestHarness(
                    wrasseConfig = wrasseConfig,
                    warnOnly = true,
                    fixOutputDir = fixOutputDir,
                )
                harness.compile(listOf(source), workDir)

                val entries = WReportReader.read(
                    Files.readString(fixOutputDir.resolve("patch").resolve("wrasse-report.txt")),
                )
                entries.size shouldBe 1
                entries[0].diagnostics.map { "${it.line}:${it.column}:${it.level}:${it.message}" } shouldBe
                    listOf(
                        "4:13:warn:magic-number: This expression contains a magic number; consider defining it as a well-named constant",
                        "4:16:error:no-semicolons: Unnecessary semicolon",
                    )
                entries[0].diagnostics.map { it.fixable } shouldBe listOf(false, true)
                val sourcePath = harness.sourcePath(workDir, source).toUri()
                replayReports(
                    listOf(fixOutputDir.toString()),
                ) shouldBe
                    listOf(
                        "w: $sourcePath:4:13 wrasse: magic-number: This expression contains a magic number; consider defining it as a well-named constant",
                        "e: $sourcePath:4:16 wrasse: no-semicolons: Unnecessary semicolon",
                    )
            }
        }
    }

    should("record the autofixable diagnostic a format run keeps quiet") {
        useTempDir { workDir ->
            useTempDir { fixOutputDir ->
                FormatRequest.write(fixOutputDir)
                val harness = WrasseTestHarness(wrasseConfig = wrasseConfig, fixOutputDir = fixOutputDir)
                val result = harness.compile(listOf(source), workDir)

                result.wrasseDiagnostics.map { it.message } shouldBe
                    listOf(
                        "wrasse: magic-number: This expression contains a magic number; consider defining it as a well-named constant",
                    )
                val entries = WReportReader.read(
                    Files.readString(fixOutputDir.resolve("patch").resolve("wrasse-report.txt")),
                )
                entries[0].diagnostics.map { it.message } shouldBe
                    listOf(
                        "magic-number: This expression contains a magic number; consider defining it as a well-named constant",
                        "no-semicolons: Unnecessary semicolon",
                    )
            }
        }
    }

    should("drop a file's entry once a recompile finds nothing") {
        useTempDir { workDir ->
            useTempDir { fixOutputDir ->
                val harness = WrasseTestHarness(
                    wrasseConfig = wrasseConfig,
                    warnOnly = true,
                    fixOutputDir = fixOutputDir,
                )
                harness.compile(listOf(source), workDir)
                harness.compile(
                    listOf(TestSource(source.path, "package sample\n\nfun main() {\n    println(\"ok\")\n}\n")),
                    workDir,
                )

                WReportReader.read(
                    Files.readString(fixOutputDir.resolve("patch").resolve("wrasse-report.txt")),
                ) shouldBe emptyList()
            }
        }
    }

    should("print nothing during a quiet run while still recording every diagnostic") {
        useTempDir { workDir ->
            useTempDir { fixOutputDir ->
                FormatRequest.write(fixOutputDir, formatting = false, quiet = true)
                val harness = WrasseTestHarness(wrasseConfig = wrasseConfig, fixOutputDir = fixOutputDir)
                val result = harness.compile(listOf(source), workDir)

                result.wrasseDiagnostics shouldBe emptyList()
                val entries = WReportReader.read(
                    Files.readString(fixOutputDir.resolve("patch").resolve("wrasse-report.txt")),
                )
                entries[0].diagnostics.map { it.level } shouldBe listOf("warn", "error")
            }
        }
    }
})
