# ktlint Rules Catalog (Part 1: A-M)

### annotation
- **Name:** AnnotationRule
- **Source:** ktlint-ruleset-standard/src/main/kotlin/com/pinterest/ktlint/ruleset/standard/rules/AnnotationRule.kt
- **Category:** wrapping
- **What it checks:** Ensures annotations are wrapped to separate lines according to Kotlin coding conventions (annotations with parameters on own line, multiple annotations on separate lines, file annotations separated by blank line).
- **Autocorrectable:** yes
- **Needs semantic info:** none
- **Node types used:** FILE_ANNOTATION_LIST, ANNOTATED_EXPRESSION, MODIFIER_LIST, ANNOTATION, ANNOTATION_ENTRY, ANNOTATION_TARGET, CLASS, COLON, CONSTRUCTOR_CALLEE, CONSTRUCTOR_KEYWORD, GT, IDENTIFIER, LAMBDA_EXPRESSION, OPERATION_REFERENCE, REFERENCE_EXPRESSION, RPAR, TYPE_ARGUMENT_LIST, TYPE_PROJECTION, TYPE_REFERENCE, USER_TYPE, VALUE_ARGUMENT, VALUE_ARGUMENT_LIST, VALUE_PARAMETER
- **Tree traversal:** declaration-level
- **Complexity:** 3
- **Config options:** INDENT_SIZE_PROPERTY, INDENT_STYLE_PROPERTY, ANNOTATIONS_WITH_PARAMETERS_NOT_TO_BE_WRAPPED_PROPERTY (ktlint_annotation_handle_annotations_with_parameters_same_as_annotations_without_parameters)
- **Has detekt equivalent:** none
- **Notes:** Uses CODE_STYLE_PROPERTY to vary behavior between ktlint_official and other styles. Complex sibling/parent navigation with annotation use-site target awareness. Interacts heavily with IndentationRule.

### annotation-spacing
- **Name:** AnnotationSpacingRule
- **Source:** ktlint-ruleset-standard/src/main/kotlin/com/pinterest/ktlint/ruleset/standard/rules/AnnotationSpacingRule.kt
- **Category:** spacing
- **What it checks:** Ensures annotations occur immediately prior to the annotated construct with no extra blank lines or comments between them.
- **Autocorrectable:** yes
- **Needs semantic info:** none
- **Node types used:** MODIFIER_LIST, FILE_ANNOTATION_LIST, ANNOTATION_ENTRY
- **Tree traversal:** declaration-level
- **Complexity:** 2
- **Config options:** none
- **Has detekt equivalent:** none
- **Notes:** Handles special case of swapping annotation and comment order during autocorrect. Uses remove/addChild for reordering nodes.

### argument-list-wrapping
- **Name:** ArgumentListWrappingRule
- **Source:** ktlint-ruleset-standard/src/main/kotlin/com/pinterest/ktlint/ruleset/standard/rules/ArgumentListWrappingRule.kt
- **Category:** wrapping
- **What it checks:** Wraps each argument in a function call to a separate line if at least one argument is already on a separate line or max line length is exceeded.
- **Autocorrectable:** yes
- **Needs semantic info:** none
- **Node types used:** VALUE_ARGUMENT_LIST, VALUE_ARGUMENT, LPAR, RPAR, FUNCTION_LITERAL, BINARY_EXPRESSION, DOT_QUALIFIED_EXPRESSION, EQ, OPERATION_REFERENCE, TYPE_ARGUMENT_LIST, COLLECTION_LITERAL_EXPRESSION
- **Tree traversal:** expression-level
- **Complexity:** 2
- **Config options:** INDENT_SIZE_PROPERTY, INDENT_STYLE_PROPERTY, MAX_LINE_LENGTH_PROPERTY, IGNORE_WHEN_PARAMETER_COUNT_GREATER_OR_EQUAL_THAN_PROPERTY (ktlint_argument_list_wrapping_ignore_when_parameter_count_greater_or_equal_than)
- **Has detekt equivalent:** none
- **Notes:** Delegates final indentation to IndentationRule. Has IDEA quirk workarounds for generic type argument lists and dot-qualified assignment expressions. Uses CONTROL_FLOW_KEYWORDS TokenSet.

### backing-property-naming
- **Name:** BackingPropertyNamingRule
- **Source:** ktlint-ruleset-standard/src/main/kotlin/com/pinterest/ktlint/ruleset/standard/rules/BackingPropertyNamingRule.kt
- **Category:** naming
- **What it checks:** Enforces that backing properties (prefixed with underscore) use lower camel case, are private, and have a corresponding public property or function.
- **Autocorrectable:** no
- **Needs semantic info:** none
- **Node types used:** PROPERTY, IDENTIFIER, CLASS_BODY, COMPANION_KEYWORD, FUN, MODIFIER_LIST, OBJECT_DECLARATION, OVERRIDE_KEYWORD, PRIVATE_KEYWORD, PROTECTED_KEYWORD, INTERNAL_KEYWORD, VALUE_PARAMETER, VALUE_PARAMETER_LIST
- **Tree traversal:** declaration-level
- **Complexity:** 2
- **Config options:** CODE_STYLE_PROPERTY
- **Has detekt equivalent:** none
- **Notes:** Searches class body and companion object for correlated properties/functions. Behavior varies between android_studio and other code styles for visibility checking.

### binary-expression-wrapping
- **Name:** BinaryExpressionWrappingRule
- **Source:** ktlint-ruleset-standard/src/main/kotlin/com/pinterest/ktlint/ruleset/standard/rules/BinaryExpressionWrappingRule.kt
- **Category:** wrapping
- **What it checks:** Wraps binary expressions when they exceed max line length, preferring to wrap the entire expression before wrapping inner arguments.
- **Autocorrectable:** yes
- **Needs semantic info:** none
- **Node types used:** BINARY_EXPRESSION, CALL_EXPRESSION, CONDITION, ELVIS, EQ, FUN, FUNCTION_LITERAL, LAMBDA_ARGUMENT, LAMBDA_EXPRESSION, LBRACE, LONG_STRING_TEMPLATE_ENTRY, OPERATION_REFERENCE, PROPERTY, RBRACE, VALUE_ARGUMENT
- **Tree traversal:** expression-level
- **Complexity:** 2
- **Config options:** INDENT_SIZE_PROPERTY, INDENT_STYLE_PROPERTY, MAX_LINE_LENGTH_PROPERTY
- **Has detekt equivalent:** none
- **Notes:** Uses custom anyOf extension on IElementType. Handles elvis operator specially (wraps before instead of after). Ignores binary expressions inside raw string literals.

### blank-line-before-declaration
- **Name:** BlankLineBeforeDeclarationRule
- **Source:** ktlint-ruleset-standard/src/main/kotlin/com/pinterest/ktlint/ruleset/standard/rules/BlankLineBeforeDeclarationRule.kt
- **Category:** whitespace
- **What it checks:** Inserts a blank line before class, function, object, property, and property accessor declarations, with exceptions for first-in-body and consecutive properties.
- **Autocorrectable:** yes
- **Needs semantic info:** none
- **Node types used:** CLASS, CLASS_BODY, CLASS_INITIALIZER, FUN, FUNCTION_LITERAL, LBRACE, OBJECT_DECLARATION, OBJECT_LITERAL, PROPERTY, PROPERTY_ACCESSOR, BLOCK, EQ, RETURN_KEYWORD, VALUE_ARGUMENT, WHEN
- **Tree traversal:** declaration-level
- **Complexity:** 2
- **Config options:** CODE_STYLE_PROPERTY (stops traversal for intellij_idea style)
- **Has detekt equivalent:** none
- **Notes:** Only active in ktlint_official and android_studio code styles. Has many exclusion cases (first in class body, first in block, consecutive properties, local properties, anonymous functions, etc.).

### blank-line-before-file-annotation
- **Name:** BlankLineBeforeFileAnnotation
- **Source:** ktlint-ruleset-standard/src/main/kotlin/com/pinterest/ktlint/ruleset/standard/rules/BlankLineBeforeFileAnnotation.kt
- **Category:** whitespace
- **What it checks:** Ensures a blank line exists before file-level annotations.
- **Autocorrectable:** yes
- **Needs semantic info:** none
- **Node types used:** FILE_ANNOTATION_LIST, PACKAGE_DIRECTIVE, IMPORT_LIST, WHITE_SPACE
- **Tree traversal:** file-level
- **Complexity:** 1
- **Config options:** CODE_STYLE_PROPERTY (stops traversal for intellij_idea style)
- **Has detekt equivalent:** none
- **Notes:** Experimental rule (RuleV2.Experimental). Stops traversal early once file annotation list or package/import is found.

### blank-line-before-imports
- **Name:** BlankLineBeforeImports
- **Source:** ktlint-ruleset-standard/src/main/kotlin/com/pinterest/ktlint/ruleset/standard/rules/BlankLineBeforeImports.kt
- **Category:** whitespace
- **What it checks:** Ensures a blank line exists before the import list.
- **Autocorrectable:** yes
- **Needs semantic info:** none
- **Node types used:** IMPORT_LIST, PACKAGE_DIRECTIVE, WHITE_SPACE
- **Tree traversal:** file-level
- **Complexity:** 1
- **Config options:** CODE_STYLE_PROPERTY (stops traversal for intellij_idea style)
- **Has detekt equivalent:** none
- **Notes:** Experimental rule (RuleV2.Experimental). Only fires when import list is non-empty. Stops traversal early.

### blank-line-before-package
- **Name:** BlankLineBeforePackage
- **Source:** ktlint-ruleset-standard/src/main/kotlin/com/pinterest/ktlint/ruleset/standard/rules/BlankLineBeforePackage.kt
- **Category:** whitespace
- **What it checks:** Ensures a blank line exists before the package statement.
- **Autocorrectable:** yes
- **Needs semantic info:** none
- **Node types used:** PACKAGE_DIRECTIVE, IMPORT_LIST, WHITE_SPACE
- **Tree traversal:** file-level
- **Complexity:** 1
- **Config options:** CODE_STYLE_PROPERTY (stops traversal for intellij_idea style)
- **Has detekt equivalent:** none
- **Notes:** Experimental rule (RuleV2.Experimental). Stops traversal early once package or import is found.

### blank-line-between-when-conditions
- **Name:** BlankLineBetweenWhenConditions
- **Source:** ktlint-ruleset-standard/src/main/kotlin/com/pinterest/ktlint/ruleset/standard/rules/BlankLineBetweenWhenConditions.kt
- **Category:** whitespace
- **What it checks:** Consistently adds or removes blank lines between when-conditions based on whether any multiline when-condition exists in the statement.
- **Autocorrectable:** yes
- **Needs semantic info:** none
- **Node types used:** WHEN, WHEN_ENTRY
- **Tree traversal:** expression-level
- **Complexity:** 2
- **Config options:** LINE_BREAK_AFTER_WHEN_CONDITION_PROPERTY (ij_kotlin_line_break_after_multiline_when_entry)
- **Has detekt equivalent:** none
- **Notes:** Detects multiline when-conditions by checking for newlines in text or preceding standalone comments. Behavior is controlled by the ij_kotlin_line_break_after_multiline_when_entry property.

### block-comment-initial-star-alignment
- **Name:** BlockCommentInitialStarAlignmentRule
- **Source:** ktlint-ruleset-standard/src/main/kotlin/com/pinterest/ktlint/ruleset/standard/rules/BlockCommentInitialStarAlignmentRule.kt
- **Category:** comments
- **What it checks:** Aligns the initial star (*) in block comment continuation lines with the start of the block comment.
- **Autocorrectable:** yes
- **Needs semantic info:** none
- **Node types used:** BLOCK_COMMENT
- **Tree traversal:** expression-level
- **Complexity:** 2
- **Config options:** none
- **Has detekt equivalent:** none
- **Notes:** Works by splitting the block comment text on newlines and regex-matching continuation lines. Replaces entire text via replaceTextWith to fix all lines at once.

### call-expression-wrapping
- **Name:** CallExpressionWrappingRule
- **Source:** ktlint-ruleset-standard/src/main/kotlin/com/pinterest/ktlint/ruleset/standard/rules/CallExpressionWrappingRule.kt
- **Category:** wrapping
- **What it checks:** Wraps call expression value argument lists and lambda arguments when they contain newlines or exceed max line length.
- **Autocorrectable:** yes
- **Needs semantic info:** none
- **Node types used:** CALL_EXPRESSION, REFERENCE_EXPRESSION, VALUE_ARGUMENT_LIST, LAMBDA_ARGUMENT, LAMBDA_EXPRESSION, FUNCTION_LITERAL, ARROW, LBRACE, LPAR, RBRACE, RPAR
- **Tree traversal:** expression-level
- **Complexity:** 2
- **Config options:** INDENT_SIZE_PROPERTY, INDENT_STYLE_PROPERTY, MAX_LINE_LENGTH_PROPERTY
- **Has detekt equivalent:** none
- **Notes:** Experimental rule (RuleV2.Experimental). Handles wrapping after '(' and before ')', and after '{'/'->' and before '}' in lambda arguments.

