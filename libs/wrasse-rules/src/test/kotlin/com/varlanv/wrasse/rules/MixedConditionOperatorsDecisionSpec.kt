package com.varlanv.wrasse.rules

import com.varlanv.wrasse.testing.BaseSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe

class MixedConditionOperatorsDecisionSpec : BaseSpec({

    should("report a chain using both && and ||") {
        MixedConditionOperatorsDecision.decide(hasAnd = true, hasOr = true) shouldBe
            MixedConditionOperatorsDecision.MESSAGE
    }

    should("not report a chain using only &&") {
        MixedConditionOperatorsDecision.decide(hasAnd = true, hasOr = false) shouldBe null
    }

    should("not report a chain using only ||") {
        MixedConditionOperatorsDecision.decide(hasAnd = false, hasOr = true) shouldBe null
    }

    should("wrap an && child that is a direct operand of an || parent") {
        val edits = MixedConditionOperatorsDecision.wrapEdits(
            parentIsAnd = false,
            childIsAnd = true,
            childStart = 0,
            childEnd = 6,
        )

        edits.size shouldBe 2
        val (open, close) = edits
        open.startOffset shouldBe 0
        open.endOffset shouldBe 0
        open.replacement shouldBe "("
        close.startOffset shouldBe 6
        close.endOffset shouldBe 6
        close.replacement shouldBe ")"
    }

    should("not wrap when the parent is also &&") {
        MixedConditionOperatorsDecision
            .wrapEdits(parentIsAnd = true, childIsAnd = true, childStart = 0, childEnd = 6)
            .shouldBeEmpty()
    }

    should("not wrap when the child is not &&") {
        MixedConditionOperatorsDecision
            .wrapEdits(parentIsAnd = false, childIsAnd = false, childStart = 0, childEnd = 6)
            .shouldBeEmpty()
    }

    should("apply the wrap edits of a && || chain to parenthesize the && sub-chain") {
        val source = "a && b || c"
        val childStart = source.indexOf("a")
        val childEnd = source.indexOf(" || ")

        val edits = MixedConditionOperatorsDecision.wrapEdits(
            parentIsAnd = false,
            childIsAnd = true,
            childStart = childStart,
            childEnd = childEnd,
        )

        val fixed = StringBuilder(source)
        for (edit in edits.sortedByDescending { it.startOffset }) {
            fixed.replace(edit.startOffset, edit.endOffset, edit.replacement)
        }
        fixed.toString() shouldBe "(a && b) || c"
    }
})
