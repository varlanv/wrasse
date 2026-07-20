package com.varlanv.wrasse.rules

import com.varlanv.wrasse.testing.BaseSpec
import io.kotest.matchers.shouldBe

class WhenEntryBracingDecisionSpec : BaseSpec({

    should("require both a braced sibling and a multiline entry to brace anything") {
        WhenEntryBracingDecision.shouldBraceEntries(anyEntryHasBlockBody = true, anyEntryHasMultilineBody = true) shouldBe true
        WhenEntryBracingDecision.shouldBraceEntries(anyEntryHasBlockBody = true, anyEntryHasMultilineBody = false) shouldBe false
        WhenEntryBracingDecision.shouldBraceEntries(anyEntryHasBlockBody = false, anyEntryHasMultilineBody = true) shouldBe false
        WhenEntryBracingDecision.shouldBraceEntries(anyEntryHasBlockBody = false, anyEntryHasMultilineBody = false) shouldBe false
    }

    should("wrap a bare entry body in braces via a leading and a zero-width trailing edit") {
        val source = "2 ->\n        \"two\""
        val contentStart = source.indexOf("\"two\"")
        val contentEnd = contentStart + "\"two\"".length
        val candidate = WhenEntryBracingCandidate(
            leadingGapStart = source.indexOf("->") + 2,
            contentStart = contentStart,
            contentEnd = contentEnd,
            hasAdjacentComment = false,
        )

        val verdict = WhenEntryBracingDecision.decideEntry(source, candidate, baseIndentColumn = 4, indentWidth = 4)

        verdict.reportStart shouldBe contentStart
        verdict.reportEnd shouldBe contentEnd
        verdict.edits.size shouldBe 2
        val (leading, trailing) = verdict.edits
        leading.startOffset shouldBe candidate.leadingGapStart
        leading.endOffset shouldBe contentStart
        leading.replacement shouldBe " {\n        "
        trailing.startOffset shouldBe contentEnd
        trailing.endOffset shouldBe contentEnd
        trailing.replacement shouldBe "\n    }"

        val fixed = source.substring(0, leading.startOffset) + leading.replacement +
            source.substring(leading.endOffset, trailing.startOffset) + trailing.replacement +
            source.substring(trailing.endOffset)
        fixed shouldBe "2 -> {\n        \"two\"\n    }"
    }

    should("bail with no edits, report-only, when a comment is adjacent to the entry body") {
        val source = "2 ->\n        \"two\""
        val contentStart = source.indexOf("\"two\"")
        val contentEnd = contentStart + "\"two\"".length
        val candidate = WhenEntryBracingCandidate(
            leadingGapStart = source.indexOf("->") + 2,
            contentStart = contentStart,
            contentEnd = contentEnd,
            hasAdjacentComment = true,
        )

        val verdict = WhenEntryBracingDecision.decideEntry(source, candidate, baseIndentColumn = 4, indentWidth = 4)

        verdict.reportStart shouldBe contentStart
        verdict.reportEnd shouldBe contentStart
        verdict.edits shouldBe emptyList()
    }

    should("bail with no edits, report-only, when the entry's own body already spans multiple lines") {
        val source = "2 -> x\n            .plus(\"!\")"
        val contentStart = source.indexOf("x")
        val contentEnd = source.length
        val candidate = WhenEntryBracingCandidate(
            leadingGapStart = source.indexOf("->") + 2,
            contentStart = contentStart,
            contentEnd = contentEnd,
            hasAdjacentComment = false,
        )

        val verdict = WhenEntryBracingDecision.decideEntry(source, candidate, baseIndentColumn = 4, indentWidth = 4)

        verdict.edits shouldBe emptyList()
    }
})