### chain-method-continuation
- **Name:** ChainMethodContinuationRule
- **Source:** ktlint-ruleset-standard/src/main/kotlin/com/pinterest/ktlint/ruleset/standard/rules/ChainMethodContinuationRule.kt
- **Category:** wrapping
- **What it checks:** Enforces that chained method calls with '.' or '?.' operators are either all on one line or each on a separate line.
- **Autocorrectable:** yes
- **Needs semantic info:** none
- **Node types used:** DOT, SAFE_ACCESS, DOT_QUALIFIED_EXPRESSION, SAFE_ACCESS_EXPRESSION, ARRAY_ACCESS_EXPRESSION, CALL_EXPRESSION, CLASS_LITERAL_EXPRESSION, CLOSING_QUOTE, FUNCTION_LITERAL, IMPORT_DIRECTIVE, LAMBDA_ARGUMENT, LAMBDA_EXPRESSION, LBRACE, LONG_STRING_TEMPLATE_ENTRY, PACKAGE_DIRECTIVE, POSTFIX_EXPRESSION, PREFIX_EXPRESSION, RBRACE, RBRACKET, REFERENCE_EXPRESSION, RPAR, STRING_TEMPLATE
- **Tree traversal:** expression-level
- **Complexity:** 3
- **Config options:** CODE_STYLE_PROPERTY, INDENT_SIZE_PROPERTY, INDENT_STYLE_PROPERTY, MAX_LINE_LENGTH_PROPERTY, FORCE_MULTILINE_WHEN_CHAIN_OPERATOR_COUNT_GREATER_OR_EQUAL_THAN_PROPERTY (ktlint_chain_method_rule_force_multiline_when_chain_operator_count_greater_or_equal_than)
- **Has detekt equivalent:** none
- **Notes:** Restricted to ktlint_official code style (RuleV2.OfficialCodeStyle). Contains complex ChainedExpression data class that reconstructs the chain hierarchy from arbitrary AST depth. Disallows comments between dot and call expression.

### chain-wrapping
- **Name:** ChainWrappingRule
- **Source:** ktlint-ruleset-standard/src/main/kotlin/com/pinterest/ktlint/ruleset/standard/rules/ChainWrappingRule.kt
- **Category:** wrapping
- **What it checks:** Ensures dot/safe-access/elvis operators are at the beginning of continuation lines, and arithmetic/logical operators are at the end of lines.
- **Autocorrectable:** yes
- **Needs semantic info:** none
- **Node types used:** DOT, SAFE_ACCESS, ELVIS, ANDAND, OROR, MUL, DIV, PERC, PLUS, MINUS, COMMA, LBRACE, LPAR, ELSE_KEYWORD, OPERATION_REFERENCE, PREFIX_EXPRESSION
- **Tree traversal:** expression-level
- **Complexity:** 2
- **Config options:** INDENT_SIZE_PROPERTY, INDENT_STYLE_PROPERTY
- **Has detekt equivalent:** none
- **Notes:** Distinguishes three token groups: sameLineTokens (stay at end), prefixTokens (position-dependent), nextLineTokens (move to start). Handles spread operator (*) and prefix position exceptions.

### class-naming
- **Name:** ClassNamingRule
- **Source:** ktlint-ruleset-standard/src/main/kotlin/com/pinterest/ktlint/ruleset/standard/rules/ClassNamingRule.kt
- **Category:** naming
- **What it checks:** Enforces that class and object names start with an uppercase letter and use camel case.
- **Autocorrectable:** no
- **Needs semantic info:** none
- **Node types used:** CLASS, OBJECT_DECLARATION, IDENTIFIER, IMPORT_DIRECTIVE, DOT_QUALIFIED_EXPRESSION
- **Tree traversal:** declaration-level
- **Complexity:** 1
- **Config options:** none
- **Has detekt equivalent:** ClassNaming
- **Notes:** Allows backticked class names in test classes (detected by JUnit Jupiter imports). Excludes Kotlin keyword identifiers wrapped in backticks.

### class-signature
- **Name:** ClassSignatureRule
- **Source:** ktlint-ruleset-standard/src/main/kotlin/com/pinterest/ktlint/ruleset/standard/rules/ClassSignatureRule.kt
- **Category:** wrapping
- **What it checks:** Formats class signatures by wrapping constructor parameters and super type lists according to max line length and parameter count thresholds.
- **Autocorrectable:** yes
- **Needs semantic info:** none
- **Node types used:** CLASS, CLASS_BODY, COLON, COMMA, CONSTRUCTOR_DELEGATION_CALL, CONSTRUCTOR_DELEGATION_REFERENCE, CONSTRUCTOR_KEYWORD, EOL_COMMENT, EXPECT_KEYWORD, MODIFIER_LIST, PRIMARY_CONSTRUCTOR, RPAR, SECONDARY_CONSTRUCTOR, SUPER_TYPE_CALL_ENTRY, SUPER_TYPE_LIST, VALUE_PARAMETER, VALUE_PARAMETER_LIST, WHITE_SPACE, ANNOTATION, ANNOTATION_ENTRY
- **Tree traversal:** declaration-level
- **Complexity:** 3
- **Config options:** INDENT_SIZE_PROPERTY, INDENT_STYLE_PROPERTY, MAX_LINE_LENGTH_PROPERTY, FORCE_MULTILINE_WHEN_PARAMETER_COUNT_GREATER_OR_EQUAL_THAN_PROPERTY (ktlint_class_signature_rule_force_multiline_when_parameter_count_greater_or_equal_than)
- **Has detekt equivalent:** none
- **Notes:** Very complex rule with dry-run mode for length calculation. Reorders super type call entry to be first. In ktlint_official style, default multiline threshold is 1 parameter. Removes empty parenthesis from no-arg constructors.

### comment-spacing
- **Name:** CommentSpacingRule
- **Source:** ktlint-ruleset-standard/src/main/kotlin/com/pinterest/ktlint/ruleset/standard/rules/CommentSpacingRule.kt
- **Category:** comments
- **What it checks:** Ensures a space exists before and after the // in end-of-line comments, with exceptions for special directives (noinspection, region, endregion, language=).
- **Autocorrectable:** yes
- **Needs semantic info:** none
- **Node types used:** EOL_COMMENT
- **Tree traversal:** expression-level
- **Complexity:** 1
- **Config options:** none
- **Has detekt equivalent:** none
- **Notes:** Creates a new PsiCommentImpl to replace the node rather than modifying text directly, to preserve parent/sibling references for subsequent rules.

### comment-wrapping
- **Name:** CommentWrappingRule
- **Source:** ktlint-ruleset-standard/src/main/kotlin/com/pinterest/ktlint/ruleset/standard/rules/CommentWrappingRule.kt
- **Category:** comments
- **What it checks:** Checks external wrapping of block comments, disallowing block comments that start on one line and end on another when surrounded by other code elements.
- **Autocorrectable:** yes (partially -- some cases are not autocorrectable)
- **Needs semantic info:** none
- **Node types used:** BLOCK_COMMENT, LBRACE, RBRACE
- **Tree traversal:** expression-level
- **Complexity:** 2
- **Config options:** INDENT_SIZE_PROPERTY, INDENT_STYLE_PROPERTY
- **Has detekt equivalent:** none
- **Notes:** Does not alter wrapping inside the comment itself. Allows single-line blocks containing a block comment like `{ /* no-op */ }`. Some violations are reported as non-autocorrectable.

### context-receiver-list-wrapping
- **Name:** ContextReceiverListWrappingRule
- **Source:** ktlint-ruleset-standard/src/main/kotlin/com/pinterest/ktlint/ruleset/standard/rules/ContextReceiverListWrappingRule.kt
- **Category:** wrapping
- **What it checks:** Wraps context parameter lists (not deprecated context receivers) to a separate line and wraps parameters when max line length is exceeded.
- **Autocorrectable:** yes
- **Needs semantic info:** none
- **Node types used:** CONTEXT_RECEIVER_LIST, CONTEXT_RECEIVER, FUN, FUNCTION_TYPE, GT, RPAR, TYPE_ARGUMENT_LIST, TYPE_PROJECTION, TYPE_REFERENCE, VALUE_PARAMETER, VALUE_PARAMETER_LIST
- **Tree traversal:** declaration-level
- **Complexity:** 2
- **Config options:** INDENT_SIZE_PROPERTY, INDENT_STYLE_PROPERTY, MAX_LINE_LENGTH_PROPERTY
- **Has detekt equivalent:** none
- **Notes:** Only applies to context parameter lists that do NOT contain a CONTEXT_RECEIVER child (i.e., new-style context parameters). Handles type argument list wrapping separately. Excludes function type references in value parameter lists.

### context-receiver-wrapping
- **Name:** ContextReceiverWrappingRule
- **Source:** ktlint-ruleset-standard/src/main/kotlin/com/pinterest/ktlint/ruleset/standard/rules/ContextReceiverWrappingRule.kt
- **Category:** wrapping
- **What it checks:** Wraps deprecated context receivers to a separate line and wraps receiver arguments when max line length is exceeded.
- **Autocorrectable:** yes
- **Needs semantic info:** none
- **Node types used:** CONTEXT_RECEIVER_LIST, CONTEXT_RECEIVER, FUNCTION_TYPE, GT, RPAR, TYPE_ARGUMENT_LIST, TYPE_PROJECTION, TYPE_REFERENCE, VALUE_PARAMETER, VALUE_PARAMETER_LIST
- **Tree traversal:** declaration-level
- **Complexity:** 2
- **Config options:** INDENT_SIZE_PROPERTY, INDENT_STYLE_PROPERTY, MAX_LINE_LENGTH_PROPERTY
- **Has detekt equivalent:** none
- **Notes:** Only applies to context receiver lists that DO contain a CONTEXT_RECEIVER child (deprecated context receivers from Kotlin <2.2). Will be removed once context receivers are no longer supported by the Kotlin compiler.

### enum-entry-name-case
- **Name:** EnumEntryNameCaseRule
- **Source:** ktlint-ruleset-standard/src/main/kotlin/com/pinterest/ktlint/ruleset/standard/rules/EnumEntryNameCaseRule.kt
- **Category:** naming
- **What it checks:** Enforces that enum entry names use UPPER_CASE, CamelCase, or either, depending on the configured casing option.
- **Autocorrectable:** no
- **Needs semantic info:** none
- **Node types used:** ENUM_ENTRY, IDENTIFIER
- **Tree traversal:** declaration-level
- **Complexity:** 1
- **Config options:** ENUM_ENTRY_NAME_CASING_PROPERTY (ktlint_enum_entry_name_casing)
- **Has detekt equivalent:** EnumNaming
- **Notes:** Uses regExIgnoringDiacriticsAndStrokesOnLetters for regex matching. Default allows both upper_cases and camel_cases. Uses SafeEnumValueParser for the editorconfig property.

### enum-wrapping
- **Name:** EnumWrappingRule
- **Source:** ktlint-ruleset-standard/src/main/kotlin/com/pinterest/ktlint/ruleset/standard/rules/EnumWrappingRule.kt
- **Category:** wrapping
- **What it checks:** Wraps each enum entry to a separate line when the enum class body is multiline, has annotated entries, or has commented entries, and ensures a blank line between entries and other declarations.
- **Autocorrectable:** yes
- **Needs semantic info:** none
- **Node types used:** CLASS, CLASS_BODY, ENUM_ENTRY, ENUM_KEYWORD, MODIFIER_LIST, ANNOTATION_ENTRY, RBRACE
- **Tree traversal:** declaration-level
- **Complexity:** 2
- **Config options:** INDENT_SIZE_PROPERTY, INDENT_STYLE_PROPERTY
- **Has detekt equivalent:** none
- **Notes:** Handles comment-before-first-entry wrapping separately. Also wraps closing brace and adds blank line separator between enum entries and other declarations.

### expression-operand-wrapping
- **Name:** ExpressionOperandWrappingRule
- **Source:** ktlint-ruleset-standard/src/main/kotlin/com/pinterest/ktlint/ruleset/standard/rules/ExpressionOperandWrappingRule.kt
- **Category:** wrapping
- **What it checks:** Wraps operands in multiline binary expressions (logical and arithmetic) to a newline after the operator.
- **Autocorrectable:** yes
- **Needs semantic info:** none
- **Node types used:** BINARY_EXPRESSION, OPERATION_REFERENCE, EOL_COMMENT, ANDAND, OROR, PLUS, MINUS, MUL, DIV
- **Tree traversal:** expression-level
- **Complexity:** 2
- **Config options:** INDENT_SIZE_PROPERTY, INDENT_STYLE_PROPERTY
- **Has detekt equivalent:** none
- **Notes:** Experimental rule (RuleV2.Experimental). Only wraps operands matching WRAPPABLE_OPERAND TokenSet. Checks parent binary expressions for multiline status.

### filename
- **Name:** FilenameRule
- **Source:** ktlint-ruleset-standard/src/main/kotlin/com/pinterest/ktlint/ruleset/standard/rules/FilenameRule.kt
- **Category:** naming
- **What it checks:** Enforces that files with a single class are named after that class, and all filenames use PascalCase.
- **Autocorrectable:** no
- **Needs semantic info:** none
- **Node types used:** CLASS, FUN, IDENTIFIER, MODIFIER_LIST, OBJECT_DECLARATION, PROPERTY, TYPEALIAS, TYPE_REFERENCE
- **Tree traversal:** file-level
- **Complexity:** 2
- **Config options:** none
- **Has detekt equivalent:** MatchingDeclarationName
- **Notes:** Ignores non-.kt files and package.kt. Checks for single top-level class with extension functions. Uses isRoot check to only process once per file.

