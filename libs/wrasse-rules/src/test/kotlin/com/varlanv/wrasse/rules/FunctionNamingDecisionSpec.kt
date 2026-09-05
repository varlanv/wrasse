package com.varlanv.wrasse.rules

import com.varlanv.wrasse.testing.BaseSpec
import io.kotest.matchers.shouldBe

class FunctionNamingDecisionSpec : BaseSpec({
    should("accept lowerCamelCase") {
        FunctionNamingDecision.decide(
            "foo",
            isOverride = false,
            isFactory = false,
            isTestLibraryImported = false,
        ) shouldBe null
    }

    should("reject PascalCase when not a factory") {
        FunctionNamingDecision.decide(
            "Foo",
            isOverride = false,
            isFactory = false,
            isTestLibraryImported = false,
        ) shouldBe FunctionNamingDecision.MESSAGE
    }

    should("accept PascalCase when it is a factory function") {
        FunctionNamingDecision.decide(
            "Foo",
            isOverride = false,
            isFactory = true,
            isTestLibraryImported = false,
        ) shouldBe null
    }

    should("accept any name when it is an override") {
        FunctionNamingDecision.decide(
            "Foo_Bar",
            isOverride = true,
            isFactory = false,
            isTestLibraryImported = false,
        ) shouldBe null
    }

    should("accept a backtick-wrapped keyword") {
        FunctionNamingDecision.decide(
            "`fun`",
            isOverride = false,
            isFactory = false,
            isTestLibraryImported = false,
        ) shouldBe null
    }

    should("reject a backtick-wrapped non-keyword name outside test code") {
        FunctionNamingDecision.decide(
            "`should do X`",
            isOverride = false,
            isFactory = false,
            isTestLibraryImported = false,
        ) shouldBe FunctionNamingDecision.MESSAGE
    }

    should("accept a backtick-wrapped name in test code") {
        FunctionNamingDecision.decide(
            "`should do X`",
            isOverride = false,
            isFactory = false,
            isTestLibraryImported = true,
        ) shouldBe null
    }

    should("accept an underscore-separated lowercase name in test code") {
        FunctionNamingDecision.decide(
            "should_do_x",
            isOverride = false,
            isFactory = false,
            isTestLibraryImported = true,
        ) shouldBe null
    }

    should("reject an underscore-separated lowercase name outside test code") {
        FunctionNamingDecision.decide(
            "should_do_x",
            isOverride = false,
            isFactory = false,
            isTestLibraryImported = false,
        ) shouldBe FunctionNamingDecision.MESSAGE
    }
})
