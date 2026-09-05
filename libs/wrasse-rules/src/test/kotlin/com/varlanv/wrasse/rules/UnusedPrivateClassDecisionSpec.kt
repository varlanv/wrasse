package com.varlanv.wrasse.rules

import com.varlanv.wrasse.testing.BaseSpec
import io.kotest.matchers.shouldBe

class UnusedPrivateClassDecisionSpec : BaseSpec({

    should("report an unused private class") {
        UnusedPrivateClassDecision.decide(
            isPrivate = true,
            isUsed = false,
            className = "Unused",
        ) shouldBe "Private class 'Unused' is unused"
    }

    should("not report a non-private class") {
        UnusedPrivateClassDecision.decide(isPrivate = false, isUsed = false, className = "Public") shouldBe null
    }

    should("not report a used private class") {
        UnusedPrivateClassDecision.decide(isPrivate = true, isUsed = true, className = "Used") shouldBe null
    }
})
