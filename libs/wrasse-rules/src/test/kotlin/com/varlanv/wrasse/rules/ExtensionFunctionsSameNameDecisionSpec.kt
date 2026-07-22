package com.varlanv.wrasse.rules

import com.varlanv.wrasse.testing.BaseSpec
import io.kotest.matchers.shouldBe

class ExtensionFunctionsSameNameDecisionSpec :
    BaseSpec(
        {
            val related = listOf("Base" to "Derived")

            should("report both functions of a same-signature pair on related classes") {
                val candidates =
                listOf(
                    ExtensionFunctionsSameNameDecision.Candidate("Base", "process", listOf("x"), "Int"),
                    ExtensionFunctionsSameNameDecision.Candidate("Derived", "process", listOf("x"), "Int"),
                )

                ExtensionFunctionsSameNameDecision.indicesToReport(candidates, related) shouldBe mapOf(0 to 1, 1 to 0)
            }

            should("not report when the receiver classes are unrelated") {
                val candidates =
                listOf(
                    ExtensionFunctionsSameNameDecision.Candidate("Base", "process", listOf("x"), "Int"),
                    ExtensionFunctionsSameNameDecision.Candidate("Unrelated", "process", listOf("x"), "Int"),
                )

                ExtensionFunctionsSameNameDecision.indicesToReport(candidates, related) shouldBe emptyMap()
            }

            should("not report when parameter names differ") {
                val candidates =
                listOf(
                    ExtensionFunctionsSameNameDecision.Candidate("Base", "process", listOf("x"), "Int"),
                    ExtensionFunctionsSameNameDecision.Candidate("Derived", "process", listOf("y"), "Int"),
                )

                ExtensionFunctionsSameNameDecision.indicesToReport(candidates, related) shouldBe emptyMap()
            }

            should("not report when return types differ") {
                val candidates =
                listOf(
                    ExtensionFunctionsSameNameDecision.Candidate("Base", "process", listOf("x"), "Int"),
                    ExtensionFunctionsSameNameDecision.Candidate("Derived", "process", listOf("x"), "String"),
                )

                ExtensionFunctionsSameNameDecision.indicesToReport(candidates, related) shouldBe emptyMap()
            }

            should("pair a third same-signature function only with the first occurrence, never the second") {
                val candidates =
                listOf(
                    ExtensionFunctionsSameNameDecision.Candidate("Base", "process", listOf("x"), "Int"),
                    ExtensionFunctionsSameNameDecision.Candidate("Derived", "process", listOf("x"), "Int"),
                    ExtensionFunctionsSameNameDecision.Candidate("Derived", "process", listOf("x"), "Int"),
                )

                ExtensionFunctionsSameNameDecision.indicesToReport(candidates, related) shouldBe mapOf(0 to 2, 1 to 0, 2 to 0)
            }
        },
    )
