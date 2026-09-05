package com.varlanv.wrasse.rules

import com.varlanv.wrasse.testing.BaseSpec
import io.kotest.matchers.shouldBe

class SafeCastDecisionSpec : BaseSpec({

    should("report a non-negated is-check whose then branch is the identifier and else is null") {
        SafeCastDecision.decide(
            "number",
            negated = false,
            thenText = "number",
            elseText = "null",
        ) shouldBe SafeCastDecision.MESSAGE
    }

    should("report a negated is-check whose else branch is the identifier and then is null") {
        SafeCastDecision.decide(
            "number",
            negated = true,
            thenText = "null",
            elseText = "number",
        ) shouldBe SafeCastDecision.MESSAGE
    }

    should("not report a non-negated is-check whose then branch is not the identifier") {
        SafeCastDecision.decide(
            "number",
            negated = false,
            thenText = "number.toString()",
            elseText = "null",
        ) shouldBe null
    }

    should("not report a non-negated is-check whose else branch is not null") {
        SafeCastDecision.decide("number", negated = false, thenText = "number", elseText = "5") shouldBe null
    }

    should("not report a negated is-check whose then branch is not null") {
        SafeCastDecision.decide("number", negated = true, thenText = "number", elseText = "number") shouldBe null
    }
})
