# Wrasse — High-Level Design

Kotlin compiler plugin for linting and formatting that rides the compiler's own parse,
avoiding the double/triple compilation overhead of detekt and ktlint.

## Motivation

1. **Zero parse overhead.** ktlint and detekt each embed `kotlin-compiler-embeddable` and parse
   every source file independently. On large projects (2000+ files) this adds 30-80s of redundant
   work on top of compilation. Wrasse hooks into the compiler's FIR analysis phase and reads the
   LightTree that kotlinc already built — no second parse.

2. **No Gradle plugin API dependency.** Gradle's API churn (configuration cache, isolated projects,
   build cache changes) forces ktlint and detekt maintainers to spend significant effort on Gradle
   compatibility. Wrasse ships as a compiler plugin JAR — users add it to `kotlinCompilerPluginClasspath`
   and pass options via `CommandLineProcessor`. Works with Gradle, Amper, Bazel, or raw kotlinc.

3. **Decoupled from kotlinc internals.** All rule logic operates on an intermediate model that wrasse
   controls. An adapter layer translates kotlinc's LightTree into this model. When kotlinc APIs change
   (and they will — K2 APIs are unstable), only the adapter needs updating; rules stay untouched.

## Architecture

```
┌─────────────────────────────────────────────────────────────────────┐
│ kotlinc                                                             │
│                                                                     │
│  Source → [LightTree] → FIR → IR → Bytecode                        │
│               │           │                                         │
│               │     ┌─────┘                                         │
│               ▼     ▼                                               │
│     ┌─────────────────────┐                                         │
│     │ WrasseFirChecker     │   FirAdditionalCheckersExtension        │
│     │                     │   accesses source.treeStructure          │
│     └────────┬────────────┘   (full file LightTree with whitespace, │
│              │                 comments, all tokens)                 │
└──────────────┼──────────────────────────────────────────────────────┘
               │
               ▼
     ┌─────────────────────┐
     │ Adapter Layer        │  Translates LighterASTNode / IElementType
     │                     │  into wrasse's own WNode / WNodeType
     └────────┬────────────┘
               │
               ▼
     ┌─────────────────────┐
     │ Rule Engine          │  Walks WNode tree, applies rules,
     │                     │  collects violations
     └────────┬────────────┘
               │
          ┌────┴────┐
          ▼         ▼
     ┌────────┐ ┌────────────┐
     │ Lint   │ │ Format     │
     │ Report │ │ Write temp │
     │ diags  │ │ files      │
     └────────┘ └────────────┘
```

### Compilation pipeline integration

- **FIR phase (linting):** `FirAdditionalCheckersExtension` runs during compiler analysis.
  The checker accesses the full file LightTree via `KtLightSourceElement.treeStructure`.
  This tree preserves all whitespace, comments (EOL_COMMENT, BLOCK_COMMENT, KDoc), and
  token-level detail. Violations are reported as proper `KtDiagnostic` instances via
  `DiagnosticReporter.reportOn()` — these show as red/yellow squiggles in IntelliJ
  (requires "Kotlin External FIR Support" plugin installed).

- **IR phase (code transformation, optional):** `IrGenerationExtension` is available for
  future use cases (e.g., injecting runtime checks). Not used for linting/formatting.

- **Formatting output:** When format mode is enabled (CLI flag), the rule engine writes
  reformatted source to a temp directory, one file per changed source file, mirroring the
  source tree structure. A post-compile step moves formatted files into actual sources.
  Formatting is always one-build-behind: the current build compiles the original source,
  the next build compiles the formatted source.

### Module structure

```
wrasse/
├── wrasse-model/            # WNode, WNodeType, WRule, WSemanticRule — zero kotlinc deps
├── wrasse-rules/            # Rule implementations — depends only on wrasse-model
├── wrasse-adapter/          # LightTree → WNode, FIR → WResolvedCall — depends on kotlinc
├── wrasse-engine/           # Dispatch table, config loading, tree walking, reporting
├── compiler-plugin/         # Glue: loads config, creates adapter, runs engine
│   └── compileOnly: kotlin-compiler-embeddable
├── sample/                  # Test project that applies the plugin
└── testing/
    └── adapter-test/        # Adapter tests parameterized across kotlinc versions
```

`wrasse-model` and `wrasse-rules` have zero dependency on kotlinc.
Rules are testable without a compiler and portable to other hosts
(e.g., a future IntelliJ plugin could use a PSI → WNode adapter instead).

`wrasse-adapter` is published separately — external users who want to build
their own tools on top of the LightTree → WNode translation can depend on it
without pulling in the rule engine.

## Intermediate Model (wrasse-model)

### WNode — the core tree node

