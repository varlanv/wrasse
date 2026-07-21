package com.varlanv.wrasse.rules

import com.varlanv.wrasse.testing.BaseSpec
import io.kotest.matchers.shouldBe

class ExplicitItLambdaMultipleParametersDecisionSpec :
    BaseSpec(
        {

            should("report a multi-parameter lambda naming one parameter it") {
                ExplicitItLambdaMultipleParametersDecision.decide(parameterCount = 2, hasItParameter = true) shouldBe
                    ExplicitItLambdaMultipleParametersDecision.MESSAGE
            }

            should("not report a single-parameter lambda even if named it") {
                ExplicitItLambdaMultipleParametersDecision.decide(parameterCount = 1, hasItParameter = true) shouldBe null
            }

            should("not report a multi-parameter lambda with no it parameter") {
                ExplicitItLambdaMultipleParametersDecision.decide(parameterCount = 2, hasItParameter = false) shouldBe null
            }

            should("not report a zero-parameter lambda") {
                ExplicitItLambdaMultipleParametersDecision.decide(parameterCount = 0, hasItParameter = false) shouldBe null
            }
        },
    )
