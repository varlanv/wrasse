package com.varlanv.wrasse.rules

import com.varlanv.wrasse.testing.BaseSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

class FunctionMetricsDecisionsSpec : BaseSpec({

    should("not report return-count at the threshold") {
        ReturnCountDecision.decide(2, "foo") shouldBe null
    }

    should("report return-count just above the threshold") {
        ReturnCountDecision.decide(3, "foo") shouldBe "Function 'foo' has 3 return statements; the maximum allowed is 2"
    }

    should("never report return-count for a function literally named equals") {
        ReturnCountDecision.decide(50, "equals") shouldBe null
    }

    should("report return-count just above a configured threshold") {
        ReturnCountDecision.decide(
            2,
            "foo",
            threshold = 1,
        ) shouldBe "Function 'foo' has 2 return statements; the maximum allowed is 1"
        ReturnCountDecision.decide(1, "foo", threshold = 1) shouldBe null
    }

    should("not report throws-count at the threshold") {
        ThrowsCountDecision.decide(2, "foo") shouldBe null
    }

    should("report throws-count just above the threshold") {
        ThrowsCountDecision.decide(3, "foo") shouldBe "Function 'foo' has 3 throw statements; the maximum allowed is 2"
    }

    should("report throws-count just above a configured threshold") {
        ThrowsCountDecision.decide(
            2,
            "foo",
            threshold = 1,
        ) shouldBe "Function 'foo' has 2 throw statements; the maximum allowed is 1"
        ThrowsCountDecision.decide(1, "foo", threshold = 1) shouldBe null
    }

    should("not report nested-block-depth at the threshold") {
        NestedBlockDepthDecision.decide(4, "foo") shouldBe null
    }

    should("report nested-block-depth just above the threshold") {
        NestedBlockDepthDecision.decide(5, "foo") shouldNotBe null
    }

    should("report nested-block-depth just above a configured threshold") {
        NestedBlockDepthDecision.decide(
            3,
            "foo",
            threshold = 2,
        ) shouldBe "Function 'foo' is nested too deeply (depth 3); the maximum allowed is 2"
        NestedBlockDepthDecision.decide(2, "foo", threshold = 2) shouldBe null
    }

    should("not report cyclomatic-complexity at the threshold") {
        CyclomaticComplexityDecision.decide(14, "foo") shouldBe null
    }

    should("report cyclomatic-complexity just above the threshold") {
        CyclomaticComplexityDecision.decide(15, "foo") shouldNotBe null
        CyclomaticComplexityDecision.decide(
            4,
            "foo",
            threshold = 3,
        ) shouldBe "Function 'foo' has a cyclomatic complexity of 4; the maximum allowed is 3"
        CyclomaticComplexityDecision.decide(3, "foo", threshold = 3) shouldBe null
    }

    should("not report long-method at the threshold") {
        LongMethodDecision.decide(60, "foo") shouldBe null
    }

    should("report long-method just above the threshold") {
        LongMethodDecision.decide(61, "foo") shouldNotBe null
    }

    should("report long-method just above a configured threshold") {
        LongMethodDecision.decide(
            31,
            "foo",
            threshold = 30,
        ) shouldBe "Function 'foo' is too long (31 lines); the maximum allowed is 30"
        LongMethodDecision.decide(30, "foo", threshold = 30) shouldBe null
    }
})
