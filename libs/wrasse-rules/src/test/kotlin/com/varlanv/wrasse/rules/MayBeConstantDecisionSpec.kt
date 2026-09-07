package com.varlanv.wrasse.rules

import com.varlanv.wrasse.testing.BaseSpec
import io.kotest.matchers.shouldBe

class MayBeConstantDecisionSpec : BaseSpec({

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
    ) = MayBeConstantDecision.decide(
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

    should("allow the fix for an inferred type") {
        MayBeConstantDecision.canAutofix(hasJvmFieldAnnotation = false, declaredType = null) shouldBe true
    }

    should("allow the fix for each primitive type and String") {
        for (type in listOf("Boolean", "Byte", "Short", "Int", "Long", "Float", "Double", "Char", "String")) {
            MayBeConstantDecision.canAutofix(hasJvmFieldAnnotation = false, declaredType = type) shouldBe true
        }
    }

    should("decline the fix for a non-primitive declared type") {
        MayBeConstantDecision.canAutofix(hasJvmFieldAnnotation = false, declaredType = "Any") shouldBe false
    }

    should("decline the fix for a nullable primitive type") {
        MayBeConstantDecision.canAutofix(hasJvmFieldAnnotation = false, declaredType = "Int?") shouldBe false
    }

    should("decline the fix when @JvmField is present, regardless of type") {
        MayBeConstantDecision.canAutofix(hasJvmFieldAnnotation = true, declaredType = null) shouldBe false
        MayBeConstantDecision.canAutofix(hasJvmFieldAnnotation = true, declaredType = "Int") shouldBe false
    }

    should("insert const right before the val keyword, preserving the gap up to the name") {
        val source = "private val X = 1"
        val valStart = source.indexOf("val")
        val nameStart = source.indexOf("X")

        val edit = MayBeConstantDecision.autofixEdit(valStart, nameStart, source.substring(valStart, nameStart))

        edit.startOffset shouldBe valStart
        edit.endOffset shouldBe nameStart
        edit.replacement shouldBe "const val "
        val fixed = source.substring(0, edit.startOffset) + edit.replacement + source.substring(edit.endOffset)
        fixed shouldBe "private const val X = 1"
    }

    should("preserve an unusual gap between val and the name verbatim") {
        val source = "val  /* c */  greeting = 1"
        val valStart = source.indexOf("val")
        val nameStart = source.indexOf("greeting")

        val edit = MayBeConstantDecision.autofixEdit(valStart, nameStart, source.substring(valStart, nameStart))

        val fixed = source.substring(0, edit.startOffset) + edit.replacement + source.substring(edit.endOffset)
        fixed shouldBe "const val  /* c */  greeting = 1"
    }
})
