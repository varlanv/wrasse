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
│     │ internal package     │   Thin kotlinc shells — extend FIR      │
│     │ (FirSyntacticChecker │   classes, extract data, delegate to    │
│     │  FirRestrictedApi...)│   WrassePlugin immediately              │
│     └────────┬────────────┘                                         │
└──────────────┼──────────────────────────────────────────────────────┘
               │
               ▼
     ┌─────────────────────┐
     │ WrassePlugin         │  "main" — owns config, rules, severity.
     │ (outer package)      │  Builds WNode tree via adapter, runs
     │                      │  dispatch table, returns ViolationReports.
     └────────┬────────────┘
               │
               ▼
     ┌─────────────────────┐
     │ Adapter Layer        │  LightTreeAdapter: iterative stack-based
     │ (wrasse-kotlinc-     │  conversion of LighterASTNode → WNode.
     │  adapter)            │  WNodeTypeMapping: IElementType → WNodeType.
     └────────┬────────────┘
               │
               ▼
     ┌─────────────────────┐
     │ Rule Engine          │  SplitRules: dispatch table indexed by
     │ (wrasse-model +     │  WNodeType ordinal. Single tree traversal,
     │  wrasse-rules)      │  O(nodes × avg-matching-rules).
     └─────────────────────┘
```

### Plugin lifecycle

1. **WrasseCommandLineProcessor** (SPI) — parses CLI options (`enabled`, `warnOnly`),
   writes to `CompilerConfiguration`. Runs first.
2. **WrasseCompilerPluginRegistrar** (SPI) — reads config, extracts source roots from
   `CompilerConfiguration`, calls `wrasseMain()` to build `WrassePlugin` with config,
   rules, and global severity. Registers FIR extensions.
3. **WrasseFirExtensionRegistrar** — registers `WrasseFirChecker` and diagnostic containers.
4. **WrasseFirChecker** — instantiated per FIR session (per module). Declares
   `FirSyntacticChecker` and `FirRestrictedApiChecker`, both receiving `WrassePlugin`.
5. **FirSyntacticChecker** — per file: extracts `KtLightSourceElement`, calls
   `plugin.checkFile()`, maps `ViolationReport` results to `reportOn()`.
6. **FirRestrictedApiChecker** — per function call: extracts resolved callee, calls
   `plugin.checkCall()`.

All of 1-6 live in `app/wrasse-kotlinc-plugin`. Items 1-4 plus the FIR checker shells are
in the `internal` subpackage — pure kotlinc glue with zero wrasse logic. `WrassePlugin`,
`wrasseMain()`, `ViolationReport`, and constants live in the outer package.

### Module structure

```
wrasse/
├── libs/
│   ├── wrasse-model/            # WNode, WNodeType, WRule, WFile, WViolation — zero kotlinc deps
│   ├── wrasse-config/           # WrasseConfig, WrasseRuleToggle, WrasseSeverity — depends on wrasse-lang
│   ├── wrasse-rules/            # Rule implementations — depends on wrasse-model + wrasse-config
│   ├── wrasse-kotlinc-adapter/  # LightTreeAdapter, WNodeTypeMapping — depends on kotlinc + wrasse-model
│   ├── wrasse-lang/             # ConfigValueJsonc, FileWalkUp — zero-dep utilities
│   └── wrasse-format/           # (placeholder) formatting pipeline
├── app/
│   └── wrasse-kotlinc-plugin/   # Plugin entry points, WrassePlugin, internal/ kotlinc shells
│       └── compileOnly: kotlin-compiler-embeddable
├── testing/
│   ├── common-test/             # BaseSpec, shared test utilities
│   ├── wrasse-test-harness/     # WrasseTestHarness, FixtureLoader, FixtureParser
│   └── wrasse-kotlinc-plugin-tests/  # Auto-discovery fixture tests
└── doc/
    ├── hld.md                   # This file
    ├── ktlint-rules-catalog.md  # 105 rules
    ├── detekt-rules-catalog.md  # 94 rules
    └── diktat-unique-rules.md   # 42 unique rules
