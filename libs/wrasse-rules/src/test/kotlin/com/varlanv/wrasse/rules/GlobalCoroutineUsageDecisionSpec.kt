package com.varlanv.wrasse.rules

import com.varlanv.wrasse.testing.BaseSpec
import io.kotest.matchers.shouldBe

class GlobalCoroutineUsageDecisionSpec :
    BaseSpec(
        {

            should("report GlobalScope.launch") {
                GlobalCoroutineUsageDecision.decide("GlobalScope", "launch") shouldBe GlobalCoroutineUsageDecision.MESSAGE
            }

            should("report GlobalScope.async") {
                GlobalCoroutineUsageDecision.decide("GlobalScope", "async") shouldBe GlobalCoroutineUsageDecision.MESSAGE
            }

            should("not report a different receiver") {
                GlobalCoroutineUsageDecision.decide("myScope", "launch") shouldBe null
            }

            should("not report a different callee on GlobalScope") {
                GlobalCoroutineUsageDecision.decide("GlobalScope", "cancel") shouldBe null
            }

            should("not report when no callee was found") {
                GlobalCoroutineUsageDecision.decide("GlobalScope", null) shouldBe null
            }
        },
    )
