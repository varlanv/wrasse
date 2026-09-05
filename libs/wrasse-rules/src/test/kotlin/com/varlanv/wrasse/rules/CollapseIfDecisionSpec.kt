package com.varlanv.wrasse.rules

import com.varlanv.wrasse.testing.BaseSpec
import io.kotest.matchers.shouldBe

class CollapseIfDecisionSpec : BaseSpec({

    should("report a nested if when neither level has an else") {
        CollapseIfDecision.decide(outerHasElse = false, innerHasElse = false) shouldBe CollapseIfDecision.MESSAGE
    }

    should("not report when the outer if has an else") {
        CollapseIfDecision.decide(outerHasElse = true, innerHasElse = false) shouldBe null
    }

    should("not report when the inner if has an else") {
        CollapseIfDecision.decide(outerHasElse = false, innerHasElse = true) shouldBe null
    }

    should("not report when both levels have an else") {
        CollapseIfDecision.decide(outerHasElse = true, innerHasElse = true) shouldBe null
    }
})
