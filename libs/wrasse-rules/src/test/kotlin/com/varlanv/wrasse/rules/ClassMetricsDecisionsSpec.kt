package com.varlanv.wrasse.rules

import com.varlanv.wrasse.testing.BaseSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

class ClassMetricsDecisionsSpec : BaseSpec({

    should("not report too-many-functions for a class at the threshold") {
        TooManyFunctionsDecision.decide(11, "Class", "Foo") shouldBe null
    }

    should("report too-many-functions for a class just above the threshold") {
        TooManyFunctionsDecision.decide(12, "Class", "Foo") shouldBe
            "Class 'Foo' has 12 functions; the maximum allowed is 11"
    }

    should("use the given kind label verbatim in the message") {
        TooManyFunctionsDecision.decide(12, "Interface", "Bar") shouldBe
            "Interface 'Bar' has 12 functions; the maximum allowed is 11"
        TooManyFunctionsDecision.decide(12, "Enum class", "Baz") shouldBe
            "Enum class 'Baz' has 12 functions; the maximum allowed is 11"
        TooManyFunctionsDecision.decide(12, "Object", "Qux") shouldBe
            "Object 'Qux' has 12 functions; the maximum allowed is 11"
    }

    should("not report too-many-functions for a file at the threshold") {
        TooManyFunctionsDecision.decideFile(11) shouldBe null
    }

    should("report too-many-functions for a file just above the threshold") {
        TooManyFunctionsDecision.decideFile(12) shouldNotBe null
    }

    should("report too-many-functions just above a configured threshold") {
        TooManyFunctionsDecision.decide(6, "Class", "Foo", threshold = 5) shouldBe
            "Class 'Foo' has 6 functions; the maximum allowed is 5"
        TooManyFunctionsDecision.decide(5, "Class", "Foo", threshold = 5) shouldBe null
    }

    should("report too-many-functions for a file just above a configured threshold") {
        TooManyFunctionsDecision.decideFile(6, threshold = 5) shouldBe
            "File has 6 top-level functions; the maximum allowed is 5"
        TooManyFunctionsDecision.decideFile(5, threshold = 5) shouldBe null
    }

    should("not report large-class at the threshold") {
        LargeClassDecision.decide(600, "Foo") shouldBe null
    }

    should("report large-class just above the threshold") {
        LargeClassDecision.decide(601, "Foo") shouldBe
            "Class 'Foo' is too large (601 lines); the maximum allowed is 600"
    }

    should("report large-class just above a configured threshold") {
        LargeClassDecision.decide(101, "Foo", threshold = 100) shouldBe
            "Class 'Foo' is too large (101 lines); the maximum allowed is 100"
        LargeClassDecision.decide(100, "Foo", threshold = 100) shouldBe null
    }
})
