package com.varlanv.wrasse.rules

import com.varlanv.wrasse.testing.BaseSpec
import io.kotest.matchers.shouldBe

class GetterSetterFieldsDecisionSpec : BaseSpec({

    should("report a bare self-reference with no other disqualifying fact") {
        GetterSetterFieldsDecision.decide(
            foundSelfReference = true,
            isCallExpressionCallee = false,
            shadowedByLocalVar = false,
            isExtensionProperty = false,
        ) shouldBe GetterSetterFieldsDecision.MESSAGE
    }

    should("not report when no self-reference was found") {
        GetterSetterFieldsDecision.decide(
            foundSelfReference = false,
            isCallExpressionCallee = false,
            shadowedByLocalVar = false,
            isExtensionProperty = false,
        ) shouldBe null
    }

    should("not report a same-named function call") {
        GetterSetterFieldsDecision.decide(
            foundSelfReference = true,
            isCallExpressionCallee = true,
            shadowedByLocalVar = false,
            isExtensionProperty = false,
        ) shouldBe null
    }

    should("not report when shadowed by an earlier local variable of the same name") {
        GetterSetterFieldsDecision.decide(
            foundSelfReference = true,
            isCallExpressionCallee = false,
            shadowedByLocalVar = true,
            isExtensionProperty = false,
        ) shouldBe null
    }

    should("not report an extension property") {
        GetterSetterFieldsDecision.decide(
            foundSelfReference = true,
            isCallExpressionCallee = false,
            shadowedByLocalVar = false,
            isExtensionProperty = true,
        ) shouldBe null
    }
})
