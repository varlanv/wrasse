# detekt Rules Catalog (Part 1: style + naming)

---

## Style rules

### also-could-be-apply
- **Name:** AlsoCouldBeApply
- **Source:** detekt-rules-style/src/main/kotlin/dev/detekt/rules/style/AlsoCouldBeApply.kt
- **Module:** detekt-rules-style
- **Category:** style
- **What it checks:** Detects `also` blocks whose body statements all start with `it`, suggesting replacement with `apply`.
- **Needs semantic info:** none
- **PSI types visited:** visitCallExpression
- **Tree traversal:** expression-level
- **Complexity:** 2 (walks lambda body statements)
- **Config options:** none
- **Has ktlint equivalent:** none
- **Uses Analysis API:** no
- **Notes:** --

### braces-on-if-statements
- **Name:** BracesOnIfStatements
- **Source:** detekt-rules-style/src/main/kotlin/dev/detekt/rules/style/BracesOnIfStatements.kt
- **Module:** detekt-rules-style
- **Category:** style
- **What it checks:** Enforces a configurable brace policy (always/never/consistent/necessary) on `if`/`else` branches.
- **Needs semantic info:** none
- **PSI types visited:** visitIfExpression
- **Tree traversal:** expression-level
- **Complexity:** 2 (walks the `if`-`else if`-`else` chain via a `walk` loop)
- **Config options:** `singleLine` (BracePolicy, default "never"), `multiLine` (BracePolicy, default "always")
- **Has ktlint equivalent:** none
- **Uses Analysis API:** no
- **Notes:** Contains a private `BracePolicy` enum parsed from config strings.

### braces-on-when-statements
- **Name:** BracesOnWhenStatements
- **Source:** detekt-rules-style/src/main/kotlin/dev/detekt/rules/style/BracesOnWhenStatements.kt
- **Module:** detekt-rules-style
- **Category:** style
- **What it checks:** Enforces a configurable brace policy (always/never/consistent/necessary) on `when` branches.
- **Needs semantic info:** none
- **PSI types visited:** visitWhenExpression
- **Tree traversal:** expression-level
- **Complexity:** 2 (iterates over when entries and inspects arrow siblings for line-break detection)
- **Config options:** `singleLine` (BracePolicy, default "necessary"), `multiLine` (BracePolicy, default "consistent")
- **Has ktlint equivalent:** none
- **Uses Analysis API:** no
- **Notes:** Shares the same `BracePolicy` enum pattern as BracesOnIfStatements, but is a separate class.

### cascading-call-wrapping
- **Name:** CascadingCallWrapping
- **Source:** detekt-rules-style/src/main/kotlin/dev/detekt/rules/style/CascadingCallWrapping.kt
- **Module:** detekt-rules-style
- **Category:** style
- **What it checks:** Requires that all chained calls are wrapped to a new line if a preceding chained call was wrapped.
- **Needs semantic info:** none
- **PSI types visited:** visitQualifiedExpression, visitBinaryExpression
- **Tree traversal:** expression-level
- **Complexity:** 2 (walks receiver chain checking newlines between segments)
- **Config options:** `includeElvis` (Boolean, default true)
- **Has ktlint equivalent:** none
- **Uses Analysis API:** no
- **Notes:** Handles both dot-qualified and elvis expressions.

### destructuring-declaration-with-too-many-entries
- **Name:** DestructuringDeclarationWithTooManyEntries
- **Source:** detekt-rules-style/src/main/kotlin/dev/detekt/rules/style/DestructuringDeclarationWithTooManyEntries.kt
- **Module:** detekt-rules-style
- **Category:** style
- **What it checks:** Reports destructuring declarations exceeding a configurable maximum number of entries.
- **Needs semantic info:** none
- **PSI types visited:** visitDestructuringDeclaration
- **Tree traversal:** declaration-level
- **Complexity:** 1
- **Config options:** `maxDestructuringEntries` (Int, default 3)
- **Has ktlint equivalent:** none
- **Uses Analysis API:** no
- **Notes:** Active by default since 1.21.0.

### equals-null-call
- **Name:** EqualsNullCall
- **Source:** detekt-rules-style/src/main/kotlin/dev/detekt/rules/style/EqualsNullCall.kt
- **Module:** detekt-rules-style
- **Category:** style
- **What it checks:** Reports usage of `.equals(null)` suggesting `== null` instead.
- **Needs semantic info:** none
- **PSI types visited:** visitCallExpression
- **Tree traversal:** expression-level
- **Complexity:** 1
- **Config options:** none
- **Has ktlint equivalent:** none
- **Uses Analysis API:** no
- **Notes:** Matches purely on callee text `"equals"` and argument text `"null"`.

### equals-on-signature-line
- **Name:** EqualsOnSignatureLine
- **Source:** detekt-rules-style/src/main/kotlin/dev/detekt/rules/style/EqualsOnSignatureLine.kt
- **Module:** detekt-rules-style
- **Category:** style
- **What it checks:** Requires that the `=` sign of an expression-style function is on the same line as the signature.
- **Needs semantic info:** none
- **PSI types visited:** visitNamedFunction
- **Tree traversal:** declaration-level
- **Complexity:** 1 (walks preceding siblings of the equals token for whitespace)
- **Config options:** none
- **Has ktlint equivalent:** none
- **Uses Analysis API:** no
- **Notes:** --

### explicit-it-lambda-multiple-parameters
- **Name:** ExplicitItLambdaMultipleParameters
- **Source:** detekt-rules-style/src/main/kotlin/dev/detekt/rules/style/ExplicitItLambdaMultipleParameters.kt
- **Module:** detekt-rules-style
- **Category:** style
- **What it checks:** Reports lambdas with multiple parameters where one of them is named `it`.
- **Needs semantic info:** none
- **PSI types visited:** visitLambdaExpression
- **Tree traversal:** declaration-level
- **Complexity:** 1
- **Config options:** none
- **Has ktlint equivalent:** none
- **Uses Analysis API:** no
- **Notes:** Active by default since 1.21.0.

### explicit-it-lambda-parameter
- **Name:** ExplicitItLambdaParameter
- **Source:** detekt-rules-style/src/main/kotlin/dev/detekt/rules/style/ExplicitItLambdaParameter.kt
- **Module:** detekt-rules-style
- **Category:** style
- **What it checks:** Reports single-parameter lambdas where the parameter is explicitly named `it`, which is redundant.
- **Needs semantic info:** none
- **PSI types visited:** visitLambdaExpression
- **Tree traversal:** declaration-level
- **Complexity:** 1
- **Config options:** none
- **Has ktlint equivalent:** none
- **Uses Analysis API:** no
- **Notes:** Active by default since 1.21.0.

### forbidden-comment
- **Name:** ForbiddenComment
- **Source:** detekt-rules-style/src/main/kotlin/dev/detekt/rules/style/ForbiddenComment.kt
- **Module:** detekt-rules-style
- **Category:** style
- **What it checks:** Reports code comments matching configurable forbidden patterns (default: TODO:, FIXME:, STOPSHIP:).
- **Needs semantic info:** none
- **PSI types visited:** visitComment, visitKtFile
- **Tree traversal:** file-level
- **Complexity:** 2 (collects all KDocSection descendants from the file and applies regex matching)
- **Config options:** `comments` (List<ValuesWithReason>, default FIXME:/STOPSHIP:/TODO:), `allowedPatterns` (Regex, default "")
- **Has ktlint equivalent:** none
- **Uses Analysis API:** no
- **Notes:** Uses `collectDescendantsOfType<KDocSection>()` for KDoc; includes a comment-content stripping utility function.

### forbidden-suppress
- **Name:** ForbiddenSuppress
- **Source:** detekt-rules-style/src/main/kotlin/dev/detekt/rules/style/ForbiddenSuppress.kt
- **Module:** detekt-rules-style
- **Category:** style
- **What it checks:** Reports `@Suppress`/`@SuppressWarnings` annotations that suppress rules listed in the forbidden set.
- **Needs semantic info:** none
- **PSI types visited:** visitAnnotationEntry
- **Tree traversal:** declaration-level
- **Complexity:** 2 (walks annotation children to extract suppressed rule names)
- **Config options:** `rules` (List<String>, default empty)
- **Has ktlint equivalent:** none
- **Uses Analysis API:** no
- **Notes:** This rule itself cannot be suppressed by design.

### function-only-returning-constant
- **Name:** FunctionOnlyReturningConstant
- **Source:** detekt-rules-style/src/main/kotlin/dev/detekt/rules/style/FunctionOnlyReturningConstant.kt
- **Module:** detekt-rules-style
- **Category:** style
- **What it checks:** Reports functions whose body is only a single constant expression, suggesting `const val` instead.
- **Needs semantic info:** none
- **PSI types visited:** visitNamedFunction
- **Tree traversal:** declaration-level
- **Complexity:** 2 (inspects body expression / return expression for constant-ness, checks containing class for interface)
- **Config options:** `ignoreOverridableFunction` (Boolean, default true), `ignoreActualFunction` (Boolean, default true), `excludedFunctions` (List<Regex>, default empty)
- **Has ktlint equivalent:** none
- **Uses Analysis API:** no
- **Notes:** --

