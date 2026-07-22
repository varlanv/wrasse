package com.varlanv.wrasse.rules

import com.varlanv.wrasse.model.WNodeType
import com.varlanv.wrasse.testing.BaseSpec
import io.kotest.matchers.shouldBe

class FunctionExpressionBodyDecisionSpec :
    BaseSpec(
        {

            should("report a block containing only a single return statement") {
                FunctionExpressionBodyDecision.decide(WNodeType.RETURN, returnKeywordCount = 1) shouldBe
                    FunctionExpressionBodyDecision.MESSAGE
            }

            should("report a block containing only a single throw statement") {
                FunctionExpressionBodyDecision.decide(WNodeType.THROW, returnKeywordCount = 0) shouldBe
                    FunctionExpressionBodyDecision.MESSAGE
            }

            should("not report a return statement whose own expression contains a nested return") {
                FunctionExpressionBodyDecision.decide(WNodeType.RETURN, returnKeywordCount = 2) shouldBe null
            }

            should("not report a block whose sole child is neither return nor throw") {
                FunctionExpressionBodyDecision.decide(WNodeType.PROPERTY, returnKeywordCount = 0) shouldBe null
            }

            should("not report an empty block") {
                FunctionExpressionBodyDecision.decide(null, returnKeywordCount = 0) shouldBe null
            }
        },
    )
