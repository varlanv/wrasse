package com.varlanv.wrasse.rules

import com.varlanv.wrasse.testing.BaseSpec
import io.kotest.matchers.shouldBe

class DestructuringTooManyEntriesDecisionSpec :
    BaseSpec(
        {

            should("not report at the threshold") {
                DestructuringTooManyEntriesDecision.decide(3) shouldBe null
            }

            should("not report below the threshold") {
                DestructuringTooManyEntriesDecision.decide(1) shouldBe null
                DestructuringTooManyEntriesDecision.decide(2) shouldBe null
            }

            should("report just above the threshold") {
                DestructuringTooManyEntriesDecision.decide(4) shouldBe "Destructuring declaration has 4 entries; the maximum allowed is 3"
            }

            should("report well above the threshold") {
                DestructuringTooManyEntriesDecision.decide(8) shouldBe "Destructuring declaration has 8 entries; the maximum allowed is 3"
            }
        },
    )
