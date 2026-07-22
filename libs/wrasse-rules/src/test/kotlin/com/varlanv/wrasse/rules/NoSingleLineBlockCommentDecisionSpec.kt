package com.varlanv.wrasse.rules

import com.varlanv.wrasse.testing.BaseSpec
import io.kotest.matchers.shouldBe

class NoSingleLineBlockCommentDecisionSpec :
    BaseSpec(
        {

            should("report a single-line block comment followed by nothing but a newline") {
                NoSingleLineBlockCommentDecision.decide("/* text */", followedByCodeOnSameLine = false) shouldBe
                    NoSingleLineBlockCommentDecision.MESSAGE
            }

            should("not report a single-line block comment followed by code on the same line") {
                NoSingleLineBlockCommentDecision.decide("/* text */", followedByCodeOnSameLine = true) shouldBe null
            }

            should("not report a multi-line block comment") {
                NoSingleLineBlockCommentDecision.decide("/* line one\nline two */", followedByCodeOnSameLine = false) shouldBe null
            }
        },
    )
