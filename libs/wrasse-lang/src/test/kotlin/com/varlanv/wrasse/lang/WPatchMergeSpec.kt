package com.varlanv.wrasse.lang

import com.varlanv.wrasse.testing.BaseSpec
import io.kotest.matchers.shouldBe

private fun FileEdits.describe(): String =
"$filePath|$sourceHash|" + edits.joinToString(",") { "${it.startOffset}:${it.endOffset}:${it.replacement}" }

private fun List<FileEdits>.describe(): List<String> = map { it.describe() }

class WPatchMergeSpec :
    BaseSpec(
        {

            context("upsert") {
                should("replace an existing file's entry in place, keeping its original position") {
                    val a = FileEdits("src/A.kt", "hashA", listOf(WEdit(0, 1, "")))
                    val b = FileEdits("src/B.kt", "hashB", listOf(WEdit(0, 1, "")))
                    val c = FileEdits("src/C.kt", "hashC", listOf(WEdit(0, 1, "")))
                    val updatedB = FileEdits("src/B.kt", "hashB2", listOf(WEdit(2, 3, "x")))

                    val result = WPatchMerge.upsert(listOf(a, b, c), updatedB)

                    result.describe() shouldBe listOf(a, updatedB, c).describe()
                }

                should("append a new file's entry when its path did not previously exist") {
                    val a = FileEdits("src/A.kt", "hashA", listOf(WEdit(0, 1, "")))
                    val newEntry = FileEdits("src/New.kt", "hashNew", listOf(WEdit(5, 6, "")))

                    val result = WPatchMerge.upsert(listOf(a), newEntry)

                    result.describe() shouldBe listOf(a, newEntry).describe()
                }

                should("upsert into an empty existing list") {
                    val entry = FileEdits("src/A.kt", "hashA", listOf(WEdit(0, 1, "")))

                    val result = WPatchMerge.upsert(emptyList(), entry)

                    result.describe() shouldBe listOf(entry).describe()
                }
            }

            context("remove") {
                should("remove a file's entry when it produced zero edits (self-cleaning)") {
                    val a = FileEdits("src/A.kt", "hashA", listOf(WEdit(0, 1, "")))
                    val b = FileEdits("src/B.kt", "hashB", listOf(WEdit(0, 1, "")))

                    val result = WPatchMerge.remove(listOf(a, b), "src/A.kt")

                    result.describe() shouldBe listOf(b).describe()
                }

                should("be a no-op when the path is not present") {
                    val a = FileEdits("src/A.kt", "hashA", listOf(WEdit(0, 1, "")))

                    val result = WPatchMerge.remove(listOf(a), "src/Missing.kt")

                    result.describe() shouldBe listOf(a).describe()
                }

                should("leave every other file's entry untouched, in the same order") {
                    val a = FileEdits("src/A.kt", "hashA", listOf(WEdit(0, 1, "")))
                    val b = FileEdits("src/B.kt", "hashB", listOf(WEdit(0, 1, "")))
                    val c = FileEdits("src/C.kt", "hashC", listOf(WEdit(0, 1, "")))

                    val result = WPatchMerge.remove(listOf(a, b, c), "src/B.kt")

                    result.describe() shouldBe listOf(a, c).describe()
                }
            }

            context("round-trip through the patch format") {
                should("survive an upsert then a write/read round-trip") {
                    val a = FileEdits("src/A.kt", "hashA", listOf(WEdit(0, 1, "")))
                    val updatedA = FileEdits("src/A.kt", "hashA2", listOf(WEdit(3, 4, "y")))

                    val merged = WPatchMerge.upsert(listOf(a), updatedA)
                    val sb = StringBuilder()
                    WPatchWriter.writeAll(sb, merged)
                    val read = WPatchReader.read(sb)

                    read.describe() shouldBe listOf(updatedA).describe()
                }

                should("survive a remove then a write/read round-trip, leaving only the preserved entry") {
                    val a = FileEdits("src/A.kt", "hashA", listOf(WEdit(0, 1, "")))
                    val b = FileEdits("src/B.kt", "hashB", listOf(WEdit(0, 1, "")))

                    val merged = WPatchMerge.remove(listOf(a, b), "src/A.kt")
                    val sb = StringBuilder()
                    WPatchWriter.writeAll(sb, merged)
                    val read = WPatchReader.read(sb)

                    read.describe() shouldBe listOf(b).describe()
                }

                should("round-trip a header-only patch (every file removed) to an empty entry list") {
                    val a = FileEdits("src/A.kt", "hashA", listOf(WEdit(0, 1, "")))

                    val merged = WPatchMerge.remove(listOf(a), "src/A.kt")
                    val sb = StringBuilder()
                    WPatchWriter.writeAll(sb, merged)
                    val read = WPatchReader.read(sb)

                    merged shouldBe emptyList()
                    read shouldBe emptyList()
                }
            }
        },
    )