```kotlin
interface WNode {
    val type: WNodeType
    val startOffset: Int
    val endOffset: Int
    val isLeaf: Boolean
    val leafText: CharSequence?   // non-null only for leaf (token) nodes

    val parent: WNode?
    val children: List<WNode>     // lazy — only built when accessed
    val firstChild: WNode?
    val lastChild: WNode?
    val nextSibling: WNode?
    val prevSibling: WNode?

    // Convenience
    fun childrenOfType(type: WNodeType): List<WNode>
    fun firstChildOfType(type: WNodeType): WNode?
    fun findParentOfType(type: WNodeType): WNode?
    fun descendants(): Sequence<WNode>
    fun descendantsOfType(type: WNodeType): Sequence<WNode>
    fun leaves(): Sequence<WNode>
}
```

`WNode` is an interface, not a data class. The primary implementation (`LightTreeWNode`)
is a lazy view over kotlinc's `LighterASTNode` — no copies, no allocation until a rule
accesses `.children` or `.leafText`. Composite nodes have no text; rules that need
source text of a subtree use `WFile.sourceText.subSequence(node.startOffset, node.endOffset)`.

```kotlin
data class WFile(
    val path: String,
    val root: WNode,              // FILE node — the full tree
    val sourceText: CharSequence, // whole file source, backed by compiler's char buffer
)
```

### WNodeType — element type enum

Maps 1:1 from kotlinc's `IElementType` constants. Wrasse defines its own enum to avoid
importing `KtTokens` / `KtNodeTypes` in rule code.

```kotlin
enum class WNodeType {
    // File structure
    FILE, PACKAGE_DIRECTIVE, IMPORT_LIST, IMPORT_DIRECTIVE,

    // Declarations
    FUN, CLASS, OBJECT_DECLARATION, PROPERTY, TYPEALIAS,
    VALUE_PARAMETER_LIST, VALUE_PARAMETER, TYPE_PARAMETER_LIST, TYPE_PARAMETER,
    CLASS_BODY, ENUM_ENTRY,
    PRIMARY_CONSTRUCTOR, SECONDARY_CONSTRUCTOR,
    PROPERTY_ACCESSOR,

    // Modifiers & annotations
    MODIFIER_LIST, ANNOTATION_ENTRY, ANNOTATION_TARGET,

    // Type references
    TYPE_REFERENCE, USER_TYPE, NULLABLE_TYPE, FUNCTION_TYPE,
    TYPE_ARGUMENT_LIST, TYPE_PROJECTION,

    // Expressions
    BLOCK, LAMBDA_EXPRESSION, FUNCTION_LITERAL,
    CALL_EXPRESSION, VALUE_ARGUMENT_LIST, VALUE_ARGUMENT,
    REFERENCE_EXPRESSION, DOT_QUALIFIED_EXPRESSION, SAFE_ACCESS_EXPRESSION,
    BINARY_EXPRESSION, PREFIX_EXPRESSION, POSTFIX_EXPRESSION,
    IF, WHEN, WHEN_ENTRY, WHEN_CONDITION_EXPRESSION, WHEN_CONDITION_IS_PATTERN,
    FOR, WHILE, DO_WHILE,
    TRY, CATCH, FINALLY,
    RETURN, THROW, BREAK, CONTINUE,
    IS_EXPRESSION, AS_EXPRESSION,
    OBJECT_LITERAL, THIS_EXPRESSION, SUPER_EXPRESSION,
    PARENTHESIZED, LABELED_EXPRESSION,

    // Literals
    INTEGER_CONSTANT, FLOAT_CONSTANT, CHARACTER_CONSTANT, BOOLEAN_CONSTANT, NULL,
    STRING_TEMPLATE, LONG_STRING_TEMPLATE_ENTRY, SHORT_STRING_TEMPLATE_ENTRY,
    LITERAL_STRING_TEMPLATE_ENTRY, ESCAPE_STRING_TEMPLATE_ENTRY,
    OPEN_QUOTE, CLOSING_QUOTE, REGULAR_STRING_PART,

    // Tokens (leaves)
    IDENTIFIER,
    WHITE_SPACE,
    EOL_COMMENT, BLOCK_COMMENT, KDOC,
    LPAR, RPAR, LBRACE, RBRACE, LBRACKET, RBRACKET,
    COMMA, DOT, SAFE_ACCESS, ELVIS, RANGE, COLONCOLON,
    COLON, SEMICOLON, ARROW, DOUBLE_ARROW,
    EQ, EQEQ, EXCLEQ, LT, GT, LTEQ, GTEQ,
    PLUS, MINUS, MUL, DIV, PERC,
    PLUSEQ, MINUSEQ, MULEQ, DIVEQ, PERCEQ,
    ANDAND, OROR, EXCL,
    PLUSPLUS, MINUSMINUS,

    // Keywords
    KW_FUN, KW_VAL, KW_VAR, KW_CLASS, KW_INTERFACE, KW_OBJECT,
    KW_IF, KW_ELSE, KW_WHEN, KW_FOR, KW_WHILE, KW_DO,
    KW_RETURN, KW_THROW, KW_BREAK, KW_CONTINUE,
    KW_TRY, KW_CATCH, KW_FINALLY,
    KW_IN, KW_IS, KW_AS,
    KW_NULL, KW_TRUE, KW_FALSE, KW_THIS, KW_SUPER,
    KW_PACKAGE, KW_IMPORT,
    KW_PUBLIC, KW_PRIVATE, KW_PROTECTED, KW_INTERNAL,
    KW_OPEN, KW_ABSTRACT, KW_SEALED, KW_DATA, KW_OVERRIDE,
    KW_SUSPEND, KW_INLINE, KW_TAILREC, KW_OPERATOR, KW_INFIX,
    KW_COMPANION, KW_CONST, KW_LATEINIT, KW_ENUM,
    KW_TYPEALIAS,

    // Fallback
    UNKNOWN,
}
```

