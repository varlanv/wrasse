package com.varlanv.wrasse.rules

import com.varlanv.wrasse.testing.BaseSpec
import io.kotest.matchers.shouldBe

class DoubleNegativeDecisionSpec :
    BaseSpec(
        {

            should("count a single exclamation as a chain of one") {
                DoubleNegativeDecision.exclamationChainLength("!isValid", 0, 8) shouldBe 1
            }

            should("count two adjacent exclamations as a chain of two") {
                DoubleNegativeDecision.exclamationChainLength("!!isValid", 0, 9) shouldBe 2
            }

            should("count exclamations separated by whitespace") {
                DoubleNegativeDecision.exclamationChainLength("! ! isValid", 0, 11) shouldBe 2
            }

            should("count three chained exclamations") {
                DoubleNegativeDecision.exclamationChainLength("!!!isValid", 0, 10) shouldBe 3
            }

            should("not report a single negation") {
                DoubleNegativeDecision.decide(1) shouldBe null
            }

            should("report a chain of two or more") {
                DoubleNegativeDecision.decide(2) shouldBe DoubleNegativeDecision.MESSAGE
            }
        },
    )
