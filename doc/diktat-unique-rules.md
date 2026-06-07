# Diktat Rules: Coverage Analysis vs ktlint + detekt

## Summary Table

| # | Diktat rule (NAME_ID) | Class | Already in ktlint | Already in detekt | Unique? |
|---|---|---|---|---|---|
| 1 | `file-naming` | FileNaming | `filename` (file named after class, PascalCase) | `MatchingDeclarationName` | No |
| 2 | `identifier-naming` | IdentifierNaming | Partial (`class-naming`, `function-naming`, `property-naming`, `enum-entry-name-case`, `backing-property-naming`) | Partial (`ClassNaming`, `FunctionNaming`, `EnumNaming`, `VariableNaming`, `PropertyNaming`) | **Partial** -- sub-checks EXCEPTION_SUFFIX, FUNCTION_BOOLEAN_PREFIX, CONFUSING_IDENTIFIER_NAMING, GENERIC_NAME, VARIABLE_HAS_PREFIX are unique |
| 3 | `package-naming` | PackageNaming | `package-name` (pattern only) | `PackageNaming`, `InvalidPackageDeclaration` | **Partial** -- PACKAGE_NAME_INCORRECT_PATH (auto-fix path match), PACKAGE_NAME_INCORRECT_PREFIX (domain prefix enforcement) are unique |
| 4 | `comments` | CommentsRule | -- | `ForbiddenComment` (different: pattern-based) | **Yes** -- detects commented-out code by parsing |
| 5 | `header-comment` | HeaderCommentRule | -- | `AbsentOrWrongFileLicense` (similar copyright) | **Partial** -- copyright year auto-update, header KDoc for multi-class files, position enforcement are unique |
| 6 | `kdoc-comments-codeblocks-formatting` | CommentsFormatting | `comment-spacing` (partial) | -- | **Yes** -- enforces whitespace in KDoc/block/EOL comments, blank lines around KDoc, if-else comment placement |
| 7 | `kdoc-comments` | KdocComments | -- | `UndocumentedPublicClass`, `UndocumentedPublicFunction`, `UndocumentedPublicProperty` (similar) | **Partial** -- @property/@param tag management for constructor params, COMMENTED_BY_KDOC, KDOC_DUPLICATE_PROPERTY are unique |
| 8 | `kdoc-formatting` | KdocFormatting | -- | `OutdatedDocumentation` (partial) | **Yes** -- tag ordering, @author/@since checks, empty tags, deprecated tag, spacing after tags |
| 9 | `kdoc-methods` | KdocMethods | -- | -- | **Yes** -- missing @param/@return/@throws tags, trivial KDoc detection |
| 10 | `annotation-new-line` | AnnotationNewLineRule | `annotation` (similar) | -- | No |
| 11 | `block-structure-braces` | BlockStructureBraces | `statement-wrapping`, `if-else-wrapping` (partial) | `BracesOnIfStatements` (partial) | **Partial** -- unified brace structure for all block types in one rule |
| 12 | `boolean-expressions` | BooleanExpressionsRule | -- | -- | **Yes** -- simplifies boolean expressions using jbool_expressions library |
| 13 | `braces-rule` | BracesInConditionalsAndLoopsRule | `multiline-if-else`, `multiline-loop` (similar) | `BracesOnIfStatements`, `BracesOnWhenStatements` | No |
| 14 | `class-like-structures` | ClassLikeStructuresOrderRule | -- | -- | **Yes** -- enforces ordering of declarations inside class bodies (properties, init, constructors, methods, companions) |
| 15 | `collapse-if` | CollapseIfStatementsRule | -- | -- | **Yes** -- detects and collapses redundant nested if-statements |
| 16 | `consecutive-spaces` | ConsecutiveSpacesRule | `no-multi-spaces` (same) | -- | No |
| 17 | `debug-print` | DebugPrintRule | -- | -- | **Yes** -- warns on print()/println()/console.log() debug statements |
| 18 | `empty-block-structure` | EmptyBlock | -- | `EmptyClassBlock`, `EmptyFunctionBlock`, etc. (similar per-type) | No |
| 19 | `enum-separated` | EnumsSeparated | `enum-wrapping` (similar) | -- | No |
| 20 | `blank-lines` | BlankLinesRule | `no-consecutive-blank-lines`, `no-blank-line-before-rbrace` (partial) | -- | **Partial** -- also enforces no blank lines at start/end of code blocks |
| 21 | `file-size` | FileSize | -- | -- | **Yes** -- reports files exceeding configurable line count |
| 22 | `file-structure` | FileStructureRule | `import-ordering`, `no-wildcard-imports`, `no-unused-imports` (partial) | `UnusedImport` (partial) | **Partial** -- FILE_INCORRECT_BLOCKS_ORDER (package/import/code ordering), FILE_CONTAINS_ONLY_COMMENTS are unique |
| 23 | `indentation` | IndentationRule | `indent` (similar) | -- | No |
| 24 | `newlines` | NewlinesRule | `chain-wrapping`, `no-line-break-before-assignment` (partial) | -- | **Partial** -- COMPLEX_EXPRESSION (wrapping of long dot-qualified chains), many newline placement rules are more opinionated |
| 25 | `semicolon` | SemicolonsRule | `no-semi` (same) | -- | No |
| 26 | `top-level-order` | TopLevelOrderRule | -- | -- | **Yes** -- enforces ordering of top-level declarations (properties, functions, classes) |
| 27 | `white-space` | WhiteSpaceRule | Various spacing rules (`op-spacing`, `colon-spacing`, `keyword-spacing`, etc.) | -- | No |
| 28 | `local-variables` | LocalVariablesRule | -- | -- | **Yes** -- warns when local variables are declared too far from first usage |
| 29 | `long-line` | LineLength | `max-line-length` (same) | `MaxLineLength` | No |
| 30 | `long-numerical-values` | LongNumericalValuesSeparatedRule | -- | -- | **Yes** -- enforces underscore separators in long numeric literals (e.g. 1_000_000) |
| 31 | `magic-number` | MagicNumberRule | -- | `MagicNumber` (detekt) | No |
| 32 | `modifier-order` | MultipleModifiersSequence | `modifier-order` (same) | `ModifierOrder` | No |
| 33 | `nullable-type` | NullableTypeRule | -- | -- | **Yes** -- suggests non-nullable types when property is initialized with a non-null value |
| 34 | `preview-annotation` | PreviewAnnotationRule | -- | -- | **Yes** -- @Preview functions must be private and end with "Preview" suffix |
| 35 | `range-conventional` | RangeConventionalRule | -- | `RangeUntilInsteadOfRangeTo` (partial) | **Partial** -- also replaces rangeTo() with .. operator |
| 36 | `statement` | SingleLineStatementsRule | `no-semi` (partial) | -- | **Partial** -- multiple statements per line |
| 37 | `sort-rule` | SortRule | -- | -- | **Yes** -- sorts class properties and enum members alphabetically |
| 38 | `string-concatenation` | StringConcatenationRule | -- | -- | **Yes** -- warns on string concatenation with +, suggests string templates |
| 39 | `string-template-format` | StringTemplateFormatRule | `string-template` (similar) | -- | No |
| 40 | `trailing-comma` | TrailingCommaRule | `trailing-comma-on-call-site`, `trailing-comma-on-declaration-site` (same) | -- | No |
| 41 | `when-must-have-else` | WhenMustHaveElseRule | -- | -- | **Yes** -- when used as statement must have else branch |
| 42 | `accurate-calculations` | AccurateCalculationsRule | -- | -- | **Yes** -- warns on floating-point arithmetic in accurate calculations |
| 43 | `no-var-rule` | ImmutableValNoVarRule | -- | -- | **Yes** -- suggests val instead of var when variable is never reassigned |
| 44 | `null-checks` | NullChecksRule | -- | `UseLet` (partial) | **Partial** -- more comprehensive: replaces if(x!=null) patterns with ?:, .let, .also, etc. |
| 45 | `smart-cast` | SmartCastRule | -- | -- | **Yes** -- detects redundant explicit casts after is-checks (smart cast available) |
| 46 | `type-alias` | TypeAliasRule | -- | -- | **Yes** -- suggests typealias for long/complex type references with nested generics |
| 47 | `variable-generic-type` | VariableGenericTypeDeclarationRule | -- | -- | **Yes** -- warns when generic type args are redundantly specified on both sides of assignment |
| 48 | `sync-in-async` | AsyncAndSyncRule | -- | `GlobalCoroutineUsage` (different) | **Yes** -- detects runBlocking inside async/launch coroutines |
| 49 | `avoid-nested-functions` | AvoidNestedFunctionsRule | -- | -- | **Yes** -- warns on nested function declarations (suggests moving to enclosing scope) |
| 50 | `inverse-method` | CheckInverseMethodRule | -- | -- | **Yes** -- suggests !isEmpty() -> isNotEmpty(), !filter -> filterNot, etc. |
| 51 | `custom-label` | CustomLabel | -- | `LabeledExpression` (partial) | **Partial** -- specifically targets custom labels on return/break/continue |
| 52 | `argument-size` | FunctionArgumentsSize | -- | `LongParameterList` (detekt) | No |
| 53 | `function-length` | FunctionLength | -- | `LongMethod` (detekt) | No |
| 54 | `lambda-length` | LambdaLengthRule | -- | -- | **Yes** -- reports lambdas exceeding configurable line count |
| 55 | `lambda-parameter-order` | LambdaParameterOrder | -- | -- | **Yes** -- lambda parameters should be last in function parameter list |
| 56 | `nested-block` | NestedFunctionBlock | -- | `NestedBlockDepth` (detekt) | No |
| 57 | `overloading-default-values` | OverloadingArgumentsFunction | -- | -- | **Yes** -- suggests default parameter values instead of function overloads |
| 58 | `parameter-name-in-outer-lambda` | ParameterNameInOuterLambdaRule | -- | -- | **Yes** -- requires explicit parameter names in outer lambdas when nesting depth exceeds threshold |
| 59 | `avoid-empty-primary-constructor` | AvoidEmptyPrimaryConstructor | -- | `EmptyDefaultConstructor` (same) | No |
| 60 | `avoid-utility-class` | AvoidUtilityClass | -- | `UtilityClassWithPublicConstructor` (detekt, similar) | No |
| 61 | `abstract-classes` | AbstractClassesRule | -- | `UnnecessaryAbstractClass` (detekt) | No |
| 62 | `compact-initialization` | CompactInitialization | -- | `AlsoCouldBeApply` (partial) | **Partial** -- wraps consecutive property-setter calls into apply {} block |
| 63 | `data-classes` | DataClassesRule | -- | `DataClassShouldBeImmutable` (different) | **Yes** -- suggests converting class to data class when appropriate |
| 64 | `inline-classes` | InlineClassesRule | -- | -- | **Yes** -- suggests using inline/value class for single-property classes |
| 65 | `single-constructor` | SingleConstructorRule | -- | -- | **Yes** -- suggests converting single secondary constructor to primary constructor |
| 66 | `single-init` | SingleInitRule | -- | -- | **Yes** -- warns when class has multiple init blocks, suggests merging |
| 67 | `stateless-class` | StatelessClassesRule | -- | -- | **Yes** -- suggests converting stateless class to object |
| 68 | `custom-getter-setter` | CustomGetterSetterRule | -- | -- | **Yes** -- warns on non-trivial custom getters/setters (suggests backing property or function) |
| 69 | `extension-functions-class-file` | ExtensionFunctionsInFileRule | -- | -- | **Yes** -- extension functions for a class should be in same file as the class |
| 70 | `extension-functions-same-name` | ExtensionFunctionsSameNameRule | -- | -- | **Yes** -- warns when extension function has same signature as class method |
| 71 | `implicit-backing-property` | ImplicitBackingPropertyRule | -- | -- | **Yes** -- warns on backing properties without corresponding public property |
| 72 | `getter-setter-fields` | PropertyAccessorFields | -- | -- | **Yes** -- enforces using `field` keyword inside custom get/set accessors |
| 73 | `run-in-script` | RunInScript | -- | -- | **Yes** -- kts scripts should wrap code in run{} blocks |
| 74 | `trivial-accessors` | TrivialPropertyAccessors | -- | -- | **Yes** -- removes trivial get/set accessors that just return/assign field |
| 75 | `last-index` | UseLastIndex | -- | -- | **Yes** -- replaces `x.length - 1` with `x.lastIndex` |
| 76 | `useless-supertype` | UselessSupertype | -- | `UnnecessaryInheritance` (different) | **Yes** -- removes redundant explicit supertype qualifiers in super.method() calls |

