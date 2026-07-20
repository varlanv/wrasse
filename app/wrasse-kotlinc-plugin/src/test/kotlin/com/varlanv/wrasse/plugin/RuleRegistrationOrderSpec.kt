package com.varlanv.wrasse.plugin

import com.varlanv.wrasse.testing.BaseSpec
import io.kotest.matchers.shouldBe

/**
 * Locks the declared id set for single-id rules and for the fused
 * [com.varlanv.wrasse.rules.ImportEngine] behind [registeredRuleGroups], so an accidental id
 * typo or omission fails loudly here rather than silently going unconfigurable.
 */
class RuleRegistrationOrderSpec : BaseSpec({

    should(
        "register if-else-bracing, modifier-order, no-empty-class-body, no-empty-parens-before-trailing-lambda, " +
            "no-semicolons, no-unit-return, redundant-visibility-modifier, trailing-newline, and when-entry-bracing " +
            "as single-id rules"
    ) {
        val ids = registeredRules().map { it.id }

        ids shouldBe
            listOf(
                "if-else-bracing",
                "modifier-order",
                "no-empty-class-body",
                "no-empty-parens-before-trailing-lambda",
                "no-semicolons",
                "no-unit-return",
                "redundant-visibility-modifier",
                "trailing-newline",
                "when-entry-bracing",
            )
    }

    should("register the import engine group with exactly the four import-family ids") {
        val groups = registeredRuleGroups()

        groups.map { it.ids } shouldBe
            listOf(setOf("no-unused-imports", "no-wildcard-imports", "import-ordering", "no-unnecessary-fqn"))
    }
})
