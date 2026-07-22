package com.varlanv.wrasse.rules

import com.varlanv.wrasse.testing.BaseSpec
import io.kotest.matchers.shouldBe

class LambdaReturnDecisionSpec :
    BaseSpec(
        {

            should("report a labeled return whose label names its own lambda") {
                LambdaReturnDecision.decide(labelMatchesOwnLambda = true) shouldBe LambdaReturnDecision.MESSAGE
            }

            should("not report a labeled return whose label names an outer scope") {
                LambdaReturnDecision.decide(labelMatchesOwnLambda = false) shouldBe null
            }
        },
    )
