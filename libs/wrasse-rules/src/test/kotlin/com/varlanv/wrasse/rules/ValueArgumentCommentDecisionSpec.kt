package com.varlanv.wrasse.rules

import com.varlanv.wrasse.model.WNodeType
import com.varlanv.wrasse.testing.BaseSpec
import io.kotest.matchers.shouldBe

class ValueArgumentCommentDecisionSpec :
    BaseSpec(
        {

            should("report a comment whose parent is a value argument") {
                ValueArgumentCommentDecision.decide(WNodeType.VALUE_ARGUMENT) shouldBe
                    "A comment inside or on the same line after a value argument is not allowed. Place it on a separate " +
                    "line above."
            }

            should("not report a comment whose parent is not a value argument") {
                ValueArgumentCommentDecision.decide(WNodeType.VALUE_ARGUMENT_LIST) shouldBe null
            }
        },
    )
