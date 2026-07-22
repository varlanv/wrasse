package com.varlanv.wrasse.rules

import com.varlanv.wrasse.testing.BaseSpec
import io.kotest.matchers.shouldBe

class TooGenericExceptionCaughtDecisionSpec :
    BaseSpec(
        {

            should("report catching Exception") {
                TooGenericExceptionCaughtDecision.decide("Exception", "e") shouldBe TooGenericExceptionCaughtDecision.MESSAGE
            }

            should("report catching Throwable") {
                TooGenericExceptionCaughtDecision.decide("Throwable", "t") shouldBe TooGenericExceptionCaughtDecision.MESSAGE
            }

            should("not report catching a specific exception type") {
                TooGenericExceptionCaughtDecision.decide("IOException", "e") shouldBe null
            }

            should("not report when the parameter name is an allowed exemption") {
                TooGenericExceptionCaughtDecision.decide("Exception", "ignored") shouldBe null
                TooGenericExceptionCaughtDecision.decide("Exception", "_") shouldBe null
            }
        },
    )
