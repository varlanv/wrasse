package com.varlanv.wrasse.rules

import com.varlanv.wrasse.testing.BaseSpec
import io.kotest.matchers.shouldBe

class UnconditionalJumpDecisionSpec : BaseSpec({

    should("always report a bare break") {
        UnconditionalJumpDecision.decideBreak() shouldBe UnconditionalJumpDecision.MESSAGE
    }

    should("report a bare return") {
        UnconditionalJumpDecision.decideReturn("return") shouldBe UnconditionalJumpDecision.MESSAGE
    }

    should("report a return of a plain value") {
        UnconditionalJumpDecision.decideReturn("return compute()") shouldBe UnconditionalJumpDecision.MESSAGE
    }

    should("not report a return whose value is an elvis-break fallback") {
        UnconditionalJumpDecision.decideReturn("return compute() ?: break") shouldBe null
    }

    should("not report a return whose value is an elvis-continue fallback") {
        UnconditionalJumpDecision.decideReturn("return compute() ?: continue") shouldBe null
    }
})
