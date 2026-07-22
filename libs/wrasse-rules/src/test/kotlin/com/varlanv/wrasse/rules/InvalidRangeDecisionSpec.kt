package com.varlanv.wrasse.rules

import com.varlanv.wrasse.testing.BaseSpec
import io.kotest.matchers.shouldBe

class InvalidRangeDecisionSpec :
    BaseSpec(
        {

            should("report a decreasing .. range") {
                InvalidRangeDecision.decide("..", 2, 1) shouldBe InvalidRangeDecision.MESSAGE
            }

            should("not report an increasing .. range") {
                InvalidRangeDecision.decide("..", 1, 2) shouldBe null
            }

            should("not report an equal .. range") {
                InvalidRangeDecision.decide("..", 2, 2) shouldBe null
            }

            should("report an increasing downTo range") {
                InvalidRangeDecision.decide("downTo", 1, 2) shouldBe InvalidRangeDecision.MESSAGE
            }

            should("not report a decreasing downTo range") {
                InvalidRangeDecision.decide("downTo", 2, 1) shouldBe null
            }

            should("report an equal until range") {
                InvalidRangeDecision.decide("until", 2, 2) shouldBe InvalidRangeDecision.MESSAGE
            }

            should("report a decreasing until range") {
                InvalidRangeDecision.decide("until", 2, 1) shouldBe InvalidRangeDecision.MESSAGE
            }

            should("not report an increasing until range") {
                InvalidRangeDecision.decide("until", 1, 2) shouldBe null
            }

            should("report an equal ..< range") {
                InvalidRangeDecision.decide("..<", 2, 2) shouldBe InvalidRangeDecision.MESSAGE
            }

            should("not report an unrecognized operator") {
                InvalidRangeDecision.decide("step", 2, 1) shouldBe null
            }
        },
    )
