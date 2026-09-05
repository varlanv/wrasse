package com.varlanv.wrasse.rules

import com.varlanv.wrasse.model.WNodeType
import com.varlanv.wrasse.testing.BaseSpec
import io.kotest.matchers.shouldBe

class TypeParameterCommentDecisionSpec : BaseSpec({

    should("report a comment inside a type parameter") {
        TypeParameterCommentDecision.decide(
            WNodeType.TYPE_PARAMETER,
            precededByNewline = false,
        ) shouldBe
            "A comment inside or on the same line after a type parameter is not allowed. Place it on a separate " +
            "line above."
    }

    should("report a comment directly in a type parameter list not preceded by a newline") {
        TypeParameterCommentDecision.decide(
            WNodeType.TYPE_PARAMETER_LIST,
            precededByNewline = false,
        ) shouldBe "A comment in a type parameter list is only allowed when placed on a separate line"
    }

    should("not report a comment directly in a type parameter list preceded by a newline") {
        TypeParameterCommentDecision.decide(WNodeType.TYPE_PARAMETER_LIST, precededByNewline = true) shouldBe null
    }

    should("not report an unrelated parent") {
        TypeParameterCommentDecision.decide(WNodeType.VALUE_ARGUMENT, precededByNewline = false) shouldBe null
    }
})
