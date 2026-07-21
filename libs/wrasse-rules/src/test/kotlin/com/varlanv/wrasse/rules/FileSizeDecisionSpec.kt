package com.varlanv.wrasse.rules

import com.varlanv.wrasse.testing.BaseSpec
import io.kotest.matchers.shouldBe

class FileSizeDecisionSpec :
    BaseSpec(
        {

            should("not report a file at the threshold") {
                FileSizeDecision.decide(2_000) shouldBe null
            }

            should("report a file just above the threshold") {
                FileSizeDecision.decide(2_001) shouldBe "File has 2001 lines; the maximum allowed is 2000"
            }
        },
    )
