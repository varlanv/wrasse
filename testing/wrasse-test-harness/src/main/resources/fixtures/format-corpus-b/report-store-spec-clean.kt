package com.varlanv.wrasse.lang

import com.varlanv.wrasse.testing.BaseSpec
import com.varlanv.wrasse.testing.useTempDir
import io.kotest.matchers.shouldBe
import java.nio.file.Files
import java.nio.file.Path

class WReportStoreSpec : BaseSpec({

    fun diagnostic(
        line: Int,
        level: String = "error",
        message: String = "no-semicolons: Unnecessary semicolon",
        offset: Int = 0,
        fixable: Boolean = false,
    ) = ReportedDiagnostic(line, 3, offset, level, fixable, message)

    fun journal(dir: Path): String = Files.readString(dir.resolve("patch").resolve("wrasse-report.txt"))

    should("append one block per change, replace a path's earlier block, and forget a cleared path") {
        useTempDir { dir ->
            val store = WReportStore(dir.resolve("patch"))
            store.record(ReportedFile("/a.kt", "h1", listOf(diagnostic(1))))
            store.record(ReportedFile("/b.kt", "h2", listOf(diagnostic(2, "warn"))))
            store.record(ReportedFile("/a.kt", "h3", listOf(diagnostic(5))))
            store.clear("/b.kt")

            val entries = WReportReader.read(journal(dir))
            entries.map { it.filePath } shouldBe listOf("/a.kt")
            entries[0].sourceHash shouldBe "h3"
            entries[0].diagnostics.map { it.line } shouldBe listOf(5)
        }
    }

    should("write nothing when a recorded entry equals the live one and when clearing an absent path") {
        useTempDir { dir ->
            val store = WReportStore(dir.resolve("patch"))
            store.record(ReportedFile("/a.kt", "h1", listOf(diagnostic(1))))
            val before = journal(dir)
            store.record(ReportedFile("/a.kt", "h1", listOf(diagnostic(1))))
            store.clear("/missing.kt")
            journal(dir) shouldBe before
        }
    }

    should("round-trip a message holding backslashes, colons and newlines") {
        val message = "rule: a \\ b : c\nsecond line"
        val out = StringBuilder()
        WReportWriter.writeAll(
            out,
            listOf(ReportedFile("/x.kt", "h", listOf(ReportedDiagnostic(7, 9, 42, "warn", true, message)))),
        )
        val back = WReportReader.read(out)
        back[0].diagnostics[0].message shouldBe message
        back[0].diagnostics[0].line shouldBe 7
        back[0].diagnostics[0].column shouldBe 9
        back[0].diagnostics[0].offset shouldBe 42
        back[0].diagnostics[0].level shouldBe "warn"
        back[0].diagnostics[0].fixable shouldBe true
    }

    should("treat a report with an unknown or missing header as empty") {
        WReportReader.read("file:/a.kt\nhash:h\ndiag:1:1:0:error:0:x\n") shouldBe emptyList()
        WReportReader.read("# wrasse-report v1\nfile:/a.kt\nhash:h\ndiag:1:1:error:x\n") shouldBe emptyList()
        WReportReader.read("") shouldBe emptyList()
    }

    should("replay only entries whose file still has the recorded content, in kotlinc's own line format") {
        useTempDir { dir ->
            val current = dir.resolve("Current.kt")
            val changed = dir.resolve("Changed.kt")
            Files.writeString(current, "val a = 1;\n")
            Files.writeString(changed, "val b = 2;\n")
            val store = WReportStore(dir.resolve("build").resolve("wrasse").resolve("main").resolve("patch"))
            store.record(
                ReportedFile(
                    current.toString(),
                    Sha256.ofText("val a = 1;\n"),
                    listOf(ReportedDiagnostic(1, 10, 9, "error", true, "no-semicolons: Unnecessary semicolon")),
                ),
            )
            store.record(
                ReportedFile(
                    changed.toString(),
                    Sha256.ofText("val b = 2;\n"),
                    listOf(ReportedDiagnostic(1, 10, 9, "warn", true, "no-semicolons: Unnecessary semicolon")),
                ),
            )
            store.record(
                ReportedFile(
                    dir.resolve("Gone.kt").toString(),
                    "h",
                    listOf(ReportedDiagnostic(1, 1, 0, "error", false, "x")),
                ),
            )
            Files.writeString(changed, "val b = 2\n")

            replayReports(listOf(dir.resolve("build").resolve("wrasse").toString())) shouldBe
                listOf("e: ${current.toUri()}:1:10 wrasse: no-semicolons: Unnecessary semicolon")
        }
    }
})

// fixture-option: trailing-newline
// fixture-aux-file: aux/Stubs.kt
// fixture-aux-file: aux/Kotest.kt
// fixture-aux-file: aux/KotestCollections.kt
// fixture-aux-file: aux/KotestNulls.kt
// fixture-aux-file: aux/KotestTypes.kt
// fixture-aux-file: aux/KotestAssertions.kt
// fixture-aux-file: aux/KotestThrowables.kt
// fixture-aux-file: aux/lang/ConfigValueJsonc.kt
// fixture-aux-file: aux/lang/FileEdits.kt
// fixture-aux-file: aux/lang/FileWalkUp.kt
// fixture-aux-file: aux/lang/FormatRequest.kt
// fixture-aux-file: aux/lang/HexEncoding.kt
// fixture-aux-file: aux/lang/PerfRecorder.kt
// fixture-aux-file: aux/lang/SafeProperties.kt
// fixture-aux-file: aux/lang/Sha256.kt
// fixture-aux-file: aux/lang/StringSlice.kt
// fixture-aux-file: aux/lang/WEdit.kt
// fixture-aux-file: aux/lang/WPatchApplier.kt
// fixture-aux-file: aux/lang/WPatchReader.kt
// fixture-aux-file: aux/lang/WPatchStore.kt
// fixture-aux-file: aux/lang/WPatchWriter.kt
// fixture-aux-file: aux/lang/WPerf.kt
// fixture-aux-file: aux/lang/WReport.kt
// fixture-aux-file: aux/lang/WReportReplay.kt
// fixture-aux-file: aux/lang/WReportStore.kt
// expect-clean
