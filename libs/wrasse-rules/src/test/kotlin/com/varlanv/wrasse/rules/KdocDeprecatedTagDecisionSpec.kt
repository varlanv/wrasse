package com.varlanv.wrasse.rules

import com.varlanv.wrasse.testing.BaseSpec
import io.kotest.matchers.shouldBe

class KdocDeprecatedTagDecisionSpec :
    BaseSpec(
        {

            should("detect a deprecated block tag") {
                KdocDeprecatedTagDecision.hasDeprecatedTag("/** @deprecated use bar instead */") shouldBe true
            }

            should("not detect an unrelated tag") {
                KdocDeprecatedTagDecision.hasDeprecatedTag("/** @param foo the foo */") shouldBe false
            }

            should("not match a longer tag merely starting with the same word") {
                KdocDeprecatedTagDecision.hasDeprecatedTag("/** @deprecatedly noted */") shouldBe false
            }
        },
    )
