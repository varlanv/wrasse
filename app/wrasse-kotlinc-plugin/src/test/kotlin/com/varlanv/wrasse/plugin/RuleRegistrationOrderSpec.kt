package com.varlanv.wrasse.plugin

import com.varlanv.wrasse.testing.BaseSpec
import io.kotest.matchers.shouldBe

/**
 * [registeredRules] no longer has a registration-order constraint to lock — that constraint
 * existed only because `no-unused-imports`/`no-wildcard-imports`/`import-ordering` were three
 * independent rules composing via `afterFile` order (design.md §8, retired). Now that they are
 * one fused [com.varlanv.wrasse.rules.ImportEngine] behind [registeredRuleGroups], what still
 * needs locking is the engine's declared id set, so an accidental id typo or omission fails
 * loudly here rather than silently going unconfigurable.
 */
class RuleRegistrationOrderSpec : BaseSpec({

    should("register no-empty-class-body, no-semicolons, no-unit-return, and trailing-newline as single-id rules") {
        val ids = registeredRules().map { it.id }

        ids shouldBe listOf("no-empty-class-body", "no-semicolons", "no-unit-return", "trailing-newline")
    }

    should("register the import engine group with exactly the four import-family ids") {
        val groups = registeredRuleGroups()

        groups.map { it.ids } shouldBe
            listOf(setOf("no-unused-imports", "no-wildcard-imports", "import-ordering", "no-unnecessary-fqn"))
    }
})