### final-newline
- **Name:** FinalNewlineRule
- **Source:** ktlint-ruleset-standard/src/main/kotlin/com/pinterest/ktlint/ruleset/standard/rules/FinalNewlineRule.kt
- **Category:** whitespace
- **What it checks:** Ensures the file ends with a newline character (or does not, based on insert_final_newline setting).
- **Autocorrectable:** yes
- **Needs semantic info:** none
- **Node types used:** (checks isRoot)
- **Tree traversal:** file-level
- **Complexity:** 1
- **Config options:** INSERT_FINAL_NEWLINE_PROPERTY
- **Has detekt equivalent:** NewLineAtEndOfFile
- **Notes:** Processes only the root node and immediately stops traversal. Uses tailrec to find last child node.

### function-expression-body
- **Name:** FunctionExpressionBodyRule
- **Source:** ktlint-ruleset-standard/src/main/kotlin/com/pinterest/ktlint/ruleset/standard/rules/FunctionExpressionBodyRule.kt
- **Category:** style
- **What it checks:** Converts function bodies consisting of a single return or throw statement to expression bodies.
- **Autocorrectable:** yes
- **Needs semantic info:** none
- **Node types used:** BLOCK, COLON, EQ, FUN, LBRACE, RBRACE, RETURN, RETURN_KEYWORD, THROW, TYPE_REFERENCE
- **Tree traversal:** declaration-level
- **Complexity:** 2
- **Config options:** CODE_STYLE_PROPERTY, INDENT_SIZE_PROPERTY, INDENT_STYLE_PROPERTY
- **Has detekt equivalent:** UseExpressionBody (detekt-formatting)
- **Notes:** For throw statements, inserts Unit return type if no explicit return type exists. Uses KtlintKotlinCompiler.createASTNodeFromText to create the Unit type reference AST node. Guards against multiple return keywords.

### function-literal
- **Name:** FunctionLiteralRule
- **Source:** ktlint-ruleset-standard/src/main/kotlin/com/pinterest/ktlint/ruleset/standard/rules/FunctionLiteralRule.kt
- **Category:** wrapping
- **What it checks:** Formats lambda/function literal parameter lists, arrows, and blocks according to Kotlin coding conventions (parameters on first line with arrow, or arrow on separate line for long parameter lists).
- **Autocorrectable:** yes
- **Needs semantic info:** none
- **Node types used:** FUNCTION_LITERAL, VALUE_PARAMETER_LIST, VALUE_PARAMETER, ARROW, BLOCK, LBRACE, RBRACE, LAMBDA_ARGUMENT, LAMBDA_EXPRESSION, ELSE, THEN, WHEN_ENTRY
- **Tree traversal:** expression-level
- **Complexity:** 3
- **Config options:** CODE_STYLE_PROPERTY, INDENT_SIZE_PROPERTY, INDENT_STYLE_PROPERTY, MAX_LINE_LENGTH_PROPERTY
- **Has detekt equivalent:** none
- **Notes:** Removes redundant arrows when parameter list is empty. Handles both single-line and multiline rewriting. Complex interaction with max line length for wrapping decisions. Checks for lambda expressions in when/if-else contexts.

### function-naming
- **Name:** FunctionNamingRule
- **Source:** ktlint-ruleset-standard/src/main/kotlin/com/pinterest/ktlint/ruleset/standard/rules/FunctionNamingRule.kt
- **Category:** naming
- **What it checks:** Enforces that function names start with a lowercase letter and use camel case, with exceptions for factory methods and test functions.
- **Autocorrectable:** no
- **Needs semantic info:** none
- **Node types used:** FUN, FUN_KEYWORD, IDENTIFIER, IMPORT_DIRECTIVE, MODIFIER_LIST, OVERRIDE_KEYWORD, ANNOTATION, ANNOTATION_ENTRY, CONSTRUCTOR_CALLEE, CALL_EXPRESSION, REFERENCE_EXPRESSION, TYPE_REFERENCE, USER_TYPE, VALUE_PARAMETER_LIST
- **Tree traversal:** declaration-level
- **Complexity:** 2
- **Config options:** IGNORE_WHEN_ANNOTATED_WITH_PROPERTY (ktlint_function_naming_ignore_when_annotated_with)
- **Has detekt equivalent:** FunctionNaming
- **Notes:** Detects test classes via imports from JUnit, Kotest, TestNG, and kotlin.test. Factory methods are detected by matching function name against return type. Excludes overridden functions and anonymous functions.

### function-return-type-spacing
- **Name:** FunctionReturnTypeSpacingRule
- **Source:** ktlint-ruleset-standard/src/main/kotlin/com/pinterest/ktlint/ruleset/standard/rules/FunctionReturnTypeSpacingRule.kt
- **Category:** spacing
- **What it checks:** Ensures no whitespace before the colon and a single space after the colon in function return type declarations.
- **Autocorrectable:** yes
- **Needs semantic info:** none
- **Node types used:** FUN, COLON
- **Tree traversal:** declaration-level
- **Complexity:** 1
- **Config options:** MAX_LINE_LENGTH_PROPERTY
- **Has detekt equivalent:** none
- **Notes:** Only merges lines when the merged result fits within max line length. Does not attempt rewriting multi-line function signatures (leaves that to FunctionSignatureRule).

### function-signature
- **Name:** FunctionSignatureRule
- **Source:** ktlint-ruleset-standard/src/main/kotlin/com/pinterest/ktlint/ruleset/standard/rules/FunctionSignatureRule.kt
- **Category:** wrapping
- **What it checks:** Formats function signatures by wrapping parameters to separate lines when max line length is exceeded or parameter count threshold is met, and controls function body expression wrapping.
- **Autocorrectable:** yes
- **Needs semantic info:** none
- **Node types used:** FUN, FUN_KEYWORD, BLOCK, CONTEXT_RECEIVER_LIST, EQ, LPAR, RPAR, MODIFIER_LIST, ANNOTATION, ANNOTATION_ENTRY, ANNOTATED_EXPRESSION, OPEN_QUOTE, VALUE_PARAMETER, VALUE_PARAMETER_LIST, WHITE_SPACE, EOL_COMMENT, BLOCK_COMMENT
- **Tree traversal:** declaration-level
- **Complexity:** 3
- **Config options:** INDENT_SIZE_PROPERTY, INDENT_STYLE_PROPERTY, MAX_LINE_LENGTH_PROPERTY, FORCE_MULTILINE_WHEN_PARAMETER_COUNT_GREATER_OR_EQUAL_THAN_PROPERTY (ktlint_function_signature_rule_force_multiline_when_parameter_count_greater_or_equal_than), FUNCTION_BODY_EXPRESSION_WRAPPING_PROPERTY (ktlint_function_signature_body_expression_wrapping)
- **Has detekt equivalent:** none
- **Notes:** Very complex rule with dry-run mode for calculating whitespace corrections. Has three body expression wrapping modes: default, multiline, always. Skips functions with comments in signature. In ktlint_official, default multiline threshold is 2 parameters. The FUNCTION_BODY_EXPRESSION_WRAPPING_PROPERTY is also used by MultilineExpressionWrappingRule.

### function-start-of-body-spacing
- **Name:** FunctionStartOfBodySpacingRule
- **Source:** ktlint-ruleset-standard/src/main/kotlin/com/pinterest/ktlint/ruleset/standard/rules/FunctionStartOfBodySpacingRule.kt
- **Category:** spacing
- **What it checks:** Ensures a single space before and after the '=' in expression body functions, and a single space before the '{' in block body functions.
- **Autocorrectable:** yes
- **Needs semantic info:** none
- **Node types used:** FUN, EQ, BLOCK, WHITE_SPACE
- **Tree traversal:** declaration-level
- **Complexity:** 1
- **Config options:** none
- **Has detekt equivalent:** none
- **Notes:** Allows newline after '=' (does not force single space if already on new line). Straightforward whitespace checks.

### function-type-modifier-spacing
- **Name:** FunctionTypeModifierSpacingRule
- **Source:** ktlint-ruleset-standard/src/main/kotlin/com/pinterest/ktlint/ruleset/standard/rules/FunctionTypeModifierSpacingRule.kt
- **Category:** spacing
- **What it checks:** Ensures a single space between the modifier list and the function type.
- **Autocorrectable:** yes
- **Needs semantic info:** none
- **Node types used:** MODIFIER_LIST, FUNCTION_TYPE
- **Tree traversal:** declaration-level
- **Complexity:** 1
- **Config options:** none
- **Has detekt equivalent:** none
- **Notes:** Simple pattern match -- checks nextCodeSibling of MODIFIER_LIST for FUNCTION_TYPE.

### function-type-reference-spacing
- **Name:** FunctionTypeReferenceSpacingRule
- **Source:** ktlint-ruleset-standard/src/main/kotlin/com/pinterest/ktlint/ruleset/standard/rules/FunctionTypeReferenceSpacingRule.kt
- **Category:** spacing
- **What it checks:** Removes unexpected whitespace between a function receiver type reference and the value parameter list.
- **Autocorrectable:** yes
- **Needs semantic info:** none
- **Node types used:** FUN, TYPE_REFERENCE, VALUE_PARAMETER_LIST, NULLABLE_TYPE
- **Tree traversal:** declaration-level
- **Complexity:** 1
- **Config options:** none
- **Has detekt equivalent:** none
- **Notes:** Iterates siblings until VALUE_PARAMETER_LIST, removing any non-empty whitespace nodes encountered. Handles nullable type wrapping.

### fun-keyword-spacing
- **Name:** FunKeywordSpacingRule
- **Source:** ktlint-ruleset-standard/src/main/kotlin/com/pinterest/ktlint/ruleset/standard/rules/FunKeywordSpacingRule.kt
- **Category:** spacing
- **What it checks:** Ensures a single space after the 'fun' keyword, including before backticked identifiers.
- **Autocorrectable:** yes
- **Needs semantic info:** none
- **Node types used:** FUN_KEYWORD, WHITE_SPACE, IDENTIFIER
- **Tree traversal:** declaration-level
- **Complexity:** 1
- **Config options:** none
- **Has detekt equivalent:** none
- **Notes:** Handles edge case where backticked identifier is adjacent to fun keyword without any whitespace.

### if-else-bracing
- **Name:** IfElseBracingRule
- **Source:** ktlint-ruleset-standard/src/main/kotlin/com/pinterest/ktlint/ruleset/standard/rules/IfElseBracingRule.kt
- **Category:** style
- **What it checks:** Ensures all branches of an if-else statement are wrapped in braces if at least one branch uses braces.
- **Autocorrectable:** yes
- **Needs semantic info:** none
- **Node types used:** IF, THEN, ELSE, ELSE_KEYWORD, BLOCK, LBRACE, RBRACE, RPAR
- **Tree traversal:** expression-level
- **Complexity:** 2
- **Config options:** INDENT_SIZE_PROPERTY, INDENT_STYLE_PROPERTY
- **Has detekt equivalent:** BracesOnIfStatements (similar)
- **Notes:** Restricted to ktlint_official code style (RuleV2.OfficialCodeStyle). Creates KtBlockExpression to wrap unbraced branches. Checks parent if-else for consistent bracing.

### if-else-wrapping
- **Name:** IfElseWrappingRule
- **Source:** ktlint-ruleset-standard/src/main/kotlin/com/pinterest/ktlint/ruleset/standard/rules/IfElseWrappingRule.kt
- **Category:** wrapping
- **What it checks:** Enforces that single-line if statements are kept simple (no blocks) and multiline if statements have proper newline wrapping for then/else branches.
- **Autocorrectable:** yes (partially -- single-line block violations are not autocorrectable)
- **Needs semantic info:** none
- **Node types used:** IF, IF_KEYWORD, THEN, ELSE, ELSE_KEYWORD, BLOCK, LBRACE, RBRACE, RPAR
- **Tree traversal:** expression-level
- **Complexity:** 2
- **Config options:** INDENT_SIZE_PROPERTY, INDENT_STYLE_PROPERTY
- **Has detekt equivalent:** none
- **Notes:** Restricted to ktlint_official code style (RuleV2.OfficialCodeStyle). Disallows comments between RPAR/THEN, THEN/ELSE_KEYWORD, and ELSE_KEYWORD/ELSE. Allows "else if" on same line.

### import-ordering
- **Name:** ImportOrderingRule
- **Source:** ktlint-ruleset-standard/src/main/kotlin/com/pinterest/ktlint/ruleset/standard/rules/ImportOrderingRule.kt
- **Category:** imports
- **What it checks:** Enforces import ordering according to configured layout pattern (IDEA default, ASCII/Android, or custom).
- **Autocorrectable:** yes
- **Needs semantic info:** none
- **Node types used:** IMPORT_LIST, IMPORT_DIRECTIVE, BLOCK_COMMENT, EOL_COMMENT
- **Tree traversal:** file-level
- **Complexity:** 3
- **Config options:** IJ_KOTLIN_IMPORTS_LAYOUT_PROPERTY (ij_kotlin_imports_layout)
- **Has detekt equivalent:** none
- **Notes:** Removes duplicate imports. Does not autocorrect when comments exist in import list. Uses ImportSorter and PatternEntry for custom layout parsing. Supports blank line patterns via '|' separator. Legacy "idea"/"ascii" values are deprecated.

