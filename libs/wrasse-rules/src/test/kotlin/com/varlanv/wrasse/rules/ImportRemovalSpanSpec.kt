package com.varlanv.wrasse.rules

import com.varlanv.wrasse.testing.BaseSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe

class ImportRemovalSpanSpec : BaseSpec({

    should("delete the whole line including its terminating newline when the directive is alone on its line") {
        val source = "package sample\n\nimport kotlin.text.Regex\n\nval x = 1"
        val start = source.indexOf("import kotlin.text.Regex")
        val end = start + "import kotlin.text.Regex".length

        val edit = ImportRemovalSpan.compute(source, start, end).shouldNotBeNull()

        edit.startOffset shouldBe 16
        edit.endOffset shouldBe 41
        edit.replacement shouldBe ""
        (source.substring(0, edit.startOffset) + source.substring(edit.endOffset)) shouldBe
            "package sample\n\n\nval x = 1"
    }

    should("delete to end of text when the directive is the last line with no trailing newline") {
        val source = "package sample\n\nimport kotlin.text.Regex"
        val start = source.indexOf("import kotlin.text.Regex")
        val end = source.length

        val edit = ImportRemovalSpan.compute(source, start, end).shouldNotBeNull()

        edit.startOffset shouldBe 16
        edit.endOffset shouldBe source.length
        (source.substring(0, edit.startOffset) + source.substring(edit.endOffset)) shouldBe "package sample\n\n"
    }

    should("delete the whole line including its own trailing semicolon when alone on its line") {
        val source = "package sample\n\nimport kotlin.text.Regex;\n\nval x = 1"
        val start = source.indexOf("import kotlin.text.Regex")
        val end = start + "import kotlin.text.Regex;".length

        val edit = ImportRemovalSpan.compute(source, start, end).shouldNotBeNull()

        edit.startOffset shouldBe 16
        (source.substring(0, edit.startOffset) + source.substring(edit.endOffset)) shouldBe
            "package sample\n\n\nval x = 1"
    }

    should("bail with no edit when a sibling import shares the line via a separator semicolon") {
        val source = "package sample\n\nimport kotlin.math.abs; import kotlin.text.Regex\n\nval x = 1"
        val start = source.indexOf("import kotlin.text.Regex")
        val end = start + "import kotlin.text.Regex".length

        val edit = ImportRemovalSpan.compute(source, start, end)

        edit shouldBe null
    }

    should("bail with no edit when a trailing comment shares the line") {
        val source = "package sample\n\nimport kotlin.text.Regex // used elsewhere\n\nval x = 1"
        val start = source.indexOf("import kotlin.text.Regex")
        val end = start + "import kotlin.text.Regex".length

        val edit = ImportRemovalSpan.compute(source, start, end)

        edit shouldBe null
    }

    should("delete two adjacent unused directives as two disjoint whole-line edits") {
        val source = "package sample\n\nimport kotlin.math.max\nimport kotlin.math.min\n\nval x = 1"
        val maxStart = source.indexOf("import kotlin.math.max")
        val maxEnd = maxStart + "import kotlin.math.max".length
        val minStart = source.indexOf("import kotlin.math.min")
        val minEnd = minStart + "import kotlin.math.min".length

        val maxEdit = ImportRemovalSpan.compute(source, maxStart, maxEnd).shouldNotBeNull()
        val minEdit = ImportRemovalSpan.compute(source, minStart, minEnd).shouldNotBeNull()

        maxEdit.endOffset shouldBe minEdit.startOffset
        (maxEdit.startOffset < maxEdit.endOffset) shouldBe true
        (minEdit.startOffset < minEdit.endOffset) shouldBe true
    }
})
