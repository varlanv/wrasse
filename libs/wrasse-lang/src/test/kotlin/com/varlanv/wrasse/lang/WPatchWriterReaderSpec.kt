package com.varlanv.wrasse.lang

import com.varlanv.wrasse.testing.BaseSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe

class WPatchWriterReaderSpec : BaseSpec({

    fun roundTrip(vararg fileEdits: FileEdits): List<FileEdits> {
        val sb = StringBuilder()
        WPatchWriter.writeHeader(sb)
        for (fe in fileEdits) WPatchWriter.write(sb, fe)
        return WPatchReader.read(sb)
    }

    context("round-trip") {
        should("handle a single deletion edit") {
            val input = FileEdits("src/Foo.kt", "abc123", listOf(WEdit(10, 11, "")))
            val result = roundTrip(input)
            result shouldHaveSize 1
            result[0].filePath shouldBe "src/Foo.kt"
            result[0].sourceHash shouldBe "abc123"
            result[0].edits shouldHaveSize 1
            result[0].edits[0].startOffset shouldBe 10
            result[0].edits[0].endOffset shouldBe 11
            result[0].edits[0].replacement shouldBe ""
        }

        should("handle an insertion edit") {
            val input = FileEdits("src/Bar.kt", "def456", listOf(WEdit(5, 5, "inserted")))
            val result = roundTrip(input)
            result shouldHaveSize 1
            result[0].edits[0].startOffset shouldBe 5
            result[0].edits[0].endOffset shouldBe 5
            result[0].edits[0].replacement shouldBe "inserted"
        }

        should("handle a replacement edit") {
            val input = FileEdits("src/Baz.kt", "ghi789", listOf(WEdit(0, 10, "new text")))
            val result = roundTrip(input)
            result shouldHaveSize 1
            result[0].edits[0].replacement shouldBe "new text"
        }

        should("escape and unescape newlines in replacement") {
            val input = FileEdits("src/A.kt", "h1", listOf(WEdit(0, 0, "line1\nline2\nline3")))
            val result = roundTrip(input)
            result[0].edits[0].replacement shouldBe "line1\nline2\nline3"
        }

        should("escape and unescape backslashes in replacement") {
            val input = FileEdits("src/B.kt", "h2", listOf(WEdit(0, 0, "a\\b\\\\c")))
            val result = roundTrip(input)
            result[0].edits[0].replacement shouldBe "a\\b\\\\c"
        }

        should("handle mixed newlines and backslashes") {
            val input = FileEdits("src/C.kt", "h3", listOf(WEdit(0, 0, "a\\n\nb")))
            val result = roundTrip(input)
            result[0].edits[0].replacement shouldBe "a\\n\nb"
        }

        should("preserve significant leading and trailing spaces in a replacement around an escaped newline") {
            val input = FileEdits("src/E.kt", "h5", listOf(WEdit(0, 0, " {\n        body\n    } ")))
            val result = roundTrip(input)
            result[0].edits[0].replacement shouldBe " {\n        body\n    } "
        }

        should("handle multiple edits in one file sorted descending") {
            val edits = listOf(WEdit(5, 6, ""), WEdit(20, 25, "x"), WEdit(10, 11, "y"))
            val input = FileEdits("src/D.kt", "h4", edits)
            val result = roundTrip(input)
            result[0].edits shouldHaveSize 3
            result[0].edits[0].startOffset shouldBe 20
            result[0].edits[1].startOffset shouldBe 10
            result[0].edits[2].startOffset shouldBe 5
        }

        should("handle multiple file blocks") {
            val fe1 = FileEdits("src/X.kt", "hx", listOf(WEdit(0, 1, "")))
            val fe2 = FileEdits("src/Y.kt", "hy", listOf(WEdit(5, 5, "z")))
            val result = roundTrip(fe1, fe2)
            result shouldHaveSize 2
            result[0].filePath shouldBe "src/X.kt"
            result[1].filePath shouldBe "src/Y.kt"
        }
    }

    context("reader edge cases") {
        should("skip comment lines") {
            val input = """
                # wrasse-fixes v1
                # this is a comment
                file:src/Foo.kt
                hash:abc
                edit:0:1:
            """.trimIndent()
            val result = WPatchReader.read(input)
            result shouldHaveSize 1
        }

        should("skip empty lines") {
            val input = """
                # wrasse-fixes v1

                file:src/Foo.kt

                hash:abc

                edit:0:1:

            """.trimIndent()
            val result = WPatchReader.read(input)
            result shouldHaveSize 1
        }
    }
})
