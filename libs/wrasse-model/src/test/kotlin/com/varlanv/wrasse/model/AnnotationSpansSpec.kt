package com.varlanv.wrasse.model

import com.varlanv.wrasse.testing.BaseSpec
import io.kotest.matchers.shouldBe

class AnnotationSpansSpec : BaseSpec({

    fun spansOf(source: String, vararg annotations: String): AnnotationSpans {
        val spans = AnnotationSpans()
        var cursor = 0
        for (annotation in annotations) {
            val start = source.indexOf(annotation, cursor)
            require(start >= 0) { "annotation '$annotation' not found after offset $cursor" }
            spans.add(start, start + annotation.length)
            cursor = start + annotation.length
        }
        return spans
    }

    fun linesOf(source: String, spans: AnnotationSpans): List<Boolean> {
        val result = ArrayList<Boolean>()
        var lineStart = 0
        while (lineStart <= source.length) {
            val lineEnd = source.indexOf('\n', lineStart).let { if (it < 0) source.length else it }
            result.add(spans.onlyOnLine(source, lineStart, lineEnd))
            lineStart = lineEnd + 1
        }
        return result
    }

    should("report a line that is one annotation") {
        val source = "@JvmField\nval x = 1"

        linesOf(source, spansOf(source, "@JvmField")) shouldBe listOf(true, false)
    }

    should("ignore the indentation around the annotations of a line") {
        val source = "  \t@A   @B  \nval x = 1"

        linesOf(source, spansOf(source, "@A", "@B")) shouldBe listOf(true, false)
    }

    should("report a line with an annotation and code after it as not annotation-only") {
        val source = "@JvmField val x = 1"

        linesOf(source, spansOf(source, "@JvmField")) shouldBe listOf(false)
    }

    should("report a line with code before an annotation as not annotation-only") {
        val source = "val x = @A 1"

        linesOf(source, spansOf(source, "@A")) shouldBe listOf(false)
    }

    should("report a line with an annotation and a comment as not annotation-only") {
        val source = "@A // note"

        linesOf(source, spansOf(source, "@A")) shouldBe listOf(false)
    }

    should("report a blank line as not annotation-only") {
        val source = "@A\n\n   \n@B"

        linesOf(source, spansOf(source, "@A", "@B")) shouldBe listOf(true, false, false, true)
    }

    should("report every line of an annotation spanning several lines") {
        val source = "@Foo(\n    a = 1,\n    b = [1, 2],\n)\nval x = 1"

        linesOf(source, spansOf(source, "@Foo(\n    a = 1,\n    b = [1, 2],\n)")) shouldBe
            listOf(true, true, true, true, false)
    }

    should("report the last line of an annotation as code when more code follows it") {
        val source = "@Foo(\n    1,\n) val x = 1"

        linesOf(source, spansOf(source, "@Foo(\n    1,\n)")) shouldBe listOf(true, true, false)
    }

    should("report the first line of an annotation as code when code precedes it") {
        val source = "val x = @Foo(\n    1,\n)"

        linesOf(source, spansOf(source, "@Foo(\n    1,\n)")) shouldBe listOf(false, true, true)
    }

    should("treat a carriage return as blank") {
        val source = "@A\r\n@B @C\r\nval x = 1"

        linesOf(source, spansOf(source, "@A", "@B", "@C")) shouldBe listOf(true, true, false)
    }

    should("report a line as not annotation-only when no span exists") {
        linesOf("@A\nval x = 1", AnnotationSpans()) shouldBe listOf(false, false)
    }

    should("drop a span nested inside the previous one") {
        val source = "@Outer(@Inner)\nval x = 1"
        val spans = AnnotationSpans()
        spans.add(0, 14)
        spans.add(7, 13)

        linesOf(source, spans) shouldBe listOf(true, false)
    }

    should("find a span among many") {
        val source = (1..40).joinToString("\n") { "@A$it" } + "\nval x = 1"
        val spans = AnnotationSpans()
        var offset = 0
        for (index in 1..40) {
            spans.add(offset, offset + "@A$index".length)
            offset += "@A$index".length + 1
        }

        linesOf(source, spans) shouldBe List(40) { true } + false
    }
})