```

`wrasse-model` and `wrasse-rules` have zero dependency on kotlinc.
Rules are testable without a compiler and portable to other hosts
(e.g., a future IntelliJ plugin could use a PSI → WNode adapter instead).

## Intermediate Model (wrasse-model)

### WNode — concrete CST node

```kotlin
class WNode(
    val type: WNodeType,
    val startOffset: Int,
    val endOffset: Int,
    val leafText: CharSequence?,   // non-null only for leaf (token) nodes
    private val sourceText: CharSequence,
) {
    var parent: WNode?
    var children: List<WNode>

    // Navigation
    val firstChild / lastChild / nextSibling / prevSibling
    fun childrenOfType(type) / firstChildOfType(type) / lastChildOfType(type)
    fun findParentOfType(type) / isInsideNodeOfType(type)
    fun descendants() / descendantsOfType(type) / leaves()
    fun nextLeaf() / prevLeaf() / nextCodeLeaf() / prevCodeLeaf()
    fun nextCodeSibling() / prevCodeSibling()

    // Properties
    val isLeaf / isWhitespaceOrComment / isNewline
    val column (lazy) / indent (lazy)
    fun hasModifier(modifier)
}
```

`WNode` is a concrete class, not an interface. The tree is built eagerly by
`LightTreeAdapter` using an iterative stack-based traversal (no recursion).
All nodes share a single `sourceText: CharSequence` backed by the compiler's
char buffer — one reference per file, no copies.

`nextSibling` / `prevSibling` are lazy (computed on first access from `parent.children`).
`column` and `indent` are lazy (scan backward through `sourceText`).

```kotlin
data class WFile(
    val path: String,
    val root: WNode,              // FILE node — the full tree
    val sourceText: CharSequence, // whole file source, backed by compiler's char buffer
)
```

### WRule — sealed rule hierarchy

```kotlin
sealed interface WRule {
    val id: String
}

interface NodeVisitorWRule : WRule {
    val targetTypes: Set<WNodeType>
    fun visit(node: WNode, violations: MutableCollection<WViolation>)
}

interface FileVisitorWRule : WRule {
    fun visit(file: WFile, violations: MutableCollection<WViolation>)
}
```

Rules are split by the information they need:

- **NodeVisitorWRule** — declares which `WNodeType` values it targets. The engine walks
  the tree once and dispatches each node to matching rules via a dispatch table indexed
  by `WNodeType.ordinal`. O(1) lookup per node.
- **FileVisitorWRule** — receives the whole `WFile`. For rules that need file-level context
  (trailing newline, import ordering, file length).

Rules receive a `WrasseRuleToggle` (enabled + exclude globs) at construction time.
Disabled rules are never instantiated — `assembleRules()` filters by `enabled` before
creating rule objects.

### WViolation

```kotlin
class WViolation(
    val ruleId: String,
    val message: String,
    val node: WNode,
)
```

No severity field. Severity is a global plugin setting, not per-violation.

## Config

Walk up directories from source root until a `wrasse.json` (or `wrasse.jsonc`) is found.
Config is loaded once in `WrasseCompilerPluginRegistrar.registerExtensions()` using
source roots from `CompilerConfiguration.javaSourceRoots`. Parsed by a hand-rolled
JSONC parser (`ConfigValueJsonc`) — zero external dependencies.

```json
{
  "$schema": "https://varlanv.github.io/wrasse/schema.json",
  "exclude": ["**/build/**", "**/generated/**"],
  "rules": {
    "no-semicolons": { "enabled": true, "exclude": [] },
    "no-wildcard-imports": { "enabled": true, "exclude": [] },
    "trailing-newline": { "enabled": true, "exclude": [] }
  }
}
```

### Config design decisions

- **No per-rule severity.** A rule is either enabled or disabled. The global `warnOnly`
  CLI flag (`-P plugin:com.varlanv.wrasse:warnOnly=true`) switches all violations to
  warnings for rollout purposes.
- **Exclude-only.** No `include` field. A rule runs on all files unless excluded.
  Include + exclude together create precedence ambiguity.
- **Globs pre-compiled** to `java.nio.file.PathMatcher` at config parse time.
- **Autofix** (future): fixable rules will get an `autofix: true/false` property.
  Baked into schema, only allowed on rules that support it.
- **Nursery** (future): `nursery` block alongside `rules` for explicitly non-stable rules.

### Exclude semantics

- **Global `exclude`:** applies to all rules. Union with per-rule excludes.
- **Per-rule `exclude`:** additional excludes for a specific rule.
- **No `include` field.** A rule runs on all files unless excluded.

## Diagnostics (IDE integration)

Wrasse reports `KtDiagnostic` instances so IntelliJ shows violations inline.
No separate IntelliJ plugin needed for basic error/warning display (requires
"Kotlin External FIR Support" plugin installed in the IDE).

```kotlin
object WrasseErrors : KtDiagnosticsContainer() {
    val WRASSE_ERROR   by error1<KtElement, String>(SourceElementPositioningStrategies.DEFAULT)
    val WRASSE_WARNING by warning1<KtElement, String>(SourceElementPositioningStrategies.DEFAULT)

