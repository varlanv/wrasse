package com.varlanv.wrasse.rules

import com.varlanv.wrasse.testing.BaseSpec
import io.kotest.matchers.shouldBe

class VariableNameMaxLengthDecisionSpec : BaseSpec({

    should("not report a name at exactly the maximum length") {
        val name = "a".repeat(64)

        VariableNameMaxLengthDecision.decide(name, hasOverride = false) shouldBe null
    }

    should("report a name over the maximum length") {
        val name = "a".repeat(65)

        VariableNameMaxLengthDecision.decide(name, hasOverride = false) shouldBe
            "Variable name should be at most 64 characters long"
    }

    should("not report an override, regardless of length") {
        val name = "a".repeat(100)

        VariableNameMaxLengthDecision.decide(name, hasOverride = true) shouldBe null
    }

    should("report a name over a configured threshold") {
        val name = "a".repeat(11)

        VariableNameMaxLengthDecision.decide(name, hasOverride = false, threshold = 10) shouldBe
            "Variable name should be at most 10 characters long"
        VariableNameMaxLengthDecision.decide("a".repeat(10), hasOverride = false, threshold = 10) shouldBe null
    }
})
