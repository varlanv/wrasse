package com.varlanv.wrasse.rules

import com.varlanv.wrasse.testing.BaseSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

class LongParameterListDecisionSpec : BaseSpec({

    should("not report a function at the threshold") {
        LongParameterListDecision.decide(
            ParameterListOwner.FUNCTION,
            5,
            isOverride = false,
            isDataClassConstructor = false,
        ) shouldBe null
    }

    should("report a function just above the threshold") {
        LongParameterListDecision.decide(
            ParameterListOwner.FUNCTION,
            6,
            isOverride = false,
            isDataClassConstructor = false,
        ) shouldNotBe null
    }

    should("not report an override function regardless of parameter count") {
        LongParameterListDecision.decide(
            ParameterListOwner.FUNCTION,
            20,
            isOverride = true,
            isDataClassConstructor = false,
        ) shouldBe null
    }

    should("not report a primary constructor at the threshold") {
        LongParameterListDecision.decide(
            ParameterListOwner.PRIMARY_CONSTRUCTOR,
            6,
            isOverride = false,
            isDataClassConstructor = false,
        ) shouldBe null
    }

    should("report a primary constructor just above the threshold") {
        LongParameterListDecision.decide(
            ParameterListOwner.PRIMARY_CONSTRUCTOR,
            7,
            isOverride = false,
            isDataClassConstructor = false,
        ) shouldNotBe null
    }

    should("not report a data class constructor regardless of parameter count") {
        LongParameterListDecision.decide(
            ParameterListOwner.PRIMARY_CONSTRUCTOR,
            20,
            isOverride = false,
            isDataClassConstructor = true,
        ) shouldBe null
        LongParameterListDecision.decide(
            ParameterListOwner.SECONDARY_CONSTRUCTOR,
            20,
            isOverride = false,
            isDataClassConstructor = true,
        ) shouldBe null
    }

    should("report a secondary constructor just above the threshold") {
        LongParameterListDecision.decide(
            ParameterListOwner.SECONDARY_CONSTRUCTOR,
            7,
            isOverride = false,
            isDataClassConstructor = false,
        ) shouldBe "The constructor has 7 parameters; the maximum allowed is 6"
    }

    should("report a function with the exact message and counts") {
        LongParameterListDecision.decide(
            ParameterListOwner.FUNCTION,
            9,
            isOverride = false,
            isDataClassConstructor = false,
        ) shouldBe "The function has 9 parameters; the maximum allowed is 5"
    }

    should("report a function just above a configured function-threshold") {
        LongParameterListDecision.decide(
            ParameterListOwner.FUNCTION,
            4,
            isOverride = false,
            isDataClassConstructor = false,
            functionThreshold = 3,
        ) shouldBe "The function has 4 parameters; the maximum allowed is 3"
        LongParameterListDecision.decide(
            ParameterListOwner.FUNCTION,
            3,
            isOverride = false,
            isDataClassConstructor = false,
            functionThreshold = 3,
        ) shouldBe null
    }

    should("report a constructor just above a configured constructor-threshold") {
        LongParameterListDecision.decide(
            ParameterListOwner.PRIMARY_CONSTRUCTOR,
            4,
            isOverride = false,
            isDataClassConstructor = false,
            constructorThreshold = 3,
        ) shouldBe "The constructor has 4 parameters; the maximum allowed is 3"
        LongParameterListDecision.decide(
            ParameterListOwner.PRIMARY_CONSTRUCTOR,
            3,
            isOverride = false,
            isDataClassConstructor = false,
            constructorThreshold = 3,
        ) shouldBe null
    }
})
