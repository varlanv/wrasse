package com.varlanv.wrasse.rules

import com.varlanv.wrasse.testing.BaseSpec
import io.kotest.matchers.shouldBe

class SwallowedExceptionDecisionSpec : BaseSpec({

    should("report an unreferenced caught exception") {
        SwallowedExceptionDecision.decide(
            "Exception",
            "e",
            isReferenced = false,
            hasBodyContent = true,
        ) shouldBe SwallowedExceptionDecision.MESSAGE
    }

    should("not report a referenced caught exception") {
        SwallowedExceptionDecision.decide("Exception", "e", isReferenced = true, hasBodyContent = true) shouldBe null
    }

    should("not report when the type is one of the ignored exception types") {
        SwallowedExceptionDecision.decide(
            "NumberFormatException",
            "e",
            isReferenced = false,
            hasBodyContent = true,
        ) shouldBe null
    }

    should("not report when the type merely contains an ignored exception type's name") {
        SwallowedExceptionDecision.decide(
            "MyNumberFormatExceptionWrapper",
            "e",
            isReferenced = false,
            hasBodyContent = true,
        ) shouldBe null
    }

    should("not report when the parameter name is an allowed exemption") {
        SwallowedExceptionDecision.decide(
            "Exception",
            "ignored",
            isReferenced = false,
            hasBodyContent = true,
        ) shouldBe null
    }

    should("not report when the catch body has no content beyond whitespace or comments") {
        SwallowedExceptionDecision.decide("Exception", "e", isReferenced = false, hasBodyContent = false) shouldBe null
    }

    should("recognize the parameter name itself as a usage") {
        SwallowedExceptionDecision.isUsageText("e", "e") shouldBe true
    }

    should("recognize an ignored exception type name as a usage") {
        SwallowedExceptionDecision.isUsageText("ParseException", "e") shouldBe true
    }

    should("not recognize an unrelated identifier as a usage") {
        SwallowedExceptionDecision.isUsageText("other", "e") shouldBe false
    }
})
