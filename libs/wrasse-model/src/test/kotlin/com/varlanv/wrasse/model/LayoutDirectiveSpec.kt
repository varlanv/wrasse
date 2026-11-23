package com.varlanv.wrasse.model

import com.varlanv.wrasse.testing.BaseSpec
import io.kotest.matchers.shouldBe

class LayoutDirectiveSpec : BaseSpec({

    fun directivesOf(source: String, vararg annotations: String): LayoutDirectives {
        val spans = AnnotationSpans()
        var cursor = 0
        for (annotation in annotations) {
            val start = source.indexOf(annotation, cursor)
            require(start >= 0) { "annotation '$annotation' not found after offset $cursor" }
            spans.add(start, start + annotation.length)
            cursor = start + annotation.length
        }
        val directives = LayoutDirectives(spans)
        var lineStart = 0
        while (lineStart < source.length) {
            val lineEnd = source.indexOf('\n', lineStart).let { if (it < 0) source.length else it }
            val indent = source.substring(lineStart, lineEnd).takeWhile { it == ' ' || it == '\t' }.length
            val text = source.substring(lineStart + indent, lineEnd)
            LayoutDirectiveKind.ofText(text)?.let {
                directives.add(it, text, lineStart + indent, lineEnd, lineStart, source)
            }
            lineStart = lineEnd + 1
        }
        return directives
    }

    fun directive(
        text: String,
        source: String = "$text\nval x = f(1)\n",
    ): LayoutDirective = directivesOf(source).all().first { it.text == text }

    fun claimCall(
        directives: LayoutDirectives,
        source: String,
        call: String,
        regions: FormatRegions = FormatRegions(),
    ): LayoutClaim? = directives.claim(source.indexOf(call) + call.indexOf('('), source, regions)

    should("name each kind by its keyword, prefix and rule id") {
        LayoutDirectiveKind.MATRIX.prefix shouldBe "// wrasse matrix:"
        LayoutDirectiveKind.MATRIX.ruleId shouldBe "format-matrix"
        LayoutDirectiveKind.MATRIX.wholeRows shouldBe true
        LayoutDirectiveKind.COLUMNS.prefix shouldBe "// wrasse columns:"
        LayoutDirectiveKind.COLUMNS.ruleId shouldBe "format-columns"
        LayoutDirectiveKind.COLUMNS.wholeRows shouldBe false
    }

    should("recognise a comment starting with exactly the prefix of a kind") {
        LayoutDirectiveKind.ofText("// wrasse matrix:2") shouldBe LayoutDirectiveKind.MATRIX
        LayoutDirectiveKind.ofText("// wrasse matrix:") shouldBe LayoutDirectiveKind.MATRIX
        LayoutDirectiveKind.ofText("// wrasse matrix:abc") shouldBe LayoutDirectiveKind.MATRIX
        LayoutDirectiveKind.ofText("// wrasse columns:3") shouldBe LayoutDirectiveKind.COLUMNS
        LayoutDirectiveKind.ofText("// wrasse columns:") shouldBe LayoutDirectiveKind.COLUMNS
        LayoutDirective.isDirectiveText("// wrasse columns:3") shouldBe true
        LayoutDirective.isDirectiveText("// wrasse matrix:3") shouldBe true
    }

    should("reject every near-miss prefix of either kind") {
        val nearMisses = listOf("matrix", "columns").flatMap { keyword ->
            listOf(
                "//wrasse $keyword:2",
                "// wrasse  $keyword:2",
                "// Wrasse $keyword:2",
                "// wrasse ${keyword.replaceFirstChar { it.uppercase() }}:2",
                "// wrasse $keyword :2",
                "// wrasse $keyword",
                " // wrasse $keyword:2",
                "/* wrasse $keyword:2 */",
                "// wrasse\t$keyword:2",
            )
        } + listOf("", "// wrasse fmt:off", "// wrasse column:2", "// wrasse matrices:2")
        nearMisses.map { LayoutDirectiveKind.ofText(it) } shouldBe nearMisses.map { null }
        nearMisses.map { LayoutDirective.isDirectiveText(it) } shouldBe nearMisses.map { false }
    }

    should("read the size of a whole number whatever the kind") {
        for (kind in LayoutDirectiveKind.entries) {
            fun sizeOf(text: String) = directive("${kind.prefix}$text").size

            sizeOf("2") shouldBe 2
            sizeOf("16") shouldBe 16
            sizeOf("007") shouldBe 7
            sizeOf("2147483647") shouldBe Int.MAX_VALUE
        }
    }

    should("read a negative size so its value can be reported") {
        directive("// wrasse columns:-1").size shouldBe -1
        directive("// wrasse columns:-0").size shouldBe 0
        directive("// wrasse columns:-2147483648").size shouldBe Int.MIN_VALUE
    }

    should("read no size from anything but an optional minus and ascii digits") {
        val invalid = listOf("", "abc", "2x", "x2", "2 ", " 2", "+2", "--2", "-", "2.0", "1_0", "٢", "2147483648")
        for (kind in LayoutDirectiveKind.entries) {
            invalid.map { directive("${kind.prefix}$it").size } shouldBe invalid.map { null }
        }
    }

    should("keep the text after the prefix as the size text") {
        directive("// wrasse matrix:2 ").sizeText shouldBe "2 "
        directive("// wrasse columns:").sizeText shouldBe ""
        directive("// wrasse columns:abc").sizeText shouldBe "abc"
    }

    should("offer a column count only for a size of at least two") {
        directive("// wrasse matrix:2").columns shouldBe 2
        directive("// wrasse columns:9").columns shouldBe 9
        directive("// wrasse columns:1").columns shouldBe null
        directive("// wrasse columns:0").columns shouldBe null
        directive("// wrasse columns:-3").columns shouldBe null
        directive("// wrasse columns:abc").columns shouldBe null
    }

    should("number the line of each directive from one") {
        val source = "package p\n\n// wrasse matrix:2\nval x = f(1)\n  // wrasse columns:3\nval y = f(1)\n"

        directivesOf(source).all().map { it.line } shouldBe listOf(3, 5)
    }

    should("keep only the first failure recorded") {
        val target = directive("// wrasse matrix:2")
        target.failure shouldBe null

        target.fail("first")
        target.fail("second")

        target.failure shouldBe "first"
    }

    should("target the line directly below the comment") {
        val source = "// wrasse matrix:2\nval x = f(1)\nval y = 2\n"
        val directives = directivesOf(source)

        directives.targetLineStart(directives.all().single(), source) shouldBe 19
    }

    should("target the last line of a file without a trailing newline") {
        val source = "// wrasse matrix:2\nval x = f(1)"
        val directives = directivesOf(source)

        directives.targetLineStart(directives.all().single(), source) shouldBe 19
    }

    should("target no line when the comment ends the file") {
        val atEnd = "// wrasse matrix:2"
        val directives = directivesOf(atEnd)

        directives.targetLineStart(directives.all().single(), atEnd) shouldBe LayoutDirectives.NO_LINE
    }

    should("target the empty line that follows a comment ending the file with a newline") {
        val source = "// wrasse matrix:2\n"
        val directives = directivesOf(source)

        directives.targetLineStart(directives.all().single(), source) shouldBe 19
    }

    should("target an empty line below instead of skipping it") {
        val source = "// wrasse matrix:2\n\nval x = f(1)\n"
        val directives = directivesOf(source)

        directives.targetLineStart(directives.all().single(), source) shouldBe 19
    }

    should("skip a line holding only an annotation") {
        val source = "// wrasse matrix:2\n@JvmField\nval x = f(1)\n"
        val directives = directivesOf(source, "@JvmField")

        directives.targetLineStart(directives.all().single(), source) shouldBe source.indexOf("val x")
    }

    should("skip every annotation-only line in a row, whatever its indentation") {
        val source = "// wrasse columns:2\n  @A\n\t@B(1)\n@field:C\nval x = f(1)\n"
        val directives = directivesOf(source, "@A", "@B(1)", "@field:C")

        directives.targetLineStart(directives.all().single(), source) shouldBe source.indexOf("val x")
    }

    should("skip a line of several annotations") {
        val source = "// wrasse matrix:2\n@A @B(2)   @C\nval x = f(1)\n"
        val directives = directivesOf(source, "@A", "@B(2)", "@C")

        directives.targetLineStart(directives.all().single(), source) shouldBe source.indexOf("val x")
    }

    should("skip every line of an annotation written over several lines") {
        val source = "// wrasse matrix:2\n@Foo(\n    a = 1,\n    b = [1, 2],\n)\nval x = f(1)\n"
        val directives = directivesOf(source, "@Foo(\n    a = 1,\n    b = [1, 2],\n)")

        directives.targetLineStart(directives.all().single(), source) shouldBe source.indexOf("val x")
    }

    should("stop at the line that closes an annotation and goes on with code") {
        val source = "// wrasse matrix:2\n@Foo(\n    1,\n) val x = f(1)\n"
        val directives = directivesOf(source, "@Foo(\n    1,\n)")

        directives.targetLineStart(directives.all().single(), source) shouldBe source.indexOf(") val x")
    }

    should("not skip a line holding an annotation and code") {
        val source = "// wrasse matrix:2\n@JvmField val x = f(1)\n"
        val directives = directivesOf(source, "@JvmField")

        directives.targetLineStart(directives.all().single(), source) shouldBe 19
    }

    should("not skip a line holding an annotation and a comment") {
        val source = "// wrasse matrix:2\n@JvmField // keep\nval x = f(1)\n"
        val directives = directivesOf(source, "@JvmField")

        directives.targetLineStart(directives.all().single(), source) shouldBe 19
    }

    should("stop at a blank line after an annotation") {
        val source = "// wrasse matrix:2\n@JvmField\n\nval x = f(1)\n"
        val directives = directivesOf(source, "@JvmField")

        directives.targetLineStart(directives.all().single(), source) shouldBe source.indexOf("\n\nval") + 1
    }

    should("stop at an ordinary comment after an annotation") {
        val source = "// wrasse matrix:2\n@JvmField\n// note\nval x = f(1)\n"
        val directives = directivesOf(source, "@JvmField")

        directives.targetLineStart(directives.all().single(), source) shouldBe source.indexOf("// note")
    }

    should("stop at a format directive after an annotation") {
        val source = "// wrasse matrix:2\n@JvmField\n// wrasse fmt:off\nval x = f(1)\n"
        val directives = directivesOf(source, "@JvmField")

        directives.targetLineStart(directives.all().single(), source) shouldBe source.indexOf("// wrasse fmt:off")
    }

    should("skip the line of another directive, whatever its size") {
        val source = "// wrasse matrix:2\n// wrasse columns:abc\n// wrasse columns:3\nval x = f(1)\n"
        val directives = directivesOf(source)

        directives.all().map { directives.targetLineStart(it, source) } shouldBe List(3) { source.indexOf("val x") }
    }

    should("skip directives and annotations mixed in any order") {
        val source = "// wrasse matrix:2\n@A\n// wrasse columns:3\n@B\nval x = f(1)\n"
        val directives = directivesOf(source, "@A", "@B")

        directives.all().map { directives.targetLineStart(it, source) } shouldBe List(2) { source.indexOf("val x") }
    }

    should("target no line when annotations run to the end of the file") {
        val source = "// wrasse matrix:2\n@A\n@B"
        val directives = directivesOf(source, "@A", "@B")

        directives.targetLineStart(directives.all().single(), source) shouldBe LayoutDirectives.NO_LINE
    }

    should("target the empty line when annotations are followed by a final newline only") {
        val source = "// wrasse matrix:2\n@A\n"
        val directives = directivesOf(source, "@A")

        directives.targetLineStart(directives.all().single(), source) shouldBe source.length
    }

    should("treat a carriage return as blank when judging a line") {
        val source = "// wrasse matrix:2\r\n@A \r\nval x = f(1)\r\n"
        val directives = directivesOf(source, "@A")

        directives.targetLineStart(directives.all().single(), source) shouldBe source.indexOf("val x")
    }

    should("lie inside a region holding its own line or its target line") {
        val source = "// wrasse matrix:2\nval x = f(1)\n"
        val directives = directivesOf(source)
        val target = directives.all().single()
        val own = FormatRegions().apply { addSpan(0, 5) }
        val below = FormatRegions().apply { addSpan(19, 25) }
        val elsewhere = FormatRegions().apply { addSpan(40, 50) }

        directives.isInsideRegion(target, source, own) shouldBe true
        directives.isInsideRegion(target, source, below) shouldBe true
        directives.isInsideRegion(target, source, elsewhere) shouldBe false
        directives.isInsideRegion(target, source, FormatRegions()) shouldBe false
    }

    should("judge the target line past annotations for a region") {
        val source = "// wrasse matrix:2\n@A\nval x = f(1)\n"
        val directives = directivesOf(source, "@A")
        val target = directives.all().single()
        val annotationLine = FormatRegions().apply { addSpan(19, 21) }
        val targetLine = FormatRegions().apply { addSpan(source.indexOf("val x"), source.length) }

        directives.isInsideRegion(target, source, annotationLine) shouldBe false
        directives.isInsideRegion(target, source, targetLine) shouldBe true
    }

    should("claim nothing without directives") {
        val directives = LayoutDirectives(AnnotationSpans())

        directives.isEmpty shouldBe true
        directives.claim(0, "val x = f(1)", FormatRegions()) shouldBe null
    }

    should("claim a call on the line below a directive as a single claim") {
        val source = "// wrasse matrix:2\nval x = f(1)\n"
        val directives = directivesOf(source)

        val claim = claimCall(directives, source, "f(1)") as LayoutClaim.Single

        claim.directive shouldBe directives.all().single()
        claim.columns shouldBe 2
        claim.directive.claimed shouldBe true
    }

    should("claim a call past annotation lines") {
        val source = "// wrasse columns:4\n@JvmField\n@Suppress(\"X\")\nval x = f(1)\n"
        val directives = directivesOf(source, "@JvmField", "@Suppress(\"X\")")

        val claim = claimCall(directives, source, "f(1)") as LayoutClaim.Single

        claim.directive shouldBe directives.all().single()
        claim.columns shouldBe 4
    }

    should("claim nothing for a call that is not on the line below") {
        val source = "// wrasse matrix:2\n\nval x = f(1)\n"
        val directives = directivesOf(source)

        claimCall(directives, source, "f(1)") shouldBe null
        directives.all().single().claimed shouldBe false
    }

    should("claim nothing for a call after an ordinary comment below the directive") {
        val source = "// wrasse matrix:2\n// note\nval x = f(1)\n"
        val directives = directivesOf(source)

        claimCall(directives, source, "f(1)") shouldBe null
    }

    should("hand the claim to the first call of the line and nothing to the later ones") {
        val source = "// wrasse matrix:2\nval x = f(1) + g(2)\n"
        val directives = directivesOf(source)

        (claimCall(directives, source, "f(1)") is LayoutClaim.Single) shouldBe true
        claimCall(directives, source, "g(2)") shouldBe null
    }

    should("claim a clash of every valid directive stacked above one call, in source order") {
        val source = "// wrasse matrix:3\n// wrasse columns:3\nval x = f(1)\n"
        val directives = directivesOf(source)

        val claim = claimCall(directives, source, "f(1)") as LayoutClaim.Clash

        claim.directives shouldBe directives.all()
        directives.all().map { it.claimed } shouldBe listOf(true, true)
    }

    should("claim a clash of directives separated by an annotation") {
        val source = "// wrasse matrix:3\n@A\n// wrasse matrix:2\n@B\nval x = f(1)\n"
        val directives = directivesOf(source, "@A", "@B")

        val claim = claimCall(directives, source, "f(1)") as LayoutClaim.Clash

        claim.directives shouldBe directives.all()
    }

    should("leave an invalid size out of a clash and out of the single claim") {
        val source = "// wrasse matrix:abc\n// wrasse columns:3\n// wrasse matrix:1\nval x = f(1)\n"
        val directives = directivesOf(source)

        val claim = claimCall(directives, source, "f(1)") as LayoutClaim.Single

        claim.directive shouldBe directives.all()[1]
        claim.columns shouldBe 3
    }

    should("claim as invalid when every directive above the call has an invalid size") {
        val source = "// wrasse matrix:abc\n// wrasse columns:1\nval x = f(1)\n"
        val directives = directivesOf(source)

        claimCall(directives, source, "f(1)") shouldBe LayoutClaim.Invalid
        directives.all().map { it.claimed } shouldBe listOf(true, true)
    }

    should("keep directives of different calls apart") {
        val source = "// wrasse matrix:2\nval x = f(1)\n// wrasse columns:3\nval y = g(2)\n"
        val directives = directivesOf(source)
        val (first, second) = directives.all()

        (claimCall(directives, source, "f(1)") as LayoutClaim.Single).directive shouldBe first
        (claimCall(directives, source, "g(2)") as LayoutClaim.Single).directive shouldBe second
    }

    should("not let a directive reach past a blank line to share a call with the one below it") {
        val source = "// wrasse matrix:2\n\n// wrasse columns:3\nval x = f(1)\n"
        val directives = directivesOf(source)

        (claimCall(directives, source, "f(1)") as LayoutClaim.Single).directive shouldBe directives.all()[1]
        directives.all()[0].claimed shouldBe false
    }

    should("not let a directive reach past an ordinary comment to share a call with the one below it") {
        val source = "// wrasse matrix:2\n// note\n// wrasse columns:3\nval x = f(1)\n"
        val directives = directivesOf(source)

        (claimCall(directives, source, "f(1)") as LayoutClaim.Single).directive shouldBe directives.all()[1]
    }

    should("leave a directive inside a region out of the claim") {
        val source = "// wrasse matrix:2\n// wrasse columns:3\nval x = f(1)\n"
        val directives = directivesOf(source)
        val regions = FormatRegions().apply { addSpan(0, 19) }

        val claim = claimCall(directives, source, "f(1)", regions) as LayoutClaim.Single

        claim.directive shouldBe directives.all()[1]
        directives.all()[0].claimed shouldBe false
    }

    should("claim nothing for a call whose line starts inside a region") {
        val source = "// wrasse matrix:2\nval x = f(1)\n"
        val directives = directivesOf(source)
        val regions = FormatRegions().apply { addSpan(19, 30) }

        claimCall(directives, source, "f(1)", regions) shouldBe null
        directives.all().single().claimed shouldBe false
    }

    should("claim nothing for a call on an annotation-only line") {
        val source = "// wrasse matrix:2\n@Foo(intArrayOf(1))\nval x = f(1)\n"
        val directives = directivesOf(source, "@Foo(intArrayOf(1))")

        claimCall(directives, source, "intArrayOf(1)") shouldBe null
        (claimCall(directives, source, "= f(1)") as LayoutClaim.Single).directive shouldBe directives.all().single()
    }

    should("claim for a call whose annotation ends on the line it starts") {
        val source = "// wrasse matrix:2\n@Foo(\n    1,\n) val x = f(1)\n"
        val directives = directivesOf(source, "@Foo(\n    1,\n)")

        (claimCall(directives, source, "f(1)") as LayoutClaim.Single).directive shouldBe directives.all().single()
    }
})
