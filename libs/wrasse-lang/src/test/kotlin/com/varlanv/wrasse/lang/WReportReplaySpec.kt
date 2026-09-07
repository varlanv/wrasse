package com.varlanv.wrasse.lang

import com.varlanv.wrasse.testing.BaseSpec
import com.varlanv.wrasse.testing.useTempDir
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import java.nio.file.Files

class WReportReplaySpec : BaseSpec({

    context("remap") {
        should("shift a diagnostic after a multi-line insertion and recompute its line and column") {
            val newContent = "line1\nXX\nline2\nline3\n"
            val diagnostics = listOf(ReportedDiagnostic(3, 1, 12, "warn", false, "rule: message"))
            val edits = listOf(AppliedEdit(6, 6, 3))

            val result = WReportReplay.remap(diagnostics, edits, newContent)

            result shouldHaveSize 1
            result[0].offset shouldBe 15
            result[0].line shouldBe 4
            result[0].column shouldBe 1
            result[0].level shouldBe "warn"
            result[0].fixable shouldBe false
            result[0].message shouldBe "rule: message"
        }

        should("shift a diagnostic after a deletion") {
            val newContent = "aaabbb\nccc"
            val diagnostics = listOf(ReportedDiagnostic(2, 1, 10, "error", false, "rule: gone"))
            val edits = listOf(AppliedEdit(3, 6, 0))

            val result = WReportReplay.remap(diagnostics, edits, newContent)

            result shouldHaveSize 1
            result[0].offset shouldBe 7
            result[0].line shouldBe 2
            result[0].column shouldBe 1
        }

        should("leave a diagnostic before every edit unshifted") {
            val newContent = "value = 100\nnext = 2\n"
            val diagnostics = listOf(ReportedDiagnostic(1, 1, 0, "warn", false, "rule: unchanged"))
            val edits = listOf(AppliedEdit(8, 9, 3))

            val result = WReportReplay.remap(diagnostics, edits, newContent)

            result shouldHaveSize 1
            result[0].offset shouldBe 0
            result[0].line shouldBe 1
            result[0].column shouldBe 1
        }

        should("drop a diagnostic whose offset falls inside a replaced span") {
            val newContent = "x = 1\n"
            val diagnostics = listOf(ReportedDiagnostic(1, 3, 4, "warn", false, "rule: inside"))
            val edits = listOf(AppliedEdit(0, 5, 1))

            WReportReplay.remap(diagnostics, edits, newContent) shouldBe emptyList()
        }

        should("drop a fixable diagnostic even when its offset is untouched") {
            val newContent = "abc"
            val diagnostics = listOf(ReportedDiagnostic(1, 1, 0, "error", true, "rule: fixed"))

            WReportReplay.remap(diagnostics, emptyList(), newContent) shouldBe emptyList()
        }
    }

    context("collect with an apply result") {
        should(
            "show a non-fixable finding right after the apply that fixed the other one, and again on a later plain replay",
        ) {
            useTempDir { dir ->
                val sourceFile = dir.resolve("Sample.kt")
                val content = "val x = 1; val y = 42\n"
                Files.writeString(sourceFile, content)
                val hash = Sha256.ofText(content)
                val patchDir = dir.resolve("patch")
                WPatchStore(patchDir).record(FileEdits(sourceFile.toString(), hash, listOf(WEdit(9, 10, ""))))
                WReportStore(patchDir)
                    .record(
                        ReportedFile(
                            sourceFile.toString(),
                            hash,
                            listOf(
                                ReportedDiagnostic(1, 10, 9, "error", true, "no-semicolons: Unnecessary semicolon"),
                                ReportedDiagnostic(1, 20, 19, "warn", false, "magic-number: magic number"),
                            ),
                        ),
                    )

                val applyResult = WPatchApplier.apply(patchDir)
                Files.readString(sourceFile) shouldBe "val x = 1 val y = 42\n"

                val expected = listOf("w: ${sourceFile.toUri()}:1:19 wrasse: magic-number: magic number")
                replayReports(listOf(patchDir.toString()), applyResult = applyResult) shouldBe expected
                replayReports(listOf(patchDir.toString())) shouldBe expected
            }
        }
    }

    context("CRLF") {
        should("treat a CRLF file as current when its LF-normalized hash matches the recorded one") {
            useTempDir { dir ->
                val sourceFile = dir.resolve("Crlf.kt")
                val crlfContent = "package sample\r\n\r\nval x = 1\r\n"
                Files.write(sourceFile, crlfContent.toByteArray(Charsets.UTF_8))
                val normalizedHash = Sha256.ofText(crlfContent.replace("\r\n", "\n"))
                val patchDir = dir.resolve("patch")
                WReportStore(patchDir)
                    .record(
                        ReportedFile(
                            sourceFile.toString(),
                            normalizedHash,
                            listOf(ReportedDiagnostic(3, 1, 16, "warn", false, "rule: x")),
                        ),
                    )

                replayReports(listOf(patchDir.toString())) shouldBe
                    listOf("w: ${sourceFile.toUri()}:3:1 wrasse: rule: x")
            }
        }
    }

    context("relocatable paths") {
        should("resolve a relative stored path against the caller's own project directory") {
            useTempDir { rootA ->
                useTempDir { rootB ->
                    val relativePath = "src/Sample.kt"
                    val content = "val x = 1\n"
                    val fileUnderB = rootB.resolve(relativePath)
                    Files.createDirectories(fileUnderB.parent)
                    Files.writeString(fileUnderB, content)
                    val patchDir = rootA.resolve("patch")
                    WReportStore(patchDir)
                        .record(
                            ReportedFile(
                                relativePath,
                                Sha256.ofText(content),
                                listOf(ReportedDiagnostic(1, 1, 0, "warn", false, "rule: x")),
                            ),
                        )

                    replayReports(listOf(patchDir.toString()), projectDir = rootB) shouldBe
                        listOf("w: ${fileUnderB.toUri()}:1:1 wrasse: rule: x")
                }
            }
        }
    }

    context("robustness") {
        should("skip an entry whose file is unreadable or whose path is invalid, without failing the whole replay") {
            useTempDir { dir ->
                val goodFile = dir.resolve("Good.kt")
                val goodContent = "val x = 1\n"
                Files.writeString(goodFile, goodContent)
                val patchDir = dir.resolve("patch")
                val store = WReportStore(patchDir)
                store.record(
                    ReportedFile(
                        goodFile.toString(),
                        Sha256.ofText(goodContent),
                        listOf(ReportedDiagnostic(1, 1, 0, "warn", false, "rule: good")),
                    ),
                )
                store.record(
                    ReportedFile(dir.toString(), "h", listOf(ReportedDiagnostic(1, 1, 0, "warn", false, "rule: dir"))),
                )
                store.record(
                    ReportedFile(
                        "bad\u0000path.kt",
                        "h",
                        listOf(ReportedDiagnostic(1, 1, 0, "warn", false, "rule: invalid")),
                    ),
                )

                replayReports(listOf(patchDir.toString())) shouldBe
                    listOf("w: ${goodFile.toUri()}:1:1 wrasse: rule: good")
            }
        }
    }
})
