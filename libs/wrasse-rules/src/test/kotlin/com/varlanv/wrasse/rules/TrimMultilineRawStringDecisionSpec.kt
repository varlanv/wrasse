package com.varlanv.wrasse.rules

import com.varlanv.wrasse.testing.BaseSpec
import io.kotest.matchers.shouldBe

class TrimMultilineRawStringDecisionSpec :
    BaseSpec(
        {

            should("report an untrimmed multiline raw string") {
                TrimMultilineRawStringDecision.decide(isRawWithLineBreak = true, isTrimmed = false, isExpectedAsConstant = false) shouldBe
                    TrimMultilineRawStringDecision.MESSAGE
            }

            should("not report a single-line raw string") {
                TrimMultilineRawStringDecision.decide(isRawWithLineBreak = false, isTrimmed = false, isExpectedAsConstant = false) shouldBe
                    null
            }

            should("not report an already-trimmed string") {
                TrimMultilineRawStringDecision.decide(isRawWithLineBreak = true, isTrimmed = true, isExpectedAsConstant = false) shouldBe
                    null
            }

            should("not report a string expected to be a compile-time constant") {
                TrimMultilineRawStringDecision.decide(isRawWithLineBreak = true, isTrimmed = false, isExpectedAsConstant = true) shouldBe
                    null
            }
        },
    )
