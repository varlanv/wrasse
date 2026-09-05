package com.varlanv.wrasse.rules

import com.varlanv.wrasse.testing.BaseSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe

private fun record(
    text: String,
    startOffset: Int,
): ImportOrderingRecord = ImportOrderingRecord(startOffset, startOffset + text.length, text)

class ImportInsertionDecisionSpec : BaseSpec({

    should("insert before the one directive that sorts after the new import, on a clean pairwise gap") {
        val text = "import a.A\nimport z.Z\n"
        val records = listOf(record("import a.A", 0), record("import z.Z", 11))

        val groups = ImportInsertionDecision.standaloneEdits(text, 0, 21, records, listOf("m.M"))

        groups shouldHaveSize 1
        groups[0].fqns shouldBe listOf("m.M")
        groups[0].edit.startOffset shouldBe 11
        groups[0].edit.endOffset shouldBe 11
        groups[0].edit.replacement shouldBe "import m.M\n"
    }

    should("insert before the first directive when the new import sorts first, on a clean gap to list start") {
        val text = "import m.M\nimport z.Z\n"
        val records = listOf(record("import m.M", 0), record("import z.Z", 11))

        val groups = ImportInsertionDecision.standaloneEdits(text, 0, 21, records, listOf("a.A"))

        groups shouldHaveSize 1
        groups[0].edit.startOffset shouldBe 0
        groups[0].edit.replacement shouldBe "import a.A\n"
    }

    should("append after the last directive when the new import sorts last") {
        val text = "import a.A\nimport m.M\n"
        val records = listOf(record("import a.A", 0), record("import m.M", 11))
        val listEnd = 21

        val groups = ImportInsertionDecision.standaloneEdits(text, 0, listEnd, records, listOf("z.Z"))

        groups shouldHaveSize 1
        groups[0].edit.startOffset shouldBe listEnd
        groups[0].edit.endOffset shouldBe listEnd
        groups[0].edit.replacement shouldBe "\nimport z.Z"
    }

    should("fall back to appending after the last directive when the pairwise gap has a comment") {
        val text = "import a.A\n// about Z\nimport z.Z\n"
        val zStart = text.indexOf("import z.Z")
        val records = listOf(record("import a.A", 0), record("import z.Z", zStart))
        val listEnd = zStart + "import z.Z".length

        val groups = ImportInsertionDecision.standaloneEdits(text, 0, listEnd, records, listOf("m.M"))

        groups shouldHaveSize 1
        groups[0].edit.startOffset shouldBe listEnd
        groups[0].edit.replacement shouldBe "\nimport m.M"
    }

    should("fall back to appending after the last directive when a leading comment precedes the first directive") {
        val text = "// header\nimport z.Z\n"
        val zStart = text.indexOf("import z.Z")
        val records = listOf(record("import z.Z", zStart))
        val listEnd = zStart + "import z.Z".length

        val groups = ImportInsertionDecision.standaloneEdits(text, 0, listEnd, records, listOf("a.A"))

        groups shouldHaveSize 1
        groups[0].edit.startOffset shouldBe listEnd
    }

    should("merge several new imports sharing the same seam into one edit, sorted among themselves") {
        val text = "import a.A\nimport z.Z\n"
        val records = listOf(record("import a.A", 0), record("import z.Z", 11))

        val groups = ImportInsertionDecision.standaloneEdits(text, 0, 21, records, listOf("n.N", "m.M"))

        groups shouldHaveSize 1
        groups[0].fqns shouldBe listOf("m.M", "n.N")
        groups[0].edit.replacement shouldBe "import m.M\nimport n.N\n"
    }

    should("split new imports across two different seams when they don't collapse to the same one") {
        val text = "import m1.M\nimport z.Z\n"
        val records = listOf(record("import m1.M", 0), record("import z.Z", 12))
        val listEnd = 22

        val groups = ImportInsertionDecision.standaloneEdits(text, 0, listEnd, records, listOf("a.A", "zz.Z"))

        groups shouldHaveSize 2
        val bySeam = groups.associateBy { it.edit.startOffset }
        bySeam.getValue(0).fqns shouldBe listOf("a.A")
        bySeam.getValue(listEnd).fqns shouldBe listOf("zz.Z")
    }

    should("produce nothing for an empty new-import list or an empty directive list") {
        val records = listOf(record("import a.A", 0))
        ImportInsertionDecision.standaloneEdits("import a.A\n", 0, 10, records, emptyList()).shouldBeEmpty()
        ImportInsertionDecision.standaloneEdits("", 0, 0, emptyList(), listOf("a.A")).shouldBeEmpty()
    }

    should("replace the whole whitespace gap with a blank line on both sides when content precedes and follows") {
        val text = "package sample\n\nval x = 1\n"
        val listStart = "package sample".length

        val edit = ImportInsertionDecision.emptyListInsertion(text, listStart, listOf("a.A"))

        edit.startOffset shouldBe listStart
        edit.endOffset shouldBe text.indexOf("val x")
        edit.replacement shouldBe "\n\nimport a.A\n\n"
    }

    should("insert at file start with only a trailing blank line when nothing precedes the import list") {
        val text = "val x = 1\n"

        val edit = ImportInsertionDecision.emptyListInsertion(text, 0, listOf("a.A"))

        edit.startOffset shouldBe 0
        edit.endOffset shouldBe 0
        edit.replacement shouldBe "import a.A\n\n"
    }

    should("insert with only a leading blank line when nothing follows the import list") {
        val text = "package sample\n"
        val listStart = "package sample".length

        val edit = ImportInsertionDecision.emptyListInsertion(text, listStart, listOf("a.A"))

        edit.startOffset shouldBe listStart
        edit.endOffset shouldBe text.length
        edit.replacement shouldBe "\n\nimport a.A\n"
    }

    should("sort several simultaneously-added imports within the empty-list replacement block") {
        val text = "val x = 1\n"

        val edit = ImportInsertionDecision.emptyListInsertion(text, 0, listOf("z.Z", "a.A"))

        edit.replacement shouldBe "import a.A\nimport z.Z\n\n"
    }
})
