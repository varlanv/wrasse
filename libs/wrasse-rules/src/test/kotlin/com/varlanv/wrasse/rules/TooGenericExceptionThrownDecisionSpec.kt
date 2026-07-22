package com.varlanv.wrasse.rules

import com.varlanv.wrasse.testing.BaseSpec
import io.kotest.matchers.shouldBe

class TooGenericExceptionThrownDecisionSpec :
    BaseSpec(
        {

            should("report throwing a bare Exception") {
                TooGenericExceptionThrownDecision.decide("Exception") shouldBe
                    "Exception is a too generic Exception. Prefer throwing specific exceptions that indicate a specific error case."
            }

            should("not report throwing a specific exception type") {
                TooGenericExceptionThrownDecision.decide("IllegalArgumentException") shouldBe null
            }

            should("not report a caught-exception-catch generic type not shared by the thrown set") {
                TooGenericExceptionThrownDecision.decide("NullPointerException") shouldBe null
            }
        },
    )
