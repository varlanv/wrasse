package com.varlanv.wrasse.rules

import com.varlanv.wrasse.model.WNodeType
import com.varlanv.wrasse.testing.BaseSpec
import io.kotest.matchers.shouldBe

class StringConcatenationDecisionSpec : BaseSpec({

    should("start a concatenation from a string template literal") {
        StringConcatenationDecision.isStringConcatenationStart(
            WNodeType.STRING_TEMPLATE,
            "\"a\"",
            WNodeType.INTEGER_CONSTANT,
        ) shouldBe true
    }

    should("start a concatenation from a toString()-suffixed call") {
        StringConcatenationDecision.isStringConcatenationStart(
            WNodeType.DOT_QUALIFIED_EXPRESSION,
            "x.toString()",
            WNodeType.STRING_TEMPLATE,
        ) shouldBe true
    }

    should("not start when the toString()-suffixed left side pairs with a non-string right side") {
        StringConcatenationDecision.isStringConcatenationStart(
            WNodeType.DOT_QUALIFIED_EXPRESSION,
            "x.toString()",
            WNodeType.INTEGER_CONSTANT,
        ) shouldBe false
    }

    should("not start when the left side is a reference expression") {
        StringConcatenationDecision.isStringConcatenationStart(
            WNodeType.REFERENCE_EXPRESSION,
            "x",
            WNodeType.STRING_TEMPLATE,
        ) shouldBe false
    }

    should("not start when the dot-qualified left side does not end with toString()") {
        StringConcatenationDecision.isStringConcatenationStart(
            WNodeType.DOT_QUALIFIED_EXPRESSION,
            "x.length",
            WNodeType.STRING_TEMPLATE,
        ) shouldBe false
    }
})
