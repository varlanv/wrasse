package com.varlanv.wrasse.rules

import com.varlanv.wrasse.testing.BaseSpec
import io.kotest.matchers.shouldBe

class UseLetDecisionSpec : BaseSpec({

    should("report when the checked branch reduces to null") {
        UseLetDecision.decide("null") shouldBe UseLetDecision.MESSAGE
    }

    should("not report when the checked branch is not null") {
        UseLetDecision.decide("5") shouldBe null
    }

    should("not report an empty branch text") {
        UseLetDecision.decide("") shouldBe null
    }
})
