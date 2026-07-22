package com.varlanv.wrasse.rules

import com.varlanv.wrasse.model.WNodeType
import com.varlanv.wrasse.testing.BaseSpec
import io.kotest.matchers.shouldBe

class RedundantToStringInTemplateDecisionSpec :
    BaseSpec(
        {

            should("report a plain receiver's own toString() call") {
                RedundantToStringInTemplateDecision.decide(WNodeType.REFERENCE_EXPRESSION, WNodeType.CALL_EXPRESSION, "toString()") shouldBe
                    RedundantToStringInTemplateDecision.MESSAGE
            }

            should("not report super.toString()") {
                RedundantToStringInTemplateDecision.decide(WNodeType.SUPER_EXPRESSION, WNodeType.CALL_EXPRESSION, "toString()") shouldBe
                    null
            }

            should("not report a call with arguments") {
                RedundantToStringInTemplateDecision.decide(WNodeType.REFERENCE_EXPRESSION, WNodeType.CALL_EXPRESSION, "toString(radix)") shouldBe
                    null
            }

            should("not report a selector that is not a call expression") {
                RedundantToStringInTemplateDecision.decide(WNodeType.REFERENCE_EXPRESSION, WNodeType.REFERENCE_EXPRESSION, "toString()") shouldBe
                    null
            }
        },
    )
