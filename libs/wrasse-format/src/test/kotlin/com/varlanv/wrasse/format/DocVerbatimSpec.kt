package com.varlanv.wrasse.format

import com.varlanv.wrasse.model.FormatDirective
import com.varlanv.wrasse.model.FormatRegions
import com.varlanv.wrasse.model.FormatStyle
import com.varlanv.wrasse.testing.BaseSpec
import io.kotest.matchers.shouldBe

class DocVerbatimSpec : BaseSpec({

    val style = FormatStyle(indentWidth = 4)

    fun regionOf(start: Int, end: Int): FormatRegions = FormatRegions().also {
        it.add(FormatDirective.OFF, start, start)
        if (end != FormatRegions.OPEN_END) it.add(FormatDirective.ON, end, end)
    }

    fun render(
        doc: Doc,
        regions: FormatRegions,
        source: String,
    ): String = Layout.render(DocVerbatim.apply(doc, regions, source), style)

    val source = "{\n    a   =   1\n    // off\n  b  =  2\n    // on\n    c   =   3\n}"
    val regionStart = source.indexOf("    // off")
    val regionEnd = source.indexOf("\n    c")

    fun block(): Doc = Doc.Concat(
        listOf(
            Doc.Text("{", 0, 1),
            Doc.Indent(
                Doc.Concat(
                    listOf(
                        Doc.Break(BreakKind.HARD, "\n", start = 1, end = 6),
                        Doc.Text("a = 1", 6, 15),
                        Doc.Break(BreakKind.HARD, "\n", start = 15, end = regionStart + 4),
                        Doc.Text("// off", regionStart + 4, regionStart + 10),
                        Doc.Break(BreakKind.HARD, "\n", start = regionStart + 10, end = regionStart + 13),
                        Doc.Text("b = 2", regionStart + 13, regionStart + 22),
                        Doc.Break(BreakKind.HARD, "\n", start = regionStart + 22, end = regionEnd - 6),
                        Doc.Text("// on", regionEnd - 6, regionEnd),
                        Doc.Break(BreakKind.HARD, "\n", start = regionEnd, end = regionEnd + 5),
                        Doc.Text("c = 3", regionEnd + 5, regionEnd + 14),
                    ),
                    1,
                    regionEnd + 14,
                ),
            ),
            Doc.Break(BreakKind.HARD, "\n", start = regionEnd + 14, end = regionEnd + 15),
            Doc.Text("}", regionEnd + 15, regionEnd + 16),
        ),
        0,
        regionEnd + 16,
    )

    should("render a doc untouched when there are no regions") {
        render(block(), FormatRegions(), source) shouldBe "{\n    a = 1\n    // off\n    b = 2\n    // on\n    c = 3\n}"
    }

    should("render a closed region as its exact source and format around it") {
        render(block(), regionOf(regionStart, regionEnd), source) shouldBe
            "{\n    a = 1\n    // off\n  b  =  2\n    // on\n    c = 3\n}"
    }

    should("render an open-ended region to the end of the source") {
        render(block(), regionOf(regionStart, FormatRegions.OPEN_END), source) shouldBe
            "{\n    a = 1\n" + source.substring(regionStart)
    }

    should("keep the literal of a straddling hard break and drop its synthesized indent") {
        val doc = Doc.Concat(
            listOf(
                Doc.Text("x", 0, 1),
                Doc.Break(BreakKind.HARD, "\n\n", start = 1, end = 9),
                Doc.Text("// off", 9, 15),
            ),
            0,
            15,
        )
        val text = "x\n\n\n\n\n\n   // off"
        val regions = FormatRegions().also { it.add(FormatDirective.OFF, 6, 6) }

        render(Doc.Indent(doc), regions, text) shouldBe "x\n\n" + text.substring(6)
    }

    should("drop a zero-width leaf inside a region and keep one at its end") {
        val doc = Doc.Concat(
            listOf(Doc.Text("a", 0, 1), Doc.Text("?", 2, 2), Doc.Text("b", 3, 4), Doc.Text("!", 4, 4)),
            0,
            4,
        )
        val regions = FormatRegions().also {
            it.add(FormatDirective.OFF, 1, 1)
            it.add(FormatDirective.ON, 4, 4)
        }

        render(doc, regions, "a  b") shouldBe "a  b!"
    }

    should("emit a region once even when several leaves claim its source") {
        val doc = Doc.Concat(listOf(Doc.Text("q", 0, 3), Doc.Text("r", 0, 3)), 0, 3)
        val regions = FormatRegions().also {
            it.add(FormatDirective.OFF, 0, 0)
            it.add(FormatDirective.ON, 3, 3)
        }

        render(doc, regions, "xyz") shouldBe "xyz"
    }
})