### WRule — rule interface

```kotlin
interface WRule {
    val id: String
    val description: String
    val severity: WSeverity           // ERROR, WARNING, INFO
    val kotlinVersionRange: WVersionRange?

    fun check(file: WFile, config: WRuleConfig, reporter: WReporter)
}

interface WReporter {
    fun report(node: WNode, message: String)
}

data class WFile(
    val path: String,
    val root: WNode,               // FILE node — the full tree
)

enum class WSeverity { ERROR, WARNING, INFO }

data class WVersionRange(val min: String?, val max: String?)

interface WRuleConfig {
    fun string(key: String): String?
    fun boolean(key: String): Boolean?
    fun stringList(key: String): List<String>?
}
```

## Config file

Walk up directories from source root until a `wrasse.json` (or `wrasse.jsonc`) is found.
JSON format with a `$schema` reference for IDE autocomplete — IntelliJ and VS Code provide
inline documentation, validation, and completion for every field. The schema is hosted on
GitHub Pages.

JSON is parsed by a hand-rolled recursive descent parser (~200-300 lines of Kotlin).
Zero external dependencies. JSONC support strips `//` and `/* */` comments before parsing.

```json
{
  "$schema": "https://varlanv.github.io/wrasse/schema.json",
  "kotlin": "2.4",
  "exclude": ["**/build/**", "**/generated/**"],
  "rules": {
    "no-wildcard-imports": {
      "severity": "error"
    },
    "no-trailing-comma": {
      "severity": "warning",
      "kotlin": "2.0+"
    },
    "max-line-length": {
      "severity": "warning",
      "max": 140
    },
    "restricted-api": {
      "severity": "error",
      "targets": [
        { "target": "kotlin.io.println", "reason": "Use project logger" },
        { "target": "java.lang.Thread.sleep", "reason": "Use kotlinx.coroutines.delay" }
      ]
    },
    "enforce-result": {
      "severity": "warning",
      "known-throwing": [
        { "target": "kotlin.text.toInt", "exception": "NumberFormatException" },
        { "target": "kotlin.collections.first", "exception": "NoSuchElementException" }
      ],
      "include": ["wrasse-throws.json"]
    }
  },
  "format": {
    "enabled": false,
    "output-dir": ".wrasse/formatted"
  }
}
```

### Exclude semantics

- **Global `exclude`:** applies to all rules. Union with per-rule excludes.
- **Per-rule `exclude`:** additional excludes for a specific rule.
- **No `include` field.** A rule runs on all files unless excluded.
  If a rule should only run on test files, exclude everything else.
  Include and exclude together create precedence ambiguity — exclude-only avoids this.
- Globs use `java.nio.file.FileSystem.getPathMatcher` (JDK built-in, zero deps).

## Novel rules (beyond ktlint/detekt)

These checks are uniquely possible or significantly better in wrasse because of
FIR access and LightTree fidelity. They should inform the rule engine design from
the start — particularly the "match resolved call + inspect arguments" primitive.

### Function visual line limit

Enforce a hard limit on the number of visual lines in a function body (e.g., 70 lines).
Unlike detekt's `LongMethod` which counts statements, this counts actual rendered lines
including blank lines and comments — what matters for "fits on screen."

- **Input:** LightTree — count `\n` characters in `WHITE_SPACE` leaf nodes between `LBRACE`
  and `RBRACE` of the function body.
- **Config:** `max-visual-lines: 70`
- **Needs semantic info:** none
- **Why better:** detekt counts statements (misses blank lines, comments, multi-line
  expressions). ktlint doesn't have this rule at all.

### Split compound boolean conditions

Warn when `if`/`when` conditions contain deeply nested `&&`/`||` operators.
Encourages splitting into nested `if`s or extracting named booleans.
Inspired by TigerStyle: "Split compound conditions into simple conditions."

- **Input:** LightTree — check `BINARY_EXPRESSION` nodes with `ANDAND`/`OROR` operator
  children, count nesting depth.
- **Config:** `max-condition-depth: 2`
- **Needs semantic info:** none

### Split compound assertions

Warn when `require()`, `check()`, or `assert()` is called with a compound boolean argument.
`require(a && b)` should be `require(a); require(b)` for more precise failure messages.
Also enforce that assertion calls include a message argument (the lambda overload).

- **Input:** FIR — `FirFunctionCallChecker` matches resolved callees `kotlin.require`,
  `kotlin.check`, `kotlin.assert` by `CallableId`. Inspect the argument expression: if it's
  a `BINARY_EXPRESSION` with `ANDAND`/`OROR`, report. If the call only has one argument
  (no message lambda), report a separate "assertion should include a message" violation.
