# Wrasse — High-Level Design

A Kotlin linter and formatter that rides the compiler's own parse instead of re-parsing
the world. One tool to replace ktlint + detekt, faster, with type-resolution-powered
fixes (notably import optimization) that resolution-free formatters cannot do.

> This document is **architecture only**. For the phased plan see [roadmap.md](roadmap.md);
> for the rationale behind specific choices (and what was rejected) see [decisions.md](decisions.md).

## Why it exists

1. **Zero parse overhead for linting.** ktlint and detekt each embed `kotlin-compiler-embeddable`
   and parse every source file independently — on large projects that is tens of seconds of
   redundant work on top of compilation. Wrasse hooks the compiler's FIR analysis phase and reads
   the LightTree kotlinc already built. No second parse.

2. **No build-tool plugin coupling.** Gradle's API churn (configuration cache, isolated projects,
   build-cache changes) forces ktlint/detekt maintainers into constant compatibility work. Wrasse
   ships as a compiler-plugin JAR added to `kotlinCompilerPluginClasspath`, with options passed via
   `CommandLineProcessor`. Works under Gradle, Amper, Bazel, or raw kotlinc.

3. **Decoupled from kotlinc internals.** Rules operate on an intermediate model wrasse owns. An
   adapter translates kotlinc's LightTree (and, for resolution rules, FIR) into that model. When
   kotlinc APIs shift — and K2 APIs do — only the adapter moves; rules stay put.

## The two-host model

Linting is a **read-only** activity; formatting and fixing are **writes**. A compiler plugin
observing a live compile cannot rewrite the files being compiled. So wrasse is one rule library
behind two hosts:

| | Host A — compiler plugin | Host B — standalone tool |
|---|---|---|
| Mode | Read-only observer | Read-write |
| Runs | During a real `kotlinc` compile | On demand (CLI / format task / pre-commit) |
| Output | `KtDiagnostic`s (IDE + build) | Rewritten files / patches / reports |
| Parse cost | None (rides the compile) | Its own parse (LightTree, standalone) |
| Has FIR resolution | Yes (intrinsic to the compile) | Only if it runs its own frontend |

Both build the same `WNode` tree and run the same rules. The split is the spine of the design:
the read-only lint path is the free, always-on surface; the write path is a separate, explicitly
invoked tool.

## Pipeline

```
┌─ kotlinc ─────────────────────────────────────────────────────────┐
│  Source → [LightTree] → FIR → IR → Bytecode                        │
│               │           │                                        │
│               ▼           ▼                                        │
│      internal/ shells  (FirFileChecker, FirFunctionCallChecker)    │
│      thin kotlinc glue, extract data, delegate out immediately     │
└──────────────────────────────┬─────────────────────────────────────┘
                               ▼
                       WrassePlugin            owns config + rules; builds WNode
                               │               via adapter; runs dispatch; returns reports
                               ▼
                       Adapter layer           LightTreeAdapter: LighterASTNode → WNode
                               │               WNodeTypeMapping: IElementType → WNodeType
                               ▼
                       Rule engine             SplitRules: dispatch table by WNodeType ordinal,
                                               single traversal
```

The `internal/` package is pure kotlinc glue (FIR shells, registrars, command-line processor,
diagnostics container) and holds zero wrasse logic. `WrassePlugin`, `wrasseMain()`, and the
report types live in the outer package. Everything below the shells is kotlinc-free.

## Intermediate model

### WNode — concrete CST node

A concrete class (not an interface), built eagerly by `LightTreeAdapter` with an explicit stack
(no recursion). Every node — whitespace, comments, punctuation, keywords — is preserved: this is a
concrete syntax tree, not an AST. All nodes in a file share one `sourceText: CharSequence` backed
by the compiler's char buffer; leaf text delegates to kotlinc's token slice. No copies.

```
class WNode(type, startOffset, endOffset, leafText, sourceText)
  parent / childIndex / children            (set during construction)
  firstChild / lastChild / nextSibling / prevSibling   (siblings lazy)
  childrenOfType / firstChildOfType / lastChildOfType
  findParentOfType / isInsideNodeOfType
  descendants / descendantsOfType / leaves
  nextLeaf / prevLeaf / nextCodeLeaf / prevCodeLeaf / nextCodeSibling / prevCodeSibling
  isLeaf / isWhitespaceOrComment / isNewline
  column / indent          (lazy; scan backward through sourceText)
  hasModifier(modifier)
```

```
WFile(path, root: WNode /* FILE node */, sourceText)
```

### WRule — sealed hierarchy

Rules are constructed in two steps: an `WUninitializedRule` declares its `id`, then `initRule(config)`
produces a configured rule instance bound to its `WrasseRuleConfig`.

```
interface WUninitializedRule {
    val id: String
    fun initRule(config: WrasseRuleConfig): WRule
}

sealed interface WRule {
    val id: String
    val config: WrasseRuleConfig
}

interface WNodeRule : WRule {               // syntactic, node-targeted
    val targetTypes: Set<WNodeType>
    fun visit(node: WNode, reporter: WReporter)
}

interface WFileRule : WRule {               // syntactic, whole-file
    fun visit(file: WFile, reporter: WReporter)
}

interface SemanticWRule : WRule { ... }     // PLANNED — receives a resolution facade
                                            // alongside the WNode (see below)
```

