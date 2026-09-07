package com.varlanv.wrasse.rules

import com.varlanv.wrasse.testing.BaseSpec
import io.kotest.matchers.shouldBe

class LoopWithTooManyJumpStatementsDecisionSpec : BaseSpec({

    should("not report zero jumps") {
        LoopWithTooManyJumpStatementsDecision.decide(0) shouldBe null
    }

    should("not report exactly one jump") {
        LoopWithTooManyJumpStatementsDecision.decide(1) shouldBe null
    }

    should("report more than one jump, with the count inlined") {
        LoopWithTooManyJumpStatementsDecision.decide(
            2,
        ) shouldBe "The loop contains 2 break or continue statements; the maximum allowed is 1"
    }

    should("report just above a configured threshold") {
        LoopWithTooManyJumpStatementsDecision.decide(
            1,
            threshold = 0,
        ) shouldBe "The loop contains 1 break or continue statements; the maximum allowed is 0"
        LoopWithTooManyJumpStatementsDecision.decide(0, threshold = 0) shouldBe null
    }
})
