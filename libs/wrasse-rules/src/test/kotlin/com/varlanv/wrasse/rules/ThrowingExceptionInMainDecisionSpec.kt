package com.varlanv.wrasse.rules

import com.varlanv.wrasse.testing.BaseSpec
import io.kotest.matchers.shouldBe

class ThrowingExceptionInMainDecisionSpec :
    BaseSpec(
        {

            should("report a top-level public main function with a throw") {
                ThrowingExceptionInMainDecision
                        .decide(
                            name = "main",
                            isTopLevel = true,
                            isOverride = false,
                            hasNonPublicVisibility = false,
                            paramCount = 1,
                            hasThrow = true,
                        ) shouldBe
                    ThrowingExceptionInMainDecision.MESSAGE
            }

            should("not report a differently named function") {
                ThrowingExceptionInMainDecision
                        .decide(
                            name = "run",
                            isTopLevel = true,
                            isOverride = false,
                            hasNonPublicVisibility = false,
                            paramCount = 0,
                            hasThrow = true,
                        ) shouldBe
                    null
            }

            should("not report a member function named main") {
                ThrowingExceptionInMainDecision
                        .decide(
                            name = "main",
                            isTopLevel = false,
                            isOverride = false,
                            hasNonPublicVisibility = false,
                            paramCount = 0,
                            hasThrow = true,
                        ) shouldBe
                    null
            }

            should("not report an override") {
                ThrowingExceptionInMainDecision
                        .decide(
                            name = "main",
                            isTopLevel = true,
                            isOverride = true,
                            hasNonPublicVisibility = false,
                            paramCount = 0,
                            hasThrow = true,
                        ) shouldBe
                    null
            }

            should("not report a non-public main") {
                ThrowingExceptionInMainDecision
                        .decide(
                            name = "main",
                            isTopLevel = true,
                            isOverride = false,
                            hasNonPublicVisibility = true,
                            paramCount = 0,
                            hasThrow = true,
                        ) shouldBe
                    null
            }

            should("not report a main with more than one parameter") {
                ThrowingExceptionInMainDecision
                        .decide(
                            name = "main",
                            isTopLevel = true,
                            isOverride = false,
                            hasNonPublicVisibility = false,
                            paramCount = 2,
                            hasThrow = true,
                        ) shouldBe
                    null
            }

            should("not report a main without a throw") {
                ThrowingExceptionInMainDecision
                        .decide(
                            name = "main",
                            isTopLevel = true,
                            isOverride = false,
                            hasNonPublicVisibility = false,
                            paramCount = 0,
                            hasThrow = false,
                        ) shouldBe
                    null
            }
        },
    )
