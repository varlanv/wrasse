package com.varlanv.wrasse.rules

import com.varlanv.wrasse.testing.BaseSpec
import io.kotest.matchers.shouldBe

class StringShouldBeRawStringDecisionSpec :
    BaseSpec(
        {

            should("not report at the threshold of two escapes") {
                StringShouldBeRawStringDecision.decide(2) shouldBe null
            }

            should("report with three escapes") {
                StringShouldBeRawStringDecision.decide(3) shouldBe StringShouldBeRawStringDecision.MESSAGE
            }

            should("not report zero escapes") {
                StringShouldBeRawStringDecision.decide(0) shouldBe null
            }
        },
    )
