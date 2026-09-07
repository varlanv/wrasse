package com.varlanv.wrasse.rules

import com.varlanv.wrasse.testing.BaseSpec
import io.kotest.matchers.shouldBe

class ExceptionRaisedInUnexpectedLocationDecisionSpec : BaseSpec({

    should("report toString throwing") {
        ExceptionRaisedInUnexpectedLocationDecision.decide("toString", hasThrow = true) shouldBe
            ExceptionRaisedInUnexpectedLocationDecision.MESSAGE
    }

    should("report equals, hashCode, and finalize throwing") {
        ExceptionRaisedInUnexpectedLocationDecision.decide("equals", hasThrow = true) shouldBe
            ExceptionRaisedInUnexpectedLocationDecision.MESSAGE
        ExceptionRaisedInUnexpectedLocationDecision.decide("hashCode", hasThrow = true) shouldBe
            ExceptionRaisedInUnexpectedLocationDecision.MESSAGE
        ExceptionRaisedInUnexpectedLocationDecision.decide("finalize", hasThrow = true) shouldBe
            ExceptionRaisedInUnexpectedLocationDecision.MESSAGE
    }

    should("not report an unrelated function name throwing") {
        ExceptionRaisedInUnexpectedLocationDecision.decide("compute", hasThrow = true) shouldBe null
    }

    should("not report a candidate function name that never throws") {
        ExceptionRaisedInUnexpectedLocationDecision.decide("toString", hasThrow = false) shouldBe null
    }
})