### indent
- **Name:** IndentationRule
- **Source:** ktlint-ruleset-standard/src/main/kotlin/com/pinterest/ktlint/ruleset/standard/rules/IndentationRule.kt
- **Category:** whitespace
- **What it checks:** Enforces consistent indentation throughout the file using either spaces or tabs, with correct indent levels for all code constructs.
- **Autocorrectable:** yes
- **Needs semantic info:** none
- **Node types used:** Nearly all element types including ANNOTATED_EXPRESSION, ANNOTATION, ANNOTATION_ENTRY, ARRAY_ACCESS_EXPRESSION, ARROW, BINARY_EXPRESSION, BINARY_WITH_TYPE, BLOCK, BLOCK_COMMENT, BODY, CALL_EXPRESSION, CATCH, CLASS, CLOSING_QUOTE, COLON, CONDITION, CONSTRUCTOR_DELEGATION_CALL, CONSTRUCTOR_KEYWORD, CONTEXT_RECEIVER_LIST, DELEGATED_SUPER_TYPE_ENTRY, DESTRUCTURING_DECLARATION, DOT, DOT_QUALIFIED_EXPRESSION, ELSE, ELVIS, EQ, FINALLY, FOR, FUN, FUNCTION_LITERAL, IDENTIFIER, IF, IS_EXPRESSION, KDOC, KDOC_END, KDOC_LEADING_ASTERISK, KDOC_START, LBRACE, LBRACKET, LITERAL_STRING_TEMPLATE_ENTRY, LONG_STRING_TEMPLATE_ENTRY, LPAR, MODIFIER_LIST, NULLABLE_TYPE, OBJECT_DECLARATION, OPEN_QUOTE, OPERATION_REFERENCE, PARENTHESIZED, POSTFIX_EXPRESSION, PREFIX_EXPRESSION, PRIMARY_CONSTRUCTOR, PROPERTY, PROPERTY_ACCESSOR, RBRACE, RBRACKET, REGULAR_STRING_PART, RETURN_KEYWORD, RPAR, SAFE_ACCESS_EXPRESSION, SECONDARY_CONSTRUCTOR, STRING_TEMPLATE, SUPER_TYPE_LIST, THEN, TRY, TYPEALIAS, TYPE_ARGUMENT_LIST, TYPE_CONSTRAINT, TYPE_CONSTRAINT_LIST, TYPE_PARAMETER_LIST, TYPE_REFERENCE, USER_TYPE, VALUE_ARGUMENT, VALUE_ARGUMENT_LIST, VALUE_PARAMETER, VALUE_PARAMETER_LIST, WHEN, WHEN_ENTRY, WHERE_KEYWORD, WHILE
- **Tree traversal:** file-level
- **Complexity:** 3
- **Config options:** CODE_STYLE_PROPERTY, INDENT_SIZE_PROPERTY, INDENT_STYLE_PROPERTY, INDENT_WHEN_ARROW_ON_NEW_LINE (ij_kotlin_indent_before_arrow_on_new_line)
- **Has detekt equivalent:** Indentation (detekt-formatting wraps ktlint)
- **Notes:** The most complex rule in ktlint. Uses a Deque-based IndentContext stack to track nested indent levels. Contains a separate StringTemplateIndenter inner class for raw string handling. Handles tabs vs spaces normalization. Extensive code-style-dependent behavior differences between ktlint_official and IntelliJ IDEA defaults. Has afterVisitChildNodes and afterLastNode lifecycle hooks.

### kdoc
- **Name:** KdocRule
- **Source:** ktlint-ruleset-standard/src/main/kotlin/com/pinterest/ktlint/ruleset/standard/rules/KdocRule.kt
- **Category:** comments
- **What it checks:** Disallows KDoc comments except at the start of allowed declarations (class, enum entry, function, object, property, secondary constructor, typealias, value parameter) and forbids dangling top-level KDocs.
- **Autocorrectable:** no
- **Needs semantic info:** none
- **Node types used:** KDOC, CLASS, ENUM_ENTRY, FILE, FUN, OBJECT_DECLARATION, PROPERTY, SECONDARY_CONSTRUCTOR, TYPEALIAS, VALUE_PARAMETER
- **Tree traversal:** declaration-level
- **Complexity:** 1
- **Config options:** INDENT_SIZE_PROPERTY, INDENT_STYLE_PROPERTY
- **Has detekt equivalent:** none
- **Notes:** Uses TokenSet for allowed parent types. Reports different messages for dangling top-level KDoc vs KDoc inside wrong parent vs KDoc not at start of declaration.

### kdoc-wrapping
- **Name:** KdocWrappingRule
- **Source:** ktlint-ruleset-standard/src/main/kotlin/com/pinterest/ktlint/ruleset/standard/rules/KdocWrappingRule.kt
- **Category:** comments
- **What it checks:** Checks that KDoc comments start on their own line and are not followed by other elements on the same line.
- **Autocorrectable:** yes (partially -- "must be on new line before" is not autocorrectable)
- **Needs semantic info:** none
- **Node types used:** KDOC, KDOC_START, KDOC_END
- **Tree traversal:** expression-level
- **Complexity:** 1
- **Config options:** INDENT_SIZE_PROPERTY, INDENT_STYLE_PROPERTY
- **Has detekt equivalent:** none
- **Notes:** Does not alter wrapping inside the KDoc. Only autocorrects the trailing element case (inserts newline after KDoc). The "before" case is reported but not autocorrected.

### lambda-return
- **Name:** LambdaReturnRule
- **Source:** ktlint-ruleset-standard/src/main/kotlin/com/pinterest/ktlint/ruleset/standard/rules/LambdaReturnRule.kt
- **Category:** style
- **What it checks:** Removes unnecessary labeled return statements at the end of lambda expressions.
- **Autocorrectable:** yes
- **Needs semantic info:** none
- **Node types used:** FUNCTION_LITERAL, BLOCK, RETURN, LABEL_QUALIFIER
- **Tree traversal:** expression-level
- **Complexity:** 2
- **Config options:** CODE_STYLE_PROPERTY, INDENT_SIZE_PROPERTY, INDENT_STYLE_PROPERTY, MAX_LINE_LENGTH_PROPERTY
- **Has detekt equivalent:** none
- **Notes:** Experimental rule (RuleV2.Experimental). Moves the return value expression before the RETURN node and then removes the RETURN. Only processes the last return statement in the block.

### max-line-length
- **Name:** MaxLineLengthRule
- **Source:** ktlint-ruleset-standard/src/main/kotlin/com/pinterest/ktlint/ruleset/standard/rules/MaxLineLengthRule.kt
- **Category:** style
- **What it checks:** Reports lines that exceed the configured maximum line length.
- **Autocorrectable:** no
- **Needs semantic info:** none
- **Node types used:** PACKAGE_DIRECTIVE, IMPORT_DIRECTIVE, KDOC, STRING_TEMPLATE, IDENTIFIER, COMMA
- **Tree traversal:** file-level
- **Complexity:** 1
- **Config options:** MAX_LINE_LENGTH_PROPERTY, IGNORE_BACKTICKED_IDENTIFIER_PROPERTY (ktlint_ignore_back_ticked_identifier)
- **Has detekt equivalent:** MaxLineLength
- **Notes:** Excludes package/import directives, KDoc, raw multiline strings, lines containing only a single template string, and lines containing only comments. Also provides the maxLineLength() extension function on EditorConfig used by many other rules. This extension checks whether the max-line-length rule is enabled before returning the property value.

### mixed-condition-operators
- **Name:** MixedConditionOperatorsRule
- **Source:** ktlint-ruleset-standard/src/main/kotlin/com/pinterest/ktlint/ruleset/standard/rules/MixedConditionOperatorsRule.kt
- **Category:** style
- **What it checks:** Disallows mixing '&&' and '||' operators at the same level in a condition without parentheses.
- **Autocorrectable:** no
- **Needs semantic info:** none
- **Node types used:** BINARY_EXPRESSION, OPERATION_REFERENCE, ANDAND, OROR
- **Tree traversal:** expression-level
- **Complexity:** 2
- **Config options:** none
- **Has detekt equivalent:** none
- **Notes:** Walks parent binary expressions to find the root and checks for mixed operators. Reports at the root binary expression offset. Uses TokenSet for logical operators.

### modifier-list-spacing
- **Name:** ModifierListSpacingRule
- **Source:** ktlint-ruleset-standard/src/main/kotlin/com/pinterest/ktlint/ruleset/standard/rules/ModifierListSpacingRule.kt
- **Category:** spacing
- **What it checks:** Ensures proper spacing between modifiers in a modifier list (single space between modifiers, single newline after annotations, newline after context receiver list).
- **Autocorrectable:** yes
- **Needs semantic info:** none
- **Node types used:** MODIFIER_LIST, ANNOTATION, ANNOTATION_ENTRY, CONTEXT_RECEIVER_LIST
- **Tree traversal:** declaration-level
- **Complexity:** 2
- **Config options:** INDENT_SIZE_PROPERTY, INDENT_STYLE_PROPERTY
- **Has detekt equivalent:** none
- **Notes:** Treats the whitespace after the last modifier list entry specially (it is outside the modifier list in the AST). Allows newlines after annotations. Disallows double blank lines after annotations.

### modifier-order
- **Name:** ModifierOrderRule
- **Source:** ktlint-ruleset-standard/src/main/kotlin/com/pinterest/ktlint/ruleset/standard/rules/ModifierOrderRule.kt
- **Category:** style
- **What it checks:** Enforces the correct ordering of modifiers (annotations, visibility, expect/actual, inheritance, const, external, override, lateinit, tailrec, vararg, suspend, inner, enum, annotation, companion, inline, infix, operator, data).
- **Autocorrectable:** yes
- **Needs semantic info:** none
- **Node types used:** MODIFIER_LIST, ANNOTATION_ENTRY, plus all modifier keyword element types (PUBLIC_KEYWORD, PRIVATE_KEYWORD, PROTECTED_KEYWORD, INTERNAL_KEYWORD, EXPECT_KEYWORD, ACTUAL_KEYWORD, FINAL_KEYWORD, OPEN_KEYWORD, ABSTRACT_KEYWORD, SEALED_KEYWORD, CONST_KEYWORD, EXTERNAL_KEYWORD, OVERRIDE_KEYWORD, LATEINIT_KEYWORD, TAILREC_KEYWORD, VARARG_KEYWORD, SUSPEND_KEYWORD, INNER_KEYWORD, ENUM_KEYWORD, ANNOTATION_KEYWORD, COMPANION_KEYWORD, INLINE_KEYWORD, INFIX_KEYWORD, OPERATOR_KEYWORD, DATA_KEYWORD, CONTEXT_RECEIVER_LIST)
- **Tree traversal:** declaration-level
- **Complexity:** 2
- **Config options:** none
- **Has detekt equivalent:** ModifierOrder
- **Notes:** Sorts modifiers using the ORDERED_MODIFIERS array as the canonical order. Squashes annotations into a single placeholder in error messages for readability. Uses replaceChild with clone for autocorrection.

### multiline-expression-wrapping
- **Name:** MultilineExpressionWrappingRule
- **Source:** ktlint-ruleset-standard/src/main/kotlin/com/pinterest/ktlint/ruleset/standard/rules/MultilineExpressionWrappingRule.kt
- **Category:** wrapping
- **What it checks:** Wraps multiline expressions to start on a new line when used as assignments, lambda bodies, value arguments, or after arrows.
- **Autocorrectable:** yes
- **Needs semantic info:** none
- **Node types used:** ARRAY_ACCESS_EXPRESSION, ARROW, BINARY_EXPRESSION, BINARY_WITH_TYPE, BLOCK, CALL_EXPRESSION, COMMA, DOT_QUALIFIED_EXPRESSION, ELVIS, EQ, FUN, FUNCTION_LITERAL, IF, IS_EXPRESSION, LAMBDA_EXPRESSION, MUL, OBJECT_LITERAL, OPERATION_REFERENCE, POSTFIX_EXPRESSION, PREFIX_EXPRESSION, REFERENCE_EXPRESSION, REGULAR_STRING_PART, RPAR, SAFE_ACCESS_EXPRESSION, TRY, VALUE_ARGUMENT, VALUE_ARGUMENT_LIST, VALUE_PARAMETER_LIST, WHEN
- **Tree traversal:** expression-level
- **Complexity:** 2
- **Config options:** INDENT_SIZE_PROPERTY, INDENT_STYLE_PROPERTY, FUNCTION_BODY_EXPRESSION_WRAPPING_PROPERTY (from FunctionSignatureRule)
- **Has detekt equivalent:** none
- **Notes:** Restricted to ktlint_official code style (RuleV2.OfficialCodeStyle). Checks for chainable expressions including if/when/try. Handles comma preservation on same line after multiline expression. Respects FUNCTION_BODY_EXPRESSION_WRAPPING_PROPERTY from FunctionSignatureRule.

### multiline-if-else
- **Name:** MultiLineIfElseRule
- **Source:** ktlint-ruleset-standard/src/main/kotlin/com/pinterest/ktlint/ruleset/standard/rules/MultiLineIfElseRule.kt
- **Category:** style
- **What it checks:** Enforces that multiline if-else statements wrap their branches in braces (curly brackets).
- **Autocorrectable:** yes
- **Needs semantic info:** none
- **Node types used:** THEN, ELSE, ELSE_KEYWORD, BLOCK, LBRACE, RBRACE, RPAR, IF, BINARY_EXPRESSION, DOT_QUALIFIED_EXPRESSION
- **Tree traversal:** expression-level
- **Complexity:** 2
- **Config options:** INDENT_SIZE_PROPERTY, INDENT_STYLE_PROPERTY
- **Has detekt equivalent:** BracesOnIfStatements (similar)
- **Notes:** Allows single-line if statements without braces if the entire statement has no newlines. Allows "else if" without braces. Handles nested if-else-if chains. Creates KtBlockExpression for wrapping.

