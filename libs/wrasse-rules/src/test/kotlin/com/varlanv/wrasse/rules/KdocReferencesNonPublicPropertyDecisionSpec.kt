package com.varlanv.wrasse.rules

import com.varlanv.wrasse.testing.BaseSpec
import io.kotest.matchers.shouldBe

class KdocReferencesNonPublicPropertyDecisionSpec :
    BaseSpec(
        {

            should("detect a same-name link") {
                KdocReferencesNonPublicPropertyDecision.isReferenced("/** see [prop1] */", "prop1") shouldBe true
            }

            should("not detect a link with custom display text") {
                KdocReferencesNonPublicPropertyDecision.isReferenced("/** see [prop1][SomeOther] */", "prop1") shouldBe false
            }

            should("not detect an unrelated name") {
                KdocReferencesNonPublicPropertyDecision.isReferenced("/** see [prop2] */", "prop1") shouldBe false
            }

            should("format the violation message") {
                KdocReferencesNonPublicPropertyDecision.message("prop1") shouldBe
                    "The property 'prop1' is non-public and should not be referenced from KDoc comments."
            }
        },
    )
