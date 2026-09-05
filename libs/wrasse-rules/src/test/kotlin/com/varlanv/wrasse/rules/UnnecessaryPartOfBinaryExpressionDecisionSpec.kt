package com.varlanv.wrasse.rules

import com.varlanv.wrasse.testing.BaseSpec
import io.kotest.matchers.shouldBe

class UnnecessaryPartOfBinaryExpressionDecisionSpec : BaseSpec({

    should("not report distinct operands") {
        UnnecessaryPartOfBinaryExpressionDecision.decide(listOf("foo", "bar")) shouldBe null
    }

    should("report a duplicate operand") {
        UnnecessaryPartOfBinaryExpressionDecision.decide(
            listOf("foo", "bar", "foo"),
        ) shouldBe UnnecessaryPartOfBinaryExpressionDecision.MESSAGE
    }

    should("strip whitespace outside string and character literals only") {
        UnnecessaryPartOfBinaryExpressionDecision.normalizeOperand("a  &&\n  b") shouldBe "a&&b"
        UnnecessaryPartOfBinaryExpressionDecision.normalizeOperand(
            "\"buy back\" in lower",
        ) shouldBe "\"buy back\"inlower"
        UnnecessaryPartOfBinaryExpressionDecision.normalizeOperand(
            "s.endsWith(\"x \\\" y\")",
        ) shouldBe "s.endsWith(\"x \\\" y\")"
        UnnecessaryPartOfBinaryExpressionDecision.normalizeOperand("c == ' '") shouldBe "c==' '"
    }

    should("not report a single operand") {
        UnnecessaryPartOfBinaryExpressionDecision.decide(listOf("foo")) shouldBe null
    }

    should("report when every operand is the same") {
        UnnecessaryPartOfBinaryExpressionDecision.decide(
            listOf("foo", "foo"),
        ) shouldBe UnnecessaryPartOfBinaryExpressionDecision.MESSAGE
    }
})