    // Must stay a function (not property) to avoid cyclic init — see KtDiagnosticsContainer docs
    override fun getRendererFactory() = Renderers
}
```

The `internal` package picks `WRASSE_ERROR` or `WRASSE_WARNING` based on the global
`WrassePlugin.severity` (set from CLI `warnOnly` flag). All violations in a compilation
share the same severity level.

Diagnostic containers are registered via `registerDiagnosticContainers(WrasseErrors)` in
`WrasseFirExtensionRegistrar.configurePlugin()`.

## Novel rules (beyond ktlint/detekt)

These checks are uniquely possible or significantly better in wrasse because of
FIR access and LightTree fidelity.

### Function visual line limit

Enforce a hard limit on visual lines in a function body (e.g., 70 lines).
Count actual rendered lines including blank lines and comments.

### Split compound boolean conditions

Warn when `if`/`when` conditions contain deeply nested `&&`/`||` operators.
Inspired by TigerStyle.

### Split compound assertions

Warn when `require()`, `check()`, `assert()` is called with a compound boolean argument.
`require(a && b)` should be `require(a); require(b)`.

### No recursion

Warn when a function calls itself. Uses FIR resolved callee symbol — no false positives
from name shadowing. Inspired by TigerStyle / NASA's Power of Ten.

### Explicit library defaults

Warn when a function call uses default parameter values for a configurable set of library
functions. Uses FIR resolved parameter defaults. Impossible without type resolution.

### Design implication

All semantic rules share a pattern: match a resolved function call by `CallableId` and
inspect arguments/context. `FirFunctionCallChecker` in the `internal` package handles this,
delegating to `WrassePlugin.checkCall()`.

## Kotlinc APIs wrapped in adapter layer

These are internal to the adapter — rule code never imports any of these.

### LightTree traversal

| kotlinc type | Used for |
|---|---|
| `KtLightSourceElement` | Access `treeStructure` and `lighterASTNode` from FIR `source` |
| `FlyweightCapableTreeStructure<LighterASTNode>` | Walk tree: `root`, `getChildren(node, ref)` |
| `LighterASTNode` | Node interface: `tokenType`, `startOffset`, `endOffset` |
| `LighterASTTokenNode` | Leaf node with `.text` |
| `Ref<Array<LighterASTNode?>>` | Output param for `getChildren` |
| `IElementType` | Node/token type identifier |

### FIR checker registration

| kotlinc type | Used for |
|---|---|
| `CompilerPluginRegistrar` | Plugin entry point |
| `CommandLineProcessor` | CLI options |
| `CompilerConfiguration` | Read plugin options |
| `FirExtensionRegistrar` | Register FIR extensions |
| `FirExtensionRegistrarAdapter` | IDE-safe adapter for registrar |
| `FirAdditionalCheckersExtension` | Declare checkers |
| `FirFileChecker` | Per-file syntactic check |
| `FirFunctionCallChecker` | Per-call semantic check |

### Diagnostics

| kotlinc type | Used for |
|---|---|
| `KtDiagnosticsContainer` | Declare diagnostic factories |
| `DiagnosticReporter` / `reportOn` | Report diagnostics |
| `error1` / `warning1` | Factory DSL functions |

## Comparison with ktlint and detekt

| Aspect | wrasse | ktlint | detekt |
|---|---|---|---|
| Parse overhead | None (rides compiler) | Full PSI parse | Full PSI parse + optional type resolution |
| Tree type | LightTree (read-only, flyweight) | PSI (mutable, heavy) | PSI (mutable, heavy) |
| Rule input | WNode (own model) | ASTNode (kotlinc PSI) | KtElement (kotlinc PSI) |
| Rule dispatch | Dispatch table by node type | Visitor per rule | Visitor per rule |
| Severity | Global (error or warn-only) | Per-rule | Per-rule |
| IDE integration | KtDiagnostic (native) | Separate IntelliJ plugin | Separate IntelliJ plugin |
| Gradle coupling | None (compiler plugin) | Gradle plugin | Gradle plugin |
| Type resolution | FIR (for semantic rules) | None | Analysis API (optional) |
| Build tool support | Any kotlinc host | Gradle, Maven, CLI | Gradle, CLI |

## Build target

**JVM target: Java 8 bytecode.** The plugin JAR loads into the kotlinc daemon's
classloader. That daemon runs on whatever JDK the build tool uses. Every bundled Kotlin
compiler plugin (compose, serialization, all-open, no-arg) targets JVM 8.

**Gradle toolchain: JDK 26.** The build itself uses the latest JDK for compilation.
The `javaTargetVersion` and `javaToolchainVersion` are configured centrally in
`libs.versions.toml`.

**Kotlin version: single JAR, runtime compatibility across a range.** Wrasse compiles
against the latest Kotlin in its supported range (currently 2.4.0). API differences
across versions are handled inside the adapter layer.

## Performance design decisions

### Eager WNode tree — stack-based, no recursion

`LightTreeAdapter` builds the full WNode tree using an explicit `ArrayDeque` stack.
No recursion, no lazy proxies. The `Ref` for `getChildren()` is reused across iterations.
Leaf nodes (`LighterASTTokenNode`) are never pushed onto the stack.

On a 2000-line file (~10K nodes), tree construction is ~1ms. The tree is immutable
after construction — safe to share across rules.

### Source text: one CharSequence per file

`WFile.sourceText` holds the whole file's source as a `CharSequence` backed by the
compiler's existing char buffer. `WNode.leafText` on tokens delegates to the kotlinc
`LighterASTTokenNode.text` — already a slice, not a copy.

### Rule dispatch: array indexed by WNodeType ordinal

`SplitRules` builds an `Array<List<NodeVisitorWRule>>` at startup, indexed by
`WNodeType.ordinal`. Per-node dispatch is an array index lookup — O(1), no hashing.
If 5 out of 100 rules target SEMICOLON, only 5 are invoked per semicolon node.

### Incremental compilation: free filtering

kotlinc only recompiles changed files. Wrasse's FIR checker only fires on files the
compiler visits. On a million-LOC codebase where one file changes, wrasse processes
one file.

## Test strategy

### Fixture-based auto-discovery harness

Tests are fixture files on disk, auto-discovered at runtime. Adding a test = adding
a `.kt` file to the right directory. No spec code to touch.

```
fixtures/
├── wrasse.json                  # Base config (all rules disabled)
├── no-semicolons/
│   ├── wrasse.json              # Override: enable no-semicolons
│   ├── unnecessary-trailing.kt  # // expect-error 3:10 no-semicolons "..."
│   └── clean.kt                 # // expect-clean
├── no-wildcard-imports/
│   ├── wrasse.json
│   ├── wildcard-flagged.kt
│   └── explicit-ok.kt
└── trailing-newline/
    ├── wrasse.json
    ├── missing-newline.kt
    └── has-newline.kt           # // fixture-option: trailing-newline
