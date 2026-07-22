package com.varlanv.wrasse.rules

import com.varlanv.wrasse.testing.BaseSpec
import io.kotest.matchers.shouldBe

class WhenMustHaveElseDecisionSpec :
    BaseSpec(
        {

            should("report a statement when with no else and non-enum entries") {
                WhenMustHaveElseDecision.decide(isExempt = false, hasElse = false, isEnumOnly = false, isLambdaLastStatement = false) shouldBe
                    WhenMustHaveElseDecision.MESSAGE
            }

            should("not report when the when already has an else") {
                WhenMustHaveElseDecision.decide(isExempt = false, hasElse = true, isEnumOnly = false, isLambdaLastStatement = false) shouldBe
                    null
            }

            should("not report when every entry is enum-entry-shaped") {
                WhenMustHaveElseDecision.decide(isExempt = false, hasElse = false, isEnumOnly = true, isLambdaLastStatement = false) shouldBe
                    null
            }

            should("not report an exempt position (return value, when-branch, or declaration value)") {
                WhenMustHaveElseDecision.decide(isExempt = true, hasElse = false, isEnumOnly = false, isLambdaLastStatement = false) shouldBe
                    null
            }

            should("not report a lambda's own last statement") {
                WhenMustHaveElseDecision.decide(isExempt = false, hasElse = false, isEnumOnly = false, isLambdaLastStatement = true) shouldBe
                    null
            }
        },
    )
