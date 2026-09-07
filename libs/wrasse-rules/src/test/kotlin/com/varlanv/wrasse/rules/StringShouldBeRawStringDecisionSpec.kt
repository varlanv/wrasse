package com.varlanv.wrasse.rules

import com.varlanv.wrasse.testing.BaseSpec
import io.kotest.matchers.shouldBe

class StringShouldBeRawStringDecisionSpec : BaseSpec({

    should("not report at the threshold of two escapes") {
        StringShouldBeRawStringDecision.decide(2) shouldBe null
    }

    should("report with three escapes") {
        StringShouldBeRawStringDecision.decide(3) shouldBe StringShouldBeRawStringDecision.MESSAGE
    }

    should("not report zero escapes") {
        StringShouldBeRawStringDecision.decide(0) shouldBe null
    }

    should("report above a configured threshold") {
        StringShouldBeRawStringDecision.decide(1, threshold = 0) shouldBe StringShouldBeRawStringDecision.MESSAGE
        StringShouldBeRawStringDecision.decide(0, threshold = 0) shouldBe null
    }
})