```

**Config inheritance:** root `wrasse.json` has all rules disabled. Per-rule subdirectory
overrides only what changes (deep merge via `org.json` — test-only dependency).

**`FixtureLoader.load(dir)`** scans once, reads everything into memory, returns
`List<Fixture>`. Each `Fixture` holds ruleId, fixtureId, merged config, source,
expectations.

**`FixtureParser`** extracts directives from fixture files:
- `// expect-error <line>:<col> <ruleId> "<message>"` — expected violation
- `// expect-clean` — no violations expected
- `// fixture-option: trailing-newline` — append `\n` to stripped source

**Fixture spec** (`WrasseFixtureSpec`): one `ShouldSpec` that iterates all fixtures.
Test names: `should handle spec - no-semicolons -> clean`.

### Kotlin version matrix (planned)

Four layers:
1. **Unit tests** — rule logic against WNode trees, no compiler
2. **Current kotlinc** — fixture tests with wrasse plugin loaded (current: 2.4.0)
3. **Minor versions** — same fixtures across Kotlin 2.0-2.4, one test task per version
4. **All patches** — every published patch in supported range, pre-release only

Fixtures dir is passed via Gradle system property `wrasse.fixtures.dir`. Version-agnostic
by design — same fixtures, different `kotlin-compiler-embeddable` on classpath.

