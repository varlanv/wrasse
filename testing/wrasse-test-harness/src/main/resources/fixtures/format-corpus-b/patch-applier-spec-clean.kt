package com.varlanv.wrasse.lang

import com.varlanv.wrasse.testing.BaseSpec
import com.varlanv.wrasse.testing.useTempDir
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import java.nio.file.Files
import java.nio.file.Path

class WPatchApplierSpec : BaseSpec({

    context("apply") {
        should("apply a deletion edit") {
            useTempDir { dir ->
                val sourceFile = dir.resolve("Foo.kt")
                val content = "val x = 1;"
                Files.writeString(sourceFile, content)
                val hash = WPatchApplier.sha256(content)
                writePatch(dir, FileEdits(sourceFile.toString(), hash, listOf(WEdit(9, 10, ""))))

                val result = WPatchApplier.apply(dir)

                result.files[0].shouldBeInstanceOf<FileApplyResult.Applied>()
                Files.readString(sourceFile) shouldBe "val x = 1"
            }
        }

        should("apply an insertion edit") {
            useTempDir { dir ->
                val sourceFile = dir.resolve("Bar.kt")
                val content = "val x = 1"
                Files.writeString(sourceFile, content)
                val hash = WPatchApplier.sha256(content)
                writePatch(dir, FileEdits(sourceFile.toString(), hash, listOf(WEdit(9, 9, "\n"))))

                val result = WPatchApplier.apply(dir)

                result.files[0].shouldBeInstanceOf<FileApplyResult.Applied>()
                Files.readString(sourceFile) shouldBe "val x = 1\n"
            }
        }

        should("apply a replacement edit") {
            useTempDir { dir ->
                val sourceFile = dir.resolve("Baz.kt")
                val content = "val x = old"
                Files.writeString(sourceFile, content)
                val hash = WPatchApplier.sha256(content)
                writePatch(dir, FileEdits(sourceFile.toString(), hash, listOf(WEdit(8, 11, "new"))))

                val result = WPatchApplier.apply(dir)

                result.files[0].shouldBeInstanceOf<FileApplyResult.Applied>()
                Files.readString(sourceFile) shouldBe "val x = new"
            }
        }

        should("apply multiple edits in correct descending order") {
            useTempDir { dir ->
                val sourceFile = dir.resolve("Multi.kt")
                val content = "val x = 1; val y = 2;"
                Files.writeString(sourceFile, content)
                val hash = WPatchApplier.sha256(content)
                writePatch(dir, FileEdits(sourceFile.toString(), hash, listOf(WEdit(9, 10, ""), WEdit(20, 21, ""))))

                val result = WPatchApplier.apply(dir)

                result.files[0].shouldBeInstanceOf<FileApplyResult.Applied>()
                (result.files[0] as FileApplyResult.Applied).editCount shouldBe 2
                Files.readString(sourceFile) shouldBe "val x = 1 val y = 2"
            }
        }

        should("skip when source hash does not match") {
            useTempDir { dir ->
                val sourceFile = dir.resolve("Changed.kt")
                Files.writeString(sourceFile, "val x = 1;")
                writePatch(dir, FileEdits(sourceFile.toString(), "stale-hash", listOf(WEdit(9, 10, ""))))

                val result = WPatchApplier.apply(dir)

                result.files[0].shouldBeInstanceOf<FileApplyResult.Skipped>()
                (result.files[0] as FileApplyResult.Skipped).reason shouldBe "source changed since compilation"
                Files.readString(sourceFile) shouldBe "val x = 1;"
            }
        }

        should("skip with a CRLF-specific reason when disk line endings differ from the hashed buffer") {
            useTempDir { dir ->
                val sourceFile = dir.resolve("Crlf.kt")
                val crlfContent = "package sample\r\n\r\nval x = 1;\r\n"
                Files.write(sourceFile, crlfContent.toByteArray(Charsets.UTF_8))
                val normalizedHash = WPatchApplier.sha256(crlfContent.replace("\r\n", "\n"))
                writePatch(dir, FileEdits(sourceFile.toString(), normalizedHash, listOf(WEdit(25, 26, ""))))

                val result = WPatchApplier.apply(dir)

                result.files[0].shouldBeInstanceOf<FileApplyResult.Skipped>()
                (result.files[0] as FileApplyResult.Skipped).reason shouldBe
                    "source line endings differ from what the compiler analyzed (CRLF vs LF); re-run the build to refresh the patch"
                Files.readString(sourceFile) shouldBe crlfContent
            }
        }

        should("skip when source file does not exist") {
            useTempDir { dir ->
                writePatch(dir, FileEdits(dir.resolve("Missing.kt").toString(), "abc", listOf(WEdit(0, 1, ""))))

                val result = WPatchApplier.apply(dir)

                result.files[0].shouldBeInstanceOf<FileApplyResult.Skipped>()
            }
        }

        should("apply same-offset insertions so earlier-collected text ends up leftmost") {
            useTempDir { dir ->
                val sourceFile = dir.resolve("SameOffset.kt")
                val content = "val x = "
                Files.writeString(sourceFile, content)
                val hash = WPatchApplier.sha256(content)
                writePatch(dir, FileEdits(sourceFile.toString(), hash, listOf(WEdit(8, 8, "B"), WEdit(8, 8, "A"))))

                val result = WPatchApplier.apply(dir)

                result.files[0].shouldBeInstanceOf<FileApplyResult.Applied>()
                Files.readString(sourceFile) shouldBe "val x = AB"
            }
        }

        should("fail on overlapping edits") {
            useTempDir { dir ->
                val sourceFile = dir.resolve("Overlap.kt")
                val content = "val x = 123456"
                Files.writeString(sourceFile, content)
                val hash = WPatchApplier.sha256(content)
                writePatch(dir, FileEdits(sourceFile.toString(), hash, listOf(WEdit(8, 12, "aa"), WEdit(10, 14, "bb"))))

                val result = WPatchApplier.apply(dir)

                result.files[0].shouldBeInstanceOf<FileApplyResult.Failed>()
                Files.readString(sourceFile) shouldBe content
            }
        }

        should("return empty result when no patch file exists") {
            useTempDir { dir ->
                val result = WPatchApplier.apply(dir)
                result.files shouldBe emptyList()
            }
        }

        should("return empty result when the passed directory itself does not exist") {
            useTempDir { dir ->
                val result = WPatchApplier.apply(dir.resolve("does-not-exist"))
                result.files shouldBe emptyList()
            }
        }

        should("walk nested per-compilation subdirectories and apply every patch file found (D22)") {
            useTempDir { dir ->
                val mainSourceFile = dir.resolve("Main.kt")
                val mainContent = "val x = 1;"
                Files.writeString(mainSourceFile, mainContent)
                val mainDir = Files.createDirectories(dir.resolve("main"))
                Files.writeString(
                    mainDir.resolve("wrasse-fixes.txt"),
                    "# wrasse-fixes v1\n" +
                        "file:$mainSourceFile\n" +
                        "hash:${WPatchApplier.sha256(mainContent)}\n" +
                        "edit:9:10:\n",
                )

                val testSourceFile = dir.resolve("MainTest.kt")
                val testContent = "val y = 2;"
                Files.writeString(testSourceFile, testContent)
                val testDir = Files.createDirectories(dir.resolve("test"))
                Files.writeString(
                    testDir.resolve("wrasse-fixes.txt"),
                    "# wrasse-fixes v1\n" +
                        "file:$testSourceFile\n" +
                        "hash:${WPatchApplier.sha256(testContent)}\n" +
                        "edit:9:10:\n",
                )

                val result = WPatchApplier.apply(dir)

                result.files.size shouldBe 2
                result.files.all { it is FileApplyResult.Applied } shouldBe true
                Files.readString(mainSourceFile) shouldBe "val x = 1"
                Files.readString(testSourceFile) shouldBe "val y = 2"
                Files.exists(mainDir.resolve("wrasse-fixes.txt")) shouldBe true
                Files.exists(testDir.resolve("wrasse-fixes.txt")) shouldBe true
            }
        }

        should("keep patch file after applying for hash-guard re-runs") {
            useTempDir { dir ->
                val sourceFile = dir.resolve("Del.kt")
                val content = "abc"
                Files.writeString(sourceFile, content)
                val hash = WPatchApplier.sha256(content)
                writePatch(dir, FileEdits(sourceFile.toString(), hash, listOf(WEdit(0, 1, "A"))))

                WPatchApplier.apply(dir)

                Files.exists(dir.resolve("wrasse-fixes.txt")) shouldBe true
            }
        }
    }
})

private fun writePatch(dir: Path, vararg fileEdits: FileEdits) {
    val sb = StringBuilder()
    WPatchWriter.writeHeader(sb)
    for (fe in fileEdits) WPatchWriter.write(sb, fe)
    Files.writeString(dir.resolve("wrasse-fixes.txt"), sb.toString())
}

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
