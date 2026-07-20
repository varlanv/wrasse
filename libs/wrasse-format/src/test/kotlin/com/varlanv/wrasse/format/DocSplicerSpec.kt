package com.varlanv.wrasse.format

import com.varlanv.wrasse.lang.IndentScope
import com.varlanv.wrasse.lang.WEdit
import com.varlanv.wrasse.model.FormatStyle
import com.varlanv.wrasse.testing.BaseSpec
import io.kotest.matchers.shouldBe

class DocSplicerSpec : BaseSpec({

    val style = FormatStyle(indentWidth = 4)

    fun render(doc: Doc?): String? = doc?.let { Layout.render(it, style) }

    should("delete a whole leaf (no-semicolons shape)") {
        val doc = Doc.Concat(
            listOf(Doc.Text("bar()", 0, 5), Doc.Text(";", 5, 6), Doc.Text("\n", 6, 7)),
            0,
            7,
        )
        val spliced = DocSplicer.splice(doc, listOf(WEdit(5, 6, "")))
        render(spliced) shouldBe "bar()\n"
    }

    should("delete a whole line spanning multiple leaves including its trailing newline (no-unused-imports shape)") {
        val doc = Doc.Concat(
            listOf(
                Doc.Text("import a.B", 0, 10),
                Doc.Break(BreakKind.HARD, "\n", start = 10, end = 11),
                Doc.Text("import a.C", 11, 21),
                Doc.Break(BreakKind.HARD, "\n", start = 21, end = 22),
            ),
            0,
            22,
        )
        val spliced = DocSplicer.splice(doc, listOf(WEdit(0, 11, "")))
        render(spliced) shouldBe "import a.C\n"
    }

    should("replace a whole import directive with a multi-line atomic run (no-wildcard-imports expansion shape)") {
        val doc = Doc.Concat(
            listOf(
                Doc.Text("import a.*", 0, 10),
                Doc.Break(BreakKind.HARD, "\n", start = 10, end = 11),
            ),
            0,
            11,
        )
        val spliced = DocSplicer.splice(doc, listOf(WEdit(0, 10, "import a.B\nimport a.C")))
        render(spliced) shouldBe "import a.B\nimport a.C\n"
    }

    should("replace a whole HARD break's gap with rule-supplied indentation (if-else-bracing leading-brace shape)") {
        val doc = Doc.Concat(
            listOf(
                Doc.Text("if (true)", 0, 9),
                Doc.Break(BreakKind.HARD, "\n", start = 9, end = 17),
                Doc.Text("body()", 17, 23),
            ),
            0,
            23,
        )
        val spliced = DocSplicer.splice(doc, listOf(WEdit(9, 17, " {\n    ")))
        render(spliced) shouldBe "if (true) {\n    body()"
    }

    should("insert a zero-width replacement between two adjacent leaves (if-else-bracing trailing-brace shape)") {
        val doc = Doc.Concat(listOf(Doc.Text("body()", 0, 6), Doc.Text("\n}", 6, 8)), 0, 8)
        val spliced = DocSplicer.splice(doc, listOf(WEdit(6, 6, "\n}")))
        render(spliced) shouldBe "body()\n}\n}"
    }

    should("insert a zero-width replacement strictly inside a Text leaf") {
        val doc = Doc.Text("abcdef", 0, 6)
        val spliced = DocSplicer.splice(doc, listOf(WEdit(3, 3, "-")))
        render(spliced) shouldBe "abc-def"
    }

    should("append a zero-width insertion past the very end of the file (trailing-newline shape)") {
        val doc = Doc.Text("x", 0, 1)
        val spliced = DocSplicer.splice(doc, listOf(WEdit(1, 1, "\n")))
        render(spliced) shouldBe "x\n"
    }

    should("split a HARD break's literal at a cut inside the newline-carrying portion, keeping remaining blank lines") {
        val doc = Doc.Break(BreakKind.HARD, "\n\n\n", start = 0, end = 3)
        val spliced = DocSplicer.splice(doc, listOf(WEdit(0, 1, "")))
        render(spliced) shouldBe "\n\n"
    }

    should("apply several disjoint edits across the whole tree in one pass (grand-slam shape)") {
        val doc = Doc.Concat(
            listOf(
                Doc.Text("bar()", 0, 5),
                Doc.Text(";", 5, 6),
                Doc.Break(BreakKind.HARD, "\n", start = 6, end = 7),
                Doc.Text("baz()", 7, 12),
            ),
            0,
            12,
        )
        val spliced = DocSplicer.splice(doc, listOf(WEdit(5, 6, ""), WEdit(12, 12, ";")))
        render(spliced) shouldBe "bar()\nbaz();"
    }

    should("bail (return null) when a cut lands strictly inside a Break's elided trailing-indent tail") {
        val doc = Doc.Concat(
            listOf(
                Doc.Break(BreakKind.HARD, "\n", start = 0, end = 5),
                Doc.Text("body()", 5, 11),
            ),
            0,
            11,
        )
        val spliced = DocSplicer.splice(doc, listOf(WEdit(2, 11, "replaced")))
        spliced shouldBe null
    }

    should("leave the doc untouched when no edits are given") {
        val doc = Doc.Concat(listOf(Doc.Text("x", 0, 1)), 0, 1)
        val spliced = DocSplicer.splice(doc, emptyList())
        render(spliced) shouldBe "x"
    }

    should(
        "wrap a minimal brace pair one Indent level deeper than its enclosing block, splicing a " +
            "zero-width close as a sibling rather than into the enclosing block's own dedent " +
            "whitespace (the C.3 regression: a bare branch that is also the last statement in its " +
            "block)"
    ) {
        val innerIf = Doc.Concat(
            listOf(
                Doc.Text("if (true)", 2, 11),
                Doc.Text(" ", 11, 12),
                Doc.Text("return \"yes\"", 12, 25),
            ),
            2,
            25,
        )
        val doc = Doc.Concat(
            listOf(
                Doc.Text("{", 0, 1),
                Doc.Indent(innerIf),
                Doc.Break(BreakKind.HARD, "\n", start = 25, end = 26),
                Doc.Text("}", 26, 27),
            ),
            0,
            27,
        )
        val edits = listOf(
            WEdit(11, 12, " {\n", indentScope = IndentScope.OPEN),
            WEdit(25, 25, "\n}", indentScope = IndentScope.CLOSE),
        )

        val spliced = DocSplicer.splice(doc, edits)

        render(spliced) shouldBe "{if (true) {\n        return \"yes\"\n    }\n}"
    }

    should("splice a non-zero-width close edit (a following branch) via the ordinary fullyCovered replace, no sibling insertion needed") {
        val content = Doc.Concat(
            listOf(
                Doc.Text("if (true)", 0, 9),
                Doc.Text(" ", 9, 10),
                Doc.Text("return \"yes\"", 10, 22),
                Doc.Text(" ", 22, 23),
                Doc.Text("else", 23, 27),
            ),
            0,
            27,
        )
        val edits = listOf(
            WEdit(9, 10, " {\n", indentScope = IndentScope.OPEN),
            WEdit(22, 23, "\n} ", indentScope = IndentScope.CLOSE),
        )

        val spliced = DocSplicer.splice(content, edits)

        render(spliced) shouldBe "if (true) {\n    return \"yes\"\n} else"
    }

    should("bail (return null) when a CLOSE has no matching prior OPEN") {
        val doc = Doc.Text("x", 0, 1)
        val spliced = DocSplicer.splice(doc, listOf(WEdit(1, 1, "\n}", indentScope = IndentScope.CLOSE)))
        spliced shouldBe null
    }

    should("bail (return null) when an OPEN is never closed") {
        val doc = Doc.Text("x", 0, 1)
        val spliced = DocSplicer.splice(doc, listOf(WEdit(0, 0, " {\n", indentScope = IndentScope.OPEN)))
        spliced shouldBe null
    }
})