## Milestones

### Milestone 0: Proof of concept ✓

Compiler plugin loads, FIR checker fires, `KtDiagnostic` reported with file/line/column.

### Milestone 1: Foundation ✓

- WNode concrete class (eager, stack-based build)
- LightTreeAdapter (iterative, no recursion)
- WNodeTypeMapping (~160 entries)
- Hand-rolled JSONC config parser (zero deps)
- WRule sealed hierarchy (NodeVisitorWRule + FileVisitorWRule)
- Dispatch table by WNodeType ordinal
- Global severity with `warnOnly` CLI flag
- Config loaded from source roots at registration time
- Plugin structure: `internal` package (kotlinc shells) + outer package (wrasse logic)
- 3 rules: no-semicolons, no-wildcard-imports, trailing-newline
- Fixture-based auto-discovery test harness
- Test config inheritance (base + per-rule override, deep merge)

### Milestone 2: Rule coverage

- Port ktlint rules (complexity 1 and 2 first)
- Port detekt rules (ones not needing Analysis API)
- Port unique diktat rules worth keeping
- JSON Schema for config autocomplete

### Milestone 3: Formatting

- Read-only CST → reconstructed source text
- Temp file output pipeline
- `autofix` property on fixable rules
- Format-only rules (indentation, spacing, wrapping)

### Milestone 4: Semantic rules

- Restricted API (configurable FQN list)
- No recursion
- Split compound assertions
- Explicit library defaults
- Function visual line limit

### Milestone 5: Hardening

- Kotlin version matrix testing (layers 3 + 4)
- Fuzz testing on real-world Kotlin projects
- Performance benchmarks vs ktlint + detekt
- Nursery rules block
- Publish to Maven Central

## Design decisions

### Decided

- **WNode:** concrete class, not interface. Eager tree build, no lazy proxies.
- **Rule dispatch:** sealed hierarchy (NodeVisitor + FileVisitor), dispatch table by node type ordinal.
- **Severity:** global only (ERROR default, `warnOnly` CLI flag). No per-rule severity in config.
- **Config:** `rules` block only. No `lint`/`format` split. Fixable rules get `autofix` property.
- **Module boundaries:** `libs/` (model, config, rules, adapter, lang), `app/` (plugin), `testing/`.
- **Plugin structure:** `internal` package for kotlinc shells, outer package for wrasse logic.
- **Tree traversal:** iterative stack-based (ArrayDeque), no recursion.
- **Config loading:** at registration time from `CompilerConfiguration.javaSourceRoots`, not per-file.
- **Test harness:** fixture auto-discovery, config inheritance with deep merge.
- **`$schema` hosting:** GitHub Pages.
- **EditorConfig support:** no. Migration CLI flag instead.