### multiline-loop
- **Name:** MultilineLoopRule
- **Source:** ktlint-ruleset-standard/src/main/kotlin/com/pinterest/ktlint/ruleset/standard/rules/MultilineLoopRule.kt
- **Category:** style
- **What it checks:** Enforces that multiline for/while/do-while loop bodies are wrapped in braces.
- **Autocorrectable:** yes
- **Needs semantic info:** none
- **Node types used:** BODY, BLOCK, LBRACE, RBRACE, RPAR, DO_KEYWORD
- **Tree traversal:** expression-level
- **Complexity:** 2
- **Config options:** INDENT_SIZE_PROPERTY, INDENT_STYLE_PROPERTY
- **Has detekt equivalent:** none
- **Notes:** Allows single-line loop statements without braces. Creates KtBlockExpression for wrapping. Pattern mirrors MultiLineIfElseRule autocorrect logic.

### no-blank-line-before-rbrace
- **Name:** NoBlankLineBeforeRbraceRule
- **Source:** ktlint-ruleset-standard/src/main/kotlin/com/pinterest/ktlint/ruleset/standard/rules/NoBlankLineBeforeRbraceRule.kt
- **Category:** whitespace
- **What it checks:** Disallows blank lines immediately before a closing brace.
- **Autocorrectable:** yes
- **Needs semantic info:** none
- **Node types used:** RBRACE
- **Tree traversal:** expression-level
- **Complexity:** 1
- **Config options:** none
- **Has detekt equivalent:** none
- **Notes:** Simple whitespace check -- splits on newlines and checks for more than 2 segments (indicating a blank line).

### no-blank-line-in-list
- **Name:** NoBlankLineInListRule
- **Source:** ktlint-ruleset-standard/src/main/kotlin/com/pinterest/ktlint/ruleset/standard/rules/NoBlankLineInListRule.kt
- **Category:** whitespace
- **What it checks:** Disallows blank lines inside value parameter lists, value argument lists, super type lists, type argument/parameter/constraint lists.
- **Autocorrectable:** yes
- **Needs semantic info:** none
- **Node types used:** CLASS_BODY, SUPER_TYPE_LIST, TYPE_ARGUMENT_LIST, TYPE_CONSTRAINT_LIST, TYPE_PARAMETER_LIST, VALUE_ARGUMENT_LIST, VALUE_PARAMETER_LIST
- **Tree traversal:** expression-level
- **Complexity:** 1
- **Config options:** none
- **Has detekt equivalent:** none
- **Notes:** Restricted to ktlint_official code style (RuleV2.OfficialCodeStyle). Handles whitespace both inside and adjacent to list nodes (since AST placement varies by list type). Replaces with single space for TYPE_CONSTRAINT_LIST and CLASS_BODY-adjacent whitespace.

### no-blank-lines-in-chained-method-calls
- **Name:** NoBlankLinesInChainedMethodCallsRule
- **Source:** ktlint-ruleset-standard/src/main/kotlin/com/pinterest/ktlint/ruleset/standard/rules/NoBlankLinesInChainedMethodCallsRule.kt
- **Category:** whitespace
- **What it checks:** Disallows blank lines within chained method calls (dot-qualified expressions).
- **Autocorrectable:** yes
- **Needs semantic info:** none
- **Node types used:** DOT_QUALIFIED_EXPRESSION
- **Tree traversal:** expression-level
- **Complexity:** 1
- **Config options:** none
- **Has detekt equivalent:** none
- **Notes:** Very simple rule -- checks for "\n\n" in whitespace nodes whose parent is DOT_QUALIFIED_EXPRESSION.

### no-consecutive-blank-lines
- **Name:** NoConsecutiveBlankLinesRule
- **Source:** ktlint-ruleset-standard/src/main/kotlin/com/pinterest/ktlint/ruleset/standard/rules/NoConsecutiveBlankLinesRule.kt
- **Category:** whitespace
- **What it checks:** Disallows more than one consecutive blank line, and removes all blank lines between class name and primary constructor.
- **Autocorrectable:** yes
- **Needs semantic info:** none
- **Node types used:** CLASS, IDENTIFIER, PRIMARY_CONSTRUCTOR
- **Tree traversal:** file-level
- **Complexity:** 1
- **Config options:** none
- **Has detekt equivalent:** none
- **Notes:** Special handling for end-of-file (reduces to single newline) and between class identifier and primary constructor (reduces to no blank line). One of the simplest rules.

### no-consecutive-comments
- **Name:** NoConsecutiveCommentsRule
- **Source:** ktlint-ruleset-standard/src/main/kotlin/com/pinterest/ktlint/ruleset/standard/rules/NoConsecutiveCommentsRule.kt
- **Category:** comments
- **What it checks:** Disallows consecutive KDoc/block comments (even with blank lines between), and mixed comment types without a blank line separator.
- **Autocorrectable:** no
- **Needs semantic info:** none
- **Node types used:** EOL_COMMENT, BLOCK_COMMENT, KDOC_START, KDOC_END
- **Tree traversal:** expression-level
- **Complexity:** 2
- **Config options:** none
- **Has detekt equivalent:** none
- **Notes:** Restricted to ktlint_official code style (RuleV2.OfficialCodeStyle). Allows consecutive EOL comments. Allows different comment types separated by blank lines (except KDoc followed by anything). Reports with descriptive messages about which comment types conflict.


### no-empty-class-body
- **Name:** NoEmptyClassBodyRule
- **Source:** ktlint-ruleset-standard/src/main/kotlin/com/pinterest/ktlint/ruleset/standard/rules/NoEmptyClassBodyRule.kt
- **Category:** style
- **What it checks:** Removes unnecessary empty class bodies (`{}`), except on object literals and companion objects.
- **Autocorrectable:** yes
- **Needs semantic info:** none
- **Node types used:** CLASS_BODY, LBRACE, RBRACE, OBJECT_LITERAL
- **Tree traversal:** declaration-level
- **Complexity:** 2
- **Config options:** none
- **Has detekt equivalent:** none
- **Notes:** Checks children to exclude `companion` keyword; skips OBJECT_LITERAL nodes.

### no-empty-file
- **Name:** NoEmptyFileRule
- **Source:** ktlint-ruleset-standard/src/main/kotlin/com/pinterest/ktlint/ruleset/standard/rules/NoEmptyFileRule.kt
- **Category:** style
- **What it checks:** Reports files that contain no code (only package directive, import list, or blank script).
- **Autocorrectable:** no
- **Needs semantic info:** none
- **Node types used:** PACKAGE_DIRECTIVE, IMPORT_LIST, SCRIPT
- **Tree traversal:** file-level
- **Complexity:** 1
- **Config options:** none
- **Has detekt equivalent:** none
- **Notes:** Uses `psi.containingFile.virtualFile.name` to report the filename; cannot autocorrect since it would delete the file.

### no-empty-first-line-in-class-body
- **Name:** NoEmptyFirstLineInClassBodyRule
- **Source:** ktlint-ruleset-standard/src/main/kotlin/com/pinterest/ktlint/ruleset/standard/rules/NoEmptyFirstLineInClassBodyRule.kt
- **Category:** whitespace
- **What it checks:** Disallows blank lines at the start of a class body (after the opening brace).
- **Autocorrectable:** yes
- **Needs semantic info:** none
- **Node types used:** CLASS_BODY
- **Tree traversal:** declaration-level
- **Complexity:** 2
- **Config options:** INDENT_SIZE_PROPERTY, INDENT_STYLE_PROPERTY
- **Has detekt equivalent:** none
- **Notes:** OfficialCodeStyle marker; uses IndentConfig to compute correct replacement whitespace.

### no-empty-first-line-in-method-block
- **Name:** NoEmptyFirstLineInMethodBlockRule
- **Source:** ktlint-ruleset-standard/src/main/kotlin/com/pinterest/ktlint/ruleset/standard/rules/NoEmptyFirstLineInMethodBlockRule.kt
- **Category:** whitespace
- **What it checks:** Disallows blank lines at the start of a function body block (after the opening brace).
- **Autocorrectable:** yes
- **Needs semantic info:** none
- **Node types used:** LBRACE, FUN, CLASS_BODY
- **Tree traversal:** declaration-level
- **Complexity:** 2
- **Config options:** none
- **Has detekt equivalent:** none
- **Notes:** Allows empty first line when function body is an object expression inside a class body.

### no-line-break-after-else
- **Name:** NoLineBreakAfterElseRule
- **Source:** ktlint-ruleset-standard/src/main/kotlin/com/pinterest/ktlint/ruleset/standard/rules/NoLineBreakAfterElseRule.kt
- **Category:** wrapping
- **What it checks:** Disallows line breaks between `else` keyword and the following `if` keyword or opening brace.
- **Autocorrectable:** yes
- **Needs semantic info:** none
- **Node types used:** ELSE_KEYWORD, IF_KEYWORD, LBRACE
- **Tree traversal:** expression-level
- **Complexity:** 1
- **Config options:** none
- **Has detekt equivalent:** none
- **Notes:** ---

### no-line-break-before-assignment
- **Name:** NoLineBreakBeforeAssignmentRule
- **Source:** ktlint-ruleset-standard/src/main/kotlin/com/pinterest/ktlint/ruleset/standard/rules/NoLineBreakBeforeAssignmentRule.kt
- **Category:** wrapping
- **What it checks:** Disallows line breaks before the `=` assignment operator, moving the break to after it instead.
- **Autocorrectable:** yes
- **Needs semantic info:** none
- **Node types used:** EQ
- **Tree traversal:** declaration-level
- **Complexity:** 2
- **Config options:** none
- **Has detekt equivalent:** none
- **Notes:** Moves the `=` sign and surrounding whitespace to the end of the previous line; uses siblings() for navigation.

### no-multi-spaces
- **Name:** NoMultipleSpacesRule
- **Source:** ktlint-ruleset-standard/src/main/kotlin/com/pinterest/ktlint/ruleset/standard/rules/NoMultipleSpacesRule.kt
- **Category:** spacing
- **What it checks:** Disallows multiple consecutive spaces (except indentation and KDoc tag alignment).
- **Autocorrectable:** yes
- **Needs semantic info:** none
- **Node types used:** KDOC_MARKDOWN_LINK, KDOC_TAG
- **Tree traversal:** expression-level
- **Complexity:** 1
- **Config options:** none
- **Has detekt equivalent:** none
- **Notes:** Exempts multiple spaces when used for KDoc `@param` alignment (KDOC_MARKDOWN_LINK followed by KDOC_TAG).

### no-semi
- **Name:** NoSemicolonsRule
- **Source:** ktlint-ruleset-standard/src/main/kotlin/com/pinterest/ktlint/ruleset/standard/rules/NoSemicolonsRule.kt
- **Category:** style
- **What it checks:** Removes unnecessary semicolons, while preserving required ones (enum entries, empty loops, `object` keyword).
- **Autocorrectable:** yes
- **Needs semantic info:** none
- **Node types used:** SEMICOLON, ANNOTATION_ENTRY, BODY, CLASS, CLASS_BODY, ENUM_ENTRY, ENUM_KEYWORD, FOR, IF, KDOC, OBJECT_KEYWORD, THEN, WHILE
- **Tree traversal:** expression-level
- **Complexity:** 3
- **Config options:** none
- **Has detekt equivalent:** none
- **Notes:** Complex logic with many edge cases: loops without bodies, if-without-then, enum classes without values, last enum entry before class body closing.

### no-single-line-block-comment
- **Name:** NoSingleLineBlockCommentRule
- **Source:** ktlint-ruleset-standard/src/main/kotlin/com/pinterest/ktlint/ruleset/standard/rules/NoSingleLineBlockCommentRule.kt
- **Category:** comments
- **What it checks:** Replaces single-line block comments (`/* ... */`) with EOL comments (`// ...`) when on their own line.
- **Autocorrectable:** yes
- **Needs semantic info:** none
- **Node types used:** BLOCK_COMMENT, EOL_COMMENT
- **Tree traversal:** expression-level
- **Complexity:** 2
- **Config options:** INDENT_SIZE_PROPERTY, INDENT_STYLE_PROPERTY
- **Has detekt equivalent:** none
- **Notes:** OfficialCodeStyle marker; uses rawInsertBeforeMe/rawRemove for replacement.

### no-trailing-spaces
- **Name:** NoTrailingSpacesRule
- **Source:** ktlint-ruleset-standard/src/main/kotlin/com/pinterest/ktlint/ruleset/standard/rules/NoTrailingSpacesRule.kt
- **Category:** whitespace
- **What it checks:** Removes trailing spaces from all lines, including inside KDoc text and EOL comments.
- **Autocorrectable:** yes
- **Needs semantic info:** none
- **Node types used:** EOL_COMMENT, KDOC, KDOC_END, KDOC_LEADING_ASTERISK, KDOC_TEXT
- **Tree traversal:** file-level
- **Complexity:** 2
- **Config options:** none
- **Has detekt equivalent:** none
- **Notes:** Special handling for KDoc (both whitespace nodes and KDOC_TEXT nodes); preserves indentation on the last line of non-EOL whitespace.

