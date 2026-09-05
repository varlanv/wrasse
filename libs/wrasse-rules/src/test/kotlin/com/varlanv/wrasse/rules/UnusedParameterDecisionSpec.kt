package com.varlanv.wrasse.rules

import com.varlanv.wrasse.testing.BaseSpec
import io.kotest.matchers.shouldBe

class UnusedParameterDecisionSpec : BaseSpec({

    should("report an unused parameter") {
        UnusedParameterDecision.decide(
            functionExempt = false,
            parameterName = "unused",
            wasUsed = false,
        ) shouldBe "Function parameter 'unused' is unused"
    }

    should("not report when the function is exempt") {
        UnusedParameterDecision.decide(functionExempt = true, parameterName = "unused", wasUsed = false) shouldBe null
    }

    should("not report when the parameter was used") {
        UnusedParameterDecision.decide(functionExempt = false, parameterName = "unused", wasUsed = true) shouldBe null
    }

    should("not report a name matching the allowed-names pattern") {
        UnusedParameterDecision.decide(functionExempt = false, parameterName = "ignored", wasUsed = false) shouldBe null
        UnusedParameterDecision.decide(
            functionExempt = false,
            parameterName = "expected",
            wasUsed = false,
        ) shouldBe null
    }
})
