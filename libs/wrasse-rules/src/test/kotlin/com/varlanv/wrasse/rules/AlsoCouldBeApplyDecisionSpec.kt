package com.varlanv.wrasse.rules

import com.varlanv.wrasse.testing.BaseSpec
import io.kotest.matchers.shouldBe

class AlsoCouldBeApplyDecisionSpec : BaseSpec({

    should("report an also call with one lambda whose statements are all it-qualified") {
        AlsoCouldBeApplyDecision.decide("also", lambdaCount = 1, statementCount = 2, allItQualified = true) shouldBe
            AlsoCouldBeApplyDecision.MESSAGE
    }

    should("not report when the callee is not also") {
        AlsoCouldBeApplyDecision.decide("apply", lambdaCount = 1, statementCount = 2, allItQualified = true) shouldBe
            null
    }

    should("not report when there is not exactly one lambda argument") {
        AlsoCouldBeApplyDecision.decide("also", lambdaCount = 2, statementCount = 2, allItQualified = true) shouldBe
            null
    }

    should("not report an empty block") {
        AlsoCouldBeApplyDecision.decide("also", lambdaCount = 1, statementCount = 0, allItQualified = true) shouldBe
            null
    }

    should("not report when not every statement is it-qualified") {
        AlsoCouldBeApplyDecision.decide("also", lambdaCount = 1, statementCount = 2, allItQualified = false) shouldBe
            null
    }
})