### no-unit-return
- **Name:** NoUnitReturnRule
- **Source:** ktlint-ruleset-standard/src/main/kotlin/com/pinterest/ktlint/ruleset/standard/rules/NoUnitReturnRule.kt
- **Category:** style
- **What it checks:** Removes explicit `: Unit` return type from functions that have a block body.
- **Autocorrectable:** yes
- **Needs semantic info:** none
- **Node types used:** TYPE_REFERENCE, FUN, LBRACE, COLON
- **Tree traversal:** declaration-level
- **Complexity:** 2
- **Config options:** none
- **Has detekt equivalent:** none
- **Notes:** Only triggers when the function has a block body (next code sibling's first child is LBRACE); removes colon and surrounding whitespace.

### no-unused-imports
- **Name:** NoUnusedImportsRule
- **Source:** ktlint-ruleset-standard/src/main/kotlin/com/pinterest/ktlint/ruleset/standard/rules/NoUnusedImportsRule.kt
- **Category:** imports
- **What it checks:** Detects and removes unused import statements, accounting for operator overloads, componentN, provideDelegate, and KDoc references.
- **Autocorrectable:** yes
- **Needs semantic info:** none
- **Node types used:** BY_KEYWORD, CALL_EXPRESSION, DOT_QUALIFIED_EXPRESSION, FILE, IDENTIFIER, IMPORT_DIRECTIVE, OPERATION_REFERENCE, PACKAGE_DIRECTIVE, REFERENCE_EXPRESSION
- **Tree traversal:** file-level
- **Complexity:** 3
- **Config options:** none
- **Has detekt equivalent:** UnusedImports
- **Notes:** Opt-in rule (OnlyWhenEnabledInEditorconfig) because of false positives; uses IgnoreKtlintSuppressions; tracks references and imports across the entire file with afterVisitChildNodes.

### no-wildcard-imports
- **Name:** NoWildcardImportsRule
- **Source:** ktlint-ruleset-standard/src/main/kotlin/com/pinterest/ktlint/ruleset/standard/rules/NoWildcardImportsRule.kt
- **Category:** imports
- **What it checks:** Disallows wildcard imports (`*`) unless they match the configured allowed-packages list.
- **Autocorrectable:** no
- **Needs semantic info:** none
- **Node types used:** IMPORT_DIRECTIVE
- **Tree traversal:** file-level
- **Complexity:** 1
- **Config options:** `ij_kotlin_packages_to_use_import_on_demand` (IJ_KOTLIN_PACKAGES_TO_USE_IMPORT_ON_DEMAND)
- **Has detekt equivalent:** WildcardImport
- **Notes:** Default allows `java.util.*` and `kotlinx.android.synthetic.**`; ktlint_official code style defaults to empty list. Cannot autocorrect because individual imports are unknown.

### nullable-type-spacing
- **Name:** NullableTypeSpacingRule
- **Source:** ktlint-ruleset-standard/src/main/kotlin/com/pinterest/ktlint/ruleset/standard/rules/NullableTypeSpacingRule.kt
- **Category:** spacing
- **What it checks:** Removes whitespace before the `?` in nullable types (e.g., `String ?` becomes `String?`).
- **Autocorrectable:** yes
- **Needs semantic info:** none
- **Node types used:** QUEST
- **Tree traversal:** expression-level
- **Complexity:** 1
- **Config options:** none
- **Has detekt equivalent:** none
- **Notes:** ---

### package-import-spacing
- **Name:** PackageImportSpacingRule
- **Source:** ktlint-ruleset-standard/src/main/kotlin/com/pinterest/ktlint/ruleset/standard/rules/PackageImportSpacingRule.kt
- **Category:** whitespace
- **What it checks:** Enforces exactly one blank line between the package statement and import statements.
- **Autocorrectable:** yes
- **Needs semantic info:** none
- **Node types used:** PACKAGE_DIRECTIVE, IMPORT_LIST
- **Tree traversal:** file-level
- **Complexity:** 1
- **Config options:** none
- **Has detekt equivalent:** none
- **Notes:** Experimental rule (RuleV2.Experimental); uses siblings() to find whitespace between package and imports.

### package-name
- **Name:** PackageNameRule
- **Source:** ktlint-ruleset-standard/src/main/kotlin/com/pinterest/ktlint/ruleset/standard/rules/PackageNameRule.kt
- **Category:** naming
- **What it checks:** Validates that package names do not contain underscores and match `[a-z][a-zA-Z\d]*(\.[a-z][a-zA-Z\d]*)*` (with diacritics).
- **Autocorrectable:** no
- **Needs semantic info:** none
- **Node types used:** PACKAGE_DIRECTIVE, DOT_QUALIFIED_EXPRESSION, REFERENCE_EXPRESSION
- **Tree traversal:** file-level
- **Complexity:** 1
- **Config options:** none
- **Has detekt equivalent:** PackageNaming
- **Notes:** Uses `regExIgnoringDiacriticsAndStrokesOnLetters()` to support non-ASCII letters.

### parameter-list-spacing
- **Name:** ParameterListSpacingRule
- **Source:** ktlint-ruleset-standard/src/main/kotlin/com/pinterest/ktlint/ruleset/standard/rules/ParameterListSpacingRule.kt
- **Category:** spacing
- **What it checks:** Ensures consistent spacing inside parameter lists: no spaces before/after parens, single space after commas, no space before colons, single space after colons.
- **Autocorrectable:** yes
- **Needs semantic info:** none
- **Node types used:** VALUE_PARAMETER_LIST, VALUE_PARAMETER, WHITE_SPACE, COMMA, COLON, RPAR, MODIFIER_LIST, ANNOTATION_ENTRY, TYPE_REFERENCE
- **Tree traversal:** declaration-level
- **Complexity:** 3
- **Config options:** MAX_LINE_LENGTH_PROPERTY
- **Has detekt equivalent:** none
- **Notes:** Complex rule iterating over parameter list children; allows type wrapping when type does not fit on same line as colon.

### parameter-list-wrapping
- **Name:** ParameterListWrappingRule
- **Source:** ktlint-ruleset-standard/src/main/kotlin/com/pinterest/ktlint/ruleset/standard/rules/ParameterListWrappingRule.kt
- **Category:** wrapping
- **What it checks:** Wraps parameter lists to one-parameter-per-line when the list is multiline or exceeds max line length.
- **Autocorrectable:** yes
- **Needs semantic info:** none
- **Node types used:** NULLABLE_TYPE, VALUE_PARAMETER_LIST, LPAR, RPAR, VALUE_PARAMETER, FUNCTION_LITERAL, FUNCTION_TYPE, CALL_EXPRESSION, VALUE_ARGUMENT_LIST, FUN, TYPE_PARAMETER_LIST, ANNOTATION_ENTRY, MODIFIER_LIST
- **Tree traversal:** declaration-level
- **Complexity:** 3
- **Config options:** CODE_STYLE_PROPERTY, INDENT_SIZE_PROPERTY, INDENT_STYLE_PROPERTY, MAX_LINE_LENGTH_PROPERTY
- **Has detekt equivalent:** none
- **Notes:** Contains ktlint_official-specific logic for annotated parameters and function literals; stops AST traversal when indent is disabled.

### parameter-wrapping
- **Name:** ParameterWrappingRule
- **Source:** ktlint-ruleset-standard/src/main/kotlin/com/pinterest/ktlint/ruleset/standard/rules/ParameterWrappingRule.kt
- **Category:** wrapping
- **What it checks:** Inserts missing newlines inside a value parameter when the parameter declaration exceeds max line length.
- **Autocorrectable:** yes
- **Needs semantic info:** none
- **Node types used:** VALUE_PARAMETER, COLON, TYPE_REFERENCE, EQ, CALL_EXPRESSION, IDENTIFIER, COMMA
- **Tree traversal:** declaration-level
- **Complexity:** 2
- **Config options:** INDENT_SIZE_PROPERTY, INDENT_STYLE_PROPERTY, MAX_LINE_LENGTH_PROPERTY
- **Has detekt equivalent:** none
- **Notes:** Similar to PropertyWrappingRule; wraps at colon, type reference, equals, or call expression when exceeding max line length.

### property-naming
- **Name:** PropertyNamingRule
- **Source:** ktlint-ruleset-standard/src/main/kotlin/com/pinterest/ktlint/ruleset/standard/rules/PropertyNamingRule.kt
- **Category:** naming
- **What it checks:** Validates property naming: const properties must use screaming_snake_case (or pascal_case via config); non-const properties must use lowerCamelCase.
- **Autocorrectable:** no
- **Needs semantic info:** none
- **Node types used:** PROPERTY, IDENTIFIER, CONST_KEYWORD, FILE, GET_KEYWORD, OBJECT_DECLARATION, CLASS_BODY, PROPERTY_ACCESSOR, VAL_KEYWORD, OVERRIDE_KEYWORD
- **Tree traversal:** declaration-level
- **Complexity:** 2
- **Config options:** `ktlint_property_naming_constant_naming` (CONSTANT_NAMING_PROPERTY)
- **Has detekt equivalent:** PropertyNaming
- **Notes:** Skips top-level vals, object vals (without override), and properties with custom getters since immutability cannot be determined; ignores backtick-escaped keywords; exempts `serialVersionUID`.

### property-wrapping
- **Name:** PropertyWrappingRule
- **Source:** ktlint-ruleset-standard/src/main/kotlin/com/pinterest/ktlint/ruleset/standard/rules/PropertyWrappingRule.kt
- **Category:** wrapping
- **What it checks:** Inserts missing newlines inside a property declaration when it exceeds max line length.
- **Autocorrectable:** yes
- **Needs semantic info:** none
- **Node types used:** PROPERTY, COLON, TYPE_REFERENCE, EQ, CALL_EXPRESSION, IDENTIFIER
- **Tree traversal:** declaration-level
- **Complexity:** 2
- **Config options:** INDENT_SIZE_PROPERTY, INDENT_STYLE_PROPERTY, MAX_LINE_LENGTH_PROPERTY
- **Has detekt equivalent:** none
- **Notes:** Mirrors ParameterWrappingRule logic but for property declarations.

### spacing-around-angle-brackets
- **Name:** SpacingAroundAngleBracketsRule
- **Source:** ktlint-ruleset-standard/src/main/kotlin/com/pinterest/ktlint/ruleset/standard/rules/SpacingAroundAngleBracketsRule.kt
- **Category:** spacing
- **What it checks:** Removes unexpected whitespace inside and around angle brackets of type parameter/argument lists, except after `val`, `var`, and `fun` keywords.
- **Autocorrectable:** yes
- **Needs semantic info:** none
- **Node types used:** TYPE_PARAMETER_LIST, TYPE_ARGUMENT_LIST, VAL_KEYWORD, VAR_KEYWORD, FUN_KEYWORD
- **Tree traversal:** expression-level
- **Complexity:** 2
- **Config options:** none
- **Has detekt equivalent:** none
- **Notes:** Handles newlines differently from spaces: trims blank lines before last newline rather than removing whitespace entirely.

### colon-spacing
- **Name:** SpacingAroundColonRule
- **Source:** ktlint-ruleset-standard/src/main/kotlin/com/pinterest/ktlint/ruleset/standard/rules/SpacingAroundColonRule.kt
- **Category:** spacing
- **What it checks:** Enforces correct spacing around colons: space before for class/constructor/type-constraint declarations; no space before for parameter/return types; space after for annotations.
- **Autocorrectable:** yes
- **Needs semantic info:** none
- **Node types used:** COLON, ANNOTATION, ANNOTATION_ENTRY, BLOCK, CLASS, EQ, FUN, OBJECT_DECLARATION, PROPERTY, SECONDARY_CONSTRUCTOR, TYPE_CONSTRAINT, TYPE_PARAMETER_LIST
- **Tree traversal:** declaration-level
- **Complexity:** 3
- **Config options:** none
- **Has detekt equivalent:** SpacingAroundColon (detekt)
- **Notes:** Complex newline-before-colon handling: moves code between colon and next significant node (EQ or BLOCK) when rearranging.

### comma-spacing
- **Name:** SpacingAroundCommaRule
- **Source:** ktlint-ruleset-standard/src/main/kotlin/com/pinterest/ktlint/ruleset/standard/rules/SpacingAroundCommaRule.kt
- **Category:** spacing
- **What it checks:** Ensures no spacing before commas and mandatory spacing after commas (unless followed by `)`, `]`, or `>`).
- **Autocorrectable:** yes
- **Needs semantic info:** none
- **Node types used:** RPAR, RBRACKET, GT
- **Tree traversal:** expression-level
- **Complexity:** 2
- **Config options:** none
- **Has detekt equivalent:** SpacingAroundComma (detekt, partial)
- **Notes:** Special handling when comma is on a new line preceded by a comment: moves comma before the comment.

### curly-spacing
- **Name:** SpacingAroundCurlyRule
- **Source:** ktlint-ruleset-standard/src/main/kotlin/com/pinterest/ktlint/ruleset/standard/rules/SpacingAroundCurlyRule.kt
- **Category:** spacing
- **What it checks:** Enforces spacing around curly braces: space before `{` (except after `@` or `(`), space after `{`, and no space after `}` before `)`, `.`, `,`, `::`, etc.
- **Autocorrectable:** yes
- **Needs semantic info:** none
- **Node types used:** AT, BLOCK, CLASS_BODY, COLONCOLON, COMMA, DOT, EXCLEXCL, LAMBDA_EXPRESSION, LBRACE, LBRACKET, LPAR, RANGE, RANGE_UNTIL, RBRACE, RBRACKET, RPAR, SAFE_ACCESS, SEMICOLON
- **Tree traversal:** expression-level
- **Complexity:** 3
- **Config options:** CODE_STYLE_PROPERTY, INDENT_SIZE_PROPERTY, INDENT_STYLE_PROPERTY
- **Has detekt equivalent:** SpacingAroundCurly (detekt, partial)
- **Notes:** Handles unexpected newlines before LBRACE in CLASS_BODY/BLOCK; moves EOL comments after curly when rearranging.

### dot-spacing
- **Name:** SpacingAroundDotRule
- **Source:** ktlint-ruleset-standard/src/main/kotlin/com/pinterest/ktlint/ruleset/standard/rules/SpacingAroundDotRule.kt
- **Category:** spacing
- **What it checks:** Removes horizontal whitespace before dots and any whitespace after dots.
- **Autocorrectable:** yes
- **Needs semantic info:** none
- **Node types used:** (none from ElementType -- checks LeafPsiElement text matching ".")
- **Tree traversal:** expression-level
- **Complexity:** 1
- **Config options:** none
- **Has detekt equivalent:** none
- **Notes:** Allows newline before dot (only removes same-line whitespace before dot).

### double-colon-spacing
- **Name:** SpacingAroundDoubleColonRule
- **Source:** ktlint-ruleset-standard/src/main/kotlin/com/pinterest/ktlint/ruleset/standard/rules/SpacingAroundDoubleColonRule.kt
- **Category:** spacing
- **What it checks:** Removes unexpected whitespace around `::` in class literal and callable reference expressions.
- **Autocorrectable:** yes
- **Needs semantic info:** none
- **Node types used:** COLONCOLON, CALLABLE_REFERENCE_EXPRESSION, CLASS_LITERAL_EXPRESSION
- **Tree traversal:** expression-level
- **Complexity:** 2
- **Config options:** none
- **Has detekt equivalent:** none
- **Notes:** Special handling for `::isOdd` (no previous sibling) where multiple leading spaces are trimmed but a single space is shrunk rather than removed.

### keyword-spacing
- **Name:** SpacingAroundKeywordRule
- **Source:** ktlint-ruleset-standard/src/main/kotlin/com/pinterest/ktlint/ruleset/standard/rules/SpacingAroundKeywordRule.kt
- **Category:** spacing
- **What it checks:** Ensures a space after control-flow keywords (`if`, `for`, `when`, `while`, `do`, `try`, `catch`, `finally`, `else`) and no space after `get`/`set` when followed by a parameter list; disallows newlines before `else`/`catch`/`finally`.
- **Autocorrectable:** yes
- **Needs semantic info:** none
- **Node types used:** BLOCK, CATCH_KEYWORD, DO_KEYWORD, ELSE_KEYWORD, FINALLY_KEYWORD, FOR_KEYWORD, GET_KEYWORD, IF_KEYWORD, KDOC_NAME, PROPERTY_ACCESSOR, RBRACE, SET_KEYWORD, TRY_KEYWORD, VALUE_PARAMETER_LIST, WHEN_ENTRY, WHEN_KEYWORD, WHILE_KEYWORD
- **Tree traversal:** expression-level
- **Complexity:** 2
- **Config options:** none
- **Has detekt equivalent:** none
- **Notes:** Skips KDOC_NAME context; replaces newlines before `else`/`catch`/`finally` only when preceded by RBRACE in a BLOCK.

### op-spacing
- **Name:** SpacingAroundOperatorsRule
- **Source:** ktlint-ruleset-standard/src/main/kotlin/com/pinterest/ktlint/ruleset/standard/rules/SpacingAroundOperatorsRule.kt
- **Category:** spacing
- **What it checks:** Ensures spaces around binary operators (`+`, `-`, `*`, `/`, `%`, `=`, `==`, `!=`, `<`, `>`, `<=`, `>=`, `&&`, `||`, `->`, `?:`, and augmented assignments), excluding unary, spread, and import-star operators.
- **Autocorrectable:** yes
- **Needs semantic info:** none
- **Node types used:** ANDAND, ARROW, DIV, DIVEQ, ELVIS, EQ, EQEQ, EQEQEQ, EXCLEQ, EXCLEQEQEQ, GT, GTEQ, IDENTIFIER, IMPORT_DIRECTIVE, LT, LTEQ, MINUS, MINUSEQ, MUL, MULTEQ, OPERATION_REFERENCE, OROR, PERC, PERCEQ, PLUS, PLUSEQ, PREFIX_EXPRESSION, VALUE_ARGUMENT
- **Tree traversal:** expression-level
- **Complexity:** 2
- **Config options:** none
- **Has detekt equivalent:** SpacingAroundOperator (detekt, partial)
- **Notes:** Skips unary prefix expressions, spread operators (`*array`), and import wildcards.

### paren-spacing
- **Name:** SpacingAroundParensRule
- **Source:** ktlint-ruleset-standard/src/main/kotlin/com/pinterest/ktlint/ruleset/standard/rules/SpacingAroundParensRule.kt
- **Category:** spacing
- **What it checks:** Removes unexpected whitespace around parentheses: no space between function name and `(`, no space after `(`, no space before `)`, and no newlines that would leave empty parens on separate lines.
- **Autocorrectable:** yes
- **Needs semantic info:** none
- **Node types used:** LPAR, RPAR, VALUE_PARAMETER_LIST, VALUE_ARGUMENT_LIST, FUNCTION_TYPE, IDENTIFIER, PRIMARY_CONSTRUCTOR, SUPER_KEYWORD, SUPER_TYPE_CALL_ENTRY, CONSTRUCTOR_CALLEE, BLOCK_COMMENT, EOL_COMMENT, KDOC_START
- **Tree traversal:** expression-level
- **Complexity:** 3
- **Config options:** none
- **Has detekt equivalent:** none
- **Notes:** Many edge cases: constructors, super calls, super type call entries, function types; uses siblings() to scan for orphaned newlines.

### range-spacing
- **Name:** SpacingAroundRangeOperatorRule
- **Source:** ktlint-ruleset-standard/src/main/kotlin/com/pinterest/ktlint/ruleset/standard/rules/SpacingAroundRangeOperatorRule.kt
- **Category:** spacing
- **What it checks:** Removes whitespace around range operators (`..` and `..<`).
- **Autocorrectable:** yes
- **Needs semantic info:** none
- **Node types used:** RANGE, RANGE_UNTIL
- **Tree traversal:** expression-level
- **Complexity:** 1
- **Config options:** none
- **Has detekt equivalent:** none
- **Notes:** Uses KtSingleValueToken to get the operator text for error messages.

### square-brackets-spacing
- **Name:** SpacingAroundSquareBracketsRule
- **Source:** ktlint-ruleset-standard/src/main/kotlin/com/pinterest/ktlint/ruleset/standard/rules/SpacingAroundSquareBracketsRule.kt
- **Category:** spacing
- **What it checks:** Removes unexpected horizontal whitespace before `]` and after `[`, with allowances for COLLECTION_LITERAL_EXPRESSION and KDOC_MARKDOWN_LINK.
- **Autocorrectable:** yes
- **Needs semantic info:** none
- **Node types used:** LBRACKET, RBRACKET, COLLECTION_LITERAL_EXPRESSION, KDOC_MARKDOWN_LINK
- **Tree traversal:** expression-level
- **Complexity:** 2
- **Config options:** none
- **Has detekt equivalent:** none
- **Notes:** Allows newlines inside brackets (only removes same-line whitespace); KDoc markdown links are exempt from before-bracket checks.

### unary-op-spacing
- **Name:** SpacingAroundUnaryOperatorRule
- **Source:** ktlint-ruleset-standard/src/main/kotlin/com/pinterest/ktlint/ruleset/standard/rules/SpacingAroundUnaryOperatorRule.kt
- **Category:** spacing
- **What it checks:** Removes whitespace between unary operators and their operands in prefix and postfix expressions.
- **Autocorrectable:** yes
- **Needs semantic info:** none
- **Node types used:** PREFIX_EXPRESSION, POSTFIX_EXPRESSION
- **Tree traversal:** expression-level
- **Complexity:** 1
- **Config options:** none
- **Has detekt equivalent:** none
- **Notes:** Skips expressions that contain comments between operator and operand.

### spacing-between-declarations-with-annotations
- **Name:** SpacingBetweenDeclarationsWithAnnotationsRule
- **Source:** ktlint-ruleset-standard/src/main/kotlin/com/pinterest/ktlint/ruleset/standard/rules/SpacingBetweenDeclarationsWithAnnotationsRule.kt
- **Category:** whitespace
- **What it checks:** Requires a blank line between a declaration with annotations and the preceding declaration.
- **Autocorrectable:** yes
- **Needs semantic info:** none
- **Node types used:** ANNOTATION_ENTRY, MODIFIER_LIST, PROPERTY_ACCESSOR
- **Tree traversal:** declaration-level
- **Complexity:** 2
- **Config options:** none
- **Has detekt equivalent:** none
- **Notes:** Also applies to property accessors; uses leaves(false) to scan backwards for blank lines.

### spacing-between-declarations-with-comments
- **Name:** SpacingBetweenDeclarationsWithCommentsRule
- **Source:** ktlint-ruleset-standard/src/main/kotlin/com/pinterest/ktlint/ruleset/standard/rules/SpacingBetweenDeclarationsWithCommentsRule.kt
- **Category:** whitespace
- **What it checks:** Requires a blank line between a commented declaration and the preceding declaration.
- **Autocorrectable:** yes
- **Needs semantic info:** none
- **Node types used:** TokenSets.COMMENTS
- **Tree traversal:** declaration-level
- **Complexity:** 2
- **Config options:** none
- **Has detekt equivalent:** none
- **Notes:** Only triggers for leading comments (not tail comments where startOffset > parent startOffset).

### spacing-between-function-name-and-opening-parenthesis
- **Name:** SpacingBetweenFunctionNameAndOpeningParenthesisRule
- **Source:** ktlint-ruleset-standard/src/main/kotlin/com/pinterest/ktlint/ruleset/standard/rules/SpacingBetweenFunctionNameAndOpeningParenthesisRule.kt
- **Category:** spacing
- **What it checks:** Removes whitespace between a function name and the opening parenthesis of its parameter list.
- **Autocorrectable:** yes
- **Needs semantic info:** none
- **Node types used:** FUN, IDENTIFIER
- **Tree traversal:** declaration-level
- **Complexity:** 1
- **Config options:** none
- **Has detekt equivalent:** none
- **Notes:** ---

### statement-wrapping
- **Name:** StatementWrappingRule
- **Source:** ktlint-ruleset-standard/src/main/kotlin/com/pinterest/ktlint/ruleset/standard/rules/StatementWrappingRule.kt
- **Category:** wrapping
- **What it checks:** Enforces newlines after `{` and before `}` in blocks, class bodies, and `when` expressions; also enforces newlines after semicolons.
- **Autocorrectable:** yes
- **Needs semantic info:** none
- **Node types used:** BLOCK, CLASS_BODY, WHEN, SEMICOLON, LBRACE, RBRACE, FUNCTION_LITERAL, VALUE_PARAMETER_LIST, ARROW, ENUM_ENTRY, ENUM_KEYWORD, CLASS, MODIFIER_LIST, ANNOTATION_ENTRY
- **Tree traversal:** declaration-level
- **Complexity:** 3
- **Config options:** INDENT_SIZE_PROPERTY, INDENT_STYLE_PROPERTY
- **Has detekt equivalent:** none
- **Notes:** Allows single-line function literals, empty blocks, and single-line enum classes; uses noNewLineInClosedRange for detection.

### string-template-indent
- **Name:** StringTemplateIndentRule
- **Source:** ktlint-ruleset-standard/src/main/kotlin/com/pinterest/ktlint/ruleset/standard/rules/StringTemplateIndentRule.kt
- **Category:** strings
- **What it checks:** Enforces consistent indentation of multiline raw string literals followed by `.trimIndent()`, including newline placement around the template.
- **Autocorrectable:** yes
- **Needs semantic info:** none
- **Node types used:** STRING_TEMPLATE, OPEN_QUOTE, CLOSING_QUOTE, LITERAL_STRING_TEMPLATE_ENTRY, REGULAR_STRING_PART, BINARY_EXPRESSION, CALL_EXPRESSION, COMMA, DOT, DOT_QUALIFIED_EXPRESSION, EQ, FUN, OPERATION_REFERENCE, RETURN_KEYWORD, RPAR
- **Tree traversal:** expression-level
- **Complexity:** 3
- **Config options:** INDENT_SIZE_PROPERTY, INDENT_STYLE_PROPERTY
- **Has detekt equivalent:** none
- **Notes:** OfficialCodeStyle marker; detects mixed tab/space indentation (emits non-autocorrectable); handles newline insertion before/after template and re-indents all lines.

### string-template
- **Name:** StringTemplateRule
- **Source:** ktlint-ruleset-standard/src/main/kotlin/com/pinterest/ktlint/ruleset/standard/rules/StringTemplateRule.kt
- **Category:** strings
- **What it checks:** Removes redundant `.toString()` calls inside string templates and redundant curly braces around simple identifiers.
- **Autocorrectable:** yes
- **Needs semantic info:** none
- **Node types used:** LONG_STRING_TEMPLATE_ENTRY, LONG_TEMPLATE_ENTRY_START, LONG_TEMPLATE_ENTRY_END, DOT_QUALIFIED_EXPRESSION, CALL_EXPRESSION, CLOSING_QUOTE, LITERAL_STRING_TEMPLATE_ENTRY, REFERENCE_EXPRESSION, SHORT_STRING_TEMPLATE_ENTRY, STRING_TEMPLATE, SUPER_EXPRESSION, THIS_EXPRESSION, PROPERTY
- **Tree traversal:** expression-level
- **Complexity:** 2
- **Config options:** none
- **Has detekt equivalent:** none
- **Notes:** Uses KtlintKotlinCompiler.createASTNodeFromText to create replacement SHORT_STRING_TEMPLATE_ENTRY nodes.

### then-spacing
- **Name:** ThenSpacingRule
- **Source:** ktlint-ruleset-standard/src/main/kotlin/com/pinterest/ktlint/ruleset/standard/rules/ThenSpacingRule.kt
- **Category:** spacing
- **What it checks:** Ensures whitespace exists before and after the `then` block in an if-statement.
- **Autocorrectable:** yes
- **Needs semantic info:** none
- **Node types used:** THEN
- **Tree traversal:** expression-level
- **Complexity:** 1
- **Config options:** none
- **Has detekt equivalent:** none
- **Notes:** ---

### trailing-comma-on-call-site
- **Name:** TrailingCommaOnCallSiteRule
- **Source:** ktlint-ruleset-standard/src/main/kotlin/com/pinterest/ktlint/ruleset/standard/rules/TrailingCommaOnCallSiteRule.kt
- **Category:** style
- **What it checks:** Enforces or removes trailing commas in function call argument lists, type argument lists, collection literals, and indices based on configuration.
- **Autocorrectable:** yes
- **Needs semantic info:** none
- **Node types used:** COLLECTION_LITERAL_EXPRESSION, COMMA, FUNCTION_LITERAL, GT, INDICES, RBRACKET, RPAR, TYPE_ARGUMENT_LIST, VALUE_ARGUMENT, VALUE_ARGUMENT_LIST
- **Tree traversal:** expression-level
- **Complexity:** 3
- **Config options:** `ij_kotlin_allow_trailing_comma_on_call_site` (TRAILING_COMMA_ON_CALL_SITE_PROPERTY, default true)
- **Has detekt equivalent:** none
- **Notes:** Adds trailing comma only on multiline expressions; removes redundant trailing commas on single-line expressions regardless of config.

### trailing-comma-on-declaration-site
- **Name:** TrailingCommaOnDeclarationSiteRule
- **Source:** ktlint-ruleset-standard/src/main/kotlin/com/pinterest/ktlint/ruleset/standard/rules/TrailingCommaOnDeclarationSiteRule.kt
- **Category:** style
- **What it checks:** Enforces or removes trailing commas in parameter lists, type parameter lists, destructuring declarations, enum entries, when entries, and function literal parameter lists.
- **Autocorrectable:** yes
- **Needs semantic info:** none
- **Node types used:** ARROW, CLASS, CLASS_BODY, COLLECTION_LITERAL_EXPRESSION, COMMA, DESTRUCTURING_DECLARATION, ENUM_ENTRY, ENUM_KEYWORD, FUNCTION_LITERAL, FUNCTION_TYPE, GT, LPAR, RBRACE, RBRACKET, RPAR, SEMICOLON, TYPE_PARAMETER_LIST, VALUE_ARGUMENT, VALUE_ARGUMENT_LIST, VALUE_PARAMETER, VALUE_PARAMETER_LIST, WHEN_ENTRY, WHEN_ENTRY_GUARD
- **Tree traversal:** declaration-level
- **Complexity:** 3
- **Config options:** `ij_kotlin_allow_trailing_comma` (TRAILING_COMMA_ON_DECLARATION_SITE_PROPERTY, default true)
- **Has detekt equivalent:** none
- **Notes:** Handles enum classes specially (adds semicolon when missing); respects when-entry guard clauses (Kotlin 2.1) that disallow commas; inserts newlines before arrow when trailing comma is added to when entries.

### try-catch-finally-spacing
- **Name:** TryCatchFinallySpacingRule
- **Source:** ktlint-ruleset-standard/src/main/kotlin/com/pinterest/ktlint/ruleset/standard/rules/TryCatchFinallySpacingRule.kt
- **Category:** spacing
- **What it checks:** Enforces newlines after `{` and before `}` in try/catch/finally blocks, a single space before `catch`/`finally`, and disallows comments at certain positions.
- **Autocorrectable:** yes
- **Needs semantic info:** none
- **Node types used:** BLOCK, CATCH, FINALLY, LBRACE, RBRACE, TRY
- **Tree traversal:** expression-level
- **Complexity:** 2
- **Config options:** INDENT_SIZE_PROPERTY, INDENT_STYLE_PROPERTY
- **Has detekt equivalent:** none
- **Notes:** OfficialCodeStyle marker; emits non-autocorrectable violation for comments inside try/catch/finally token set.

### type-argument-comment
- **Name:** TypeArgumentCommentRule
- **Source:** ktlint-ruleset-standard/src/main/kotlin/com/pinterest/ktlint/ruleset/standard/rules/TypeArgumentCommentRule.kt
- **Category:** comments
- **What it checks:** Disallows block/EOL comments inside type projections and requires comments in type argument lists to be on separate lines.
- **Autocorrectable:** no
- **Needs semantic info:** none
- **Node types used:** BLOCK_COMMENT, EOL_COMMENT, TYPE_ARGUMENT_LIST, TYPE_PROJECTION
- **Tree traversal:** expression-level
- **Complexity:** 2
- **Config options:** none
- **Has detekt equivalent:** none
- **Notes:** Allows comments in TYPE_ARGUMENT_LIST only when preceded by whitespace with newline (i.e., on a separate line above).

### type-argument-list-spacing
- **Name:** TypeArgumentListSpacingRule
- **Source:** ktlint-ruleset-standard/src/main/kotlin/com/pinterest/ktlint/ruleset/standard/rules/TypeArgumentListSpacingRule.kt
- **Category:** spacing
- **What it checks:** Removes whitespace before and after type argument lists (e.g., `listOf <String>()` or `listOf<String> ()`), and inside angle brackets on single-line lists.
- **Autocorrectable:** yes
- **Needs semantic info:** none
- **Node types used:** TYPE_ARGUMENT_LIST, LT, GT, WHITE_SPACE, CALL_EXPRESSION, LAMBDA_ARGUMENT, SUPER_EXPRESSION, SUPER_TYPE_LIST, TYPE_REFERENCE
- **Tree traversal:** expression-level
- **Complexity:** 2
- **Config options:** INDENT_SIZE_PROPERTY, INDENT_STYLE_PROPERTY
- **Has detekt equivalent:** none
- **Notes:** Allows space after `>` when followed by a lambda argument; defers to indentation rule for multiline indentation fixes.

### type-parameter-comment
- **Name:** TypeParameterCommentRule
- **Source:** ktlint-ruleset-standard/src/main/kotlin/com/pinterest/ktlint/ruleset/standard/rules/TypeParameterCommentRule.kt
- **Category:** comments
- **What it checks:** Disallows block/EOL comments inside type parameters and requires comments in type parameter lists to be on separate lines.
- **Autocorrectable:** no
- **Needs semantic info:** none
- **Node types used:** BLOCK_COMMENT, EOL_COMMENT, TYPE_PARAMETER, TYPE_PARAMETER_LIST
- **Tree traversal:** expression-level
- **Complexity:** 2
- **Config options:** none
- **Has detekt equivalent:** none
- **Notes:** Error message for TYPE_PARAMETER_LIST mentions "value_argument_list" (likely a copy-paste from the original DiscouragedCommentLocationRule).

### type-parameter-list-spacing
- **Name:** TypeParameterListSpacingRule
- **Source:** ktlint-ruleset-standard/src/main/kotlin/com/pinterest/ktlint/ruleset/standard/rules/TypeParameterListSpacingRule.kt
- **Category:** spacing
- **What it checks:** Enforces correct spacing around type parameter lists: no space before `<` (except single space on `fun`), single space after `>` before constructor/class body/EQ, and correct whitespace inside `<>`.
- **Autocorrectable:** yes
- **Needs semantic info:** none
- **Node types used:** TYPE_PARAMETER_LIST, CLASS, TYPEALIAS, FUN, LT, GT, PRIMARY_CONSTRUCTOR, CONSTRUCTOR_KEYWORD, CLASS_BODY, EQ
- **Tree traversal:** declaration-level
- **Complexity:** 3
- **Config options:** INDENT_SIZE_PROPERTY, INDENT_STYLE_PROPERTY
- **Has detekt equivalent:** none
- **Notes:** Context-dependent: different spacing rules for class, typealias, and function declarations; allows newline before explicit constructor keyword.

### unnecessary-parentheses-before-trailing-lambda
- **Name:** UnnecessaryParenthesesBeforeTrailingLambdaRule
- **Source:** ktlint-ruleset-standard/src/main/kotlin/com/pinterest/ktlint/ruleset/standard/rules/UnnecessaryParenthesesBeforeTrailingLambdaRule.kt
- **Category:** style
- **What it checks:** Removes empty parentheses `()` in function calls when followed by a trailing lambda.
- **Autocorrectable:** yes
- **Needs semantic info:** none
- **Node types used:** VALUE_ARGUMENT_LIST, LPAR, RPAR, CALL_EXPRESSION, LAMBDA_ARGUMENT, LAMBDA_EXPRESSION, FUNCTION_LITERAL
- **Tree traversal:** expression-level
- **Complexity:** 2
- **Config options:** none
- **Has detekt equivalent:** none
- **Notes:** Guards against removing parens when preceded by a call expression ending with a lambda argument (would change semantics).

### value-argument-comment
- **Name:** ValueArgumentCommentRule
- **Source:** ktlint-ruleset-standard/src/main/kotlin/com/pinterest/ktlint/ruleset/standard/rules/ValueArgumentCommentRule.kt
- **Category:** comments
- **What it checks:** Disallows block/EOL comments inside value arguments (inside or on the same line after the argument).
- **Autocorrectable:** no
- **Needs semantic info:** none
- **Node types used:** VALUE_ARGUMENT
- **Tree traversal:** expression-level
- **Complexity:** 1
- **Config options:** none
- **Has detekt equivalent:** none
- **Notes:** ---

### value-parameter-comment
- **Name:** ValueParameterCommentRule
- **Source:** ktlint-ruleset-standard/src/main/kotlin/com/pinterest/ktlint/ruleset/standard/rules/ValueParameterCommentRule.kt
- **Category:** comments
- **What it checks:** Disallows comments inside value parameters, except KDoc as the first child element.
- **Autocorrectable:** no
- **Needs semantic info:** none
- **Node types used:** VALUE_PARAMETER, KDOC
- **Tree traversal:** declaration-level
- **Complexity:** 1
- **Config options:** none
- **Has detekt equivalent:** none
- **Notes:** Allows KDoc before parameter name (first child of VALUE_PARAMETER); block/EOL comments before parameters are children of VALUE_PARAMETER_LIST and thus not caught here.

### when-entry-bracing
- **Name:** WhenEntryBracing
- **Source:** ktlint-ruleset-standard/src/main/kotlin/com/pinterest/ktlint/ruleset/standard/rules/WhenEntryBracing.kt
- **Category:** style
- **What it checks:** If any when-entry body uses curly braces or is multiline, all when-entry bodies must use curly braces for consistency.
- **Autocorrectable:** yes
- **Needs semantic info:** none
- **Node types used:** WHEN, WHEN_ENTRY, ARROW, BLOCK
- **Tree traversal:** expression-level
- **Complexity:** 3
- **Config options:** INDENT_SIZE_PROPERTY, INDENT_STYLE_PROPERTY
- **Has detekt equivalent:** none
- **Notes:** OfficialCodeStyle marker; uses KtlintKotlinCompiler.createASTNodeFromText to generate replacement when-entry nodes with braces; uses removeRange to delete old nodes.

### wrapping
- **Name:** WrappingRule
- **Source:** ktlint-ruleset-standard/src/main/kotlin/com/pinterest/ktlint/ruleset/standard/rules/WrappingRule.kt
- **Category:** wrapping
- **What it checks:** Inserts missing newlines inside blocks, parenthesized groups, super-type lists, value/type argument/parameter lists, arrows, and closing quotes of multiline raw strings.
- **Autocorrectable:** yes
- **Needs semantic info:** none
- **Node types used:** ANNOTATION, ARROW, BLOCK, CALL_EXPRESSION, CLOSING_QUOTE, COMMA, CONDITION, DESTRUCTURING_DECLARATION, DOT, EOL_COMMENT, FOR, FUN, FUNCTION_LITERAL, GT, LAMBDA_EXPRESSION, LBRACE, LBRACKET, LITERAL_STRING_TEMPLATE_ENTRY, LONG_STRING_TEMPLATE_ENTRY, LPAR, LT, OBJECT_LITERAL, RBRACE, RBRACKET, RPAR, STRING_TEMPLATE, SUPER_TYPE_CALL_ENTRY, SUPER_TYPE_ENTRY, SUPER_TYPE_LIST, TYPE_ARGUMENT_LIST, TYPE_PARAMETER, TYPE_PARAMETER_LIST, TYPE_PROJECTION, VALUE_ARGUMENT, VALUE_ARGUMENT_LIST, VALUE_PARAMETER, VALUE_PARAMETER_LIST, WHEN_ENTRY, WHITE_SPACE
- **Tree traversal:** file-level
- **Complexity:** 3
- **Config options:** INDENT_SIZE_PROPERTY, INDENT_STYLE_PROPERTY, MAX_LINE_LENGTH_PROPERTY
- **Has detekt equivalent:** none
- **Notes:** The most comprehensive wrapping rule; tracks line numbers across WHITE_SPACE nodes; handles for-loop special case; uses afterVisitChildNodes for RBRACE newline insertion; checks max line length for BLOCK nodes.
