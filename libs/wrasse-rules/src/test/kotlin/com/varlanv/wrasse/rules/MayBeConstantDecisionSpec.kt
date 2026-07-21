package com.varlanv.wrasse.rules

import com.varlanv.wrasse.testing.BaseSpec
import io.kotest.matchers.shouldBe

class MayBeConstantDecisionSpec :
    BaseSpec(
        {

            fun decide(
                eligibleScope: Boolean = true,
                isVar: Boolean = false,
                isAlreadyConst: Boolean = false,
                isActual: Boolean = false,
                isOverride: Boolean = false,
                hasGetter: Boolean = false,
                hasNonJvmFieldAnnotation: Boolean = false,
                initializerIsConstant: Boolean = true,
                propertyName: String = "x",
            ) = MayBeConstantDecision
                .decide(
                    eligibleScope,
                    isVar,
                    isAlreadyConst,
                    isActual,
                    isOverride,
                    hasGetter,
                    hasNonJvmFieldAnnotation,
                    initializerIsConstant,
                    propertyName,
                )

            should("report an eligible val with a constant initializer") {
                decide() shouldBe "Property 'x' can be a 'const val'"
            }

            should("not report an ineligible scope") {
                decide(eligibleScope = false) shouldBe null
            }

            should("not report a var") {
                decide(isVar = true) shouldBe null
            }

            should("not report an already-const property") {
                decide(isAlreadyConst = true) shouldBe null
            }

            should("not report an actual property") {
                decide(isActual = true) shouldBe null
            }

            should("not report an override") {
                decide(isOverride = true) shouldBe null
            }

            should("not report a property with a getter") {
                decide(hasGetter = true) shouldBe null
            }

            should("not report a property with a non-JvmField annotation") {
                decide(hasNonJvmFieldAnnotation = true) shouldBe null
            }

            should("not report when the initializer is not constant") {
                decide(initializerIsConstant = false) shouldBe null
            }
        },
    )