### max-line-length
- **Name:** MaxLineLength
- **Source:** detekt-rules-style/src/main/kotlin/dev/detekt/rules/style/MaxLineLength.kt
- **Module:** detekt-rules-style
- **Category:** style
- **What it checks:** Reports lines exceeding a configurable maximum length, with options to exclude packages, imports, comments, and raw strings.
- **Needs semantic info:** none
- **PSI types visited:** visitKtFile
- **Tree traversal:** file-level
- **Complexity:** 2 (iterates all lines, finds PSI elements at offsets for raw-string exclusion)
- **Config options:** `maxLineLength` (Int, default 120), `excludePackageStatements` (Boolean, default true), `excludeImportStatements` (Boolean, default true), `excludeCommentStatements` (Boolean, default false), `excludeRawStrings` (Boolean, default true)
- **Has ktlint equivalent:** `max-line-length`
- **Uses Analysis API:** no
- **Notes:** Also excludes lines ending with URLs automatically. Active by default since 1.0.0.

### may-be-constant
- **Name:** MayBeConstant
- **Source:** detekt-rules-style/src/main/kotlin/dev/detekt/rules/style/MayBeConstant.kt
- **Module:** detekt-rules-style
- **Category:** style
- **What it checks:** Reports `val` properties that could be `const val` based on their initializer being a compile-time constant.
- **Needs semantic info:** none
- **PSI types visited:** visitKtFile, visitObjectDeclaration, visitProperty
- **Tree traversal:** file-level
- **Complexity:** 2 (tracks top-level and companion-object constants across the file, checks binary expressions recursively)
- **Config options:** none
- **Has ktlint equivalent:** none
- **Uses Analysis API:** no
- **Notes:** Checks for string, boolean, integer, character, and float constants, including binary expressions of constants. Active by default since 1.2.0.

### modifier-order
- **Name:** ModifierOrder
- **Source:** detekt-rules-style/src/main/kotlin/dev/detekt/rules/style/ModifierOrder.kt
- **Module:** detekt-rules-style
- **Category:** style
- **What it checks:** Reports modifiers not in the order specified by Kotlin coding conventions.
- **Needs semantic info:** none
- **PSI types visited:** visitModifierList
- **Tree traversal:** declaration-level
- **Complexity:** 1 (compares modifier list against a hardcoded order)
- **Config options:** none
- **Has ktlint equivalent:** `modifier-order`
- **Uses Analysis API:** no
- **Notes:** Active by default since 1.0.0.

### multiline-raw-string-indentation
- **Name:** MultilineRawStringIndentation
- **Source:** detekt-rules-style/src/main/kotlin/dev/detekt/rules/style/MultilineRawStringIndentation.kt
- **Module:** detekt-rules-style
- **Category:** style
- **What it checks:** Ensures multiline raw strings have consistent indentation relative to their enclosing expression.
- **Needs semantic info:** none
- **PSI types visited:** visitStringTemplateExpression
- **Tree traversal:** expression-level
- **Complexity:** 2 (computes indentation per line, validates content and closing quotes)
- **Config options:** `indentSize` (Int, default 4), `trimmingMethods` (List<String>, default ["trimIndent", "trimMargin"])
- **Has ktlint equivalent:** `string-template-indent`
- **Uses Analysis API:** no
- **Notes:** Explicitly warns about overlap with ktlint's `StringTemplateIndent`.

### nested-classes-visibility
- **Name:** NestedClassesVisibility
- **Source:** detekt-rules-style/src/main/kotlin/dev/detekt/rules/style/NestedClassesVisibility.kt
- **Module:** detekt-rules-style
- **Category:** style
- **What it checks:** Reports explicitly `public` nested classes inside `internal` parent classes, since the nested class is effectively internal.
- **Needs semantic info:** none
- **PSI types visited:** visitClass
- **Tree traversal:** declaration-level
- **Complexity:** 2 (filters nested class declarations checking modifiers)
- **Config options:** none
- **Has ktlint equivalent:** none
- **Uses Analysis API:** no
- **Notes:** Active by default since 1.16.0.

### new-line-at-end-of-file
- **Name:** NewLineAtEndOfFile
- **Source:** detekt-rules-style/src/main/kotlin/dev/detekt/rules/style/NewLineAtEndOfFile.kt
- **Module:** detekt-rules-style
- **Category:** style
- **What it checks:** Reports files that do not end with a newline character.
- **Needs semantic info:** none
- **PSI types visited:** visitKtFile
- **Tree traversal:** file-level
- **Complexity:** 1
- **Config options:** none
- **Has ktlint equivalent:** `final-newline`
- **Uses Analysis API:** no
- **Notes:** Active by default since 1.0.0.

### no-tabs
- **Name:** NoTabs
- **Source:** detekt-rules-style/src/main/kotlin/dev/detekt/rules/style/NoTabs.kt
- **Module:** detekt-rules-style
- **Category:** style
- **What it checks:** Reports usage of tab characters outside of string literals.
- **Needs semantic info:** none
- **PSI types visited:** visitWhiteSpace
- **Tree traversal:** file-level
- **Complexity:** 1
- **Config options:** none
- **Has ktlint equivalent:** `no-tabs` (indent)
- **Uses Analysis API:** no
- **Notes:** Allows tabs inside strings but not inside string interpolation expressions.

### range-until-instead-of-range-to
- **Name:** RangeUntilInsteadOfRangeTo
- **Source:** detekt-rules-style/src/main/kotlin/dev/detekt/rules/style/RangeUntilInsteadOfRangeTo.kt
- **Module:** detekt-rules-style
- **Category:** style
- **What it checks:** Reports `..` (rangeTo) calls where the upper bound is `x - 1`, suggesting `..<` (rangeUntil) instead.
- **Needs semantic info:** none
- **PSI types visited:** visitBinaryExpression, visitCallExpression
- **Tree traversal:** expression-level
- **Complexity:** 1
- **Config options:** none
- **Has ktlint equivalent:** none
- **Uses Analysis API:** no
- **Notes:** --

### redundant-constructor-keyword
- **Name:** RedundantConstructorKeyword
- **Source:** detekt-rules-style/src/main/kotlin/dev/detekt/rules/style/RedundantConstructorKeyword.kt
- **Module:** detekt-rules-style
- **Category:** style
- **What it checks:** Reports primary constructors with a redundant `constructor` keyword when no visibility or annotation modifiers are present.
- **Needs semantic info:** none
- **PSI types visited:** visitPrimaryConstructor
- **Tree traversal:** declaration-level
- **Complexity:** 1 (checks for modifier list and preceding comments)
- **Config options:** none
- **Has ktlint equivalent:** none
- **Uses Analysis API:** no
- **Notes:** --

### redundant-visibility-modifier
- **Name:** RedundantVisibilityModifier
- **Source:** detekt-rules-style/src/main/kotlin/dev/detekt/rules/style/RedundantVisibilityModifier.kt
- **Module:** detekt-rules-style
- **Category:** style
- **What it checks:** Reports explicit `public` modifiers (which are default) and `internal` modifiers on members of private/local classes.
- **Needs semantic info:** none
- **PSI types visited:** visitKtFile, visitDeclaration (plus inner visitors: visitClass, visitNamedFunction, visitProperty)
- **Tree traversal:** file-level
- **Complexity:** 2 (uses explicit API mode flag from language version settings, delegates to inner DetektVisitor subclasses)
- **Config options:** none
- **Has ktlint equivalent:** `redundant-modifier` (partial)
- **Uses Analysis API:** no
- **Notes:** Respects Kotlin's Explicit API mode; if active, the rule skips its checks entirely.

### return-count
- **Name:** ReturnCount
- **Source:** detekt-rules-style/src/main/kotlin/dev/detekt/rules/style/ReturnCount.kt
- **Module:** detekt-rules-style
- **Category:** style
- **What it checks:** Reports functions with more return statements than the configured maximum.
- **Needs semantic info:** none
- **PSI types visited:** visitNamedFunction
- **Tree traversal:** declaration-level
- **Complexity:** 2 (collects descendant return expressions, filters by labels, guard clauses, and enclosing function)
- **Config options:** `max` (Int, default 2), `excludedFunctions` (List<Regex>, default ["equals"]), `excludeLabeled` (Boolean, default false), `excludeReturnFromLambda` (Boolean, default true), `excludeGuardClauses` (Boolean, default false)
- **Has ktlint equivalent:** none
- **Uses Analysis API:** no
- **Notes:** Active by default since 1.0.0.

### safe-cast
- **Name:** SafeCast
- **Source:** detekt-rules-style/src/main/kotlin/dev/detekt/rules/style/SafeCast.kt
- **Module:** detekt-rules-style
- **Category:** style
- **What it checks:** Reports `if (x is Type) x else null` patterns that can be replaced with `x as? Type`.
- **Needs semantic info:** none
- **PSI types visited:** visitIfExpression
- **Tree traversal:** expression-level
- **Complexity:** 1 (inspects condition + then/else branches)
- **Config options:** none
- **Has ktlint equivalent:** none
- **Uses Analysis API:** no
- **Notes:** Active by default since 1.0.0.

### spacing-after-package-and-imports
- **Name:** SpacingAfterPackageAndImports
- **Source:** detekt-rules-style/src/main/kotlin/dev/detekt/rules/style/SpacingAfterPackageAndImports.kt
- **Module:** detekt-rules-style
- **Category:** style
- **What it checks:** Verifies exactly one blank line between the package declaration and imports, and between imports and the first top-level declaration.
- **Needs semantic info:** none
- **PSI types visited:** visitKtFile
- **Tree traversal:** file-level
- **Complexity:** 1 (checks newline counts in whitespace siblings)
- **Config options:** none
- **Has ktlint equivalent:** `package-name` / `import-ordering` (partial overlap)
- **Uses Analysis API:** no
- **Notes:** --

