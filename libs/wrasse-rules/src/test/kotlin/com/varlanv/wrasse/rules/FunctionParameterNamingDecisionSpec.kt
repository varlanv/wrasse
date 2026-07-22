package com.varlanv.wrasse.rules

import com.varlanv.wrasse.testing.BaseSpec
import io.kotest.matchers.shouldBe

class FunctionParameterNamingDecisionSpec :
    BaseSpec(
        {

            should("not report a lowerCamelCase parameter") {
                FunctionParameterNamingDecision.decide("userName", isOverride = false) shouldBe null
            }

            should("report a PascalCase parameter") {
                FunctionParameterNamingDecision.decide("UserName", isOverride = false) shouldBe FunctionParameterNamingDecision.MESSAGE
            }

            should("report a SCREAMING_SNAKE_CASE parameter") {
                FunctionParameterNamingDecision.decide("USER_NAME", isOverride = false) shouldBe FunctionParameterNamingDecision.MESSAGE
            }

            should("not report an override's badly-cased parameter") {
                FunctionParameterNamingDecision.decide("UserName", isOverride = true) shouldBe null
            }

            should("not report a backtick-wrapped keyword") {
                FunctionParameterNamingDecision.decide("`is`", isOverride = false) shouldBe null
            }

            should("report a backtick-wrapped non-keyword that is badly cased") {
                FunctionParameterNamingDecision.decide("`Bad Name`", isOverride = false) shouldBe FunctionParameterNamingDecision.MESSAGE
            }
        },
    )