**Summary:** Of 76 diktat rules, approximately 20 are fully covered by ktlint+detekt, ~12 are partially covered, and **~44 are unique**.

---

## Unique Rules (Detailed)

### comments
- **Name:** CommentsRule
- **Source:** chapter2/comments/CommentsRule.kt
- **What it checks:** Detects commented-out code by attempting to parse comment text as Kotlin; reports if the comment content parses as valid code (imports, classes, functions, val/var declarations).
- **Needs semantic info:** none
- **Node types used:** EOL_COMMENT, BLOCK_COMMENT, FILE (KtFileElementType.INSTANCE)
- **Complexity:** 3
- **Notes:** Uses KtPsiFactory to parse comment text and checks for ERROR_ELEMENT. Handles both consecutive EOL comments (glued into blocks) and block comments. Separates import/package lines from other code for individual parsing. Heavy: creates a BlockCodeFragment per candidate.

### header-comment (unique sub-checks)
- **Name:** HeaderCommentRule
- **Source:** chapter2/comments/HeaderCommentRule.kt
- **What it checks:** (Unique parts) Copyright year auto-update to current year; header KDoc required for files with != 1 top-level class; header KDoc must be positioned before package directive.
- **Needs semantic info:** none
- **Node types used:** BLOCK_COMMENT, KDOC, PACKAGE_DIRECTIVE, IMPORT_LIST, WHITE_SPACE
- **Complexity:** 2
- **Notes:** detekt's AbsentOrWrongFileLicense covers copyright presence but not year auto-update or header KDoc requirements. Copyright text is compared after flattening whitespace.