- **Config:** `require-assertion-message: true`, `forbid-compound-assertion: true`
- **Needs semantic info:** resolved-call (to distinguish `kotlin.require` from a local
  function named `require`)

### No recursion

Warn when a function calls itself (direct recursion). Ensures bounded execution depth.
Inspired by TigerStyle and NASA's Power of Ten rules.

- **Input:** FIR — `FirFunctionCallChecker` checks if the resolved callee symbol matches
  the enclosing function's symbol. Walk `CheckerContext.containingDeclarations` to find
  the enclosing `FirFunction` and compare symbols.
- **Config:** `allow-tailrec: true` (optionally allow `tailrec` functions which are
  compiled to loops)
- **Needs semantic info:** resolved-call
- **Why novel:** neither ktlint nor detekt detect recursion. The compiler knows the exact
  callee symbol — no false positives from name shadowing.

### Explicit library defaults

Warn when a function call uses default parameter values for a configurable set of library
functions. Forces call sites to spell out all arguments, making behavior visible and
protecting against upstream default changes.
Inspired by TigerStyle: "Explicitly pass library function options at call sites."

- **Input:** FIR — `FirFunctionCallChecker` resolves the callee, gets its
  `FirNamedFunctionSymbol.valueParameters`. Compare which parameters have defaults
  (`hasDefaultValue`) against which arguments were actually passed in
  `FirFunctionCall.argumentList`. If a defaulted parameter wasn't explicitly passed
  and the function's `CallableId` matches the configured set, report.
- **Config:** list of function FQNs where defaults should be spelled out, e.g.
  `["io.ktor.client.request.HttpRequestBuilder.timeout"]`
- **Needs semantic info:** resolved-call, resolved parameter defaults
- **Why novel:** impossible without type resolution. ktlint can't see defaults. detekt
  could theoretically do this via Analysis API but doesn't have this rule and it would
  be expensive.

### Design implication for rule engine

All five rules above (plus restricted-api and enforce-result from earlier sections) share
a common pattern: **match a resolved function call by `CallableId` and inspect its arguments
or context.** This should be a first-class primitive in the rule engine:

```kotlin
interface WSemanticRule {
    val id: String
    val description: String
    val severity: WSeverity
    val kotlinVersionRange: WVersionRange?
    val targetCallables: Set<String>  // FQNs this rule cares about

    fun checkCall(call: WResolvedCall, config: WRuleConfig, reporter: WReporter)
}

data class WResolvedCall(
    val callableId: String,           // e.g. "kotlin.require"
    val arguments: List<WArgument>,   // passed arguments
    val defaultedParameters: List<String>, // params that used defaults
    val enclosingFunction: WFunctionInfo?, // for recursion check
    val isInsideTry: Boolean,         // for enforce-result
    val source: WNode,                // for reporting location
)
```

This separates syntactic rules (operate on `WNode` tree, no kotlinc dependency) from
semantic rules (operate on `WResolvedCall`, adapter fills from FIR). Both rule types
live in `wrasse-rules` with zero kotlinc imports.

## Diagnostics (IDE integration)

Wrasse reports proper `KtDiagnostic` instances so IntelliJ shows violations inline.
No separate IntelliJ plugin needed for basic error/warning display.

```kotlin
object WrasseErrors : KtDiagnosticsContainer() {
    val WRASSE_ERROR   by error1<KtElement, String>()
    val WRASSE_WARNING by warning1<KtElement, String>()
    val WRASSE_INFO    by warning1<KtElement, String>(SourceElementPositioningStrategies.DEFAULT)

    override fun getRendererFactory() = Renderers

    object Renderers : BaseDiagnosticRendererFactory() {
        override val MAP by KtDiagnosticFactoryToRendererMap("Wrasse") {
            it.put(WRASSE_ERROR, "wrasse: {0}", TO_STRING)
            it.put(WRASSE_WARNING, "wrasse: {0}", TO_STRING)
            it.put(WRASSE_INFO, "wrasse: {0}", TO_STRING)
        }
    }
}
```

Usage in checker:
```kotlin
reporter.reportOn(declaration.source, WrasseErrors.WRASSE_WARNING, "Use logger instead of println")
```

Quickfixes (click-to-fix in IDE) require a separate IntelliJ plugin and are out of scope
for initial release.

## Kotlinc APIs to wrap in adapter layer

These are all the kotlinc types that the adapter touches. Everything below this line
is **internal to the adapter** — rule code never imports any of these.

### LightTree traversal (FIR checker entry point)

| kotlinc type | Package | Used for |
|---|---|---|
| `KtLightSourceElement` | `org.jetbrains.kotlin` | Access `treeStructure` and `lighterASTNode` from FIR `source` |
| `FlyweightCapableTreeStructure<LighterASTNode>` | `o.j.k.com.intellij.util.diff` | Walk tree: `root`, `getChildren(node, ref)` |
| `LighterASTNode` | `o.j.k.com.intellij.lang` | Node interface: `tokenType`, `startOffset`, `endOffset` |
| `LighterASTTokenNode` | `o.j.k.com.intellij.lang` | Leaf node with `.text` |
| `Ref<Array<LighterASTNode?>>` | `o.j.k.com.intellij.openapi.util` | Output param for `getChildren` |
| `IElementType` | `o.j.k.com.intellij.psi.tree` | Node/token type identifier |

