package com.varlanv.wrasse.rules

import com.varlanv.wrasse.testing.BaseSpec
import io.kotest.matchers.shouldBe

class NestedClassesVisibilityDecisionSpec : BaseSpec({

    should("report a public nested class inside a qualifying owner") {
        NestedClassesVisibilityDecision.decide(
            ownerQualifies = true,
            hasPublic = true,
            hasEnum = false,
            hasCompanion = false,
        ) shouldBe NestedClassesVisibilityDecision.MESSAGE
    }

    should("not report when the owner does not qualify") {
        NestedClassesVisibilityDecision.decide(
            ownerQualifies = false,
            hasPublic = true,
            hasEnum = false,
            hasCompanion = false,
        ) shouldBe null
    }

    should("not report a non-public nested declaration") {
        NestedClassesVisibilityDecision.decide(
            ownerQualifies = true,
            hasPublic = false,
            hasEnum = false,
            hasCompanion = false,
        ) shouldBe null
    }

    should("not report an enum") {
        NestedClassesVisibilityDecision.decide(
            ownerQualifies = true,
            hasPublic = true,
            hasEnum = true,
            hasCompanion = false,
        ) shouldBe null
    }

    should("not report a companion object") {
        NestedClassesVisibilityDecision.decide(
            ownerQualifies = true,
            hasPublic = true,
            hasEnum = false,
            hasCompanion = true,
        ) shouldBe null
    }
})