### string-should-be-raw-string
- **Name:** StringShouldBeRawString
- **Source:** detekt-rules-style/src/main/kotlin/dev/detekt/rules/style/StringShouldBeRawString.kt
- **Module:** detekt-rules-style
- **Category:** style
- **What it checks:** Reports regular strings with many escape characters that could be replaced with raw strings.
- **Needs semantic info:** none
- **PSI types visited:** visitStringTemplateExpression
- **Tree traversal:** expression-level
- **Complexity:** 2 (walks the binary-expression tree to find concatenated strings, counts escaped characters)
- **Config options:** `maxEscapedCharacterCount` (Int, default 2), `ignoredCharacters` (List<String>, default empty)
- **Has ktlint equivalent:** none
- **Uses Analysis API:** no
- **Notes:** Skips strings used as arguments to `replaceIndent` and `prependIndent`. Traverses left/right subtrees of binary `+` concatenation.

### throws-count
- **Name:** ThrowsCount
- **Source:** detekt-rules-style/src/main/kotlin/dev/detekt/rules/style/ThrowsCount.kt
- **Module:** detekt-rules-style
- **Category:** style
- **What it checks:** Reports functions with more throw statements than the configured maximum.
- **Needs semantic info:** none
- **PSI types visited:** visitNamedFunction
- **Tree traversal:** declaration-level
- **Complexity:** 2 (collects descendant throw expressions, with guard-clause skipping)
- **Config options:** `max` (Int, default 2), `excludeGuardClauses` (Boolean, default false)
- **Has ktlint equivalent:** none
- **Uses Analysis API:** no
- **Notes:** Active by default since 1.0.0.

### trailing-whitespace
- **Name:** TrailingWhitespace
- **Source:** detekt-rules-style/src/main/kotlin/dev/detekt/rules/style/TrailingWhitespace.kt
- **Module:** detekt-rules-style
- **Category:** style
- **What it checks:** Reports lines that end with trailing whitespace characters.
- **Needs semantic info:** none
- **PSI types visited:** visitKtFile
- **Tree traversal:** file-level
- **Complexity:** 2 (iterates all lines, resolves PSI elements to exclude strings)
- **Config options:** none
- **Has ktlint equivalent:** `no-trailing-spaces`
- **Uses Analysis API:** no
- **Notes:** Excludes trailing whitespace inside string literals.

### trim-multiline-raw-string
- **Name:** TrimMultilineRawString
- **Source:** detekt-rules-style/src/main/kotlin/dev/detekt/rules/style/TrimMultilineRawString.kt
- **Module:** detekt-rules-style
- **Category:** style
- **What it checks:** Reports multiline raw strings not followed by `trimMargin()` or `trimIndent()`.
- **Needs semantic info:** none
- **PSI types visited:** visitStringTemplateExpression
- **Tree traversal:** expression-level
- **Complexity:** 2 (checks for trimming call on qualified expression receiver, excludes constant contexts like annotations)
- **Config options:** `trimmingMethods` (List<String>, default ["trimIndent", "trimMargin"])
- **Has ktlint equivalent:** none
- **Uses Analysis API:** no
- **Notes:** Exempts raw strings used as `const val` initializers, annotation arguments, and annotation class default parameter values.

### unnecessary-backticks
- **Name:** UnnecessaryBackticks
- **Source:** detekt-rules-style/src/main/kotlin/dev/detekt/rules/style/UnnecessaryBackticks.kt
- **Module:** detekt-rules-style
- **Category:** style
- **What it checks:** Reports identifiers wrapped in backticks when backticks are unnecessary (the identifier is valid without them).
- **Needs semantic info:** none
- **PSI types visited:** visitKtElement
- **Tree traversal:** expression-level
- **Complexity:** 2 (walks all children of every KtElement checking IDENTIFIER tokens)
- **Config options:** none
- **Has ktlint equivalent:** `unnecessary-parentheses-before-trailing-lambda` (not a direct match; no exact equivalent)
- **Uses Analysis API:** no
- **Notes:** Checks `canPlaceAfterSimpleNameEntry` for string template contexts to avoid breaking interpolation.

### unnecessary-fully-qualified-name
- **Name:** UnnecessaryFullyQualifiedName
- **Source:** detekt-rules-style/src/main/kotlin/dev/detekt/rules/style/UnnecessaryFullyQualifiedName.kt
- **Module:** detekt-rules-style
- **Category:** style
- **What it checks:** Reports fully qualified class and function references that can be replaced with imports.
- **Needs semantic info:** resolved-call, resolved-type
- **PSI types visited:** visitUserType, visitClassLiteralExpression, visitDotQualifiedExpression
- **Tree traversal:** expression-level
- **Complexity:** 3 (requires type/call resolution via Analysis API to resolve symbols, check name collisions, and determine package names)
- **Config options:** none
- **Has ktlint equivalent:** none
- **Uses Analysis API:** yes (implements `RequiresAnalysisApi`; uses `analyze {}`, imports from `org.jetbrains.kotlin.analysis.api`)
- **Notes:** Checks for name collisions including type parameter shadowing. Skips imports, packages, and string literals. The most complex rule in this batch due to full Analysis API usage.

### unnecessary-inheritance
- **Name:** UnnecessaryInheritance
- **Source:** detekt-rules-style/src/main/kotlin/dev/detekt/rules/style/UnnecessaryInheritance.kt
- **Module:** detekt-rules-style
- **Category:** style
- **What it checks:** Reports classes explicitly inheriting from `Any()` or `Object()`, which is unnecessary.
- **Needs semantic info:** none
- **PSI types visited:** visitClassOrObject
- **Tree traversal:** declaration-level
- **Complexity:** 1 (checks supertype list text)
- **Config options:** none
- **Has ktlint equivalent:** none
- **Uses Analysis API:** no
- **Notes:** Active by default since 1.2.0.

### unnecessary-parentheses
- **Name:** UnnecessaryParentheses
- **Source:** detekt-rules-style/src/main/kotlin/dev/detekt/rules/style/UnnecessaryParentheses.kt
- **Module:** detekt-rules-style
- **Category:** style
- **What it checks:** Reports parenthesized expressions where parentheses are not needed.
- **Needs semantic info:** none
- **PSI types visited:** visitParenthesizedExpression
- **Tree traversal:** expression-level
- **Complexity:** 2 (uses `KtPsiUtil.areParenthesesUseless`, checks operator precedence, unary prefix, float-around-range edge cases)
- **Config options:** `allowForUnclearPrecedence` (Boolean, default false)
- **Has ktlint equivalent:** `unnecessary-parentheses-before-trailing-lambda` (partial)
- **Uses Analysis API:** no
- **Notes:** Contains a detailed `childToUnclearPrecedenceParentsMapping` for binary operator precedence clarity.

### unused-import
- **Name:** UnusedImport
- **Source:** detekt-rules-style/src/main/kotlin/dev/detekt/rules/style/UnusedImport.kt
- **Module:** detekt-rules-style
- **Category:** style
- **What it checks:** Reports import statements that are not used in the file.
- **Needs semantic info:** resolved-call
- **PSI types visited:** visit (KtFile), plus inner visitor: visitPackageDirective, visitImportList, visitReferenceExpression, visitDeclaration
- **Tree traversal:** file-level
- **Complexity:** 3 (resolves reference FQ names via Analysis API, handles KDoc references, operators, componentN patterns)
- **Config options:** `additionalOperatorSet` (List<String>, default empty)
- **Has ktlint equivalent:** `no-unused-imports`
- **Uses Analysis API:** yes (implements `RequiresAnalysisApi`; uses `analyze {}`, resolves `mainReference.resolveToSymbol()`)
- **Notes:** Exempts operator imports, componentN destructuring imports, and KDoc references. Uses a lazy-evaluated set of FQ names from resolved symbols.

### unused-parameter
- **Name:** UnusedParameter
- **Source:** detekt-rules-style/src/main/kotlin/dev/detekt/rules/style/UnusedParameter.kt
- **Module:** detekt-rules-style
- **Category:** style
- **What it checks:** Reports function parameters that are never referenced in the function body.
- **Needs semantic info:** none
- **PSI types visited:** visit (KtFile), plus inner visitor: visitClassOrObject, visitClass, visitNamedFunction, visitProperty, visitReferenceExpression
- **Tree traversal:** file-level
- **Complexity:** 2 (walks function body with an inner visitor collecting reference expressions and removing matched parameter names)
- **Config options:** `allowedNames` (Regex, default "ignored|expected")
- **Has ktlint equivalent:** none
- **Uses Analysis API:** no
- **Notes:** Skips abstract, open, override, operator, main, external, expect, actual, and protected functions. Has alias `UNUSED_PARAMETER` / `unused`. Active by default since 1.23.0.

### unused-private-class
- **Name:** UnusedPrivateClass
- **Source:** detekt-rules-style/src/main/kotlin/dev/detekt/rules/style/UnusedPrivateClass.kt
- **Module:** detekt-rules-style
- **Category:** style
- **What it checks:** Reports private classes that are never referenced anywhere in the file.
- **Needs semantic info:** none
- **PSI types visited:** visit (KtFile), plus inner visitor: visitClass, visitImportDirective, visitAnnotationEntry, visitParameter, visitNamedFunction, visitObjectDeclaration, visitFunctionType, visitProperty, visitBinaryWithTypeRHSExpression, visitIsExpression, visitCallExpression, visitDoubleColonExpression, visitDotQualifiedExpression
- **Tree traversal:** file-level
- **Complexity:** 2 (collects all private classes and all name references from various PSI node types, then computes the set difference)
- **Config options:** none
- **Has ktlint equivalent:** none
- **Uses Analysis API:** no
- **Notes:** Uses "first char uppercase" heuristic for distinguishing class names from package names without type resolution. Has alias `unused`. Active by default since 1.2.0.

