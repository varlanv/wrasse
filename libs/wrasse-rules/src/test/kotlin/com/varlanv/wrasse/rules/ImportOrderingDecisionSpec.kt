package com.varlanv.wrasse.rules

import com.varlanv.wrasse.lang.WEdit
import com.varlanv.wrasse.testing.BaseSpec
import io.kotest.matchers.shouldBe

private fun record(
    text: String,
    startOffset: Int,
): ImportOrderingRecord = ImportOrderingRecord(startOffset, startOffset + text.length, text)

class ImportOrderingDecisionSpec : BaseSpec({

    should("derive the sort key by stripping the import keyword and its following whitespace") {
        ImportOrderingDecision.sortKeyOf("import a.b.C") shouldBe "a.b.C"
        ImportOrderingDecision.sortKeyOf("import a.b.C as D") shouldBe "a.b.C as D"
        ImportOrderingDecision.sortKeyOf("import a.b.*") shouldBe "a.b.*"
        ImportOrderingDecision.sortKeyOf("import   a.b.C") shouldBe "a.b.C"
    }

    should("strip backticks from the sort key so a quoted identifier sorts by its plain letters") {
        ImportOrderingDecision.sortKeyOf("import org.mockito.Mockito.`when`") shouldBe "org.mockito.Mockito.when"
        val records = listOf(
            record("import org.mockito.Mockito.`when`", 0),
            record("import org.mockito.Mockito.verify", 40),
        )

        ImportOrderingDecision.firstOutOfOrder(records) shouldBe records[0]
        ImportOrderingDecision.sortedReplacement(records) shouldBe
            "import org.mockito.Mockito.verify\nimport org.mockito.Mockito.`when`"
    }

    should("find no first-out-of-order record for fewer than two records") {
        ImportOrderingDecision.firstOutOfOrder(emptyList()) shouldBe null
        ImportOrderingDecision.firstOutOfOrder(listOf(record("import a.b.C", 0))) shouldBe null
    }

    should("find no first-out-of-order record when already sorted") {
        val records = listOf(record("import a.b.C", 0), record("import a.b.D", 20))
        ImportOrderingDecision.firstOutOfOrder(records) shouldBe null
    }

    should("report the first record whose position disagrees with ascending sort-key order") {
        val first = record("import z.Z", 0)
        val second = record("import a.A", 20)
        val records = listOf(first, second)

        val firstBad = ImportOrderingDecision.firstOutOfOrder(records)

        firstBad shouldBe first
    }

    should("build the sorted, newline-joined replacement from the original directive texts") {
        val records = listOf(record("import z.Z", 0), record("import a.A", 20))

        ImportOrderingDecision.sortedReplacement(records) shouldBe "import a.A\nimport z.Z"
    }

    should("consider a list clean when directives are separated by exactly one newline with no comment") {
        val text = "import a.A\nimport b.B"
        val spans = listOf(0 to 10, 11 to 21)

        ImportOrderingDecision.isCleanList(text, 0, 21, spans, hasCommentInList = false) shouldBe true
    }

    should("reject a list containing a comment") {
        val text = "import a.A\nimport b.B"
        val spans = listOf(0 to 10, 11 to 21)

        ImportOrderingDecision.isCleanList(text, 0, 21, spans, hasCommentInList = true) shouldBe false
    }

    should("reject a list with a blank line between directives") {
        val text = "import a.A\n\nimport b.B"
        val spans = listOf(0 to 10, 12 to 22)

        ImportOrderingDecision.isCleanList(text, 0, 22, spans, hasCommentInList = false) shouldBe false
    }

    should("reject a list with two directives sharing one line") {
        val text = "import a.A; import b.B"
        val spans = listOf(0 to 11, 12 to 23)

        ImportOrderingDecision.isCleanList(text, 0, 23, spans, hasCommentInList = false) shouldBe false
    }

    should("reject a list with no directives at all") {
        ImportOrderingDecision.isCleanList("", 0, 0, emptyList(), hasCommentInList = false) shouldBe false
    }

    should("compose a region with no inner edits by just re-sorting it") {
        val text = "import z.Z\nimport a.A\n"

        val composed = ImportOrderingDecision.composeRegion(text, 0, text.length, emptyList())

        composed shouldBe "import a.A\nimport z.Z\n"
    }

    should("apply a consumed deletion edit before re-sorting, dropping the deleted line entirely") {
        val text = "import z.Z\nimport unused.U\nimport a.A\n"
        val deleteUnused = WEdit(text.indexOf("import unused.U"), text.indexOf("import a.A"), "")

        val composed = ImportOrderingDecision.composeRegion(text, 0, text.length, listOf(deleteUnused))

        composed shouldBe "import a.A\nimport z.Z\n"
    }

    should("apply a consumed replacement edit (e.g. star expansion) and interleave its new lines when sorting") {
        val text = "import a.A\nimport pkg.*\n"
        val starStart = text.indexOf("import pkg.*")
        val starEnd = text.length - 1
        val expand = WEdit(starStart, starEnd, "import pkg.B\nimport pkg.C")

        val composed = ImportOrderingDecision.composeRegion(text, 0, text.length, listOf(expand))

        composed shouldBe "import a.A\nimport pkg.B\nimport pkg.C\n"
    }

    should("preserve the absence of a trailing newline when the region itself has none") {
        val text = "import z.Z\nimport a.A"

        val composed = ImportOrderingDecision.composeRegion(text, 0, text.length, emptyList())

        composed shouldBe "import a.A\nimport z.Z"
    }

    should("collapse to an empty replacement when every directive is deleted") {
        val text = "import a.A\nimport b.B\n"
        val deleteAll = WEdit(0, text.length, "")

        val composed = ImportOrderingDecision.composeRegion(text, 0, text.length, listOf(deleteAll))

        composed shouldBe ""
    }

    should("bail composing when two consumed edits overlap") {
        val text = "import a.A\nimport b.B\n"
        val editOne = WEdit(0, 12, "import a.A\n")
        val editTwo = WEdit(6, 20, "import b.B\n")

        val composed = ImportOrderingDecision.composeRegion(text, 0, text.length, listOf(editOne, editTwo))

        composed shouldBe null
    }

    should("bail composing when the reconstructed region does not parse as clean import lines") {
        val text = "import a.A\nimport b.B\n"
        val corrupt = WEdit(text.indexOf("import b.B"), text.length - 1, "// not an import")

        val composed = ImportOrderingDecision.composeRegion(text, 0, text.length, listOf(corrupt))

        composed shouldBe null
    }

    should("fold new import lines into the composed rewrite, sorted alongside the existing directives") {
        val text = "import z.Z\nimport a.A\n"

        val composed = ImportOrderingDecision.composeRegion(text, 0, text.length, emptyList(), listOf("import m.M"))

        composed shouldBe "import a.A\nimport m.M\nimport z.Z\n"
    }

    should("fold new import lines in even when no other edit is present and the region has no trailing newline") {
        val text = "import z.Z\nimport a.A"

        val composed = ImportOrderingDecision.composeRegion(text, 0, text.length, emptyList(), listOf("import m.M"))

        composed shouldBe "import a.A\nimport m.M\nimport z.Z\n"
    }

    should("fold new import lines in alongside a deletion, sorting the survivors and the addition together") {
        val text = "import z.Z\nimport unused.U\nimport a.A\n"
        val deleteUnused = WEdit(text.indexOf("import unused.U"), text.indexOf("import a.A"), "")

        val composed = ImportOrderingDecision.composeRegion(
            text,
            0,
            text.length,
            listOf(deleteUnused),
            listOf("import m.M"),
        )

        composed shouldBe "import a.A\nimport m.M\nimport z.Z\n"
    }
})
