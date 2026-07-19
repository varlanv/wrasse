package com.varlanv.wrasse.lang

import com.varlanv.wrasse.testing.BaseSpec
import com.varlanv.wrasse.testing.useTempDir
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import java.nio.file.Files

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
                writePatch(
                    dir, FileEdits(
                        sourceFile.toString(), hash,
                        listOf(WEdit(9, 10, ""), WEdit(20, 21, ""))
                    )
                )

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
                writePatch(
                    dir,
                    FileEdits(dir.resolve("Missing.kt").toString(), "abc", listOf(WEdit(0, 1, "")))
                )

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
                writePatch(
                    dir, FileEdits(
                        sourceFile.toString(), hash,
                        listOf(WEdit(8, 8, "B"), WEdit(8, 8, "A"))
                    )
                )

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
                writePatch(
                    dir, FileEdits(
                        sourceFile.toString(), hash,
                        listOf(WEdit(8, 12, "aa"), WEdit(10, 14, "bb"))
                    )
                )

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

        should("delete patch file after applying") {
            useTempDir { dir ->
                val sourceFile = dir.resolve("Del.kt")
                val content = "abc"
                Files.writeString(sourceFile, content)
                val hash = WPatchApplier.sha256(content)
                writePatch(dir, FileEdits(sourceFile.toString(), hash, listOf(WEdit(0, 1, "A"))))

                WPatchApplier.apply(dir)

                Files.exists(dir.resolve("wrasse-fixes.txt")) shouldBe false
            }
        }
    }
})

private fun writePatch(dir: java.nio.file.Path, vararg fileEdits: FileEdits) {
    val sb = StringBuilder()
    WPatchWriter.writeHeader(sb)
    for (fe in fileEdits) WPatchWriter.write(sb, fe)
    Files.writeString(dir.resolve("wrasse-fixes.txt"), sb.toString())
}
