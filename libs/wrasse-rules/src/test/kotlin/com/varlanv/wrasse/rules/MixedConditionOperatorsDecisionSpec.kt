package com.varlanv.wrasse.rules

import com.varlanv.wrasse.testing.BaseSpec
import io.kotest.matchers.shouldBe

class MixedConditionOperatorsDecisionSpec : BaseSpec({

    should("report a chain using both && and ||") {
        MixedConditionOperatorsDecision.decide(
            hasAnd = true,
            hasOr = true,
        ) shouldBe MixedConditionOperatorsDecision.MESSAGE
    }

    should("not report a chain using only &&") {
        MixedConditionOperatorsDecision.decide(hasAnd = true, hasOr = false) shouldBe null
    }

    should("not report a chain using only ||") {
        MixedConditionOperatorsDecision.decide(hasAnd = false, hasOr = true) shouldBe null
    }
})
