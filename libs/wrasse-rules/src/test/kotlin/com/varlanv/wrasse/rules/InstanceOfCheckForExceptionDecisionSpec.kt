package com.varlanv.wrasse.rules

import com.varlanv.wrasse.testing.BaseSpec
import io.kotest.matchers.shouldBe

class InstanceOfCheckForExceptionDecisionSpec :
    BaseSpec(
        {

            should("report an ordinary checked type") {
                InstanceOfCheckForExceptionDecision.decide("MyException") shouldBe InstanceOfCheckForExceptionDecision.MESSAGE
            }

            should("not report a CancellationException check") {
                InstanceOfCheckForExceptionDecision.decide("CancellationException") shouldBe null
            }

            should("not report a qualified CancellationException check") {
                InstanceOfCheckForExceptionDecision.decide("kotlinx.coroutines.CancellationException") shouldBe null
            }
        },
    )
