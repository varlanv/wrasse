package com.varlanv.wrasse.rules

import com.varlanv.wrasse.testing.BaseSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe

class CatchParameterTextSpec :
    BaseSpec(
        {

            should("parse a plain name and type") {
                val facts = CatchParameterText.parse("(e: Exception)").shouldNotBeNull()
                facts.name shouldBe "e"
                facts.typeText shouldBe "Exception"
            }

            should("parse with extra internal whitespace") {
                val facts = CatchParameterText.parse("( e  :  IOException )").shouldNotBeNull()
                facts.name shouldBe "e"
                facts.typeText shouldBe "IOException"
            }

            should("unquote a backtick-wrapped name") {
                val facts = CatchParameterText.parse("(`my exception`: Exception)").shouldNotBeNull()
                facts.name shouldBe "my exception"
            }

            should("compute the name's relative offset within the list text") {
                val facts = CatchParameterText.parse("(ex: RuntimeException)").shouldNotBeNull()
                facts.nameStart shouldBe 1
                facts.nameEnd shouldBe 3
            }

            should("return null for an annotated catch parameter") {
                CatchParameterText.parse("""(@Suppress("x") e: Exception)""").shouldBeNull()
            }

            should("return null for non-parenthesized text") {
                CatchParameterText.parse("e: Exception").shouldBeNull()
            }
        },
    )