### use-let
- **Name:** UseLet
- **Source:** detekt-rules-style/src/main/kotlin/dev/detekt/rules/style/UseLet.kt
- **Module:** detekt-rules-style
- **Category:** style
- **What it checks:** Reports `if (x != null) ... else null` / `if (x == null) null else ...` patterns suggesting `?.let {}`.
- **Needs semantic info:** none
- **PSI types visited:** visitIfExpression
- **Tree traversal:** expression-level
- **Complexity:** 1
- **Config options:** none
- **Has ktlint equivalent:** none
- **Uses Analysis API:** no
- **Notes:** --

---

## Naming rules

### class-naming
- **Name:** ClassNaming
- **Source:** detekt-rules-naming/src/main/kotlin/dev/detekt/rules/naming/ClassNaming.kt
- **Module:** detekt-rules-naming
- **Category:** naming
- **What it checks:** Reports class or object names that do not match a configurable regex pattern.
- **Needs semantic info:** none
- **PSI types visited:** visitClassOrObject
- **Tree traversal:** declaration-level
- **Complexity:** 1
- **Config options:** `classPattern` (Regex, default "[A-Z][a-zA-Z0-9]*")
- **Has ktlint equivalent:** none
- **Uses Analysis API:** no
- **Notes:** Has alias `ClassName`. Active by default since 1.0.0.

### constructor-parameter-naming
- **Name:** ConstructorParameterNaming
- **Source:** detekt-rules-naming/src/main/kotlin/dev/detekt/rules/naming/ConstructorParameterNaming.kt
- **Module:** detekt-rules-naming
- **Category:** naming
- **What it checks:** Reports constructor parameter names that do not match configurable patterns, with separate patterns for private parameters.
- **Needs semantic info:** none
- **PSI types visited:** visitParameter
- **Tree traversal:** declaration-level
- **Complexity:** 1
- **Config options:** `parameterPattern` (Regex, default "[a-z][A-Za-z0-9]*"), `privateParameterPattern` (Regex, default "[a-z][A-Za-z0-9]*"), `excludeClassPattern` (Regex, default "$^")
- **Has ktlint equivalent:** none
- **Uses Analysis API:** no
- **Notes:** Skips override parameters. Active by default since 1.0.0.

### enum-naming
- **Name:** EnumNaming
- **Source:** detekt-rules-naming/src/main/kotlin/dev/detekt/rules/naming/EnumNaming.kt
- **Module:** detekt-rules-naming
- **Category:** naming
- **What it checks:** Reports enum entry names that do not match a configurable regex pattern.
- **Needs semantic info:** none
- **PSI types visited:** visitEnumEntry
- **Tree traversal:** declaration-level
- **Complexity:** 1
- **Config options:** `enumEntryPattern` (Regex, default "[A-Z][_a-zA-Z0-9]*")
- **Has ktlint equivalent:** none
- **Uses Analysis API:** no
- **Notes:** Has alias `EnumEntryName`. Active by default since 1.0.0.

### forbidden-class-name
- **Name:** ForbiddenClassName
- **Source:** detekt-rules-naming/src/main/kotlin/dev/detekt/rules/naming/ForbiddenClassName.kt
- **Module:** detekt-rules-naming
- **Category:** naming
- **What it checks:** Reports class names that match any of the configured forbidden glob patterns.
- **Needs semantic info:** none
- **PSI types visited:** visitClassOrObject
- **Tree traversal:** declaration-level
- **Complexity:** 1
- **Config options:** `forbiddenName` (List<Regex>, default empty -- converted via `pathGlobToRegex`)
- **Has ktlint equivalent:** none
- **Uses Analysis API:** no
- **Notes:** --

### function-name-max-length
- **Name:** FunctionNameMaxLength
- **Source:** detekt-rules-naming/src/main/kotlin/dev/detekt/rules/naming/FunctionNameMaxLength.kt
- **Module:** detekt-rules-naming
- **Category:** naming
- **What it checks:** Reports function names longer than the configured maximum length.
- **Needs semantic info:** none
- **PSI types visited:** visitNamedFunction
- **Tree traversal:** declaration-level
- **Complexity:** 1
- **Config options:** `maximumFunctionNameLength` (Int, default 30)
- **Has ktlint equivalent:** none
- **Uses Analysis API:** no
- **Notes:** Skips override and operator functions. Has alias `FunctionMaxNameLength`.

### function-name-min-length
- **Name:** FunctionNameMinLength
- **Source:** detekt-rules-naming/src/main/kotlin/dev/detekt/rules/naming/FunctionNameMinLength.kt
- **Module:** detekt-rules-naming
- **Category:** naming
- **What it checks:** Reports function names shorter than the configured minimum length.
- **Needs semantic info:** none
- **PSI types visited:** visitNamedFunction
- **Tree traversal:** declaration-level
- **Complexity:** 1
- **Config options:** `minimumFunctionNameLength` (Int, default 3)
- **Has ktlint equivalent:** none
- **Uses Analysis API:** no
- **Notes:** Skips override and operator functions. Has alias `FunctionMinNameLength`.

### function-naming
- **Name:** FunctionNaming
- **Source:** detekt-rules-naming/src/main/kotlin/dev/detekt/rules/naming/FunctionNaming.kt
- **Module:** detekt-rules-naming
- **Category:** naming
- **What it checks:** Reports function names that do not match a configurable regex pattern, with an exception for factory functions whose name matches their return type.
- **Needs semantic info:** none
- **PSI types visited:** visitNamedFunction
- **Tree traversal:** declaration-level
- **Complexity:** 1
- **Config options:** `functionPattern` (Regex, default "[a-z][a-zA-Z0-9]*"), `excludeClassPattern` (Regex, default "$^")
- **Has ktlint equivalent:** none
- **Uses Analysis API:** no
- **Notes:** Has alias `FunctionName`. Active by default since 1.0.0.

### function-parameter-naming
- **Name:** FunctionParameterNaming
- **Source:** detekt-rules-naming/src/main/kotlin/dev/detekt/rules/naming/FunctionParameterNaming.kt
- **Module:** detekt-rules-naming
- **Category:** naming
- **What it checks:** Reports function parameter names that do not match a configurable regex pattern.
- **Needs semantic info:** none
- **PSI types visited:** visitParameter
- **Tree traversal:** declaration-level
- **Complexity:** 1
- **Config options:** `parameterPattern` (Regex, default "[a-z][A-Za-z0-9]*"), `excludeClassPattern` (Regex, default "$^")
- **Has ktlint equivalent:** none
- **Uses Analysis API:** no
- **Notes:** Skips parameters of overridden functions. Active by default since 1.0.0.

### invalid-package-declaration
- **Name:** InvalidPackageDeclaration
- **Source:** detekt-rules-naming/src/main/kotlin/dev/detekt/rules/naming/InvalidPackageDeclaration.kt
- **Module:** detekt-rules-naming
- **Category:** naming
- **What it checks:** Reports when a file's package declaration does not match its directory path.
- **Needs semantic info:** none
- **PSI types visited:** visitPackageDirective
- **Tree traversal:** file-level
- **Complexity:** 2 (normalizes file path and compares against declared package, with root-package stripping)
- **Config options:** `rootPackage` (String, default ""), `requireRootInDeclaration` (Boolean, default false)
- **Has ktlint equivalent:** `package-name` (partial)
- **Uses Analysis API:** no
- **Notes:** Has alias `PackageDirectoryMismatch`. Active by default since 1.21.0.

### lambda-parameter-naming
- **Name:** LambdaParameterNaming
- **Source:** detekt-rules-naming/src/main/kotlin/dev/detekt/rules/naming/LambdaParameterNaming.kt
- **Module:** detekt-rules-naming
- **Category:** naming
- **What it checks:** Reports lambda parameter names that do not match a configurable regex pattern.
- **Needs semantic info:** none
- **PSI types visited:** visitLambdaExpression
- **Tree traversal:** declaration-level
- **Complexity:** 1 (iterates value parameters including destructured entries)
- **Config options:** `parameterPattern` (Regex, default "[a-z][A-Za-z0-9]*|_")
- **Has ktlint equivalent:** none
- **Uses Analysis API:** no
- **Notes:** Handles destructuring declaration entries within lambda parameters.

### no-name-shadowing
- **Name:** NoNameShadowing
- **Source:** detekt-rules-naming/src/main/kotlin/dev/detekt/rules/naming/NoNameShadowing.kt
- **Module:** detekt-rules-naming
- **Category:** naming
- **What it checks:** Reports variable, parameter, or destructuring declarations that shadow a name from an outer scope.
- **Needs semantic info:** none
- **PSI types visited:** visitProperty, visitDestructuringDeclarationEntry, visitParameter, visitLambdaExpression
- **Tree traversal:** expression-level
- **Complexity:** 3 (walks parent chain checking functions, lambdas, class constructors, inner/object hierarchy for name collisions; also detects implicit `it` shadowing)
- **Config options:** none
- **Has ktlint equivalent:** none
- **Uses Analysis API:** yes (implements `RequiresAnalysisApi` -- though the primary shadowing logic is PSI-based; the interface is declared)
- **Notes:** Handles inner classes, companion objects, class initializers, implicit lambda `it` parameters. Active by default since 1.21.0.