- **WNodeRule** declares the `WNodeType`s it targets; the engine walks the tree once and
  dispatches each node to matching rules via an array indexed by `WNodeType.ordinal` (O(1), no
  hashing). If 5 of 100 rules target `SEMICOLON`, only 5 fire per semicolon.
- **WFileRule** gets the whole `WFile` (trailing newline, import ordering, file length).
- **SemanticWRule** (planned) is the resolution-aware family — see *Resolution*.

Rules report violations through `WReporter`, passing themselves alongside the violation so the
reporter can read `rule.config.effectiveLevel` to pick the diagnostic severity:

```
WViolation(ruleId, message, node)

interface WReporter {
    fun report(violation: WViolation, rule: WRule)
}
```

## Inbound vs outbound — what the architecture can and cannot do cheaply

This frame decides what is feasible.

- **Inbound** — *what does this file depend on, resolved*: what `Foo` refers to, its type, what
  `foo.bar.*` exposes, whether a call resolves to itself. The compiler hands this to you per file,
  and it **survives incremental compilation** — a recompiled file's dependencies are available as
  compiled metadata even when their source isn't recompiled.
- **Outbound** — *who depends on this file's symbols*: dead code, find-usages, API-surface,
  architecture/cycles. Needs the whole program, an end-of-compilation aggregation, and **breaks
  under incremental** (a changed file recompiles only itself plus its dirty set; the plugin never
  sees unchanged referencing files).

Wrasse's flagship work — import optimization, type-aware single-file checks — is **inbound**. It
ships without any whole-program machinery and works in the incremental inner loop. Outbound rules
are a separate, deferred tier (see [roadmap.md](roadmap.md)); if ever built they live in full
builds only.

## Resolution and the moat

Riding FIR gives resolution against the full module + classpath. That enables IDE-grade fixes that
resolution-free tools structurally cannot do — chiefly **import optimization**: expanding
`import foo.bar.*` into explicit imports, and removing genuinely-unused imports. ktlint can only
flag wildcards; google-java-format can't expand them and removes unused only heuristically; the
one tool that does it correctly (IntelliJ) is the one with resolution. Wrasse is in IntelliJ's
position, at build time.

The model gap today is that `WNode`/`WFile` are purely syntactic — no types or symbols. The
`checkCall(...)` hook on `WrassePlugin` is a stubbed, separate FIR entry point, not yet unified
into `WRule`. Closing the gap is the `SemanticWRule` family plus a resolution facade. The hard
part is the **adapter**: correlating a LightTree-built `WNode` with its FIR element (by offset, or
by building the semantic view from FIR) — not the visitor shape.

## Fix application

Fixes split by whether they need resolution:

- **Syntactic fixes** (formatting, semicolons, trailing newline) → Host B writes files directly:
  standalone parse → `WNode` → transform → write. Cheap; no resolution.
- **Semantic fixes** (import expansion, unused-import removal) need resolution, which exists only
  during the read-only compile. They are emitted as an **edit-list**, then applied by a dumb
  patcher:
  - Exact, offset-based edits: `(file, startOffset, endOffset, replacement, sourceHash)`. Not
    fuzzy unified/IntelliJ diff (git diff is an optional *export* for review, never the storage).
  - **One patch file per compile task / module** in its build dir (e.g.
    `build/wrasse/autofix.patch`) — never one global file, because modules compile in parallel
    processes. Apply globs `**/build/wrasse/autofix.patch` and merges.
  - **Idempotent by content hash:** apply skips any edit whose recorded `sourceHash` no longer
    matches the file. A stale or undeleted patch is a no-op; after a successful fix the next
    compile emits an empty patch (self-cleaning).
  - Mid-write safety: temp file + atomic rename. Within a file: edits are non-overlapping, applied
    in descending offset order, with a loud failure on overlap. Flush in bounded batches so the
    compiler daemon's heap is never asked to hold a whole module.
  - Gated behind a `wrasse.fix` flag; apply is an **explicit** step, never auto-run in a normal
    build.

Resolution for the edit-list is free when riding a build you're running anyway; a standalone
resolving pass is the no-build (pre-commit) fallback.

Formatting is a *terminal, whole-file* stage (rebuild layout from a doc-IR, single pass,
idempotent), so it composes after any structural fix without convergence loops — unlike the
multi-pass fixpoint ktlint needs from many interacting local rewrites.

## Config

Discovered by walking up from the source root to the nearest `wrasse.json` / `wrasse.jsonc`,
parsed by a hand-rolled zero-dependency JSONC reader. Loaded once at registration time from
`CompilerConfiguration.javaSourceRoots`.

- **Effective config via `extends`** — a config may extend a base; scalars override, `exclude`
  lists union. Replaces the old "every rule must be listed" mandate. A rule absent from the
  effective config is off/inherited, not an error. Malformed config still fails fast.
