package com.varlanv.wrasse.rules

import com.varlanv.wrasse.testing.BaseSpec
import io.kotest.matchers.shouldBe

class DebugPrintDecisionSpec : BaseSpec({

    should("format the violation message for a bare callee") {
        DebugPrintDecision.message("println") shouldBe
            "'println()' looks like leftover debug output; remove it or replace it with a logger."
    }

    should("format the violation message for a console callee") {
        DebugPrintDecision.message("console.log") shouldBe
            "'console.log()' looks like leftover debug output; remove it or replace it with a logger."
    }
})