### object-property-naming
- **Name:** ObjectPropertyNaming
- **Source:** detekt-rules-naming/src/main/kotlin/dev/detekt/rules/naming/ObjectPropertyNaming.kt
- **Module:** detekt-rules-naming
- **Category:** naming
- **What it checks:** Reports property names inside objects (including companion objects) that do not match configurable patterns, with separate patterns for constants, properties, and private properties.
- **Needs semantic info:** none
- **PSI types visited:** visitProperty
- **Tree traversal:** declaration-level
- **Complexity:** 1
- **Config options:** `constantPattern` (Regex, default "[A-Za-z][_A-Za-z0-9]*"), `propertyPattern` (Regex, default "[A-Za-z][_A-Za-z0-9]*"), `privatePropertyPattern` (Regex, default "(_)?[A-Za-z][_A-Za-z0-9]*")
- **Has ktlint equivalent:** none
- **Uses Analysis API:** no
- **Notes:** Has alias `ObjectPropertyName`. Active by default since 1.0.0.

### package-naming
- **Name:** PackageNaming
- **Source:** detekt-rules-naming/src/main/kotlin/dev/detekt/rules/naming/PackageNaming.kt
- **Module:** detekt-rules-naming
- **Category:** naming
- **What it checks:** Reports package names that do not match a configurable regex pattern.
- **Needs semantic info:** none
- **PSI types visited:** visitPackageDirective
- **Tree traversal:** file-level
- **Complexity:** 1
- **Config options:** `packagePattern` (Regex, default `[a-z]+(\.[a-z][A-Za-z0-9]*)*`)
- **Has ktlint equivalent:** `package-name`
- **Uses Analysis API:** no
- **Notes:** Has alias `PackageName`. Active by default since 1.0.0.

### top-level-property-naming
- **Name:** TopLevelPropertyNaming
- **Source:** detekt-rules-naming/src/main/kotlin/dev/detekt/rules/naming/TopLevelPropertyNaming.kt
- **Module:** detekt-rules-naming
- **Category:** naming
- **What it checks:** Reports top-level property names that do not match configurable patterns, with separate patterns for constants, properties, and private properties.
- **Needs semantic info:** none
- **PSI types visited:** visitProperty
- **Tree traversal:** declaration-level
- **Complexity:** 1
- **Config options:** `constantPattern` (Regex, default "[A-Z][_A-Z0-9]*"), `propertyPattern` (Regex, default "[A-Za-z][_A-Za-z0-9]*"), `privatePropertyPattern` (Regex, default "_?[A-Za-z][_A-Za-z0-9]*")
- **Has ktlint equivalent:** none
- **Uses Analysis API:** no
- **Notes:** Skips extension declarations. Active by default since 1.0.0.

### variable-max-length
- **Name:** VariableMaxLength
- **Source:** detekt-rules-naming/src/main/kotlin/dev/detekt/rules/naming/VariableMaxLength.kt
- **Module:** detekt-rules-naming
- **Category:** naming
- **What it checks:** Reports variable names longer than the configured maximum length.
- **Needs semantic info:** none
- **PSI types visited:** visitProperty
- **Tree traversal:** declaration-level
- **Complexity:** 1
- **Config options:** `maximumVariableNameLength` (Int, default 64)
- **Has ktlint equivalent:** none
- **Uses Analysis API:** no
- **Notes:** Skips override properties.

### variable-min-length
- **Name:** VariableMinLength
- **Source:** detekt-rules-naming/src/main/kotlin/dev/detekt/rules/naming/VariableMinLength.kt
- **Module:** detekt-rules-naming
- **Category:** naming
- **What it checks:** Reports variable names shorter than the configured minimum length.
- **Needs semantic info:** none
- **PSI types visited:** visitProperty
- **Tree traversal:** declaration-level
- **Complexity:** 1
- **Config options:** `minimumVariableNameLength` (Int, default 1)
- **Has ktlint equivalent:** none
- **Uses Analysis API:** no
- **Notes:** Skips override properties and single-underscore names.

### variable-naming
- **Name:** VariableNaming
- **Source:** detekt-rules-naming/src/main/kotlin/dev/detekt/rules/naming/VariableNaming.kt
- **Module:** detekt-rules-naming
- **Category:** naming
- **What it checks:** Reports non-top-level, non-object variable names that do not match configurable patterns, with a separate pattern for private variables.
- **Needs semantic info:** none
- **PSI types visited:** visitProperty
- **Tree traversal:** declaration-level
- **Complexity:** 1
- **Config options:** `variablePattern` (Regex, default "[a-z][A-Za-z0-9]*"), `privateVariablePattern` (Regex, default "(_)?[a-z][A-Za-z0-9]*"), `excludeClassPattern` (Regex, default "$^")
- **Has ktlint equivalent:** none
- **Uses Analysis API:** no
- **Notes:** Has alias `PropertyName`. Excludes top-level, object, and companion-object properties (handled by other rules). Active by default since 1.0.0.


---

## comments

### absent-or-wrong-file-license
- **Name:** AbsentOrWrongFileLicense
- **Source:** detekt-rules-comments/src/main/kotlin/dev/detekt/rules/comments/AbsentOrWrongFileLicense.kt
- **Module:** detekt-rules-comments
- **Category:** comments
- **What it checks:** Reports Kotlin source files that do not have the required license header (matched literally or via regex).
- **Needs semantic info:** none
- **PSI types visited:** visitKtFile
- **Tree traversal:** file-level
- **Complexity:** 1
- **Config options:** `licenseTemplateIsRegex` (Boolean, default `false`), `licenseTemplate` (String, default `""`)
- **Has ktlint equivalent:** none
- **Uses Analysis API:** no
- **Notes:** --

### documentation-over-private-property
- **Name:** DocumentationOverPrivateProperty
- **Source:** detekt-rules-comments/src/main/kotlin/dev/detekt/rules/comments/DocumentationOverPrivateProperty.kt
- **Module:** detekt-rules-comments
- **Category:** comments
- **What it checks:** Reports KDoc documentation placed above private properties, suggesting the property should be renamed to be self-explanatory instead.
- **Needs semantic info:** none
- **PSI types visited:** visitProperty
- **Tree traversal:** declaration-level
- **Complexity:** 1
- **Config options:** none
- **Has ktlint equivalent:** none
- **Uses Analysis API:** no
- **Notes:** Uses a helper `hasKDocInPrivateMember()` -- need to find/port that utility.

### kdoc-references-non-public-property
- **Name:** KDocReferencesNonPublicProperty
- **Source:** detekt-rules-comments/src/main/kotlin/dev/detekt/rules/comments/KDocReferencesNonPublicProperty.kt
- **Module:** detekt-rules-comments
- **Category:** comments
- **What it checks:** Reports KDoc comments on classes that reference non-public properties via bracket-link syntax (`[propName]`).
- **Needs semantic info:** none
- **PSI types visited:** visitClass, visitNamedDeclaration
- **Tree traversal:** declaration-level
- **Complexity:** 2
- **Config options:** none
- **Has ktlint equivalent:** none
- **Uses Analysis API:** no
- **Notes:** Maintains mutable state (`publicPropertiesByClass`, `privatePropertiesByClass`) across visits; walks nested object hierarchies to determine inherited visibility. Handles qualified names through nested companion objects.

### outdated-documentation
- **Name:** OutdatedDocumentation
- **Source:** detekt-rules-comments/src/main/kotlin/dev/detekt/rules/comments/OutdatedDocumentation.kt
- **Module:** detekt-rules-comments
- **Category:** comments
- **What it checks:** Reports classes, functions, and constructors whose KDoc `@param`/`@property` tags do not match the actual declaration signature (wrong names, wrong order, or missing documentation).
- **Needs semantic info:** none
- **PSI types visited:** visitClass, visitSecondaryConstructor, visitNamedFunction
- **Tree traversal:** declaration-level
- **Complexity:** 2
- **Config options:** `matchTypeParameters` (Boolean, default `true`), `matchDeclarationsOrder` (Boolean, default `true`), `allowParamOnConstructorProperties` (Boolean, default `false`), `exhaustive` (Boolean, default `true`)
- **Has ktlint equivalent:** none
- **Uses Analysis API:** no
- **Notes:** Complex matching logic between doc declarations and code declarations; handles `@param` vs `@property` for constructor properties. Uses `dev.detekt.psi.isInternal`.

### undocumented-public-class
- **Name:** UndocumentedPublicClass
- **Source:** detekt-rules-comments/src/main/kotlin/dev/detekt/rules/comments/UndocumentedPublicClass.kt
- **Module:** detekt-rules-comments
- **Category:** comments
- **What it checks:** Reports public classes, objects, and interfaces that lack KDoc documentation.
- **Needs semantic info:** none
- **PSI types visited:** visitClass, visitObjectDeclaration
- **Tree traversal:** declaration-level
- **Complexity:** 2
- **Config options:** `searchInNestedClass` (Boolean, default `true`), `searchInInnerClass` (Boolean, default `true`), `searchInInnerObject` (Boolean, default `true`), `searchInInnerInterface` (Boolean, default `true`), `searchInProtectedClass` (Boolean, default `false`), `ignoreDefaultCompanionObject` (Boolean, default `false`)
- **Has ktlint equivalent:** none
- **Uses Analysis API:** no
- **Notes:** Uses `isPublicInherited` from `internal` package and `isPublicNotOverridden` from `dev.detekt.psi`. Handles default companion object edge case.

