package com.varlanv.wrasse.rules

import com.varlanv.wrasse.testing.BaseSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe

class CallShapeTextSpec :
    BaseSpec(
        {

            should("parse a zero-argument call") {
                val facts = CallShapeText.parse("TODO()").shouldNotBeNull()
                facts.simpleName shouldBe "TODO"
                facts.argumentCount shouldBe 0
            }

            should("parse a one-argument call") {
                val facts = CallShapeText.parse("""TODO("reason")""").shouldNotBeNull()
                facts.argumentCount shouldBe 1
            }

            should("drop package qualification from the callee") {
                val facts = CallShapeText.parse("pkg.sub.Exception(cause)").shouldNotBeNull()
                facts.simpleName shouldBe "Exception"
            }

            should("not count a comma nested inside a call argument as top-level") {
                val facts = CallShapeText.parse("Exception(mapOf(1 to 2, 3 to 4))").shouldNotBeNull()
                facts.argumentCount shouldBe 1
            }

            should("count two top-level arguments") {
                val facts = CallShapeText.parse("""IllegalStateException("msg", cause)""").shouldNotBeNull()
                facts.argumentCount shouldBe 2
            }

            should("not count a comma inside a string argument as top-level") {
                val facts = CallShapeText.parse("""TODO("a, b")""").shouldNotBeNull()
                facts.argumentCount shouldBe 1
            }

            should("return null for a non-call expression") {
                CallShapeText.parse("e").shouldBeNull()
            }
        },
    )
