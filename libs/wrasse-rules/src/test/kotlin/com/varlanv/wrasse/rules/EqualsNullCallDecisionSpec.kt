package com.varlanv.wrasse.rules

import com.varlanv.wrasse.testing.BaseSpec
import io.kotest.matchers.shouldBe

class EqualsNullCallDecisionSpec : BaseSpec({

    should("report a call to equals with a single null argument") {
        EqualsNullCallDecision.decide("equals", "null") shouldBe EqualsNullCallDecision.MESSAGE
    }

    should("not report when the callee is not equals") {
        EqualsNullCallDecision.decide("contentEquals", "null") shouldBe null
    }

    should("not report when the single argument is not null") {
        EqualsNullCallDecision.decide("equals", "\"x\"") shouldBe null
    }

    should("not report when there is no single argument") {
        EqualsNullCallDecision.decide("equals", null) shouldBe null
    }
})