### undocumented-public-function
- **Name:** UndocumentedPublicFunction
- **Source:** detekt-rules-comments/src/main/kotlin/dev/detekt/rules/comments/UndocumentedPublicFunction.kt
- **Module:** detekt-rules-comments
- **Category:** comments
- **What it checks:** Reports public functions that lack KDoc documentation, excluding overridden functions.
- **Needs semantic info:** none
- **PSI types visited:** visitNamedFunction
- **Tree traversal:** declaration-level
- **Complexity:** 2
- **Config options:** `searchProtectedFunction` (Boolean, default `false`)
- **Has ktlint equivalent:** none
- **Uses Analysis API:** no
- **Notes:** Walks parent hierarchy (`parents.filterIsInstance<KtClassOrObject>`) to verify all enclosing declarations are public. Uses `isPublicNotOverridden` from `dev.detekt.psi`.

### undocumented-public-property
- **Name:** UndocumentedPublicProperty
- **Source:** detekt-rules-comments/src/main/kotlin/dev/detekt/rules/comments/UndocumentedPublicProperty.kt
- **Module:** detekt-rules-comments
- **Category:** comments
- **What it checks:** Reports public properties (including primary constructor val/var parameters and enum entries) that lack KDoc documentation.
- **Needs semantic info:** none
- **PSI types visited:** visitPrimaryConstructor, visitProperty, visitEnumEntry
- **Tree traversal:** declaration-level
- **Complexity:** 2
- **Config options:** `searchProtectedProperty` (Boolean, default `false`), `ignoreEnumEntries` (Boolean, default `false`)
- **Has ktlint equivalent:** none
- **Uses Analysis API:** no
- **Notes:** Checks both inline class-level KDoc references (`[paramName]`, `@property`, `@param`) and standalone property-level KDoc. Uses `isPublicInherited` and `isPublicNotOverridden` from shared utilities.

---

## complexity

### cognitive-complex-method
- **Name:** CognitiveComplexMethod
- **Source:** detekt-rules-complexity/src/main/kotlin/dev/detekt/rules/complexity/CognitiveComplexMethod.kt
- **Module:** detekt-rules-complexity
- **Category:** complexity
- **What it checks:** Reports functions whose Cognitive Complexity (SonarSource metric) exceeds a configurable threshold.
- **Needs semantic info:** none
- **PSI types visited:** visitNamedFunction
- **Tree traversal:** declaration-level
- **Complexity:** 2
- **Config options:** `allowedComplexity` (Int, default `15`)
- **Has ktlint equivalent:** none
- **Uses Analysis API:** no
- **Notes:** Delegates complexity calculation to `dev.detekt.metrics.CognitiveComplexity.calculate()` -- that utility must also be ported.

### complex-condition
- **Name:** ComplexCondition
- **Source:** detekt-rules-complexity/src/main/kotlin/dev/detekt/rules/complexity/ComplexCondition.kt
- **Module:** detekt-rules-complexity
- **Category:** complexity
- **What it checks:** Reports `if`/`while`/`do-while` conditions that contain more than a configurable number of logical operators (`&&`/`||`).
- **Needs semantic info:** none
- **PSI types visited:** visitIfExpression, visitDoWhileExpression, visitWhileExpression
- **Tree traversal:** expression-level
- **Complexity:** 2
- **Config options:** `allowedConditions` (Int, default `3`)
- **Has ktlint equivalent:** none
- **Uses Analysis API:** no
- **Notes:** Counts `&&` and `||` via string-level frequency search on the longest binary expression descendant's text. Active by default since 1.0.0.

### cyclomatic-complex-method
- **Name:** CyclomaticComplexMethod
- **Source:** detekt-rules-complexity/src/main/kotlin/dev/detekt/rules/complexity/CyclomaticComplexMethod.kt
- **Module:** detekt-rules-complexity
- **Category:** complexity
- **What it checks:** Reports functions whose McCabe Cyclomatic Complexity exceeds a configurable threshold.
- **Needs semantic info:** none
- **PSI types visited:** visitNamedFunction
- **Tree traversal:** declaration-level
- **Complexity:** 2
- **Config options:** `allowedComplexity` (Int, default `14`), `ignoreSingleWhenExpression` (Boolean, default `false`), `ignoreSimpleWhenEntries` (Boolean, default `false`), `ignoreNestingFunctions` (Boolean, default `false`), `ignoreLocalFunctions` (Boolean, default `false`), `nestingFunctions` (Set<String>, default `["also","apply","forEach","isNotNull","ifNull","let","run","use","with"]`)
- **Has ktlint equivalent:** none
- **Uses Analysis API:** no
- **Notes:** Delegates calculation to `dev.detekt.metrics.CyclomaticComplexity.calculate()`. Has special handling for single-when-expression function bodies. Active by default since 1.0.0.

### labeled-expression
- **Name:** LabeledExpression
- **Source:** detekt-rules-complexity/src/main/kotlin/dev/detekt/rules/complexity/LabeledExpression.kt
- **Module:** detekt-rules-complexity
- **Category:** complexity
- **What it checks:** Reports labeled expressions (e.g., `break@loop`, `return@label`), which increase complexity; exempts `this@Outer` references from inner classes to outer classes.
- **Needs semantic info:** none
- **PSI types visited:** visitExpressionWithLabel
- **Tree traversal:** expression-level
- **Complexity:** 2
- **Config options:** `ignoredLabels` (List<String>, default `[]`)
- **Has ktlint equivalent:** none
- **Uses Analysis API:** no
- **Notes:** Walks the class hierarchy to determine whether a `this@Label` expression is referencing an outer class from an inner class (which is the only way to access the outer instance and is therefore allowed).

---

## coroutines

### global-coroutine-usage
- **Name:** GlobalCoroutineUsage
- **Source:** detekt-rules-coroutines/src/main/kotlin/dev/detekt/rules/coroutines/GlobalCoroutineUsage.kt
- **Module:** detekt-rules-coroutines
- **Category:** coroutines
- **What it checks:** Reports usages of `GlobalScope.launch` and `GlobalScope.async`, which are discouraged in favor of structured concurrency.
- **Needs semantic info:** none
- **PSI types visited:** visitDotQualifiedExpression
- **Tree traversal:** expression-level
- **Complexity:** 1
- **Config options:** none
- **Has ktlint equivalent:** none
- **Uses Analysis API:** no
- **Notes:** Pure text-based check on receiver name (`"GlobalScope"`) and callee name (`"launch"` or `"async"`). Does not resolve whether the receiver is actually `kotlinx.coroutines.GlobalScope`.

---

## empty-blocks

### empty-class-block
- **Name:** EmptyClassBlock
- **Source:** detekt-rules-empty-blocks/src/main/kotlin/dev/detekt/rules/emptyblocks/EmptyClassBlock.kt
- **Module:** detekt-rules-empty-blocks
- **Category:** empty-blocks
- **What it checks:** Reports classes and objects with an empty body block (no declarations).
- **Needs semantic info:** none
- **PSI types visited:** visitClassOrObject
- **Tree traversal:** declaration-level
- **Complexity:** 1
- **Config options:** none
- **Has ktlint equivalent:** none
- **Uses Analysis API:** no
- **Notes:** Extends `EmptyRule` base class. Skips object literals and bodies that contain comments. Active by default since 1.0.0.

### empty-default-constructor
- **Name:** EmptyDefaultConstructor
- **Source:** detekt-rules-empty-blocks/src/main/kotlin/dev/detekt/rules/emptyblocks/EmptyDefaultConstructor.kt
- **Module:** detekt-rules-empty-blocks
- **Category:** empty-blocks
- **What it checks:** Reports redundant empty default constructors with public visibility and no annotations that can be removed.
- **Needs semantic info:** none
- **PSI types visited:** visitPrimaryConstructor
- **Tree traversal:** declaration-level
- **Complexity:** 2
- **Config options:** none
- **Has ktlint equivalent:** none
- **Uses Analysis API:** no
- **Notes:** Extends `EmptyRule`. Exempts `expect`/`actual` classes (which need explicit constructors). Also exempts constructors called by secondary constructors via `this()` delegation. Active by default since 1.0.0.

### empty-do-while-block
- **Name:** EmptyDoWhileBlock
- **Source:** detekt-rules-empty-blocks/src/main/kotlin/dev/detekt/rules/emptyblocks/EmptyDoWhileBlock.kt
- **Module:** detekt-rules-empty-blocks
- **Category:** empty-blocks
- **What it checks:** Reports `do-while` loops with an empty body block.
- **Needs semantic info:** none
- **PSI types visited:** visitDoWhileExpression
- **Tree traversal:** expression-level
- **Complexity:** 1
- **Config options:** none
- **Has ktlint equivalent:** none
- **Uses Analysis API:** no
- **Notes:** Extends `EmptyRule`. Active by default since 1.0.0.

### empty-else-block
- **Name:** EmptyElseBlock
- **Source:** detekt-rules-empty-blocks/src/main/kotlin/dev/detekt/rules/emptyblocks/EmptyElseBlock.kt
- **Module:** detekt-rules-empty-blocks
- **Category:** empty-blocks
- **What it checks:** Reports `else` branches with an empty body block, including the degenerate semicolon-only case.
- **Needs semantic info:** none
- **PSI types visited:** visitIfExpression
- **Tree traversal:** expression-level
- **Complexity:** 1
- **Config options:** none
- **Has ktlint equivalent:** none
- **Uses Analysis API:** no
- **Notes:** Extends `EmptyRule`. Also detects lone-semicolon else bodies via `checkThenBodyForLoneSemicolon`. Active by default since 1.0.0.