### Element types to map (IElementType → WNodeType)

Source: `KtNodeTypes`, `KtTokens`, `KDocTokens` in `kotlin-compiler-embeddable`.

| kotlinc constant | Class | Maps to |
|---|---|---|
| `KtNodeTypes.FUN` | `KtNodeTypes` | `WNodeType.FUN` |
| `KtNodeTypes.CLASS` | `KtNodeTypes` | `WNodeType.CLASS` |
| `KtNodeTypes.PROPERTY` | `KtNodeTypes` | `WNodeType.PROPERTY` |
| `KtNodeTypes.OBJECT_DECLARATION` | `KtNodeTypes` | `WNodeType.OBJECT_DECLARATION` |
| `KtNodeTypes.VALUE_PARAMETER_LIST` | `KtNodeTypes` | `WNodeType.VALUE_PARAMETER_LIST` |
| `KtNodeTypes.VALUE_PARAMETER` | `KtNodeTypes` | `WNodeType.VALUE_PARAMETER` |
| `KtNodeTypes.TYPE_REFERENCE` | `KtNodeTypes` | `WNodeType.TYPE_REFERENCE` |
| `KtNodeTypes.BLOCK` | `KtNodeTypes` | `WNodeType.BLOCK` |
| `KtNodeTypes.IF` | `KtNodeTypes` | `WNodeType.IF` |
| `KtNodeTypes.WHEN` | `KtNodeTypes` | `WNodeType.WHEN` |
| `KtNodeTypes.WHEN_ENTRY` | `KtNodeTypes` | `WNodeType.WHEN_ENTRY` |
| `KtNodeTypes.FOR` | `KtNodeTypes` | `WNodeType.FOR` |
| `KtNodeTypes.WHILE` | `KtNodeTypes` | `WNodeType.WHILE` |
| `KtNodeTypes.TRY` | `KtNodeTypes` | `WNodeType.TRY` |
| `KtNodeTypes.CATCH` | `KtNodeTypes` | `WNodeType.CATCH` |
| `KtNodeTypes.CALL_EXPRESSION` | `KtNodeTypes` | `WNodeType.CALL_EXPRESSION` |
| `KtNodeTypes.BINARY_EXPRESSION` | `KtNodeTypes` | `WNodeType.BINARY_EXPRESSION` |
| `KtNodeTypes.REFERENCE_EXPRESSION` | `KtNodeTypes` | `WNodeType.REFERENCE_EXPRESSION` |
| `KtNodeTypes.DOT_QUALIFIED_EXPRESSION` | `KtNodeTypes` | `WNodeType.DOT_QUALIFIED_EXPRESSION` |
| `KtNodeTypes.LAMBDA_EXPRESSION` | `KtNodeTypes` | `WNodeType.LAMBDA_EXPRESSION` |
| `KtNodeTypes.STRING_TEMPLATE` | `KtNodeTypes` | `WNodeType.STRING_TEMPLATE` |
| `KtNodeTypes.IMPORT_LIST` | `KtNodeTypes` | `WNodeType.IMPORT_LIST` |
| `KtNodeTypes.IMPORT_DIRECTIVE` | `KtNodeTypes` | `WNodeType.IMPORT_DIRECTIVE` |
| `KtNodeTypes.PACKAGE_DIRECTIVE` | `KtNodeTypes` | `WNodeType.PACKAGE_DIRECTIVE` |
| `KtNodeTypes.RETURN` | `KtNodeTypes` | `WNodeType.RETURN` |
| `KtNodeTypes.THROW` | `KtNodeTypes` | `WNodeType.THROW` |
| `KtNodeTypes.ANNOTATION_ENTRY` | `KtNodeTypes` | `WNodeType.ANNOTATION_ENTRY` |
| `KtNodeTypes.MODIFIER_LIST` | `KtNodeTypes` | `WNodeType.MODIFIER_LIST` |
| `KtNodeTypes.CLASS_BODY` | `KtNodeTypes` | `WNodeType.CLASS_BODY` |
| `KtTokens.IDENTIFIER` | `KtTokens` | `WNodeType.IDENTIFIER` |
| `KtTokens.WHITE_SPACE` | `KtTokens` | `WNodeType.WHITE_SPACE` |
| `KtTokens.EOL_COMMENT` | `KtTokens` | `WNodeType.EOL_COMMENT` |
| `KtTokens.BLOCK_COMMENT` | `KtTokens` | `WNodeType.BLOCK_COMMENT` |
| `KtTokens.LPAR` | `KtTokens` | `WNodeType.LPAR` |
| `KtTokens.RPAR` | `KtTokens` | `WNodeType.RPAR` |
| `KtTokens.LBRACE` | `KtTokens` | `WNodeType.LBRACE` |
| `KtTokens.RBRACE` | `KtTokens` | `WNodeType.RBRACE` |
| `KtTokens.COMMA` | `KtTokens` | `WNodeType.COMMA` |
| `KtTokens.DOT` | `KtTokens` | `WNodeType.DOT` |
| `KtTokens.COLON` | `KtTokens` | `WNodeType.COLON` |
| `KtTokens.ARROW` | `KtTokens` | `WNodeType.ARROW` |
| `KtTokens.EQ` | `KtTokens` | `WNodeType.EQ` |
| `KtTokens.EQEQ` | `KtTokens` | `WNodeType.EQEQ` |
| `KtTokens.EXCLEQ` | `KtTokens` | `WNodeType.EXCLEQ` |
| `KtTokens.SEMICOLON` | `KtTokens` | `WNodeType.SEMICOLON` |
| `KtTokens.FUN_KEYWORD` | `KtTokens` | `WNodeType.KW_FUN` |
| `KtTokens.VAL_KEYWORD` | `KtTokens` | `WNodeType.KW_VAL` |
| `KtTokens.VAR_KEYWORD` | `KtTokens` | `WNodeType.KW_VAR` |
| `KtTokens.CLASS_KEYWORD` | `KtTokens` | `WNodeType.KW_CLASS` |
| `KtTokens.IF_KEYWORD` | `KtTokens` | `WNodeType.KW_IF` |
| `KtTokens.ELSE_KEYWORD` | `KtTokens` | `WNodeType.KW_ELSE` |
| `KtTokens.RETURN_KEYWORD` | `KtTokens` | `WNodeType.KW_RETURN` |
| `KtTokens.NULL_KEYWORD` | `KtTokens` | `WNodeType.KW_NULL` |
| `KDocTokens.KDOC` | `KDocTokens` | `WNodeType.KDOC` |

