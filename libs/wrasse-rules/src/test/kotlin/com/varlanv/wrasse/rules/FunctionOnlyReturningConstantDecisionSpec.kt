package com.varlanv.wrasse.rules

import com.varlanv.wrasse.testing.BaseSpec
import io.kotest.matchers.shouldBe

class FunctionOnlyReturningConstantDecisionSpec :
    BaseSpec(
        {

            should("report a function returning a constant") {
                FunctionOnlyReturningConstantDecision
                        .decide(
                            isOverride = false,
                            isOpen = false,
                            isActual = false,
                            inInterface = false,
                            returnsConstant = true,
                            functionName = "foo",
                        ) shouldBe
                    "Function 'foo' only returns a constant; consider declaring a constant instead"
            }

            should("not report when the function does not return a constant") {
                FunctionOnlyReturningConstantDecision
                        .decide(
                            isOverride = false,
                            isOpen = false,
                            isActual = false,
                            inInterface = false,
                            returnsConstant = false,
                            functionName = "foo",
                        ) shouldBe
                    null
            }

            should("not report an override") {
                FunctionOnlyReturningConstantDecision
                        .decide(
                            isOverride = true,
                            isOpen = false,
                            isActual = false,
                            inInterface = false,
                            returnsConstant = true,
                            functionName = "foo",
                        ) shouldBe
                    null
            }

            should("not report an open function") {
                FunctionOnlyReturningConstantDecision
                        .decide(
                            isOverride = false,
                            isOpen = true,
                            isActual = false,
                            inInterface = false,
                            returnsConstant = true,
                            functionName = "foo",
                        ) shouldBe
                    null
            }

            should("not report an actual function") {
                FunctionOnlyReturningConstantDecision
                        .decide(
                            isOverride = false,
                            isOpen = false,
                            isActual = true,
                            inInterface = false,
                            returnsConstant = true,
                            functionName = "foo",
                        ) shouldBe
                    null
            }

            should("not report a function declared inside an interface") {
                FunctionOnlyReturningConstantDecision
                        .decide(
                            isOverride = false,
                            isOpen = false,
                            isActual = false,
                            inInterface = true,
                            returnsConstant = true,
                            functionName = "foo",
                        ) shouldBe
                    null
            }
        },
    )
