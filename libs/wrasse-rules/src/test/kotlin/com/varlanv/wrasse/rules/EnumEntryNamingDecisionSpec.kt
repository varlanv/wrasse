package com.varlanv.wrasse.rules

import com.varlanv.wrasse.testing.BaseSpec
import io.kotest.matchers.shouldBe

class EnumEntryNamingDecisionSpec : BaseSpec({
    should("accept PascalCase") {
        EnumEntryNamingDecision.decide("EnumEntry") shouldBe null
    }

    should("accept SCREAMING_SNAKE_CASE") {
        EnumEntryNamingDecision.decide("ENUM_ENTRY") shouldBe null
    }

    should("reject lowerCamelCase") {
        EnumEntryNamingDecision.decide("enumEntry") shouldBe EnumEntryNamingDecision.MESSAGE
    }

    should("reject a mixed-case underscored name") {
        EnumEntryNamingDecision.decide("Enum_Entry") shouldBe EnumEntryNamingDecision.MESSAGE
    }

    should("unquote a backtick-wrapped name before checking casing") {
        EnumEntryNamingDecision.decide("`EnumEntry`") shouldBe null
    }

    should("reject a backtick-wrapped name whose unquoted content fails casing") {
        EnumEntryNamingDecision.decide("`enum entry`") shouldBe EnumEntryNamingDecision.MESSAGE
    }
})
