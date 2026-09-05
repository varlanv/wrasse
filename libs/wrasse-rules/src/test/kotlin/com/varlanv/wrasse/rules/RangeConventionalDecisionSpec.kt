package com.varlanv.wrasse.rules

import com.varlanv.wrasse.testing.BaseSpec
import io.kotest.matchers.shouldBe

class RangeConventionalDecisionSpec : BaseSpec({

    should("replace a rangeTo call with the .. operator") {
        val verdict = RangeConventionalDecision.decideRangeToCall(
            callStart = 0,
            callEnd = 20,
            receiverText = "a",
            argumentText = "b",
            hasComment = false,
        )

        verdict.edits.size shouldBe 1
        val edit = verdict.edits.single()
        edit.startOffset shouldBe 0
        edit.endOffset shouldBe 20
        edit.replacement shouldBe "a..b"
    }

    should("decline the rangeTo fix but still report when a comment is present") {
        val verdict = RangeConventionalDecision.decideRangeToCall(
            callStart = 0,
            callEnd = 20,
            receiverText = "a",
            argumentText = "b",
            hasComment = true,
        )

        verdict.edits shouldBe emptyList()
        verdict.message shouldBe RangeConventionalDecision.RANGE_TO_MESSAGE
    }

    should("replace .. with until and add spaces on both sides when neither existed") {
        val verdict = RangeConventionalDecision.decideUntil(
            rangeStart = 0,
            rangeEnd = 10,
            operatorStart = 1,
            operatorEnd = 3,
            hasLeadingSpace = false,
            hasTrailingSpace = false,
            minusOneStart = 4,
            minusOneEnd = 9,
            leftOperandText = "4",
            hasComment = false,
        )

        verdict.edits.size shouldBe 2
        val opEdit = verdict.edits[0]
        opEdit.startOffset shouldBe 1
        opEdit.endOffset shouldBe 3
        opEdit.replacement shouldBe " until "
        val minusOneEdit = verdict.edits[1]
        minusOneEdit.startOffset shouldBe 4
        minusOneEdit.endOffset shouldBe 9
        minusOneEdit.replacement shouldBe "4"
    }

    should("not add a space that already existed on either side") {
        val verdict = RangeConventionalDecision.decideUntil(
            rangeStart = 0,
            rangeEnd = 10,
            operatorStart = 1,
            operatorEnd = 3,
            hasLeadingSpace = true,
            hasTrailingSpace = true,
            minusOneStart = 4,
            minusOneEnd = 9,
            leftOperandText = "4",
            hasComment = false,
        )

        verdict.edits[0].replacement shouldBe "until"
    }

    should("decline the until fix but still report when a comment is present") {
        val verdict = RangeConventionalDecision.decideUntil(
            rangeStart = 0,
            rangeEnd = 10,
            operatorStart = 1,
            operatorEnd = 3,
            hasLeadingSpace = true,
            hasTrailingSpace = true,
            minusOneStart = 4,
            minusOneEnd = 9,
            leftOperandText = "4",
            hasComment = true,
        )

        verdict.edits shouldBe emptyList()
        verdict.message shouldBe RangeConventionalDecision.UNTIL_MESSAGE
    }
})
