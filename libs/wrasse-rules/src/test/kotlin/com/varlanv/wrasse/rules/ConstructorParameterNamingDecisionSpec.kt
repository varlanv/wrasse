package com.varlanv.wrasse.rules

import com.varlanv.wrasse.testing.BaseSpec
import io.kotest.matchers.shouldBe

class ConstructorParameterNamingDecisionSpec : BaseSpec({

    should("not report a lowerCamelCase parameter name") {
        ConstructorParameterNamingDecision.decide("firstName", hasOverride = false) shouldBe null
    }

    should("report a PascalCase parameter name") {
        ConstructorParameterNamingDecision.decide("FirstName", hasOverride = false) shouldBe
            ConstructorParameterNamingDecision.MESSAGE
    }

    should("not report an override, regardless of its casing") {
        ConstructorParameterNamingDecision.decide("FirstName", hasOverride = true) shouldBe null
    }

    should("not report a backtick-wrapped keyword") {
        ConstructorParameterNamingDecision.decide("`class`", hasOverride = false) shouldBe null
    }
})
