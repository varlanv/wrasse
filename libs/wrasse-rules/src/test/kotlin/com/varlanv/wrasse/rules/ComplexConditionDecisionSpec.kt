package com.varlanv.wrasse.rules

import com.varlanv.wrasse.testing.BaseSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

class ComplexConditionDecisionSpec : BaseSpec({

    should("not report a condition with a single operator") {
        ComplexConditionDecision.decide("a && b") shouldBe null
    }

    should("not report a condition with two operators") {
        ComplexConditionDecision.decide("a && b || c") shouldBe null
    }

    should("report a condition with three operators") {
        ComplexConditionDecision.decide("a && b && c && d") shouldNotBe null
    }

    should("count both && and || toward the total") {
        ComplexConditionDecision.decide("a && b || c && d") shouldNotBe null
    }

    should("not report a condition with no operators") {
        ComplexConditionDecision.decide("a") shouldBe null
    }

    should("not match a false-positive shared character run like &&&") {
        ComplexConditionDecision.decide("a &&& b") shouldBe null
    }

    should("report the exact message with the operator count") {
        ComplexConditionDecision.decide(
            "a && b && c && d",
        ) shouldBe "This condition combines 3 boolean operators; the maximum allowed is 2"
    }
})