### FIR checker registration

| kotlinc type | Package | Used for |
|---|---|---|
| `CompilerPluginRegistrar` | `o.j.k.compiler.plugin` | Plugin entry point |
| `CommandLineProcessor` | `o.j.k.compiler.plugin` | CLI options |
| `CompilerConfiguration` | `o.j.k.config` | Read plugin options |
| `CompilerConfigurationKey<T>` | `o.j.k.config` | Define option keys |
| `FirExtensionRegistrar` | `o.j.k.fir.extensions` | Register FIR extensions |
| `FirExtensionRegistrarAdapter` | `o.j.k.fir.extensions` | Adapter for registrar |
| `FirAdditionalCheckersExtension` | `o.j.k.fir.analysis.extensions` | Declare checkers |
| `FirFunctionChecker` | `o.j.k.fir.analysis.checkers.declaration` | Per-function check |
| `FirFileChecker` | `o.j.k.fir.analysis.checkers.declaration` | Per-file check (preferred) |
| `DeclarationCheckers` | `o.j.k.fir.analysis.checkers.declaration` | Checker container |
| `CheckerContext` | `o.j.k.fir.analysis.checkers.context` | Checker context |
| `FirFunction` | `o.j.k.fir.declarations` | FIR function declaration |
| `FirFile` | `o.j.k.fir.declarations` | FIR file declaration |
| `MppCheckerKind` | `o.j.k.fir.analysis.checkers` | Common/Platform discriminator |

### Diagnostics (IDE integration)

| kotlinc type | Package | Used for |
|---|---|---|
| `KtDiagnosticsContainer` | `o.j.k.diagnostics` | Declare diagnostic factories |
| `KtDiagnosticFactoryToRendererMap` | `o.j.k.diagnostics` | Map factories to messages |
| `BaseDiagnosticRendererFactory` | `o.j.k.diagnostics.rendering` | Renderer provider |
| `KtDiagnosticRenderers.TO_STRING` | `o.j.k.diagnostics` | String parameter renderer |
| `DiagnosticReporter` | `o.j.k.diagnostics` | Report diagnostics |
| `reportOn` | `o.j.k.diagnostics` | Extension function to report |
| `Severity` | `o.j.k.diagnostics` | ERROR / WARNING |
| `SourceElementPositioningStrategies` | `o.j.k.diagnostics` | Where to underline |
| `error0` / `error1` / `warning0` / `warning1` | `o.j.k.diagnostics` | Factory DSL functions |
| `KtElement` | `o.j.k.psi` | PSI type parameter for factories |

### FIR semantic access (for restricted-api and enforce-result rules)

| kotlinc type | Package | Used for |
|---|---|---|
| `FirFunctionCall` | `o.j.k.fir.expressions` | Resolved function call |
| `FirFunctionCallChecker` | `o.j.k.fir.analysis.checkers.expression` | Check call expressions |
| `FirResolvedNamedReference` | `o.j.k.fir.references` | Resolved callee symbol |
| `FirNamedFunctionSymbol` | `o.j.k.fir.symbols.impl` | Function symbol for FQN |
| `FirTryExpression` | `o.j.k.fir.expressions` | Check if call is inside try |
| `ExpressionCheckers` | `o.j.k.fir.analysis.checkers.expression` | Expression checker container |
| `CallableId` | `o.j.k.name` | Fully qualified callable name |
| `FqName` | `o.j.k.name` | Fully qualified name |
| `Name` | `o.j.k.name` | Simple name |