### empty-finally-block
- **Name:** EmptyFinallyBlock
- **Source:** detekt-rules-empty-blocks/src/main/kotlin/dev/detekt/rules/emptyblocks/EmptyFinallyBlock.kt
- **Module:** detekt-rules-empty-blocks
- **Category:** empty-blocks
- **What it checks:** Reports `finally` blocks with an empty body.
- **Needs semantic info:** none
- **PSI types visited:** visitFinallySection
- **Tree traversal:** expression-level
- **Complexity:** 1
- **Config options:** none
- **Has ktlint equivalent:** none
- **Uses Analysis API:** no
- **Notes:** Extends `EmptyRule`. Active by default since 1.0.0.

### empty-for-block
- **Name:** EmptyForBlock
- **Source:** detekt-rules-empty-blocks/src/main/kotlin/dev/detekt/rules/emptyblocks/EmptyForBlock.kt
- **Module:** detekt-rules-empty-blocks
- **Category:** empty-blocks
- **What it checks:** Reports `for` loops with an empty body block.
- **Needs semantic info:** none
- **PSI types visited:** visitForExpression
- **Tree traversal:** expression-level
- **Complexity:** 1
- **Config options:** none
- **Has ktlint equivalent:** none
- **Uses Analysis API:** no
- **Notes:** Extends `EmptyRule`. Active by default since 1.0.0.

### empty-function-block
- **Name:** EmptyFunctionBlock
- **Source:** detekt-rules-empty-blocks/src/main/kotlin/dev/detekt/rules/emptyblocks/EmptyFunctionBlock.kt
- **Module:** detekt-rules-empty-blocks
- **Category:** empty-blocks
- **What it checks:** Reports functions with an empty body block; overridden functions with only a comment are not reported.
- **Needs semantic info:** none
- **PSI types visited:** visitNamedFunction
- **Tree traversal:** declaration-level
- **Complexity:** 2
- **Config options:** `ignoreOverridden` (Boolean, default `false`)
- **Has ktlint equivalent:** none
- **Uses Analysis API:** no
- **Notes:** Extends `EmptyRule`. Skips `open` functions and default interface functions (which have a body). Uses `isOverride()` and `isOpen()` from `dev.detekt.psi`. Active by default since 1.0.0.

### empty-if-block
- **Name:** EmptyIfBlock
- **Source:** detekt-rules-empty-blocks/src/main/kotlin/dev/detekt/rules/emptyblocks/EmptyIfBlock.kt
- **Module:** detekt-rules-empty-blocks
- **Category:** empty-blocks
- **What it checks:** Reports `if` branches with an empty `then` body block, including the degenerate semicolon-only case.
- **Needs semantic info:** none
- **PSI types visited:** visitIfExpression
- **Tree traversal:** expression-level
- **Complexity:** 1
- **Config options:** none
- **Has ktlint equivalent:** none
- **Uses Analysis API:** no
- **Notes:** Extends `EmptyRule`. Active by default since 1.0.0.

### empty-init-block
- **Name:** EmptyInitBlock
- **Source:** detekt-rules-empty-blocks/src/main/kotlin/dev/detekt/rules/emptyblocks/EmptyInitBlock.kt
- **Module:** detekt-rules-empty-blocks
- **Category:** empty-blocks
- **What it checks:** Reports `init` blocks with an empty body.
- **Needs semantic info:** none
- **PSI types visited:** visitClassInitializer
- **Tree traversal:** declaration-level
- **Complexity:** 1
- **Config options:** none
- **Has ktlint equivalent:** none
- **Uses Analysis API:** no
- **Notes:** Extends `EmptyRule`. Active by default since 1.0.0.

### empty-kotlin-file
- **Name:** EmptyKotlinFile
- **Source:** detekt-rules-empty-blocks/src/main/kotlin/dev/detekt/rules/emptyblocks/EmptyKotlinFile.kt
- **Module:** detekt-rules-empty-blocks
- **Category:** empty-blocks
- **What it checks:** Reports Kotlin source files that contain no code other than an optional package directive.
- **Needs semantic info:** none
- **PSI types visited:** visitKtFile
- **Tree traversal:** file-level
- **Complexity:** 1
- **Config options:** none
- **Has ktlint equivalent:** none
- **Uses Analysis API:** no
- **Notes:** Extends `EmptyRule`. Strips the package directive text and checks if the remainder is blank. Active by default since 1.0.0.

### empty-secondary-constructor
- **Name:** EmptySecondaryConstructor
- **Source:** detekt-rules-empty-blocks/src/main/kotlin/dev/detekt/rules/emptyblocks/EmptySecondaryConstructor.kt
- **Module:** detekt-rules-empty-blocks
- **Category:** empty-blocks
- **What it checks:** Reports secondary constructors with an empty body block.
- **Needs semantic info:** none
- **PSI types visited:** visitSecondaryConstructor
- **Tree traversal:** declaration-level
- **Complexity:** 1
- **Config options:** none
- **Has ktlint equivalent:** none
- **Uses Analysis API:** no
- **Notes:** Extends `EmptyRule`. Active by default since 1.0.0.

### empty-try-block
- **Name:** EmptyTryBlock
- **Source:** detekt-rules-empty-blocks/src/main/kotlin/dev/detekt/rules/emptyblocks/EmptyTryBlock.kt
- **Module:** detekt-rules-empty-blocks
- **Category:** empty-blocks
- **What it checks:** Reports `try` blocks with an empty body.
- **Needs semantic info:** none
- **PSI types visited:** visitTryExpression
- **Tree traversal:** expression-level
- **Complexity:** 1
- **Config options:** none
- **Has ktlint equivalent:** none
- **Uses Analysis API:** no
- **Notes:** Extends `EmptyRule`. Active by default since 1.6.0.

### empty-when-block
- **Name:** EmptyWhenBlock
- **Source:** detekt-rules-empty-blocks/src/main/kotlin/dev/detekt/rules/emptyblocks/EmptyWhenBlock.kt
- **Module:** detekt-rules-empty-blocks
- **Category:** empty-blocks
- **What it checks:** Reports `when` expressions with no entries.
- **Needs semantic info:** none
- **PSI types visited:** visitWhenExpression
- **Tree traversal:** expression-level
- **Complexity:** 1
- **Config options:** none
- **Has ktlint equivalent:** none
- **Uses Analysis API:** no
- **Notes:** Extends `EmptyRule`. Checks `expression.entries.isEmpty()` rather than using the shared `addFindingIfBlockExprIsEmpty` helper. Active by default since 1.0.0.

### empty-while-block
- **Name:** EmptyWhileBlock
- **Source:** detekt-rules-empty-blocks/src/main/kotlin/dev/detekt/rules/emptyblocks/EmptyWhileBlock.kt
- **Module:** detekt-rules-empty-blocks
- **Category:** empty-blocks
- **What it checks:** Reports `while` loops with an empty body block.
- **Needs semantic info:** none
- **PSI types visited:** visitWhileExpression
- **Tree traversal:** expression-level
- **Complexity:** 1
- **Config options:** none
- **Has ktlint equivalent:** none
- **Uses Analysis API:** no
- **Notes:** Extends `EmptyRule`. Active by default since 1.0.0.

---

## exceptions

### exception-raised-in-unexpected-location
- **Name:** ExceptionRaisedInUnexpectedLocation
- **Source:** detekt-rules-exceptions/src/main/kotlin/dev/detekt/rules/exceptions/ExceptionRaisedInUnexpectedLocation.kt
- **Module:** detekt-rules-exceptions
- **Category:** exceptions
- **What it checks:** Reports functions (by configurable name list) that contain `throw` expressions, since those functions are conventionally not expected to throw.
- **Needs semantic info:** none
- **PSI types visited:** visitNamedFunction
- **Tree traversal:** declaration-level
- **Complexity:** 2
- **Config options:** `methodNames` (List<String>, default `["equals","finalize","hashCode","toString"]`)
- **Has ktlint equivalent:** none
- **Uses Analysis API:** no
- **Notes:** Uses `anyDescendantOfType<KtThrowExpression>()` to search the entire function body. Active by default since 1.16.0.

### print-stack-trace
- **Name:** PrintStackTrace
- **Source:** detekt-rules-exceptions/src/main/kotlin/dev/detekt/rules/exceptions/PrintStackTrace.kt
- **Module:** detekt-rules-exceptions
- **Category:** exceptions
- **What it checks:** Reports calls to `printStackTrace()` on caught exceptions and `Thread.dumpStack()`, recommending a proper logging framework instead.
- **Needs semantic info:** none
- **PSI types visited:** visitCallExpression, visitCatchSection
- **Tree traversal:** expression-level
- **Complexity:** 2
- **Config options:** none
- **Has ktlint equivalent:** none
- **Uses Analysis API:** no
- **Notes:** Text-based detection: checks `nextSibling` for `printStackTrace(` call text. Also detects `Thread.dumpStack()` via receiver text check. Active by default since 1.16.0.

### rethrow-caught-exception
- **Name:** RethrowCaughtException
- **Source:** detekt-rules-exceptions/src/main/kotlin/dev/detekt/rules/exceptions/RethrowCaughtException.kt
- **Module:** detekt-rules-exceptions
- **Category:** exceptions
- **What it checks:** Reports catch blocks whose only statement is rethrowing the caught exception unchanged, making the try-catch pointless.
- **Needs semantic info:** none
- **PSI types visited:** visitTryExpression
- **Tree traversal:** expression-level
- **Complexity:** 2
- **Config options:** none
- **Has ktlint equivalent:** none
- **Uses Analysis API:** no
- **Notes:** Uses `takeLastWhile` to only report consecutive trailing catch clauses that rethrow (if an earlier catch does work, later rethrowing catches are not flagged). Active by default since 1.16.0.

