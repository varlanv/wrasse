package com.varlanv.wrasse.rules

import com.varlanv.wrasse.testing.BaseSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe

class FileNamingDecisionSpec :
    BaseSpec(
        {
            should("accept a file named after its sole non-private top-level class") {
                FileNamingDecision.decide("Foo", "Foo") shouldBe null
            }

            should("reject a file whose name does not match its sole non-private top-level class") {
                FileNamingDecision.decide("Bar", "Foo").shouldNotBeNull()
            }

            should("accept a PascalCase file name with no qualifying single top-level class") {
                FileNamingDecision.decide("Utils", null) shouldBe null
            }

            should("reject a non-PascalCase file name with no qualifying single top-level class") {
                FileNamingDecision.decide("utils", null).shouldNotBeNull()
            }
        },
    )
