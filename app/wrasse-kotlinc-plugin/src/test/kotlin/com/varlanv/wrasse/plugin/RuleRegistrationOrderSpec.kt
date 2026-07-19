package com.varlanv.wrasse.plugin

import com.varlanv.wrasse.testing.BaseSpec
import io.kotest.matchers.shouldBe

class RuleRegistrationOrderSpec : BaseSpec({

    should("register import-ordering after no-unused-imports and no-wildcard-imports") {
        val ids = registeredRules().map { it.id }

        ids shouldBe listOf(
            "no-semicolons",
            "no-wildcard-imports",
            "trailing-newline",
            "no-unused-imports",
            "import-ordering",
        )
    }
})
