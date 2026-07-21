package com.varlanv.wrasse.plugin

import com.varlanv.wrasse.testing.BaseSpec
import io.kotest.matchers.shouldBe

/**
 * Locks the declared id set for single-id rules and for the fused
 * [com.varlanv.wrasse.rules.ImportEngine]/[com.varlanv.wrasse.rules.ModifierEngine] behind
 * [registeredRuleGroups], so an accidental id typo or omission fails loudly here rather than
 * silently going unconfigurable.
 */
class RuleRegistrationOrderSpec :
    BaseSpec(
        {

            should(
                "register backing-property-naming, class-naming, complex-condition, " +
                    "destructuring-declaration-with-too-many-entries, empty-default-constructor, enum-entry-naming, " +
                    "explicit-it-lambda-parameter, file-size, filename, function-naming, if-else-bracing, " +
                    "long-numerical-values, long-parameter-list, no-empty-class-body, " +
                    "no-empty-parens-before-trailing-lambda, no-semicolons, no-unit-return, package-naming, " +
                    "property-naming, range-conventional, redundant-constructor-keyword, trailing-newline, " +
                    "trivial-accessors, unnecessary-backticks, unnecessary-inheritance, and when-entry-bracing " +
                    "as single-id rules",
            ) {
                val ids = registeredRules().map { it.id }

                ids shouldBe
                    listOf(
                        "backing-property-naming",
                        "class-naming",
                        "complex-condition",
                        "destructuring-declaration-with-too-many-entries",
                        "empty-default-constructor",
                        "enum-entry-naming",
                        "explicit-it-lambda-parameter",
                        "file-size",
                        "filename",
                        "function-naming",
                        "if-else-bracing",
                        "long-numerical-values",
                        "long-parameter-list",
                        "no-empty-class-body",
                        "no-empty-parens-before-trailing-lambda",
                        "no-semicolons",
                        "no-unit-return",
                        "package-naming",
                        "property-naming",
                        "range-conventional",
                        "redundant-constructor-keyword",
                        "trailing-newline",
                        "trivial-accessors",
                        "unnecessary-backticks",
                        "unnecessary-inheritance",
                        "when-entry-bracing",
                    )
            }

            should(
                "register the import engine, modifier engine, function-name-length engine, function-metrics " +
                    "engine, and class-metrics engine groups with exactly their own ids",
            ) {
                val groups = registeredRuleGroups()

                groups.map { it.ids } shouldBe
                    listOf(
                        setOf("no-unused-imports", "no-wildcard-imports", "import-ordering", "no-unnecessary-fqn"),
                        setOf("modifier-order", "redundant-visibility-modifier"),
                        setOf("function-name-max-length", "function-name-min-length"),
                        setOf("return-count", "throws-count", "nested-block-depth", "cyclomatic-complexity", "long-method"),
                        setOf("too-many-functions", "large-class"),
                    )
            }
        },
    )
