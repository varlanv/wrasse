package com.varlanv.wrasse.rules

import com.varlanv.wrasse.testing.BaseSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

class TrivialAccessorsDecisionSpec : BaseSpec({

    should("not report at all when the body isn't trivial") {
        TrivialAccessorsDecision.decide(isTrivialBody = false, hasModifierList = false) shouldBe null
    }

    should("not report at all when the body isn't trivial even if a modifier list is present") {
        TrivialAccessorsDecision.decide(isTrivialBody = false, hasModifierList = true) shouldBe null
    }

    should("report and fix a trivial body with no modifier list") {
        val verdict = TrivialAccessorsDecision.decide(isTrivialBody = true, hasModifierList = false)

        verdict shouldNotBe null
        verdict!!.fixable shouldBe true
    }

    should("report but decline the fix when a modifier list is present") {
        val verdict = TrivialAccessorsDecision.decide(isTrivialBody = true, hasModifierList = true)

        verdict shouldNotBe null
        verdict!!.fixable shouldBe false
    }
})