### kdoc-comments-codeblocks-formatting
- **Name:** CommentsFormatting
- **Source:** chapter2/kdoc/CommentsFormatting.kt
- **What it checks:** Spacing before/after comment tokens; blank lines around KDoc; no blank line after first comment in block; if-else comment placement (comments between then/else should move inside else block).
- **Needs semantic info:** none
- **Node types used:** EOL_COMMENT, BLOCK_COMMENT, KDOC, IF, THEN, ELSE, ELSE_KEYWORD, CLASS, FUN, PROPERTY, BLOCK, CLASS_BODY, LBRACE, WHITE_SPACE
- **Complexity:** 2
- **Notes:** IF_ELSE_COMMENTS check is unique -- moves comments from between if/else keywords into the else block. Configurable number of spaces before comment and inside comment.

### kdoc-formatting
- **Name:** KdocFormatting
- **Source:** chapter2/kdoc/KdocFormatting.kt
- **What it checks:** KDoc tag ordering (@receiver, @param, @property, @return, @throws, @constructor, @since, @see, @sample, etc.); no @author tag; @since must not contain dates; no empty KDoc; no empty tags; blank line before basic tags; no blank lines between basic tags; @deprecated should use @Deprecated annotation instead; spacing after tag names.
- **Needs semantic info:** none
- **Node types used:** KDOC, KDOC_SECTION, KDOC_TAG, KDOC_TAG_NAME, KDOC_LEADING_ASTERISK, KDOC_TEXT, WHITE_SPACE
- **Complexity:** 3
- **Notes:** Very detailed KDoc structure enforcement. Uses DateTimeFormatter to detect date patterns in @since tags. Enforces a specific tag order that neither ktlint nor detekt checks.