- **`level: off | warn | error`** per rule — a single tri-state axis (not separate enable +
  severity). A global `warnOnly` CLI flag layers on top as a blanket error→warn downgrade.
- **Exclude-only**, globs precompiled to `PathMatcher`. Global `exclude` unions with per-rule
  `exclude`. No `include` (avoids precedence ambiguity).
- **`formatting-` key prefix** groups formatting rules in the single `rules` block (no separate
  lint/format sections). `formatting-opinionated` is a whole-file formatter; enabling it errors if
  any other purely-formatting rule is also set.
- **Suppression** via `@Suppress("rule-id")` only (expression and declaration scope) — no comment
  directives, no baseline.

## Diagnostics (IDE integration)

Violations are reported as `KtDiagnostic`s so IntelliJ shows them inline (with the "Kotlin External
FIR Support" plugin). `WrasseErrors` declares `WRASSE_ERROR` and `WRASSE_WARNING` factories; the
reporter picks one per violation from that rule's configured `level`.

## Performance design

- **LightTree, not PSI.** Both ktlint and detekt build PSI (heavy). Wrasse reads the LightTree
  (flyweight) the compiler already built. The standalone host (B) builds LightTree itself —
  expected to be lighter than PSI, but the **load-bearing unknown** is whether the LightTree parser
  can run without the heavy `KotlinCoreEnvironment` startup. Benchmark this first (see roadmap).
- **Dispatch table** by `WNodeType.ordinal` — array index, no hashing, one traversal.
- **Eager, stack-based tree build** — no recursion, no lazy proxies; the `getChildren` `Ref` is
  reused; leaf tokens never hit the stack.
- **Incremental compilation is free filtering for lint** — kotlinc only recompiles changed files,
  so the lint checker only fires on what changed. (This is exactly why outbound rules don't fit the
  incremental path — see *Inbound vs outbound*.)
- **Honest perf claims:** the wins are (a) lint rides the compile for free, (b) one parse for
  lint+format vs running two or three separate tools, (c) single-pass formatting vs ktlint's
  multi-pass. We do **not** claim to beat ktfmt's pure-format throughput head-to-head.

## Build target

- **JVM 8 bytecode** for the plugin JAR (loads into the kotlinc daemon's classloader, like every
  bundled compiler plugin).
- **Single JAR, runtime compatibility across a Kotlin range.** Compiled against the latest
  supported Kotlin (currently 2.4); cross-version API differences are absorbed by the adapter and
  by version-specific registrar shells selected at runtime (`k20` / `k22`).
- Gradle toolchain uses the latest JDK; target/toolchain versions are centralized in
  `libs.versions.toml`.

## Module structure

```
libs/
  wrasse-model/            WNode, WNodeType, WRule, WFile, WViolation, WConfig, SplitRules — depends on wrasse-lang, no kotlinc deps
  wrasse-rules/            rule implementations — depends on wrasse-model
  wrasse-kotlinc-adapter/  LightTreeAdapter, WNodeTypeMapping — depends on kotlinc + wrasse-model
  wrasse-lang/             ConfigValue (JSONC), FileWalkUp — zero-dep utilities
  wrasse-format/           (planned) formatting pipeline
app/
  wrasse-kotlinc-plugin/   entry points, WrassePlugin, internal/ kotlinc shells (compileOnly kotlinc)
  wrasse-kotlinc-internal-k20 / -k22   version-specific registrar shells
testing/
  common-test/             BaseSpec, shared utilities
  wrasse-test-harness/     fixture loader/parser, test harness
  wrasse-kotlinc-plugin-tests-2-1-x … -2-4-x   per-version fixture runs
```

`wrasse-model` and `wrasse-rules` have zero dependency on kotlinc — rules are testable without a
compiler and portable to other hosts (e.g. a future PSI → WNode adapter for an IntelliJ plugin).

## Test strategy

- **Fixture auto-discovery.** Tests are `.kt` files on disk; adding a test = adding a file.
  `// expect-error <line>:<col> <ruleId> "<message>"`, `// expect-clean`, and `// fixture-option:`
  directives drive expectations. Config inheritance: a base `wrasse.json` (all off) plus per-rule
  overrides.
- **Kotlin version matrix.** The same fixtures run against multiple `kotlin-compiler-embeddable`
  versions (currently 2.1–2.4, with per-patch coverage planned) via a Gradle system property —
  version-agnostic by construction. This is the primary mitigation against FIR API instability.

## Kotlinc surface (adapter-only)

Rule code never imports these; they are confined to the adapter and the `internal/` shells.

- LightTree: `KtLightSourceElement`, `FlyweightCapableTreeStructure<LighterASTNode>`,
  `LighterASTNode`, `LighterASTTokenNode`, `IElementType`.
- FIR registration: `CompilerPluginRegistrar`, `CommandLineProcessor`, `CompilerConfiguration`,
  `FirExtensionRegistrar(Adapter)`, `FirAdditionalCheckersExtension`, `FirFileChecker`,
  `FirFunctionCallChecker`.
- Diagnostics: `KtDiagnosticsContainer`, `DiagnosticReporter` / `reportOn`, `error1` / `warning1`.
