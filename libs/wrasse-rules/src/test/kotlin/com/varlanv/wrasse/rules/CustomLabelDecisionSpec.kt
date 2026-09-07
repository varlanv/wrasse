package com.varlanv.wrasse.rules

import com.varlanv.wrasse.testing.BaseSpec
import io.kotest.matchers.shouldBe

class CustomLabelDecisionSpec : BaseSpec({

    should("report a custom label with exactly one enclosing loop") {
        CustomLabelDecision.decide("@qq", matchesEnclosingCallName = false, enclosingLoopOrForEachCount = 1) shouldBe
            "Custom label @qq is unnecessary; there is no nested loop or forEach for it to disambiguate"
    }

    should("not report when nesting genuinely needs the label") {
        CustomLabelDecision.decide("@qq", matchesEnclosingCallName = false, enclosingLoopOrForEachCount = 2) shouldBe
            null
    }

    should("not report when there is no enclosing loop or forEach at all") {
        CustomLabelDecision.decide("@qq", matchesEnclosingCallName = false, enclosingLoopOrForEachCount = 0) shouldBe
            null
    }

    should("never report the conventional loop label") {
        CustomLabelDecision.decide("@loop", matchesEnclosingCallName = false, enclosingLoopOrForEachCount = 1) shouldBe
            null
    }

    should("never report a label matching its own enclosing call's name, regardless of which call") {
        CustomLabelDecision.decide(
            "@forEach",
            matchesEnclosingCallName = true,
            enclosingLoopOrForEachCount = 1,
        ) shouldBe null
        CustomLabelDecision.decide(
            "@runCatching",
            matchesEnclosingCallName = true,
            enclosingLoopOrForEachCount = 1,
        ) shouldBe null
    }
})
