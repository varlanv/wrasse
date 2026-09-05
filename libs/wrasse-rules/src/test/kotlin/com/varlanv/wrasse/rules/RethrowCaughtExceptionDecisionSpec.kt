package com.varlanv.wrasse.rules

import com.varlanv.wrasse.testing.BaseSpec
import io.kotest.matchers.shouldBe

class RethrowCaughtExceptionDecisionSpec : BaseSpec({

    should("report every catch when all trivially rethrow") {
        RethrowCaughtExceptionDecision.trailingViolationIndices(listOf(true, true, true)) shouldBe listOf(0, 1, 2)
    }

    should("report only the trailing run when an earlier catch does real work") {
        RethrowCaughtExceptionDecision.trailingViolationIndices(listOf(false, true, true)) shouldBe listOf(1, 2)
    }

    should("report nothing when the last catch does real work, even if an earlier one trivially rethrows") {
        RethrowCaughtExceptionDecision.trailingViolationIndices(listOf(true, false)) shouldBe emptyList()
    }

    should("recompute the trailing run after a real-work catch resets it, not just track the overall maximum") {
        RethrowCaughtExceptionDecision.trailingViolationIndices(listOf(true, true, false, true)) shouldBe listOf(3)
    }

    should("report nothing for an all-real-work try") {
        RethrowCaughtExceptionDecision.trailingViolationIndices(listOf(false, false)) shouldBe emptyList()
    }

    should("report nothing for a try with no catch clauses") {
        RethrowCaughtExceptionDecision.trailingViolationIndices(emptyList()) shouldBe emptyList()
    }
})
