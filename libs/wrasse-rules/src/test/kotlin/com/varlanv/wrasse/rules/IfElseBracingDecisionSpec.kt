package com.varlanv.wrasse.rules

import com.varlanv.wrasse.lang.IndentScope
import com.varlanv.wrasse.testing.BaseSpec
import io.kotest.matchers.shouldBe

class IfElseBracingDecisionSpec : BaseSpec({

    should("report the chain span as multi-line when it contains a newline anywhere") {
        val source = "if (true)\n    doSomething()"

        IfElseBracingDecision.chainSpansMultipleLines(source, 0, source.length) shouldBe true
    }

    should("report the chain span as single-line when it contains no newline") {
        val source = "if (true) doSomething() else doOther()"

        IfElseBracingDecision.chainSpansMultipleLines(source, 0, source.length) shouldBe false
    }

    should("wrap a bare branch with no following sibling in braces via a leading and a zero-width trailing edit") {
        val source = "if (true)\n        doSomething()"
        val contentStart = source.indexOf("doSomething()")
        val contentEnd = contentStart + "doSomething()".length
        val candidate = IfElseBracingCandidate(
            leadingGapStart = source.indexOf(")") + 1,
            contentStart = contentStart,
            contentEnd = contentEnd,
            trailingGapEnd = contentEnd,
            hasFollowingBranch = false,
            hasAdjacentComment = false,
        )

        val verdict = IfElseBracingDecision.decideBranch(
            source,
            candidate,
            baseIndentColumn = 4,
            indentWidth = 4,
            formatEnabled = false,
        )

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

        val fixed = source.substring(0, leading.startOffset) +
        leading.replacement +
        source.substring(leading.endOffset, trailing.startOffset) +
            trailing.replacement +
            source.substring(trailing.endOffset)
        fixed shouldBe "if (true) {\n        doSomething()\n    }"
    }

    should("wrap a bare THEN branch followed by an else, collapsing the gap to a single space before else") {
        val source = "if (true)\n        doSomething()\n    else"
        val contentStart = source.indexOf("doSomething()")
        val contentEnd = contentStart + "doSomething()".length
        val candidate = IfElseBracingCandidate(
            leadingGapStart = source.indexOf(")") + 1,
            contentStart = contentStart,
            contentEnd = contentEnd,
            trailingGapEnd = source.indexOf("else"),
            hasFollowingBranch = true,
            hasAdjacentComment = false,
        )

        val verdict = IfElseBracingDecision.decideBranch(
            source,
            candidate,
            baseIndentColumn = 4,
            indentWidth = 4,
            formatEnabled = false,
        )

        verdict.edits.size shouldBe 2
        val (leading, trailing) = verdict.edits
        trailing.startOffset shouldBe contentEnd
        trailing.endOffset shouldBe source.indexOf("else")
        trailing.replacement shouldBe "\n    } "

        val fixed = source.substring(0, leading.startOffset) +
        leading.replacement +
        source.substring(leading.endOffset, trailing.startOffset) +
            trailing.replacement +
            source.substring(trailing.endOffset)
        fixed shouldBe "if (true) {\n        doSomething()\n    } else"
    }

    should("bail with no edits, report-only, when a comment is adjacent to the branch") {
        val source = "if (true)\n        doSomething()"
        val contentStart = source.indexOf("doSomething()")
        val contentEnd = contentStart + "doSomething()".length
        val candidate = IfElseBracingCandidate(
            leadingGapStart = source.indexOf(")") + 1,
            contentStart = contentStart,
            contentEnd = contentEnd,
            trailingGapEnd = contentEnd,
            hasFollowingBranch = false,
            hasAdjacentComment = true,
        )

        val verdict = IfElseBracingDecision.decideBranch(
            source,
            candidate,
            baseIndentColumn = 4,
            indentWidth = 4,
            formatEnabled = false,
        )

        verdict.reportStart shouldBe contentStart
        verdict.reportEnd shouldBe contentStart
        verdict.edits shouldBe emptyList()
    }

    should("bail with no edits, report-only, when the branch's own content already spans multiple lines") {
        val source = "if (true)\n        50\n            .toString()"
        val contentStart = source.indexOf("50")
        val contentEnd = source.length
        val candidate = IfElseBracingCandidate(
            leadingGapStart = source.indexOf(")") + 1,
            contentStart = contentStart,
            contentEnd = contentEnd,
            trailingGapEnd = contentEnd,
            hasFollowingBranch = false,
            hasAdjacentComment = false,
        )

        val verdict = IfElseBracingDecision.decideBranch(
            source,
            candidate,
            baseIndentColumn = 4,
            indentWidth = 4,
            formatEnabled = false,
        )

        verdict.edits shouldBe emptyList()
    }

    should("wrap a bare branch with minimal, unindented edits when formatEnabled is true") {
        val source = "if (true)\n        doSomething()"
        val contentStart = source.indexOf("doSomething()")
        val contentEnd = contentStart + "doSomething()".length
        val candidate = IfElseBracingCandidate(
            leadingGapStart = source.indexOf(")") + 1,
            contentStart = contentStart,
            contentEnd = contentEnd,
            trailingGapEnd = contentEnd,
            hasFollowingBranch = false,
            hasAdjacentComment = false,
        )

        val verdict = IfElseBracingDecision.decideBranch(
            source,
            candidate,
            baseIndentColumn = 4,
            indentWidth = 4,
            formatEnabled = true,
        )

        verdict.reportStart shouldBe contentStart
        verdict.reportEnd shouldBe contentEnd
        verdict.edits.size shouldBe 2
        val (leading, trailing) = verdict.edits
        leading.startOffset shouldBe candidate.leadingGapStart
        leading.endOffset shouldBe contentStart
        leading.replacement shouldBe " {\n"
        leading.indentScope shouldBe IndentScope.OPEN
        trailing.startOffset shouldBe contentEnd
        trailing.endOffset shouldBe contentEnd
        trailing.replacement shouldBe "\n}"
        trailing.indentScope shouldBe IndentScope.CLOSE
    }

    should("not bail on a branch whose own content spans multiple lines when formatEnabled is true") {
        val source = "if (true)\n        50\n            .toString()"
        val contentStart = source.indexOf("50")
        val contentEnd = source.length
        val candidate = IfElseBracingCandidate(
            leadingGapStart = source.indexOf(")") + 1,
            contentStart = contentStart,
            contentEnd = contentEnd,
            trailingGapEnd = contentEnd,
            hasFollowingBranch = false,
            hasAdjacentComment = false,
        )

        val verdict = IfElseBracingDecision.decideBranch(
            source,
            candidate,
            baseIndentColumn = 4,
            indentWidth = 4,
            formatEnabled = true,
        )

        verdict.edits.size shouldBe 2
    }

    should("still bail with no edits when a comment is adjacent, even when formatEnabled is true") {
        val source = "if (true)\n        doSomething()"
        val contentStart = source.indexOf("doSomething()")
        val contentEnd = contentStart + "doSomething()".length
        val candidate = IfElseBracingCandidate(
            leadingGapStart = source.indexOf(")") + 1,
            contentStart = contentStart,
            contentEnd = contentEnd,
            trailingGapEnd = contentEnd,
            hasFollowingBranch = false,
            hasAdjacentComment = true,
        )

        val verdict = IfElseBracingDecision.decideBranch(
            source,
            candidate,
            baseIndentColumn = 4,
            indentWidth = 4,
            formatEnabled = true,
        )

        verdict.edits shouldBe emptyList()
    }
})
