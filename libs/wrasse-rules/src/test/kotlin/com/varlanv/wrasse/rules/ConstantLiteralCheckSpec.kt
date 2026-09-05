package com.varlanv.wrasse.rules

import com.varlanv.wrasse.model.WNodeType
import com.varlanv.wrasse.testing.BaseSpec
import io.kotest.matchers.shouldBe

class ConstantLiteralCheckSpec : BaseSpec({

    should("treat an integer constant as constant") {
        ConstantLiteralCheck.isConstant(WNodeType.INTEGER_CONSTANT, "1") shouldBe true
    }

    should("treat a float constant as constant") {
        ConstantLiteralCheck.isConstant(WNodeType.FLOAT_CONSTANT, "1.0") shouldBe true
    }

    should("treat a character constant as constant") {
        ConstantLiteralCheck.isConstant(WNodeType.CHARACTER_CONSTANT, "'a'") shouldBe true
    }

    should("treat a boolean constant as constant") {
        ConstantLiteralCheck.isConstant(WNodeType.BOOLEAN_CONSTANT, "true") shouldBe true
    }

    should("treat a non-interpolated string template as constant") {
        ConstantLiteralCheck.isConstant(WNodeType.STRING_TEMPLATE, "\"hello\"") shouldBe true
    }

    should("not treat an interpolated string template as constant") {
        ConstantLiteralCheck.isConstant(WNodeType.STRING_TEMPLATE, "\"hello \$name\"") shouldBe false
    }

    should("not treat any other node type as constant") {
        ConstantLiteralCheck.isConstant(WNodeType.REFERENCE_EXPRESSION, "x") shouldBe false
    }
})