## Comparison with ktlint and detekt

| Aspect | wrasse | ktlint | detekt |
|---|---|---|---|
| Parse overhead | None (rides compiler) | Full PSI parse | Full PSI parse + optional type resolution |
| Tree type | LightTree (read-only, flyweight) | PSI (mutable, heavy) | PSI (mutable, heavy) |
| Rule input | WNode (own model) | ASTNode (kotlinc PSI) | KtElement (kotlinc PSI) |
| Formatting | Read tree → rewrite file | Mutate PSI → serialize | N/A (lint only) |
| IDE integration | KtDiagnostic (native) | Separate IntelliJ plugin | Separate IntelliJ plugin |
| Gradle coupling | None (compiler plugin) | Gradle plugin | Gradle plugin |
| Type resolution | FIR (for semantic rules) | None | Analysis API (optional) |
| Build tool support | Any kotlinc host | Gradle, Maven, CLI | Gradle, CLI |

## What ktlint/detekt use that wrasse must replicate in WNode

### From ktlint (ASTNode API → WNode)

| ktlint ASTNode usage | WNode equivalent |
|---|---|
| `node.elementType` | `node.type` |
| `node.text` | `node.text` |
| `node.startOffset` | `node.startOffset` |
| `node.treeParent` | `node.parent` |
| `node.firstChildNode` / `node.lastChildNode` | `node.firstChild` / `node.lastChild` |
| `node.treePrev` / `node.treeNext` | `node.prevSibling` / `node.nextSibling` |
| `node.findChildByType(type)` | `node.firstChildOfType(type)` |
| `node.children()` sequence | `node.children` |
| `node.isLeaf` | `node.isLeaf` |
| `node.psi` | Not needed — wrasse works on CST, not PSI |

### From ktlint (convenience extensions → WNode helpers)

| ktlint extension | WNode equivalent needed |
|---|---|
| `node.nextLeaf()` / `node.prevLeaf()` | `node.nextLeaf()` / `node.prevLeaf()` |
| `node.nextCodeLeaf()` / `node.prevCodeLeaf()` | `node.nextCodeLeaf()` / `node.prevCodeLeaf()` |
| `node.nextCodeSibling()` / `node.prevCodeSibling()` | `node.nextCodeSibling()` / `node.prevCodeSibling()` |
| `node.isWhiteSpace` | `node.type == WNodeType.WHITE_SPACE` |
| `node.isWhiteSpaceWithNewline` | `node.isWhitespaceWithNewline()` |
| `node.isPartOfComment` | `node.isComment` |
| `node.isPartOfString` | `node.isInsideString()` |
| `node.column` | `node.column` (compute from offsets + preceding newline) |
| `node.indent` | `node.indent()` (extract from preceding WHITE_SPACE) |
| `node.findParentByType(type)` | `node.findParentOfType(type)` |
| `node.isPartOf(type)` | `node.isInsideNodeOfType(type)` |
| `node.hasModifier(type)` | `node.hasModifier(WNodeType)` |

### From detekt (KtElement visitor → WNode)

| detekt PSI usage | WNode equivalent |
|---|---|
| `visitNamedFunction(function)` | Rule checks `node.type == FUN` |
| `visitClass(klass)` | Rule checks `node.type == CLASS` |
| `visitIfExpression(expr)` | Rule checks `node.type == IF` |
| `element.parent` | `node.parent` |
| `element.children` | `node.children` |
| `element.text` | `node.text` |
| `element.containingFile` | Walk to root `FILE` node |
| `collectDescendantsOfType<T>()` | `node.descendantsOfType(type)` |
| `anyDescendantOfType<T> { }` | `node.descendantsOfType(type).any { }` |
| `element.textContains('\n')` | `node.text.contains('\n')` |

### Not needed from detekt

| detekt feature | Why wrasse skips it |
|---|---|
| `BindingContext` | Wrasse uses FIR directly, not K1 descriptors |
| `analyze { }` (Analysis API) | Wrasse accesses FIR declarations directly |
| `KtTreeVisitorVoid` | Wrasse walks WNode, not PSI |
| Mutable PSI operations | Wrasse reads LightTree (immutable), rewrites file from scratch |

## Build target

**JVM target: Java 8 bytecode.** The plugin JAR loads into the kotlinc daemon's
classloader. That daemon runs on whatever JDK the build tool uses — JDK 17 for
Gradle 9, possibly JDK 11 for older setups. Every bundled Kotlin compiler plugin
(compose, serialization, all-open, no-arg) targets JVM 8. Wrasse does the same.

This means the compiler-plugin module uses `jvmToolchain(8)`, not the project-wide
Java 26 toolchain. The convention plugin must allow per-module override.

