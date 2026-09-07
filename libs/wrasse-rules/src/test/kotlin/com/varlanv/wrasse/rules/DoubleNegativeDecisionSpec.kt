package com.varlanv.wrasse.rules

import com.varlanv.wrasse.testing.BaseSpec
import io.kotest.matchers.shouldBe

class DoubleNegativeDecisionSpec : BaseSpec({

    should("not report a single negation") {
        DoubleNegativeDecision.decide(1) shouldBe null
    }

    should("report a chain of two or more") {
        DoubleNegativeDecision.decide(2) shouldBe DoubleNegativeDecision.MESSAGE
        DoubleNegativeDecision.decide(3) shouldBe DoubleNegativeDecision.MESSAGE
    }

    should("replace an even-depth chain with the operand text alone") {
        val edits = DoubleNegativeDecision.editsFor(2, 0, 9, "isValid")

        edits.size shouldBe 1
        val edit = edits.single()
        edit.startOffset shouldBe 0
        edit.endOffset shouldBe 9
        edit.replacement shouldBe "isValid"
    }

    should("keep the operand's own parentheses on an even-depth chain") {
        val edits = DoubleNegativeDecision.editsFor(2, 0, 12, "(a && b)")

        edits.single().replacement shouldBe "(a && b)"
    }

    should("replace an odd-depth chain with a single leading exclamation") {
        val edits = DoubleNegativeDecision.editsFor(3, 0, 10, "isValid")

        edits.single().replacement shouldBe "!isValid"
    }

    should("splice an even-depth replacement into a real source string") {
        val source = "!!isValid"

        val edits = DoubleNegativeDecision.editsFor(2, 0, source.length, "isValid")
        val edit = edits.single()
        val fixed = source.substring(0, edit.startOffset) + edit.replacement + source.substring(edit.endOffset)

        fixed shouldBe "isValid"
    }

    should("splice an odd-depth replacement into a real source string") {
        val source = "!!!isValid"

        val edits = DoubleNegativeDecision.editsFor(3, 0, source.length, "isValid")
        val edit = edits.single()
        val fixed = source.substring(0, edit.startOffset) + edit.replacement + source.substring(edit.endOffset)

        fixed shouldBe "!isValid"
    }
})
