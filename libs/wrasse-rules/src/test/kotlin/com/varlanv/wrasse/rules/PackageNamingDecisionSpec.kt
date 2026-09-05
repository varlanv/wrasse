package com.varlanv.wrasse.rules

import com.varlanv.wrasse.testing.BaseSpec
import io.kotest.matchers.shouldBe

class PackageNamingDecisionSpec : BaseSpec({
    should("accept a plain lowercase dotted package name") {
        PackageNamingDecision.decide("com.example.foo") shouldBe null
    }

    should("accept digits and mixed case after a segment's first letter") {
        PackageNamingDecision.decide("com.example2.fooBar") shouldBe null
    }

    should("accept an empty package name (default package)") {
        PackageNamingDecision.decide("") shouldBe null
    }

    should("reject a package name containing an underscore") {
        PackageNamingDecision.decide("com.example.foo_bar") shouldBe PackageNamingDecision.UNDERSCORE_MESSAGE
    }

    should("reject a segment starting with an uppercase letter") {
        PackageNamingDecision.decide("com.Example.foo") shouldBe PackageNamingDecision.MESSAGE
    }

    should("reject a segment starting with a digit") {
        PackageNamingDecision.decide("com.1example.foo") shouldBe PackageNamingDecision.MESSAGE
    }
})