**Kotlin version: single JAR, runtime compatibility across a range.** Wrasse compiles
against the latest Kotlin in its supported range (currently 2.4.0). API differences
across versions are handled inside the adapter layer — reflection or version-conditional
code paths for the 2-3 methods that change between minors. Patch versions almost never
break compiler plugin APIs.

Supported range: Kotlin 2.0+. Documented in README.

## Performance design decisions

### Lazy WNode — zero allocation until accessed

`WNode` is an interface backed by a lazy view over kotlinc's `LighterASTNode`.
No copies, no tree construction until a rule accesses `.children` or `.leafText`.
Rules that only check `node.type` and `node.startOffset` trigger zero allocation.

On a 2000-line file (~10K nodes), eager tree construction would allocate ~400KB.
For 1000 files, that's 400MB of unnecessary heap pressure. Lazy wrapping avoids this.

### Source text: one CharSequence per file

`WFile.sourceText` holds the whole file's source as a `CharSequence` backed by the
compiler's existing char buffer. `WNode.leafText` on tokens delegates to the kotlinc
`LighterASTTokenNode.text` property which returns `CharSequence` (already a slice,
not a copy). Composite nodes have no text — rules use
`file.sourceText.subSequence(node.startOffset, node.endOffset)` when needed.

### Rule dispatch: register interest by node type

Rules declare which `WNodeType` values they care about. The engine builds a dispatch
table at startup, grouped by node type. During tree walk, only matching rules are
invoked per node. Turns O(nodes × rules) into O(nodes × avg-matching-rules).

The dispatch table is rebuilt per file after filtering rules by exclude globs.

### Per-file glob filtering

Exclude globs are compiled to `java.nio.file.PathMatcher` once per compilation
(when config is loaded). Per file, active rules are filtered by matching the file
path against each rule's exclude set (union of global + per-rule excludes).
This produces a per-file active rule list and dispatch table. Microseconds per file.

### FIR semantic rules: zero extra cost

FIR resolution is already complete when checkers run. Reading `calleeReference.resolvedSymbol`
is a pointer dereference — O(1). The adapter builds `WResolvedCall` objects only for
calls that match a rule's `targetCallables` set. With 10 restricted APIs and 50K function
calls in a codebase, that's ~200 adapter objects. Negligible.

### Incremental compilation: free filtering

kotlinc only recompiles changed files. Wrasse's FIR checker only fires on files the
compiler visits. On a million-LOC codebase where one file changes, wrasse processes
one file. ktlint/detekt process all files unless they maintain their own incremental
caching (which is fragile).

### File deduplication

Use `FirFileChecker` (not `FirFunctionChecker`) for syntactic rules. It fires once
per file, avoiding the need for manual deduplication with a "processed files" set.

### Formatting: diff-only output

When formatting is enabled, compare reformatted text against original source.
If identical, skip writing. Only changed files get written to the temp directory.

## Test strategy

Four layers, each progressively slower and broader.

### Layer 1: Unit tests (always)

Test rule logic against hand-built `WNode` trees. Pure unit tests — no compiler,
no version concerns. This is where 80% of tests live.

```kotlin
@Test fun `detects wildcard import`() {
    val tree = wFile {
        wNode(IMPORT_DIRECTIVE) {
            wLeaf(IDENTIFIER, "com")
            wLeaf(DOT, ".")
            wLeaf(MUL, "*")
        }
    }
    val violations = NoWildcardImportsRule().check(tree)
    assertThat(violations).hasSize(1)
}
```

A `WNode` builder DSL in the test fixtures module makes these tests concise.
Lives in `wrasse-model/src/test` and `wrasse-rules/src/test`.

### Layer 2: Current kotlinc version (always)

Test the adapter layer with real compilation using `kotlin-compile-testing`.
Compiles source strings in-process with the wrasse plugin loaded, asserts the
WNode tree and diagnostics are correct.

Uses whatever kotlinc version wrasse is compiled against (currently 2.4.0).
Lives in `wrasse-adapter/src/test` and `compiler-plugin/src/test`.

### Layer 3: All minor versions (on demand)

Same adapter tests, parameterized across Kotlin minor versions (2.0, 2.1, 2.2,
2.3, 2.4). Single test module, one Gradle `Test` task per version with the
appropriate `kotlin-compiler-embeddable` JAR on the classpath.

```
./gradlew testMinors  → runs layer 1 + 2 + 3
```

### Layer 4: All patch versions (pre-release)

Same tests across every published patch version in the supported range.
Version list resolved dynamically from Maven Central at configuration time:

```kotlin
dependencies {
    kotlinVersionProbe("org.jetbrains.kotlin:kotlin-compiler-embeddable:[2.0,3.0)")
}
```

Runs in CI before publishing a new wrasse release.

```
./gradlew testAll     → runs layer 1 + 2 + 3 + 4
```

### Default task mapping

```
./gradlew test        → Layer 1 + 2 (unit + current kotlinc)
./gradlew testMinors  → Layer 1 + 2 + 3
./gradlew testAll     → Layer 1 + 2 + 3 + 4
```
