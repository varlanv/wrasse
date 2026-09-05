package com.varlanv.wrasse.rules

import com.varlanv.wrasse.testing.BaseSpec
import io.kotest.matchers.shouldBe

class UselessPostfixExpressionDecisionSpec : BaseSpec({

    should("classify ++ as increment or decrement") {
        UselessPostfixExpressionDecision.isIncrementOrDecrement("++") shouldBe true
    }

    should("classify -- as increment or decrement") {
        UselessPostfixExpressionDecision.isIncrementOrDecrement("--") shouldBe true
    }

    should("not classify !! as increment or decrement") {
        UselessPostfixExpressionDecision.isIncrementOrDecrement("!!") shouldBe false
    }

    should("report i = i++ style self-reference") {
        UselessPostfixExpressionDecision.decide(
            isIncrementOrDecrement = true,
            baseText = "i",
            otherOperandText = "i",
            postfixText = "i++",
        ) shouldBe "The result of the postfix expression 'i++' will not be used and is therefore useless"
    }

    should("not report when the base text differs from the other operand") {
        UselessPostfixExpressionDecision.decide(
            isIncrementOrDecrement = true,
            baseText = "i",
            otherOperandText = "j",
            postfixText = "i++",
        ) shouldBe null
    }

    should("not report a non-increment-decrement postfix even with matching text") {
        UselessPostfixExpressionDecision.decide(
            isIncrementOrDecrement = false,
            baseText = "i",
            otherOperandText = "i",
            postfixText = "i!!",
        ) shouldBe null
    }
})
