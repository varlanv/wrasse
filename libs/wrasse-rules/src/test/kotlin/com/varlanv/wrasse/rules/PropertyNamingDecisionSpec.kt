package com.varlanv.wrasse.rules

import com.varlanv.wrasse.testing.BaseSpec
import io.kotest.matchers.shouldBe

class PropertyNamingDecisionSpec :
    BaseSpec(
        {
            should("accept lowerCamelCase non-const property") {
                PropertyNamingDecision
                        .decide("foo", hasConst = false, hasCustomGetter = false, isTopLevelVal = false, isObjectMemberVal = false) shouldBe
                    null
            }

            should("reject PascalCase non-const property") {
                PropertyNamingDecision
                        .decide("Foo", hasConst = false, hasCustomGetter = false, isTopLevelVal = false, isObjectMemberVal = false) shouldBe
                    PropertyNamingDecision.MESSAGE
            }

            should("accept SCREAMING_SNAKE_CASE const property") {
                PropertyNamingDecision
                        .decide("FOO_BAR", hasConst = true, hasCustomGetter = false, isTopLevelVal = false, isObjectMemberVal = false) shouldBe
                    null
            }

            should("reject camelCase const property") {
                PropertyNamingDecision
                        .decide("fooBar", hasConst = true, hasCustomGetter = false, isTopLevelVal = false, isObjectMemberVal = false) shouldBe
                    PropertyNamingDecision.CONST_MESSAGE
            }

            should("accept serialVersionUID regardless of casing when const") {
                PropertyNamingDecision
                        .decide(
                            "serialVersionUID",
                            hasConst = true,
                            hasCustomGetter = false,
                            isTopLevelVal = false,
                            isObjectMemberVal = false,
                        ) shouldBe
                    null
            }

            should("accept a backing property (leading underscore) unconditionally") {
                PropertyNamingDecision
                        .decide("_foo", hasConst = false, hasCustomGetter = false, isTopLevelVal = false, isObjectMemberVal = false) shouldBe
                    null
            }

            should("accept any casing for a property with a custom getter") {
                PropertyNamingDecision
                        .decide("Foo", hasConst = false, hasCustomGetter = true, isTopLevelVal = false, isObjectMemberVal = false) shouldBe
                    null
            }

            should("accept any casing for a top-level val") {
                PropertyNamingDecision
                        .decide("Foo", hasConst = false, hasCustomGetter = false, isTopLevelVal = true, isObjectMemberVal = false) shouldBe
                    null
            }

            should("accept any casing for an object-member val") {
                PropertyNamingDecision
                        .decide("Foo", hasConst = false, hasCustomGetter = false, isTopLevelVal = false, isObjectMemberVal = true) shouldBe
                    null
            }

            should("accept a backtick-wrapped keyword") {
                PropertyNamingDecision
                        .decide("`val`", hasConst = false, hasCustomGetter = false, isTopLevelVal = false, isObjectMemberVal = false) shouldBe
                    null
            }
        },
    )
