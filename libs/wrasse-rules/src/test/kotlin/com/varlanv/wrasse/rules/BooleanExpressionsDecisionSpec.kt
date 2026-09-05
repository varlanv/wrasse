package com.varlanv.wrasse.rules

import com.varlanv.wrasse.model.WNodeType
import com.varlanv.wrasse.testing.BaseSpec
import io.kotest.matchers.shouldBe

class BooleanExpressionsDecisionSpec : BaseSpec({

    should("detect a bare true/false literal operand") {
        BooleanExpressionsDecision.isLiteralAbsorption(
            WNodeType.BOOLEAN_CONSTANT,
            "false",
            WNodeType.REFERENCE_EXPRESSION,
            "x",
        ) shouldBe true
    }

    should("detect a bare literal on the right operand") {
        BooleanExpressionsDecision.isLiteralAbsorption(
            WNodeType.REFERENCE_EXPRESSION,
            "x",
            WNodeType.BOOLEAN_CONSTANT,
            "true",
        ) shouldBe true
    }

    should("not detect literal absorption with no boolean-constant operand") {
        BooleanExpressionsDecision.isLiteralAbsorption(
            WNodeType.REFERENCE_EXPRESSION,
            "x",
            WNodeType.REFERENCE_EXPRESSION,
            "y",
        ) shouldBe false
    }

    should("report a literal-absorption condition") {
        BooleanExpressionsDecision.decide(
            isAndOrOperator = true,
            isLiteralAbsorption = true,
            isDirectComplementPair = false,
        ) shouldBe BooleanExpressionsDecision.MESSAGE
    }

    should("report a direct complement pair") {
        BooleanExpressionsDecision.decide(
            isAndOrOperator = true,
            isLiteralAbsorption = false,
            isDirectComplementPair = true,
        ) shouldBe BooleanExpressionsDecision.MESSAGE
    }

    should("not report when the operator is neither && nor ||") {
        BooleanExpressionsDecision.decide(
            isAndOrOperator = false,
            isLiteralAbsorption = true,
            isDirectComplementPair = true,
        ) shouldBe null
    }

    should("not report when neither shape matches") {
        BooleanExpressionsDecision.decide(
            isAndOrOperator = true,
            isLiteralAbsorption = false,
            isDirectComplementPair = false,
        ) shouldBe null
    }
})
