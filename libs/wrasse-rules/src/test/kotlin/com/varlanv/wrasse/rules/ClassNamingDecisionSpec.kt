package com.varlanv.wrasse.rules

import com.varlanv.wrasse.testing.BaseSpec
import io.kotest.matchers.shouldBe

class ClassNamingDecisionSpec : BaseSpec({
    should("accept PascalCase") {
        ClassNamingDecision.decide("Foo", isJUnitJupiterImported = false) shouldBe null
    }

    should("accept PascalCase with digits") {
        ClassNamingDecision.decide("Foo2Bar", isJUnitJupiterImported = false) shouldBe null
    }

    should("reject lowercase-first name") {
        ClassNamingDecision.decide("foo", isJUnitJupiterImported = false) shouldBe ClassNamingDecision.MESSAGE
    }

    should("reject underscore-separated name") {
        ClassNamingDecision.decide("Foo_Bar", isJUnitJupiterImported = false) shouldBe ClassNamingDecision.MESSAGE
    }

    should("accept a backtick-wrapped keyword regardless of test-import status") {
        ClassNamingDecision.decide("`class`", isJUnitJupiterImported = false) shouldBe null
    }

    should("reject a backtick-wrapped non-keyword name outside test code") {
        ClassNamingDecision.decide(
            "`some weird name`",
            isJUnitJupiterImported = false,
        ) shouldBe ClassNamingDecision.MESSAGE
    }

    should("accept a backtick-wrapped non-keyword name when JUnit Jupiter is imported") {
        ClassNamingDecision.decide("`some weird name`", isJUnitJupiterImported = true) shouldBe null
    }
})
