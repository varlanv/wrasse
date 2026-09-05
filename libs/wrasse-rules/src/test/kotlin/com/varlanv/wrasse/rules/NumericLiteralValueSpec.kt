package com.varlanv.wrasse.rules

import com.varlanv.wrasse.testing.BaseSpec
import io.kotest.matchers.shouldBe

class NumericLiteralValueSpec : BaseSpec({

    should("parse a plain integer") {
        NumericLiteralValue.parse("42") shouldBe 42.0
    }

    should("parse a plain float") {
        NumericLiteralValue.parse("4.2") shouldBe 4.2
    }

    should("parse an underscore-separated literal") {
        NumericLiteralValue.parse("1_000") shouldBe 1_000.0
    }

    should("parse a long suffix") {
        NumericLiteralValue.parse("42L") shouldBe 42.0
    }

    should("parse an unsigned suffix") {
        NumericLiteralValue.parse("42U") shouldBe 42.0
    }

    should("parse an unsigned long suffix") {
        NumericLiteralValue.parse("42UL") shouldBe 42.0
    }

    should("parse a float suffix") {
        NumericLiteralValue.parse("42F") shouldBe 42.0
    }

    should("parse a double suffix") {
        NumericLiteralValue.parse("42D") shouldBe 42.0
    }

    should("parse a hex literal") {
        NumericLiteralValue.parse("0x2A") shouldBe 42.0
    }

    should("parse a binary literal") {
        NumericLiteralValue.parse("0b101010") shouldBe 42.0
    }

    should("return null for unparseable text") {
        NumericLiteralValue.parse("not-a-number") shouldBe null
    }
})
