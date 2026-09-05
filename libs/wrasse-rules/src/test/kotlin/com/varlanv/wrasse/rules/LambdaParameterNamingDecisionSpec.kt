package com.varlanv.wrasse.rules

import com.varlanv.wrasse.testing.BaseSpec
import io.kotest.matchers.shouldBe

class LambdaParameterNamingDecisionSpec : BaseSpec({

    should("not report a lowerCamelCase parameter") {
        LambdaParameterNamingDecision.decide("userName") shouldBe null
    }

    should("report a PascalCase parameter") {
        LambdaParameterNamingDecision.decide("UserName") shouldBe LambdaParameterNamingDecision.MESSAGE
    }

    should("not report a lone underscore") {
        LambdaParameterNamingDecision.decide("_") shouldBe null
    }

    should("not report a backtick-wrapped keyword") {
        LambdaParameterNamingDecision.decide("`in`") shouldBe null
    }

    should("report a backtick-wrapped non-keyword that is badly cased") {
        LambdaParameterNamingDecision.decide("`Bad`") shouldBe LambdaParameterNamingDecision.MESSAGE
    }
})