### kdoc-methods
- **Name:** KdocMethods
- **Source:** chapter2/kdoc/KdocMethods.kt
- **What it checks:** Public/internal functions must have KDoc; KDoc must have @param for each parameter, @return for non-Unit return types, @throws for thrown exceptions; detects trivial/redundant KDoc (KDoc that just restates the function name).
- **Needs semantic info:** none
- **Node types used:** FUN, MODIFIER_LIST, KDOC, BLOCK, THROW, CATCH, TYPE_REFERENCE, REFERENCE_EXPRESSION, CALL_EXPRESSION, DOT_QUALIFIED_EXPRESSION
- **Complexity:** 3
- **Notes:** Scans function body for throw expressions to determine needed @throws tags. Uses heuristics to detect trivial KDoc. Skips test methods, standard methods (toString/equals/hashCode), getters/setters, anonymous functions.

### boolean-expressions
- **Name:** BooleanExpressionsRule
- **Source:** chapter3/BooleanExpressionsRule.kt
- **What it checks:** Simplifies complex boolean expressions in if/while conditions using algebraic simplification (DeMorgan's law, distributive law, idempotent law).
- **Needs semantic info:** none
- **Node types used:** CONDITION, BINARY_EXPRESSION, PREFIX_EXPRESSION, PARENTHESIZED
- **Complexity:** 3
- **Notes:** Uses the jbool_expressions library for symbolic boolean algebra. Maps Kotlin boolean sub-expressions to symbolic variables, simplifies, then maps back. Unique -- no equivalent in ktlint or detekt.

### class-like-structures
- **Name:** ClassLikeStructuresOrderRule
- **Source:** chapter3/ClassLikeStructuresOrderRule.kt
- **What it checks:** Enforces ordering of members inside class bodies: loggers, properties, init blocks, constructors, methods, nested classes/companion objects. Also enforces blank lines between property groups.
- **Needs semantic info:** none
- **Node types used:** CLASS_BODY, PROPERTY, CLASS_INITIALIZER, SECONDARY_CONSTRUCTOR, FUN, CLASS, OBJECT_DECLARATION, COMPANION_KEYWORD, WHITE_SPACE, ENUM_ENTRY
- **Complexity:** 2
- **Notes:** Defined ordering: (1) loggers (2) properties (3) init blocks (4) constructors (5) methods (6) nested classes (7) companion objects. Has configurable ordering.

### collapse-if
- **Name:** CollapseIfStatementsRule
- **Source:** chapter3/CollapseIfStatementsRule.kt
- **What it checks:** Detects nested if-statements without else branches where the inner if is the only statement, and suggests collapsing them into a single if with && condition.
- **Needs semantic info:** none
- **Node types used:** IF, THEN, BINARY_EXPRESSION, OPERATION_REFERENCE, LPAR, RPAR, LBRACE, RBRACE, WHITE_SPACE
- **Complexity:** 2
- **Notes:** Configurable nesting level threshold. Auto-fix merges conditions with &&. Handles braced and non-braced bodies.

### debug-print
- **Name:** DebugPrintRule
- **Source:** chapter3/DebugPrintRule.kt
- **What it checks:** Warns on `print()`, `println()` calls (Kotlin stdlib) and `console.log/warn/error/info` calls (Kotlin/JS).
- **Needs semantic info:** none
- **Node types used:** CALL_EXPRESSION, REFERENCE_EXPRESSION, DOT_QUALIFIED_EXPRESSION, VALUE_ARGUMENT_LIST, LAMBDA_ARGUMENT
- **Complexity:** 1
- **Notes:** Pure text-based matching on function names. Does not resolve whether the receiver is actually kotlin.io or console.

### file-size
- **Name:** FileSize
- **Source:** chapter3/files/FileSize.kt
- **What it checks:** Reports files that exceed a configurable maximum line count.
- **Needs semantic info:** none
- **Node types used:** FILE (KtFileElementType.INSTANCE)
- **Complexity:** 1
- **Notes:** Default threshold is configurable. Simple line-count check.

### top-level-order
- **Name:** TopLevelOrderRule
- **Source:** chapter3/files/TopLevelOrderRule.kt
- **What it checks:** Enforces ordering of top-level declarations in a file: properties first, then functions, then typealiases, then classes/objects.
- **Needs semantic info:** none
- **Node types used:** PROPERTY, FUN, TYPEALIAS, CLASS, OBJECT_DECLARATION, WHITE_SPACE
- **Complexity:** 2
- **Notes:** Sorts properties by const > non-const, val > var. Preserves associated comments when reordering.

### local-variables
- **Name:** LocalVariablesRule
- **Source:** chapter3/identifiers/LocalVariablesRule.kt
- **What it checks:** Warns when local variables are declared too far from their first usage, suggesting to move declaration closer.
- **Needs semantic info:** none
- **Node types used:** FILE (KtFileElementType.INSTANCE), PROPERTY, REFERENCE_EXPRESSION
- **Complexity:** 2
- **Notes:** Uses findAllVariablesWithUsages utility to track variable declarations and usages. Reports when declaration and first usage are too many lines apart.

### long-numerical-values
- **Name:** LongNumericalValuesSeparatedRule
- **Source:** chapter3/LongNumericalValuesSeparatedRule.kt
- **What it checks:** Enforces underscore separators in long numeric literals (e.g., `1000000` should be `1_000_000`).
- **Needs semantic info:** none
- **Node types used:** INTEGER_LITERAL, FLOAT_LITERAL
- **Complexity:** 1
- **Notes:** Configurable digit group size and minimum length threshold. Auto-fixes by inserting underscores.

### nullable-type
- **Name:** NullableTypeRule
- **Source:** chapter3/NullableTypeRule.kt
- **What it checks:** Warns when a property is declared with a nullable type but is initialized with a non-null literal value, suggesting to use non-nullable type instead.
- **Needs semantic info:** none
- **Node types used:** PROPERTY, NULLABLE_TYPE, TYPE_REFERENCE, EQ, various constant types (INTEGER_CONSTANT, FLOAT_CONSTANT, STRING_TEMPLATE, BOOLEAN_CONSTANT, etc.)
- **Complexity:** 2
- **Notes:** Auto-fixes by removing the `?` from the type. Only triggers when initializer is a known non-null expression.

### preview-annotation
- **Name:** PreviewAnnotationRule
- **Source:** chapter3/PreviewAnnotationRule.kt
- **What it checks:** Functions annotated with @Preview (Jetpack Compose) must be private and their name must end with "Preview" suffix.
- **Needs semantic info:** none
- **Node types used:** FUN, MODIFIER_LIST, ANNOTATION_ENTRY, IDENTIFIER
- **Complexity:** 1
- **Notes:** Jetpack Compose specific. Auto-fixes visibility and name.

### sort-rule
- **Name:** SortRule
- **Source:** chapter3/SortRule.kt
- **What it checks:** Enforces alphabetical ordering of class properties and enum members.
- **Needs semantic info:** none
- **Node types used:** CLASS_BODY, PROPERTY, ENUM_ENTRY
- **Complexity:** 2
- **Notes:** Auto-fixes by reordering nodes. Preserves comments attached to each member.

### string-concatenation
- **Name:** StringConcatenationRule
- **Source:** chapter3/StringConcatenationRule.kt
- **What it checks:** Warns on string concatenation using `+` operator, suggesting string templates instead.
- **Needs semantic info:** none
- **Node types used:** BINARY_EXPRESSION, OPERATION_REFERENCE, STRING_TEMPLATE
- **Complexity:** 1
- **Notes:** Only triggers when one operand is a string. Does not auto-fix.

### when-must-have-else
- **Name:** WhenMustHaveElseRule
- **Source:** chapter3/WhenMustHaveElseRule.kt
- **What it checks:** When expressions used as statements (not as expressions) must have an else branch.
- **Needs semantic info:** none
- **Node types used:** WHEN, WHEN_ENTRY
- **Complexity:** 1
- **Notes:** Distinguishes statement vs expression usage by checking parent context.

### accurate-calculations
- **Name:** AccurateCalculationsRule
- **Source:** chapter4/calculations/AccurateCalculationsRule.kt
- **What it checks:** Warns when floating-point numbers are used in arithmetic operations where precision matters; suggests BigDecimal.
- **Needs semantic info:** none
- **Node types used:** BINARY_EXPRESSION, CALL_EXPRESSION, DOT_QUALIFIED_EXPRESSION, FLOAT_LITERAL
- **Complexity:** 2
- **Notes:** Exception: allows floating-point arithmetic when the absolute value is immediately compared (abs(x) < epsilon pattern).

### no-var-rule
- **Name:** ImmutableValNoVarRule
- **Source:** chapter4/ImmutableValNoVarRule.kt
- **What it checks:** Warns when a local `var` variable is never reassigned, suggesting `val` instead.
- **Needs semantic info:** none
- **Node types used:** FILE (KtFileElementType.INSTANCE), PROPERTY
- **Complexity:** 2
- **Notes:** Uses findAllVariablesWithAssignments and findAllVariablesWithUsages to track assignments. Excludes variables used in loops or lambdas (accumulators). Only checks local variables.

### null-checks (unique parts)
- **Name:** NullChecksRule
- **Source:** chapter4/NullChecksRule.kt
- **What it checks:** Replaces explicit null comparisons (`if (x != null)`) with idiomatic Kotlin constructs: `?.let {}`, `?.also {}`, `?:`, `require(x != null)`, etc.
- **Needs semantic info:** none
- **Node types used:** CONDITION, BINARY_EXPRESSION, IF, THEN, ELSE, BLOCK, NULL, REFERENCE_EXPRESSION, CALL_EXPRESSION
- **Complexity:** 3
- **Notes:** More comprehensive than detekt's UseLet. Handles both `if` conditions and standalone binary expressions. Converts `x == null` to early-return patterns, `if (x != null) { ... }` to `x?.let { ... }`, etc.

### smart-cast
- **Name:** SmartCastRule
- **Source:** chapter4/SmartCastRule.kt
- **What it checks:** Detects redundant explicit `as` casts that are unnecessary because a preceding `is` check already enabled smart casting.
- **Needs semantic info:** none
- **Node types used:** IS_EXPRESSION, BINARY_WITH_TYPE, IF, THEN, ELSE, WHEN, BLOCK, REFERENCE_EXPRESSION
- **Complexity:** 3
- **Notes:** Tracks variable usages across is-check and cast-usage scopes. Auto-fixes by removing the explicit cast. Does not use type resolution (heuristic-based).

### type-alias
- **Name:** TypeAliasRule
- **Source:** chapter4/TypeAliasRule.kt
- **What it checks:** Suggests using `typealias` for long type references (configurable length threshold) with two or more nested generics.
- **Needs semantic info:** none
- **Node types used:** TYPE_REFERENCE, LT, VALUE_PARAMETER
- **Complexity:** 1
- **Notes:** Default max type reference length is 25 characters. Counts `<` tokens and functional type parameters to detect nesting.

### variable-generic-type
- **Name:** VariableGenericTypeDeclarationRule
- **Source:** chapter4/VariableGenericTypeDeclarationRule.kt
- **What it checks:** Warns when generic type arguments are redundantly specified on both sides of an assignment (e.g., `val x: Map<Int, String> = emptyMap<Int, String>()`), suggesting to remove them from the right side.
- **Needs semantic info:** none
- **Node types used:** PROPERTY, VALUE_PARAMETER, CALL_EXPRESSION, DOT_QUALIFIED_EXPRESSION, TYPE_ARGUMENT_LIST
- **Complexity:** 1
- **Notes:** Compares left-side (declared type) and right-side (constructor/factory call) type arguments.

### sync-in-async
- **Name:** AsyncAndSyncRule
- **Source:** chapter5/AsyncAndSyncRule.kt
- **What it checks:** Detects `runBlocking` calls inside `async`, `launch`, or suspend functions, which defeats the purpose of coroutines.
- **Needs semantic info:** none
- **Node types used:** CALL_EXPRESSION, REFERENCE_EXPRESSION, FUN, LAMBDA_ARGUMENT
- **Complexity:** 2
- **Notes:** Text-based detection of function names ("runBlocking", "async", "launch"). Checks suspend modifier on enclosing functions.

### avoid-nested-functions
- **Name:** AvoidNestedFunctionsRule
- **Source:** chapter5/AvoidNestedFunctionsRule.kt
- **What it checks:** Warns when functions are nested inside other functions (local function declarations), suggesting to move them to the enclosing scope.
- **Needs semantic info:** none
- **Node types used:** FUN, BLOCK
- **Complexity:** 1
- **Notes:** Skips lambda expressions; only targets named function declarations inside other functions.

### inverse-method
- **Name:** CheckInverseMethodRule
- **Source:** chapter5/CheckInverseMethodRule.kt
- **What it checks:** Suggests replacing negated method calls with their positive counterparts: `!isEmpty()` -> `isNotEmpty()`, `!isBlank()` -> `isNotBlank()`, `filter { !it }` -> `filterNot { it }`, etc.
- **Needs semantic info:** none
- **Node types used:** CALL_EXPRESSION, OPERATION_REFERENCE, PREFIX_EXPRESSION
- **Complexity:** 1
- **Notes:** Uses a hardcoded mapping of method names to their inverses.

### lambda-length
- **Name:** LambdaLengthRule
- **Source:** chapter5/LambdaLengthRule.kt
- **What it checks:** Reports lambda expressions that exceed a configurable maximum line count.
- **Needs semantic info:** none
- **Node types used:** LAMBDA_EXPRESSION
- **Complexity:** 1
- **Notes:** Default threshold is 10 lines. Counts lines in the lambda body.

### lambda-parameter-order
- **Name:** LambdaParameterOrder
- **Source:** chapter5/LambdaParameterOrder.kt
- **What it checks:** Lambda-typed parameters should be the last parameter in function declarations.
- **Needs semantic info:** none
- **Node types used:** FUN, VALUE_PARAMETER_LIST, VALUE_PARAMETER, FUNCTION_TYPE
- **Complexity:** 1
- **Notes:** Kotlin convention for trailing lambda syntax.

### overloading-default-values
- **Name:** OverloadingArgumentsFunction
- **Source:** chapter5/OverloadingArgumentsFunction.kt
- **What it checks:** Warns when multiple function overloads could be replaced by a single function with default parameter values.
- **Needs semantic info:** none
- **Node types used:** FUN, IDENTIFIER, TYPE_REFERENCE, VALUE_PARAMETER
- **Complexity:** 2
- **Notes:** Compares function signatures among siblings to detect overload sets. Checks modifiers match. Does not auto-fix.

### parameter-name-in-outer-lambda
- **Name:** ParameterNameInOuterLambdaRule
- **Source:** chapter5/ParameterNameInOuterLambdaRule.kt
- **What it checks:** Requires explicit parameter names (not implicit `it`) in outer lambdas when lambda nesting depth exceeds a configurable threshold.
- **Needs semantic info:** none
- **Node types used:** LAMBDA_EXPRESSION, VALUE_PARAMETER_LIST, VALUE_PARAMETER
- **Complexity:** 1
- **Notes:** Configurable nesting threshold (default 1). Prevents confusion from multiple nested `it` references.

### compact-initialization (unique parts)
- **Name:** CompactInitialization
- **Source:** chapter6/classes/CompactInitialization.kt
- **What it checks:** Detects consecutive property-setter calls on a newly created object and suggests wrapping them in an `apply {}` block.
- **Needs semantic info:** none
- **Node types used:** PROPERTY, CALL_EXPRESSION, DOT_QUALIFIED_EXPRESSION, REFERENCE_EXPRESSION, EQ, OPERATION_REFERENCE
- **Complexity:** 2
- **Notes:** More specific than detekt's AlsoCouldBeApply (which checks `also` blocks). This rule identifies `val x = Foo(); x.bar = 1; x.baz = 2` and suggests `val x = Foo().apply { bar = 1; baz = 2 }`.

### data-classes
- **Name:** DataClassesRule
- **Source:** chapter6/classes/DataClassesRule.kt
- **What it checks:** Suggests converting regular classes to data classes when they have no methods other than those generated by data class, no custom constructor logic, and only properties.
- **Needs semantic info:** none
- **Node types used:** CLASS, CLASS_BODY, PRIMARY_CONSTRUCTOR, VALUE_PARAMETER_LIST, FUN, PROPERTY
- **Complexity:** 2
- **Notes:** Checks multiple conditions: no secondary constructors, no init blocks, no superclass with constructor args, no methods in body, etc.

### inline-classes
- **Name:** InlineClassesRule
- **Source:** chapter6/classes/InlineClassesRule.kt
- **What it checks:** Suggests using `inline class` (value class) when a class has exactly one property in primary constructor and no other members.
- **Needs semantic info:** none
- **Node types used:** CLASS, PRIMARY_CONSTRUCTOR, VALUE_PARAMETER_LIST, CLASS_BODY
- **Complexity:** 1
- **Notes:** Does not auto-fix. Checks for absence of interfaces, other constructors, and body members.

### single-constructor
- **Name:** SingleConstructorRule
- **Source:** chapter6/classes/SingleConstructorRule.kt
- **What it checks:** When a class has no primary constructor and exactly one secondary constructor, suggests converting that secondary constructor to a primary constructor.
- **Needs semantic info:** none
- **Node types used:** CLASS, PRIMARY_CONSTRUCTOR, SECONDARY_CONSTRUCTOR, CLASS_BODY, VALUE_PARAMETER_LIST
- **Complexity:** 2
- **Notes:** Auto-fixes by moving the constructor parameter list to primary position and extracting property assignments from constructor body.

### single-init
- **Name:** SingleInitRule
- **Source:** chapter6/classes/SingleInitRule.kt
- **What it checks:** Warns when a class has multiple `init` blocks, suggesting to merge them into one.
- **Needs semantic info:** none
- **Node types used:** CLASS_BODY, CLASS_INITIALIZER, PROPERTY, VALUE_PARAMETER
- **Complexity:** 2
- **Notes:** Auto-fixes by merging all init block bodies into the first one. Handles property initializers that can be moved into the init block.

### stateless-class
- **Name:** StatelessClassesRule
- **Source:** chapter6/classes/StatelessClassesRule.kt
- **What it checks:** Suggests converting stateless classes (classes with no state/properties, only functions) to objects.
- **Needs semantic info:** none
- **Node types used:** CLASS, CLASS_BODY, PROPERTY, FUN, SUPER_TYPE_LIST, PRIMARY_CONSTRUCTOR
- **Complexity:** 2
- **Notes:** Checks that class has no properties, no constructor parameters, and is not instantiated elsewhere in the file. Auto-fixes by changing `class` to `object`.

### custom-getter-setter
- **Name:** CustomGetterSetterRule
- **Source:** chapter6/CustomGetterSetterRule.kt
- **What it checks:** Warns on non-trivial custom property getters and setters, suggesting to use a backing property or convert to a function instead.
- **Needs semantic info:** none
- **Node types used:** PROPERTY_ACCESSOR, BLOCK, RETURN, BINARY_EXPRESSION
- **Complexity:** 1
- **Notes:** Only warns, does not auto-fix. Encourages using explicit functions for complex computed properties.

### extension-functions-class-file
- **Name:** ExtensionFunctionsInFileRule
- **Source:** chapter6/ExtensionFunctionsInFileRule.kt
- **What it checks:** Extension functions for a class should be defined in the same file as that class.
- **Needs semantic info:** none
- **Node types used:** FILE (KtFileElementType.INSTANCE), FUN, CLASS, TYPE_REFERENCE, IDENTIFIER
- **Complexity:** 2
- **Notes:** Collects all class names in file, then checks if extension functions target those classes.

### extension-functions-same-name
- **Name:** ExtensionFunctionsSameNameRule
- **Source:** chapter6/ExtensionFunctionsSameNameRule.kt
- **What it checks:** Warns when an extension function has the same name and parameter types as a member function of the class it extends.
- **Needs semantic info:** none
- **Node types used:** FILE (KtFileElementType.INSTANCE), FUN, CLASS, TYPE_REFERENCE
- **Complexity:** 2
- **Notes:** Extension functions with same signature as member functions are confusing -- member always wins at call site.

### implicit-backing-property
- **Name:** ImplicitBackingPropertyRule
- **Source:** chapter6/ImplicitBackingPropertyRule.kt
- **What it checks:** Warns when a private property prefixed with underscore (backing property pattern) exists but has no corresponding public property or accessor.
- **Needs semantic info:** none
- **Node types used:** CLASS_BODY, PROPERTY, IDENTIFIER, PROPERTY_ACCESSOR
- **Complexity:** 1
- **Notes:** Checks naming convention: `_foo` should have a corresponding `foo` property.

### getter-setter-fields
- **Name:** PropertyAccessorFields
- **Source:** chapter6/PropertyAccessorFields.kt
- **What it checks:** Inside custom get/set accessors, the backing field should be accessed via `field` keyword, not via the property name or `this.propertyName`.
- **Needs semantic info:** none
- **Node types used:** PROPERTY_ACCESSOR, REFERENCE_EXPRESSION, THIS_EXPRESSION, DOT_QUALIFIED_EXPRESSION
- **Complexity:** 1
- **Notes:** Auto-fixes by replacing property name references with `field`.

### run-in-script
- **Name:** RunInScript
- **Source:** chapter6/RunInScript.kt
- **What it checks:** In .kts files, top-level statements should be wrapped in `run {}` blocks. Gradle .kts files have relaxed rules allowing expressions and assignments.
- **Needs semantic info:** none
- **Node types used:** SCRIPT_INITIALIZER, CALL_EXPRESSION, DOT_QUALIFIED_EXPRESSION, LAMBDA_ARGUMENT, LAMBDA_EXPRESSION
- **Complexity:** 1
- **Notes:** Differentiates between regular .kts and gradle.kts files.

### trivial-accessors
- **Name:** TrivialPropertyAccessors
- **Source:** chapter6/TrivialPropertyAccessors.kt
- **What it checks:** Removes trivial property getter/setter that simply return/assign the field without additional logic.
- **Needs semantic info:** none
- **Node types used:** PROPERTY_ACCESSOR, BLOCK, RETURN, BINARY_EXPRESSION, REFERENCE_EXPRESSION
- **Complexity:** 1
- **Notes:** Auto-fixes by removing the accessor entirely. Trivial getter: `get() = field` or `get() { return field }`. Trivial setter: `set(value) { field = value }`.

### last-index
- **Name:** UseLastIndex
- **Source:** chapter6/UseLastIndex.kt
- **What it checks:** Replaces `x.length - 1` patterns with `x.lastIndex`.
- **Needs semantic info:** none
- **Node types used:** BINARY_EXPRESSION, DOT_QUALIFIED_EXPRESSION, REFERENCE_EXPRESSION, INTEGER_CONSTANT, OPERATION_REFERENCE
- **Complexity:** 1
- **Notes:** Auto-fixes. Only handles the `.length - 1` pattern.

### useless-supertype
- **Name:** UselessSupertype
- **Source:** chapter6/UselessSupertype.kt
- **What it checks:** Removes redundant explicit supertype qualifiers in `super<Type>.method()` calls when there is no ambiguity (only one supertype defines that method).
- **Needs semantic info:** none
- **Node types used:** CLASS, SUPER_TYPE_LIST, SUPER_TYPE_CALL_ENTRY, SUPER_TYPE_ENTRY, DOT_QUALIFIED_EXPRESSION, SUPER_EXPRESSION, REFERENCE_EXPRESSION, CLASS_BODY, FUN
- **Complexity:** 2
- **Notes:** Collects all super.method() calls and checks if the method exists in multiple supertypes. If only one supertype has it, removes the qualifier.

### identifier-naming (unique sub-checks)
- **Name:** IdentifierNaming
- **Source:** chapter1/IdentifierNaming.kt
- **What it checks:** (Unique parts not in ktlint/detekt) EXCEPTION_SUFFIX: classes inheriting from Exception must have "Exception" suffix. FUNCTION_BOOLEAN_PREFIX: boolean-returning functions should start with is/has/are/have/should/can. CONFUSING_IDENTIFIER_NAMING: warns on confusing single-letter names (O, l, I, Z, S, e, B, h, n, m, rn). GENERIC_NAME: generic type parameters should be single capital letter optionally followed by digits. VARIABLE_HAS_PREFIX: variables should not use Hungarian notation prefixes (mVariable, xCode). TYPEALIAS_NAME_INCORRECT_CASE: typealias names must be PascalCase.
- **Needs semantic info:** none
- **Node types used:** CLASS, OBJECT_DECLARATION, PROPERTY, VALUE_PARAMETER, ENUM_ENTRY, FUN, TYPEALIAS, TYPE_PARAMETER, IDENTIFIER
- **Complexity:** 3
- **Notes:** Very large rule with many sub-checks. Auto-fixes most naming issues. Uses findAllVariablesWithUsages to rename all usages when fixing variable names.

### package-naming (unique sub-checks)
- **Name:** PackageNaming
- **Source:** chapter1/PackageNaming.kt
- **What it checks:** (Unique parts) PACKAGE_NAME_INCORRECT_PATH: package name must match directory structure. PACKAGE_NAME_INCORRECT_PREFIX: package name must start with configured domain prefix. PACKAGE_NAME_MISSING: auto-generates package name from directory path.
- **Needs semantic info:** none
- **Node types used:** PACKAGE_DIRECTIVE, DOT_QUALIFIED_EXPRESSION, REFERENCE_EXPRESSION, IDENTIFIER
- **Complexity:** 2
- **Notes:** detekt's InvalidPackageDeclaration is similar for path matching but diktat also auto-fixes by inserting/correcting the package statement and enforces domain prefix.
