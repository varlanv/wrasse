package com.varlanv.wrasse.plugin

import com.varlanv.wrasse.testing.BaseSpec
import io.kotest.matchers.shouldBe

/**
 * Locks the declared id set for single-id rules and for the fused
 * [com.varlanv.wrasse.rules.ImportEngine]/[com.varlanv.wrasse.rules.ModifierEngine]/
 * [com.varlanv.wrasse.rules.KdocEngine] behind [registeredRuleGroups], so an accidental id typo
 * or omission fails loudly here rather than silently going unconfigurable.
 */
class RuleRegistrationOrderSpec :
    BaseSpec(
        {

            should(
                "register also-could-be-apply, backing-property-naming, class-naming, comment-over-private-declaration, " +
                    "complex-condition, destructuring-declaration-with-too-many-entries, empty-catch-block, " +
                    "empty-default-constructor, enum-entry-naming, equals-null-call, " +
                    "exception-raised-in-unexpected-location, explicit-it-lambda-multiple-parameters, " +
                    "explicit-it-lambda-parameter, file-size, filename, forbidden-comment, function-naming, " +
                    "function-only-returning-constant, if-else-bracing, instance-of-check-for-exception, " +
                    "kdoc-deprecated-tag, long-numerical-values, long-parameter-list, may-be-constant, " +
                    "nested-classes-visibility, no-empty-class-body, no-empty-parens-before-trailing-lambda, " +
                    "no-semicolons, no-unit-return, not-implemented-declaration, package-naming, print-stack-trace, " +
                    "property-naming, range-conventional, redundant-constructor-keyword, rethrow-caught-exception, " +
                    "safe-cast, string-should-be-raw-string, swallowed-exception, too-generic-exception-caught, " +
                    "too-generic-exception-thrown, trailing-newline, trim-multiline-raw-string, trivial-accessors, " +
                    "unnecessary-backticks, unnecessary-inheritance, unused-parameter, unused-private-class, use-let, " +
                    "and when-entry-bracing as single-id rules",
            ) {
                val ids = registeredRules().map { it.id }

                ids shouldBe
                    listOf(
                        "also-could-be-apply",
                        "backing-property-naming",
                        "class-naming",
                        "comment-over-private-declaration",
                        "complex-condition",
                        "destructuring-declaration-with-too-many-entries",
                        "empty-catch-block",
                        "empty-default-constructor",
                        "enum-entry-naming",
                        "equals-null-call",
                        "exception-raised-in-unexpected-location",
                        "explicit-it-lambda-multiple-parameters",
                        "explicit-it-lambda-parameter",
                        "file-size",
                        "filename",
                        "forbidden-comment",
                        "function-naming",
                        "function-only-returning-constant",
                        "if-else-bracing",
                        "instance-of-check-for-exception",
                        "kdoc-deprecated-tag",
                        "long-numerical-values",
                        "long-parameter-list",
                        "may-be-constant",
                        "nested-classes-visibility",
                        "no-empty-class-body",
                        "no-empty-parens-before-trailing-lambda",
                        "no-semicolons",
                        "no-unit-return",
                        "not-implemented-declaration",
                        "package-naming",
                        "print-stack-trace",
                        "property-naming",
                        "range-conventional",
                        "redundant-constructor-keyword",
                        "rethrow-caught-exception",
                        "safe-cast",
                        "string-should-be-raw-string",
                        "swallowed-exception",
                        "too-generic-exception-caught",
                        "too-generic-exception-thrown",
                        "trailing-newline",
                        "trim-multiline-raw-string",
                        "trivial-accessors",
                        "unnecessary-backticks",
                        "unnecessary-inheritance",
                        "unused-parameter",
                        "unused-private-class",
                        "use-let",
                        "when-entry-bracing",
                    )
            }

            should(
                "register the import engine, modifier engine, function-name-length engine, function-metrics " +
                    "engine, class-metrics engine, and kdoc engine groups with exactly their own ids",
            ) {
                val groups = registeredRuleGroups()

                groups.map { it.ids } shouldBe
                    listOf(
                        setOf("no-unused-imports", "no-wildcard-imports", "import-ordering", "no-unnecessary-fqn"),
                        setOf("modifier-order", "redundant-visibility-modifier"),
                        setOf("function-name-max-length", "function-name-min-length"),
                        setOf("return-count", "throws-count", "nested-block-depth", "cyclomatic-complexity", "long-method"),
                        setOf("too-many-functions", "large-class"),
                        setOf(
                            "undocumented-public-class",
                            "undocumented-public-function",
                            "undocumented-public-property",
                            "kdoc-tag-mismatch",
                        ),
                    )
            }
        },
    )
