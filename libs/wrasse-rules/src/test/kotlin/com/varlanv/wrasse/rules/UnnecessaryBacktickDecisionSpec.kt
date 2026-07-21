package com.varlanv.wrasse.rules

import com.varlanv.wrasse.testing.BaseSpec
import io.kotest.matchers.shouldBe

class UnnecessaryBacktickDecisionSpec :
    BaseSpec(
        {

            should("unquote a plain backtick-quoted identifier") {
                UnnecessaryBacktickDecision.unquote("`foo`").toString() shouldBe "foo"
            }

            should("unquote a unicode-letter identifier") {
                UnnecessaryBacktickDecision.unquote("`Ünnecessary`").toString() shouldBe "Ünnecessary"
            }

            should("unquote a soft/modifier keyword used as a plain name") {
                UnnecessaryBacktickDecision.unquote("`public`").toString() shouldBe "public"
                UnnecessaryBacktickDecision.unquote("`data`").toString() shouldBe "data"
                UnnecessaryBacktickDecision.unquote("`get`").toString() shouldBe "get"
            }

            should("return null for text that is not backtick-quoted at all") {
                UnnecessaryBacktickDecision.unquote("foo") shouldBe null
            }

            should("return null for an identifier containing a space") {
                UnnecessaryBacktickDecision.unquote("`foo bar`") shouldBe null
            }

            should("return null for an identifier starting with a digit") {
                UnnecessaryBacktickDecision.unquote("`1abc`") shouldBe null
            }

            should("return null for a hard keyword") {
                UnnecessaryBacktickDecision.unquote("`fun`") shouldBe null
                UnnecessaryBacktickDecision.unquote("`is`") shouldBe null
                UnnecessaryBacktickDecision.unquote("`typealias`") shouldBe null
                UnnecessaryBacktickDecision.unquote("`typeof`") shouldBe null
                UnnecessaryBacktickDecision.unquote("`class`") shouldBe null
            }

            should("return null for an all-underscore name") {
                UnnecessaryBacktickDecision.unquote("`_`") shouldBe null
                UnnecessaryBacktickDecision.unquote("`__`") shouldBe null
            }

            should("return null for text shorter than a minimal backtick-quoted identifier") {
                UnnecessaryBacktickDecision.unquote("``") shouldBe null
                UnnecessaryBacktickDecision.unquote("`") shouldBe null
                UnnecessaryBacktickDecision.unquote("") shouldBe null
            }
        },
    )