### swallowed-exception
- **Name:** SwallowedException
- **Source:** detekt-rules-exceptions/src/main/kotlin/dev/detekt/rules/exceptions/SwallowedException.kt
- **Module:** detekt-rules-exceptions
- **Category:** exceptions
- **What it checks:** Reports catch blocks where the caught exception is either unused or only partially used (e.g., `e.message` passed to a new exception instead of `e` itself as a cause).
- **Needs semantic info:** none
- **PSI types visited:** visitCatchSection
- **Tree traversal:** expression-level
- **Complexity:** 3
- **Config options:** `ignoredExceptionTypes` (List<String>, default `["InterruptedException","MalformedURLException","NumberFormatException","ParseException"]`), `allowedExceptionNameRegex` (Regex, default `"_|(ignore|expected).*"`)
- **Has ktlint equivalent:** none
- **Uses Analysis API:** no
- **Notes:** Complex rule with cross-expression analysis: tracks exception references through local variable assignments, checks whether exception is passed as cause vs only as `.message`. Uses `isAllowedExceptionName` from `dev.detekt.psi`. Active by default since 1.16.0.

### throwing-exception-in-main
- **Name:** ThrowingExceptionInMain
- **Source:** detekt-rules-exceptions/src/main/kotlin/dev/detekt/rules/exceptions/ThrowingExceptionInMain.kt
- **Module:** detekt-rules-exceptions
- **Category:** exceptions
- **What it checks:** Reports `throw` expressions inside `main` functions, since there is no higher-level handler to catch them.
- **Needs semantic info:** none
- **PSI types visited:** visitNamedFunction
- **Tree traversal:** declaration-level
- **Complexity:** 2
- **Config options:** none
- **Has ktlint equivalent:** none
- **Uses Analysis API:** no
- **Notes:** Uses `isMainFunction()` from `dev.detekt.psi` and `anyDescendantOfType<KtThrowExpression>()`.

---

## libraries

### forbidden-public-data-class
- **Name:** ForbiddenPublicDataClass
- **Source:** detekt-rules-libraries/src/main/kotlin/dev/detekt/rules/libraries/ForbiddenPublicDataClass.kt
- **Module:** detekt-rules-libraries
- **Category:** libraries
- **What it checks:** Reports public or protected `data class` declarations in library code, since data classes are harmful to binary compatibility.
- **Needs semantic info:** none
- **PSI types visited:** visitClass
- **Tree traversal:** declaration-level
- **Complexity:** 1
- **Config options:** `ignorePackages` (List<Regex>, default `["*.internal", "*.internal.*"]`)
- **Has ktlint equivalent:** none
- **Uses Analysis API:** no
- **Notes:** Skips classes in configurable internal packages. Uses `pathGlobToRegex` from `dev.detekt.psi`. Active by default since 1.16.0.

### library-entities-should-not-be-public
- **Name:** LibraryEntitiesShouldNotBePublic
- **Source:** detekt-rules-libraries/src/main/kotlin/dev/detekt/rules/libraries/LibraryEntitiesShouldNotBePublic.kt
- **Module:** detekt-rules-libraries
- **Category:** libraries
- **What it checks:** Reports public classes, type aliases, and top-level functions in library modules that should be `internal` or `private`.
- **Needs semantic info:** none
- **PSI types visited:** visitClass, visitTypeAlias, visitNamedFunction
- **Tree traversal:** declaration-level
- **Complexity:** 1
- **Config options:** none
- **Has ktlint equivalent:** none
- **Uses Analysis API:** no
- **Notes:** Skips inner classes (only reports top-level or nested public entities). Active by default since 1.16.0.

---

## performance

### array-primitive
- **Name:** ArrayPrimitive
- **Source:** detekt-rules-performance/src/main/kotlin/dev/detekt/rules/performance/ArrayPrimitive.kt
- **Module:** detekt-rules-performance
- **Category:** performance
- **What it checks:** Reports usage of `Array<Primitive>` (e.g., `Array<Int>`) which causes autoboxing; suggests specialized arrays like `IntArray` instead.
- **Needs semantic info:** resolved-call, resolved-type
- **PSI types visited:** visitCallExpression, visitNamedDeclaration
- **Tree traversal:** expression-level
- **Complexity:** 3
- **Config options:** none
- **Has ktlint equivalent:** none
- **Uses Analysis API:** yes
- **Notes:** Implements `RequiresAnalysisApi`. Uses `analyze {}` blocks to resolve function calls (`resolveToCall()`) and check return types for primitive array element types. Also resolves type references via `type.symbol?.classId`. Active by default since 1.2.0.

### for-each-on-range
- **Name:** ForEachOnRange
- **Source:** detekt-rules-performance/src/main/kotlin/dev/detekt/rules/performance/ForEachOnRange.kt
- **Module:** detekt-rules-performance
- **Category:** performance
- **What it checks:** Reports `forEach` calls on range expressions (`..`, `rangeTo`, `downTo`, `until`, `..<`), which have higher overhead than plain `for` loops.
- **Needs semantic info:** none
- **PSI types visited:** visitCallExpression
- **Tree traversal:** expression-level
- **Complexity:** 2
- **Config options:** none
- **Has ktlint equivalent:** none
- **Uses Analysis API:** no
- **Notes:** Walks the receiver chain recursively to detect chained range operators (e.g., `(1..10).step(2).forEach`). Active by default since 1.0.0.

### unnecessary-part-of-binary-expression
- **Name:** UnnecessaryPartOfBinaryExpression
- **Source:** detekt-rules-performance/src/main/kotlin/dev/detekt/rules/performance/UnnecessaryPartOfBinaryExpression.kt
- **Module:** detekt-rules-performance
- **Category:** performance
- **What it checks:** Reports binary expressions (`||`, `&&`) that contain duplicate operands (e.g., `foo || bar || foo`).
- **Needs semantic info:** none
- **PSI types visited:** visitBinaryExpression
- **Tree traversal:** expression-level
- **Complexity:** 2
- **Config options:** none
- **Has ktlint equivalent:** none
- **Uses Analysis API:** no
- **Notes:** Flattens chained binary expressions of the same operator and compares operand texts (after whitespace removal). Skips child expressions whose parent already has the same operator to avoid double-reporting.

---

## bugs

### invalid-range
- **Name:** InvalidRange
- **Source:** detekt-rules-potential-bugs/src/main/kotlin/dev/detekt/rules/potentialbugs/InvalidRange.kt
- **Module:** detekt-rules-potential-bugs
- **Category:** bugs
- **What it checks:** Reports range expressions with constant integer bounds that produce empty ranges (e.g., `2..1`, `1 downTo 2`, `2 until 2`).
- **Needs semantic info:** none
- **PSI types visited:** visitBinaryExpression
- **Tree traversal:** expression-level
- **Complexity:** 1
- **Config options:** none
- **Has ktlint equivalent:** none
- **Uses Analysis API:** no
- **Notes:** Only works on `KtConstantExpression` integer literals; does not detect invalid ranges with variable or computed bounds. Active by default since 1.2.0.

### missing-package-declaration
- **Name:** MissingPackageDeclaration
- **Source:** detekt-rules-potential-bugs/src/main/kotlin/dev/detekt/rules/potentialbugs/MissingPackageDeclaration.kt
- **Module:** detekt-rules-potential-bugs
- **Category:** bugs
- **What it checks:** Reports Kotlin source files that have no package declaration.
- **Needs semantic info:** none
- **PSI types visited:** visitKtFile
- **Tree traversal:** file-level
- **Complexity:** 1
- **Config options:** none
- **Has ktlint equivalent:** none
- **Uses Analysis API:** no
- **Notes:** --

### unconditional-jump-statement-in-loop
- **Name:** UnconditionalJumpStatementInLoop
- **Source:** detekt-rules-potential-bugs/src/main/kotlin/dev/detekt/rules/potentialbugs/UnconditionalJumpStatementInLoop.kt
- **Module:** detekt-rules-potential-bugs
- **Category:** bugs
- **What it checks:** Reports loops containing unconditional jump statements (`return`, `break`, `continue`) that cause the loop to execute at most once.
- **Needs semantic info:** none
- **PSI types visited:** visitLoopExpression
- **Tree traversal:** expression-level
- **Complexity:** 2
- **Config options:** none
- **Has ktlint equivalent:** none
- **Uses Analysis API:** no
- **Notes:** Handles labeled loops, elvis-jump patterns (`x ?: break`), and skips return statements that follow conditional jumps. Checks siblings to avoid false positives when a conditional jump precedes a return.

### useless-postfix-expression
- **Name:** UselessPostfixExpression
- **Source:** detekt-rules-potential-bugs/src/main/kotlin/dev/detekt/rules/potentialbugs/UselessPostfixExpression.kt
- **Module:** detekt-rules-potential-bugs
- **Category:** bugs
- **What it checks:** Reports postfix `++`/`--` expressions whose result is unused or immediately overwritten (e.g., `i = i++`, `return i++`).
- **Needs semantic info:** none
- **PSI types visited:** visitClass, visitReturnExpression, visitBinaryExpression
- **Tree traversal:** expression-level
- **Complexity:** 2
- **Config options:** none
- **Has ktlint equivalent:** none
- **Uses Analysis API:** no
- **Notes:** Collects class-level property names in `visitClass` to distinguish local variables from class properties when determining if a return-site postfix is actually unused. Uses mutable instance state (`properties`). Active by default since 1.21.0.
