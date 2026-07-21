package com.varlanv.wrasse.rules

import com.varlanv.wrasse.testing.BaseSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

class FunctionMetricsDecisionsSpec :
    BaseSpec(
        {

            should("not report return-count at the threshold") {
                ReturnCountDecision.decide(2, "foo") shouldBe null
            }

            should("report return-count just above the threshold") {
                ReturnCountDecision.decide(3, "foo") shouldBe "Function 'foo' has 3 return statements; the maximum allowed is 2"
            }

            should("never report return-count for a function literally named equals") {
                ReturnCountDecision.decide(50, "equals") shouldBe null
            }

            should("not report throws-count at the threshold") {
                ThrowsCountDecision.decide(2, "foo") shouldBe null
            }

            should("report throws-count just above the threshold") {
                ThrowsCountDecision.decide(3, "foo") shouldBe "Function 'foo' has 3 throw statements; the maximum allowed is 2"
            }

            should("not report nested-block-depth at the threshold") {
                NestedBlockDepthDecision.decide(4, "foo") shouldBe null
            }

            should("report nested-block-depth just above the threshold") {
                NestedBlockDepthDecision.decide(5, "foo") shouldNotBe null
            }

            should("not report cyclomatic-complexity at the threshold") {
                CyclomaticComplexityDecision.decide(14, "foo") shouldBe null
            }

            should("report cyclomatic-complexity just above the threshold") {
                CyclomaticComplexityDecision.decide(15, "foo") shouldNotBe null
            }

            should("not report long-method at the threshold") {
                LongMethodDecision.decide(60, "foo") shouldBe null
            }

            should("report long-method just above the threshold") {
                LongMethodDecision.decide(61, "foo") shouldNotBe null
            }
        },
    )
