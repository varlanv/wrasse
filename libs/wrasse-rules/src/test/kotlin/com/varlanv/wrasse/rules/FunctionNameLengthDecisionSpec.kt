package com.varlanv.wrasse.rules

import com.varlanv.wrasse.testing.BaseSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

class FunctionNameLengthDecisionSpec : BaseSpec({

    should("not report a 3-character name (the minimum) for the min-length check") {
        FunctionNameLengthDecision.decideMin("abc", isOverride = false, isOperator = false) shouldBe null
    }

    should("report a 2-character name for the min-length check") {
        FunctionNameLengthDecision.decideMin("ab", isOverride = false, isOperator = false) shouldNotBe null
    }

    should("not report a short override or operator name") {
        FunctionNameLengthDecision.decideMin("ab", isOverride = true, isOperator = false) shouldBe null
        FunctionNameLengthDecision.decideMin("ab", isOverride = false, isOperator = true) shouldBe null
    }

    should("not report a 30-character name (the maximum) for the max-length check") {
        val name = "a".repeat(30)
        FunctionNameLengthDecision.decideMax(name, isOverride = false, isOperator = false) shouldBe null
    }

    should("report a 31-character name for the max-length check") {
        val name = "a".repeat(31)
        FunctionNameLengthDecision.decideMax(name, isOverride = false, isOperator = false) shouldNotBe null
    }

    should("not report a long override or operator name") {
        val name = "a".repeat(31)
        FunctionNameLengthDecision.decideMax(name, isOverride = true, isOperator = false) shouldBe null
        FunctionNameLengthDecision.decideMax(name, isOverride = false, isOperator = true) shouldBe null
    }
})
