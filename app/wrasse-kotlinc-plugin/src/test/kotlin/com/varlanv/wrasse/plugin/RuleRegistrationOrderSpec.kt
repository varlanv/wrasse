package com.varlanv.wrasse.plugin

import com.varlanv.wrasse.testing.BaseSpec
import io.kotest.matchers.shouldBe

/**
 * Locks the declared id set for single-id rules and for the fused
 * [com.varlanv.wrasse.rules.ImportEngine]/[com.varlanv.wrasse.rules.ModifierEngine]/
 * [com.varlanv.wrasse.rules.KdocEngine]/[com.varlanv.wrasse.rules.EmptyBlockEngine]/
 * [com.varlanv.wrasse.rules.CommentPositionEngine] behind [registeredRuleGroups], so an
 * accidental id typo or omission fails loudly here rather than silently going unconfigurable.
 */
class RuleRegistrationOrderSpec : BaseSpec({

    should(
        "register also-could-be-apply, backing-property-naming, boolean-expressions, class-naming, collapse-if, " +
            "comment-over-private-declaration, " +
            "complex-condition, constructor-parameter-naming, custom-label, debug-print, " +
            "destructuring-declaration-with-too-many-entries, " +
            "double-negative, empty-catch-block, empty-default-constructor, empty-function-block, empty-kotlin-file, " +
            "empty-when-block, enum-entry-naming, equals-null-call, " +
            "exception-raised-in-unexpected-location, explicit-it-lambda-multiple-parameters, " +
            "explicit-it-lambda-parameter, extension-functions-same-name, file-size, filename, forbidden-calls, " +
            "forbidden-comment, forbidden-expression-body-functions, function-expression-body, function-naming, " +
            "function-only-returning-constant, function-parameter-naming, getter-setter-fields, " +
            "global-coroutine-usage, if-else-bracing, " +
            "instance-of-check-for-exception, invalid-range, kdoc-deprecated-tag, kdoc-references-non-public-property, " +
            "lambda-parameter-naming, lambda-return, " +
            "long-numerical-values, long-parameter-list, loop-with-too-many-jump-statements, magic-number, " +
            "may-be-constant, missing-package-declaration, mixed-condition-operators, named-arguments, nested-classes-visibility, " +
            "no-consecutive-comments, no-empty-class-body, " +
            "no-empty-parens-before-trailing-lambda, " +
            "no-semicolons, no-single-line-block-comment, no-unit-return, not-implemented-declaration, package-naming, " +
            "print-stack-trace, " +
            "property-naming, range-conventional, redundant-constructor-keyword, redundant-to-string-in-template, " +
            "rethrow-caught-exception, " +
            "safe-cast, string-concatenation, string-should-be-raw-string, swallowed-exception, sync-in-async, " +
            "throwing-exception-in-main, " +
            "too-generic-exception-caught, " +
            "too-generic-exception-thrown, trailing-newline, trim-multiline-raw-string, trivial-accessors, " +
            "unconditional-jump-statement-in-loop, unnecessary-backticks, unnecessary-inheritance, " +
            "unnecessary-part-of-binary-expression, unused-parameter, " +
            "unused-private-class, use-let, useless-postfix-expression, variable-name-max-length, when-entry-bracing, " +
            "and when-must-have-else as single-id rules",
    ) {
        val ids = registeredRules().map { it.id }

        ids shouldBe
            listOf(
                "also-could-be-apply",
                "backing-property-naming",
                "boolean-expressions",
                "class-naming",
                "collapse-if",
                "comment-over-private-declaration",
                "complex-condition",
                "constructor-parameter-naming",
                "custom-label",
                "debug-print",
                "destructuring-declaration-with-too-many-entries",
                "double-negative",
                "empty-catch-block",
                "empty-default-constructor",
                "empty-function-block",
                "empty-kotlin-file",
                "empty-when-block",
                "enum-entry-naming",
                "equals-null-call",
                "exception-raised-in-unexpected-location",
                "explicit-it-lambda-multiple-parameters",
                "explicit-it-lambda-parameter",
                "extension-functions-same-name",
                "file-size",
                "filename",
                "forbidden-calls",
                "forbidden-comment",
                "forbidden-expression-body-functions",
                "function-expression-body",
                "function-naming",
                "function-only-returning-constant",
                "function-parameter-naming",
                "getter-setter-fields",
                "global-coroutine-usage",
                "if-else-bracing",
                "instance-of-check-for-exception",
                "invalid-range",
                "kdoc-deprecated-tag",
                "kdoc-references-non-public-property",
                "lambda-parameter-naming",
                "lambda-return",
                "long-numerical-values",
                "long-parameter-list",
                "loop-with-too-many-jump-statements",
                "magic-number",
                "may-be-constant",
                "missing-package-declaration",
                "mixed-condition-operators",
                "named-arguments",
                "nested-classes-visibility",
                "no-consecutive-comments",
                "no-empty-class-body",
                "no-empty-parens-before-trailing-lambda",
                "no-semicolons",
                "no-single-line-block-comment",
                "no-unit-return",
                "not-implemented-declaration",
                "package-naming",
                "print-stack-trace",
                "property-naming",
                "range-conventional",
                "redundant-constructor-keyword",
                "redundant-to-string-in-template",
                "rethrow-caught-exception",
                "safe-cast",
                "string-concatenation",
                "string-should-be-raw-string",
                "swallowed-exception",
                "sync-in-async",
                "throwing-exception-in-main",
                "too-generic-exception-caught",
                "too-generic-exception-thrown",
                "trailing-newline",
                "trim-multiline-raw-string",
                "trivial-accessors",
                "unconditional-jump-statement-in-loop",
                "unnecessary-backticks",
                "unnecessary-inheritance",
                "unnecessary-part-of-binary-expression",
                "unused-parameter",
                "unused-private-class",
                "use-let",
                "useless-postfix-expression",
                "variable-name-max-length",
                "when-entry-bracing",
                "when-must-have-else",
            )
    }

    should(
        "register the import engine, modifier engine, function-name-length engine, function-metrics " +
            "engine, class-metrics engine, kdoc engine, empty-block engine, and comment-position engine groups " +
            "with exactly their own ids",
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
                setOf(
                    "empty-if-block",
                    "empty-else-block",
                    "empty-for-block",
                    "empty-while-block",
                    "empty-do-while-block",
                    "empty-finally-block",
                    "empty-try-block",
                    "empty-init-block",
                    "empty-secondary-constructor",
                ),
                setOf(
                    "kdoc-placement",
                    "type-argument-comment",
                    "type-parameter-comment",
                    "value-argument-comment",
                    "value-parameter-comment",
                ),
            )
    }
})
