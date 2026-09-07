package com.varlanv.wrasse.rules

import com.varlanv.wrasse.model.WNodeType
import com.varlanv.wrasse.testing.BaseSpec
import io.kotest.matchers.shouldBe

class TypeArgumentCommentDecisionSpec : BaseSpec({

    should("report a comment inside a type projection") {
        TypeArgumentCommentDecision.decide(WNodeType.TYPE_PROJECTION, precededByNewline = false) shouldBe
            "A comment inside or on the same line after a type projection is not allowed. Place it on a separate " +
                "line above."
    }

    should("report a comment inside a type projection even when preceded by a newline") {
        TypeArgumentCommentDecision.decide(WNodeType.TYPE_PROJECTION, precededByNewline = true) shouldBe
            "A comment inside or on the same line after a type projection is not allowed. Place it on a separate " +
                "line above."
    }

    should("report a comment directly in a type argument list not preceded by a newline") {
        TypeArgumentCommentDecision.decide(WNodeType.TYPE_ARGUMENT_LIST, precededByNewline = false) shouldBe
            "A comment in a type argument list is only allowed when placed on a separate line"
    }

    should("not report a comment directly in a type argument list preceded by a newline") {
        TypeArgumentCommentDecision.decide(WNodeType.TYPE_ARGUMENT_LIST, precededByNewline = true) shouldBe null
    }

    should("not report an unrelated parent") {
        TypeArgumentCommentDecision.decide(WNodeType.VALUE_ARGUMENT, precededByNewline = false) shouldBe null
    }
})
