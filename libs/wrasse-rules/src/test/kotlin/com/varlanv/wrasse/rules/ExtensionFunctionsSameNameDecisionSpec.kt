package com.varlanv.wrasse.rules

import com.varlanv.wrasse.testing.BaseSpec
import io.kotest.matchers.shouldBe

class ExtensionFunctionsSameNameDecisionSpec : BaseSpec({
    val related = listOf("Base" to "Derived")

    should("report both functions of a same-signature pair on related classes") {
        val candidates = listOf(
            ExtensionFunctionsSameNameDecision.Candidate("Base", "process", listOf("x"), "Int"),
            ExtensionFunctionsSameNameDecision.Candidate("Derived", "process", listOf("x"), "Int"),
        )

        ExtensionFunctionsSameNameDecision.indicesToReport(candidates, related) shouldBe listOf(0 to 1, 1 to 0)
    }

    should("not report when the receiver classes are unrelated") {
        val candidates = listOf(
            ExtensionFunctionsSameNameDecision.Candidate("Base", "process", listOf("x"), "Int"),
            ExtensionFunctionsSameNameDecision.Candidate("Unrelated", "process", listOf("x"), "Int"),
        )

        ExtensionFunctionsSameNameDecision.indicesToReport(candidates, related) shouldBe emptyList()
    }

    should("not report when parameter names differ") {
        val candidates = listOf(
            ExtensionFunctionsSameNameDecision.Candidate("Base", "process", listOf("x"), "Int"),
            ExtensionFunctionsSameNameDecision.Candidate("Derived", "process", listOf("y"), "Int"),
        )

        ExtensionFunctionsSameNameDecision.indicesToReport(candidates, related) shouldBe emptyList()
    }

    should("not report when return types differ") {
        val candidates = listOf(
            ExtensionFunctionsSameNameDecision.Candidate("Base", "process", listOf("x"), "Int"),
            ExtensionFunctionsSameNameDecision.Candidate("Derived", "process", listOf("x"), "String"),
        )

        ExtensionFunctionsSameNameDecision.indicesToReport(candidates, related) shouldBe emptyList()
    }

    should("pair a third same-signature occurrence with every earlier related occurrence, not just the first") {
        val candidates = listOf(
            ExtensionFunctionsSameNameDecision.Candidate("Base", "process", listOf("x"), "Int"),
            ExtensionFunctionsSameNameDecision.Candidate("Derived", "process", listOf("x"), "Int"),
            ExtensionFunctionsSameNameDecision.Candidate("Derived", "process", listOf("x"), "Int"),
        )

        ExtensionFunctionsSameNameDecision.indicesToReport(
            candidates,
            related,
        ) shouldBe listOf(0 to 1, 1 to 0, 0 to 2, 2 to 0)
    }

    should("report every pairwise relation in a star topology of three related classes") {
        val starRelated = listOf("Base" to "DerivedA", "Base" to "DerivedB")
        val candidates = listOf(
            ExtensionFunctionsSameNameDecision.Candidate("Base", "process", listOf("x"), "String"),
            ExtensionFunctionsSameNameDecision.Candidate("DerivedA", "process", listOf("x"), "String"),
            ExtensionFunctionsSameNameDecision.Candidate("DerivedB", "process", listOf("x"), "String"),
        )

        ExtensionFunctionsSameNameDecision.indicesToReport(
            candidates,
            starRelated,
        ) shouldBe listOf(0 to 1, 1 to 0, 0 to 2, 2 to 0)
    }
})
