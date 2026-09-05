package com.varlanv.wrasse.rules

import com.varlanv.wrasse.testing.BaseSpec
import io.kotest.matchers.shouldBe

class MagicNumberDecisionSpec : BaseSpec({

    fun decide(
        value: Double?,
        isInsideProperty: Boolean = false,
        isParameterDefaultValue: Boolean = false,
        isNamedArgument: Boolean = false,
        isHashCodeFunction: Boolean = false,
        isCallReceiver: Boolean = false,
        isBareFunctionReturnValue: Boolean = false,
    ) = MagicNumberDecision.decide(
        value = value,
        isInsideProperty = isInsideProperty,
        isParameterDefaultValue = isParameterDefaultValue,
        isNamedArgument = isNamedArgument,
        isHashCodeFunction = isHashCodeFunction,
        isCallReceiver = isCallReceiver,
        isBareFunctionReturnValue = isBareFunctionReturnValue,
    )

    should("report an ordinary magic number") {
        decide(42.0) shouldBe MagicNumberDecision.MESSAGE
    }

    should("not report a null (unparseable) value") {
        decide(null) shouldBe null
    }

    should("not report an ignore-listed value") {
        decide(-1.0) shouldBe null
        decide(0.0) shouldBe null
        decide(1.0) shouldBe null
        decide(2.0) shouldBe null
    }

    should("not report a value inside a property declaration") {
        decide(42.0, isInsideProperty = true) shouldBe null
    }

    should("not report a parameter default value") {
        decide(42.0, isParameterDefaultValue = true) shouldBe null
    }

    should("not report a named argument") {
        decide(42.0, isNamedArgument = true) shouldBe null
    }

    should("not report inside a hashCode function") {
        decide(42.0, isHashCodeFunction = true) shouldBe null
    }

    should("not report a dot-call receiver") {
        decide(42.0, isCallReceiver = true) shouldBe null
    }

    should("not report a bare function return value") {
        decide(42.0, isBareFunctionReturnValue = true) shouldBe null
    }
})
