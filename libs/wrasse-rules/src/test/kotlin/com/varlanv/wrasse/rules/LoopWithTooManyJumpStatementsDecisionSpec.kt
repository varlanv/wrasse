package com.varlanv.wrasse.rules

import com.varlanv.wrasse.testing.BaseSpec
import io.kotest.matchers.shouldBe

class LoopWithTooManyJumpStatementsDecisionSpec :
    BaseSpec(
        {

            should("not report zero jumps") {
                LoopWithTooManyJumpStatementsDecision.decide(0) shouldBe null
            }

            should("not report exactly one jump") {
                LoopWithTooManyJumpStatementsDecision.decide(1) shouldBe null
            }

            should("report more than one jump, with the count inlined") {
                LoopWithTooManyJumpStatementsDecision.decide(2) shouldBe
                    "The loop contains more than one break or continue statement (found 2); the code should be " +
                    "refactored to increase readability"
            }
        },
    )
