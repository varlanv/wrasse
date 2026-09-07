package com.varlanv.wrasse.rules

import com.varlanv.wrasse.model.WNodeType
import com.varlanv.wrasse.testing.BaseSpec
import io.kotest.matchers.shouldBe

class ValueParameterCommentDecisionSpec : BaseSpec({

    should("report a comment whose parent is a value parameter") {
        ValueParameterCommentDecision.decide(WNodeType.VALUE_PARAMETER, isKdocFirstChild = false) shouldBe
            "A comment inside or on the same line after a value parameter is not allowed. Place it on a separate " +
                "line above."
    }

    should("not report a KDoc that is a value parameter's own first child") {
        ValueParameterCommentDecision.decide(WNodeType.VALUE_PARAMETER, isKdocFirstChild = true) shouldBe null
    }

    should("not report a comment whose parent is not a value parameter") {
        ValueParameterCommentDecision.decide(WNodeType.VALUE_ARGUMENT, isKdocFirstChild = false) shouldBe null
    }
})
