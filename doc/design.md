# Wrasse — Design Document

**The single source of truth for wrasse's architecture, decisions, and plan.**
Finalized 2026-07-19. Supersedes and replaces the former `hld.md`, `decisions.md`, `roadmap.md`,
`architecture-before.md`, `architecture-after.md`, and `rule-port-plan.md`.

Reference data (not design, kept separate):
[autoformat-scope.md](autoformat-scope.md) — all 255 catalog rules classified into wrasse buckets;
[ktlint-rules-catalog.md](ktlint-rules-catalog.md), [detekt-rules-catalog.md](detekt-rules-catalog.md),
[diktat-unique-rules.md](diktat-unique-rules.md) — annotated source catalogs.

How to read this document:
- **§1–2** — what wrasse is and the ideas the whole design rests on.
- **§3** — what is implemented today (the honest as-built state).
- **§4–8** — the target architecture: rule model, fix/format pipeline, rule scope, config, resolution.
- **§9–11** — performance, version matrix, testing.
- **§12** — the decision log (D1–D21, rejections). **Read before reopening any settled question.**
- **§13** — the phased roadmap, including the pre-coding bug list.
- **§14** — known issues and tech debt.

---

## 1. What wrasse is and why it exists

Wrasse is a Kotlin linter **and** formatter that rides the compiler's own parse instead of
re-parsing the world. One tool to replace ktlint + detekt, much faster in real builds, with
resolution-powered fixes — chiefly **import optimization** — that resolution-free tools
structurally cannot do.

**Product focus (locked):** drop-in replacement for ktlint + detekt, plus the import-optimization
moat. Do one thing very well. Anything not in the roadmap (§13) is parked or dropped and must not
creep into earlier work.

Three reasons it exists:

1. **Zero parse overhead for linting.** ktlint and detekt each embed `kotlin-compiler-embeddable`
   and parse every source file independently — on large projects, tens of seconds of redundant
   work on top of compilation (detekt with type resolution effectively pays a second compile).
   Wrasse hooks kotlinc's FIR analysis phase and reads the LightTree the compiler already built.
   No second parse, ever.

2. **No build-tool plugin coupling.** Gradle's API churn (configuration cache, isolated projects)
   forces ktlint/detekt maintainers into constant compatibility work. Wrasse ships as a
   compiler-plugin JAR on `kotlinCompilerPluginClasspath` with options via `CommandLineProcessor`.
   Works under Gradle, Amper, Bazel, or raw kotlinc — the plugin is host-agnostic; only thin
   example wiring is build-tool-specific.

3. **The moat is resolution, not speed.** Riding FIR gives resolution against the full module +
   classpath — IDE-grade fixes at build time: expanding `import foo.*` into explicit imports,
   removing *genuinely* unused imports. ktlint can only flag wildcards and its unused-import rule
   has known false positives (no resolution); the one tool that does this correctly (IntelliJ) is
   the one with resolution. Wrasse is in IntelliJ's position, at build time. Whitespace formatting
   is table stakes; the differentiation budget goes to resolution-powered fixes.

**Honest performance positioning** (see §9 for the full story): wrasse's lint+format is *nearly
free inside builds you already run*. It decisively beats ktlint+detekt wall-clock in any real
build. It does **not** claim to beat ktfmt's standalone format latency — that scenario (IDE
format-on-save, cold format-only runs) is explicitly conceded (D16).

---

## 2. Core architectural ideas

Four ideas carry the entire design. Everything else is elaboration.

### 2.1 Single pass means single parse

The expensive, specifically-rejected operation is the *redundant full parse* other tools pay.
"Single pass" does **not** mean "touch every byte exactly once" — a second in-memory pass over
data the one walk already produced (no I/O, no parser) is categorically cheaper and is allowed.
What is rejected is the **iterate-to-a-fixed-point** model (ktlint, ESLint `--fix`, RuboCop):
re-running the whole rule set against the whole file until nothing changes.

### 2.2 One decision-maker vs. many independent rewriters

The fixed-point loop is not a performance bug in those tools — it is the *direct consequence* of
their architecture: many independently-authored rules each rewriting shared text, where each
rule's output is another rule's input. The only reconciliation available to an engine that treats
rules as black boxes is "re-run until stable."

The real dichotomy is **not** "opinionated vs. configurable" (clang-format has 100+ options and is
strictly single-pass, because every option is an *input to one engine*, not another engine). The
dichotomy is: one brain making each decision once, in one place — versus many brains editing a
shared document. Wrasse uses each model where it is correct:

| Job | Model | Why it's safe |
|---|---|---|
| Lint (reporting) | independent rules | reporting cannot conflict — no loop risk exists |
| Targeted fixes | independent rules + born-clean discipline + fusion + EditPlan (§5) | small edits, conflicts prevented by design and enforced by the harness |
| Whole-file formatting | one printer with style parameters (§5.3) | every layout decision made once, together |

### 2.3 Two hosts: read-only lint, explicit write

A compiler plugin observing a live compile cannot rewrite the files being compiled. So wrasse is
one rule library behind two hosts:

| | Host A — compiler plugin | Host B — patch applier |
|---|---|---|
| Mode | Read-only observer | Read-write |
| Runs | During a real `kotlinc` compile | After that compile, via `wrasseApply` (e.g. `./gradlew wrasseFix`) |
| Output | `KtDiagnostic`s (IDE + build) | Rewritten files |
| Parse cost | None (rides the compile) | None — pure byte-splicing against the patch Host A wrote |
| Has FIR resolution | Yes (intrinsic to the compile) | N/A — resolution already happened in Host A's compile |

Host A walks the LightTree (SAX-style, no tree of its own) and runs every rule including deciding
fixes; Host B never parses or runs a rule — it only writes what Host A already decided. There is
no standalone, build-independent entry point (D16): every real usage — `wrasseFix`, CI `--check`,
a pre-commit hook — runs through a real build-triggered compile with the plugin attached.

### 2.4 Inbound vs. outbound — what is cheaply possible

- **Inbound** — *what does this file depend on, resolved*: what `Foo` refers to, its type, what
  `foo.bar.*` exposes. The compiler hands this to you per file, and it **survives incremental
  compilation** (dependencies are available as compiled metadata even when their source isn't
  recompiled).
- **Outbound** — *who depends on this file's symbols*: dead code, find-usages, architecture
  rules. Needs the whole program and **breaks under incremental** (the plugin never sees
  unchanged referencing files).

All flagship work — import optimization, type-aware single-file checks — is **inbound** and ships
without whole-program machinery. Outbound is parked (D15); if ever built it is full-build-only.

### Pipeline overview

```
┌─ kotlinc ─────────────────────────────────────────────────────────┐
│  Source → [LightTree] → FIR → IR → Bytecode                        │
│               │           │                                        │
│               ▼           ▼                                        │
│      internal/ shells  (FirFileChecker, FirFunctionCallChecker)    │
│      thin kotlinc glue, extract data, delegate out immediately     │
└──────────────────────────────┬─────────────────────────────────────┘
                               ▼
                       WrassePlugin            owns config + rules; runs the SAX walk via
                               │               the adapter; returns reports + edits
                               ▼
                       Adapter layer           LightTreeStreamAdapter: LightTree → walk events
                               │               WNodeTypeMapping: IElementType → WNodeType
                               ▼
                       Rule engine             StreamDispatch: dispatch by WNodeType ordinal,
                                               single traversal, no tree built
                               ├─→ lint diagnostics (always)
                               ├─→ EditPlan → patch file          (fix mode, §5.2)
                               └─→ DocBuilder → Layout → patch    (format mode, §5.3, Phase C)
```

The `internal/` package is pure kotlinc glue (FIR shells, registrars, command-line processor,
diagnostics container) and holds zero wrasse logic. Everything below the shells is kotlinc-free.

### Module layout

```
libs/
  wrasse-model/            WContext, WNodeStack, WNodeType, WRule, StreamDispatch, WConfig,
                           ChildBuffer, WReporter, ViolationReport — depends on wrasse-lang, no kotlinc
  wrasse-rules/            rule implementations — depends only on wrasse-model (+ wrasse-lang for WEdit)
  wrasse-kotlinc-adapter/  LightTreeStreamAdapter, WNodeTypeMapping — the ONLY place kotlinc
                           LightTree types appear outside app/*/internal/
  wrasse-lang/             zero-dep utilities: JSONC config reader, FileWalkUp, WEdit/patch I/O
  wrasse-format/           (Phase C) Doc IR + DocBuilder + Layout — the opinionated printer (D17);
                           zero kotlinc deps; rides Host A's compile, no standalone entry point (D16)
app/
  wrasse-kotlinc-plugin/   WrassePlugin (dispatch entry), wrasseMain(); internal/ = pure kotlinc glue
  wrasse-kotlinc-internal-k20 / -k22   version-specific FIR registrar shells, selected at runtime
testing/
  common-test/                          BaseSpec (kotest ShouldSpec base), useTempDir
  wrasse-test-harness/                  FixtureLoader/Parser, WrasseTestHarness, fixtures/ resources
  wrasse-kotlinc-plugin-tests-base/     WrasseFixtureSpec — one dynamic test per fixture
  wrasse-kotlinc-plugin-tests-2-{1,2,3,4}-x/   thin subclasses running the base spec per Kotlin minor
```

`wrasse-model`, `wrasse-rules`, and `wrasse-format` have **zero dependency on kotlinc** — rules
are unit-testable without a compiler and portable to other hosts (e.g. a future PSI-backed adapter
for an IntelliJ plugin). Rule code must never import kotlinc/LightTree types; those are confined
to `wrasse-kotlinc-adapter` and `app/*/internal/`.

---

## 3. Current state (as-built, pre-Phase-A.5)

What actually runs today — the validated end-to-end skeleton, deliberately kept small (3 rules)
so the whole architecture could be tested before committing to a 200+ rule catalog:

- **SAX-style single-pass rule engine** (§4) — the original two-pass design (build a `WNode`
  tree, then traverse) was deleted; no tree is built, rules receive events off one walk.
- **3 rules shipped:** `no-semicolons` (`WStreamRule`, deferred forward-lookup, autofix),
  `no-wildcard-imports` (`WNodeRule`, flag-only — the fix is the Phase B.3 moat),
  `trailing-newline` (`WFileRule`, autofix).
- **MVP offset-patch autofix pipeline working end-to-end:** rules attach `WEdit`s to reports →
  `WrassePlugin.checkFile` collects them → with `wrasse.fix=true` writes
  `build/wrasse/wrasse-fixes.txt` per module (file + SHA-256 + descending-offset edits) →
  `wrasseApply` (`WPatchApplier`) hash-checks, overlap-checks, applies via temp file + atomic
  rename. `./gradlew wrasseFix` wires it together; the repo lints itself (`wrasseLint`).
- **Config:** `wrasse.json`/`.jsonc` discovered by walking up from source roots; hand-rolled
  zero-dep JSONC reader; `extends` inheritance (child overrides base, absent rule = off, malformed
  fails fast, circular chains caught); per-rule `level: off|warn|error`; global `warnOnly` CLI
  downgrade; `wrasse-schema.json` for editor autocomplete.
- **Diagnostics:** violations reported as `KtDiagnostic`s (`WRASSE_ERROR`/`WRASSE_WARNING`
  factories, severity from `rule.config.effectiveLevel`), visible inline in IntelliJ with the
  "Kotlin External FIR Support" plugin.
- **Version matrix:** single JAR compiled against Kotlin 2.4, JVM 8 bytecode, runtime-selected
  registrar shells (`k20`/`k22`); the same fixture set replays against Kotlin 2.1–2.4.

Known divergences between this state and the target are catalogued in §13 (Phase A.5) and §14 —
including several real bugs found in the 2026-07 design review. **Do not build Phase B on top of
the current state without completing Phase A.5.**

---

## 4. Rule model — SAX events, no tree

No node objects are built. The framework walks kotlinc's LightTree once
(`LightTreeStreamAdapter.walk`) and emits events directly to rules.

### Event surface (all in `wrasse-model`, zero kotlinc deps)

- **`WContext`** — one mutable struct per file, reused across all events (rules must read during
  their callback and never store a reference). Holds: current event type/offsets/leaf text; the
  ancestor stack (`WNodeStack` — parallel primitive `IntArray`s, O(1) `hasAncestor` via a counts
  array indexed by `WNodeType.ordinal`); previous-leaf data; framework-tracked
  `lastNewlineOffset` making `column()` an O(1) subtraction; and (target, D18) a zero-copy
  `CharSequence` view of the compiler's source buffer for span reads.
- **`WNodeType`** — enum with cached `VALUES` array and `SIZE` constant (avoids the
  `entries`-allocation-per-call gotcha).
- **`ChildBuffer`** — parallel primitive arrays recording direct children between enter/exit for
  buffered rules.

### The `WRule` sealed hierarchy — four leaf kinds

Dispatched by `StreamDispatch` off the one walk, via arrays indexed by `WNodeType.ordinal`
(O(1), no hashing — if 5 of 100 rules target `SEMICOLON`, only 5 fire per semicolon):

- **`WLeafRule`** — fires on leaf tokens whose type is in `targetTypes`. The common case
  (comment-spacing, naming, nullable-type-spacing class of rules).
- **`WNodeRule`** — enter/exit on interior nodes by `targetTypes`; `enterNode` returning `true`
  opts into `onChildLeaf` for every descendant leaf plus a matching `exitNode`.
  **`WBufferedNodeRule`** extends it: the framework buffers direct children into a `ChildBuffer`
  for exit-time inspection (argument lists, wrapping-shaped rules, future fused engines).
- **`WStreamRule`** — every leaf unfiltered, plus node boundaries. The most expensive kind — keep
  the count small. For cross-cutting concerns (`no-semicolons`' deferred forward-lookup).
- **`WFileRule`** — once after the walk with the final `WContext` (trailing-newline,
  max-line-length via offset tracking).

Lifecycle per file: `beforeFile` on every rule → recursive walk (leaf events: matching leaf rules
→ all stream rules → active node-rules' `onChildLeaf` → newline tracking → prevLeaf update;
interior: stream `enterNode` → matching node rules' `enterNode` → push ancestors → children →
pop → `exitNode`s) → `afterFile` on every rule → `WFileRule.visit`.

**Construction and state (D20):** two-phase — `WUninitializedRule` declares an `id`;
`initRule(config)` produces a configured instance, **per file**, so per-file mutable state
(`pendingStart` etc.) is race-free if kotlinc ever parallelizes file checkers. The
`StreamDispatch` *structure* (which rule targets which types) is prebuilt once; only instances
are fresh. `EditPlan` and `WContext` share the per-file lifecycle.

**Reporting:** `WReporter.report(ruleId, message, startOffset, endOffset, rule, edits)` — raw
offsets, no node objects; the reporter reads `rule.config.effectiveLevel` for severity; a
non-empty `edits: List<WEdit>` marks the violation autocorrectable.

---

## 5. Fix & format architecture (the final design)

Linting (reporting) is untouched by everything in this section — reporting rules cannot conflict.
There are exactly **two write paths**, both decided by Host A during the same compile-riding walk.

### 5.1 Why there is no fixed-point loop — the three mechanisms

Every decision is made exactly once, in exactly one place:

1. **Born clean (discipline).** Any text a rule inserts must already satisfy every enabled rule
   that could apply to it. A rule wrapping an if-body in braces computes correct indentation
   itself (`depth × indentWidth` — depth is on `WNodeStack`, indent width is a style parameter).
   No rule emits sloppy text hoping a later rule cleans it up — that hope is precisely why ktlint
   must iterate.
2. **Fighting rules get fused (pattern).** When several user-facing rule IDs would rewrite the
   same region (unused-import removal + star expansion + FQN→import + import ordering), they are
   **one internal engine** (`ImportEngine`) reading several config keys and writing the region
   once. Users see separate rules in `wrasse.json`; internally there is one decision-maker.
   `WBufferedNodeRule` is the foundation primitive for engines.
3. **Nesting rewrites compose via the EditPlan (mechanism, §5.2).** Post-order traversal
   guarantees inner regions are decided before outer ones; outer rewriters consume inner edits.

And one **enforcement** that keeps all three honest as rules accumulate — the idempotence
invariant (D19), built into the harness *before* Phase B porting starts:

> For every fixture with autofix expectations: apply the emitted edits → re-run the full enabled
> rule set on the result → assert **zero** diagnostics → assert a second fix pass emits **zero**
> edits (`fix(fix(x)) == fix(x)`). Phase C adds `format(format(x)) == format(x)` and
> "formatted fixtures re-format to themselves."

This converts "we don't iterate" from an architectural hope into a CI-checked property of every
rule ever ported. It is the single most load-bearing piece of test infrastructure in the project.

### 5.2 Path 1 — Targeted fixes (edit list)

Small, surgical, behavior-preserving edits: the **T** bucket (~15 fixes: brace insertion,
`modifier-order`, redundant-syntax deletions — see §6) plus the **S** bucket (ImportEngine).
Every fix is individually toggleable and off by default (D9).

**EditPlan** (per file, replaces the flat `collectedEdits` list):

- Edits kept sorted by span.
- **Post-order composition:** the walk exits children before parents, so when an engine rewrites
  a region at `exitNode`, every edit inside its span already exists. It calls
  `editPlan.takeEditsIn(start, end)`, applies those edits textually to its copy of the original
  span (descending offsets, purely local), performs its own transform (wrap, re-indent each line —
  a pure text operation), and emits **one composed edit** for the outer span. Consumed inner edits
  leave the plan. This is Wadler-style indent-threading done SAX-style: no rule ordering, no
  priority system, no fact-passing protocol, no observing another rule's rendered text.
- **Invariants (enforced, loud):**
  - After the walk, remaining edits must be pairwise disjoint. Overlap is a rule bug and fails
    **at compile time with rule attribution** — never at apply time, where the user would lose
    fixes. (The applier keeps a cheap sanity re-check.)
  - An edit's span must lie within the currently-open ancestor chain when emitted. Deferred
    emissions (e.g. `no-semicolons`' forward-lookup, `afterFile` reports) are fine — FILE is
    always open; emitting into an already-exited-and-consumed sibling region is a bug.
  - Same-offset insertions apply in collection order (deterministic tiebreak).

### 5.3 Path 2 — The opinionated printer

One whole-file formatter, ktfmt/prettier-class. The **F** bucket — 92 catalog rules — is not
implemented as rules at all; those concerns dissolve into one printer. There is **no à-la-carte
formatting rule family** (D17 supersedes the old `formatting-` prefix + mutual-exclusion design).

**The printer contract** (the line that classifies every future rule):

> The printer may change **only**: whitespace, line breaks, indentation, blank lines, trailing
> commas, and provably-redundant statement-separator semicolons. Every other token change is a
> separately-toggleable targeted fix (Path 1), off by default.

Consequence: the printer is *incapable* of changing behavior — nothing it touches could. That is
the entire safety story for running it unattended, and it is the same positioning as ktfmt
(which also never inserts/reorders tokens the author didn't write).

**Style parameters** — the printer's whole option surface (D21, locked):

```
indentWidth: 4          maxLineLength: 140         trailingCommas: on
importLayout: ascii     multilineSignatureThreshold (param count forcing multiline)
```

No per-rule format toggles. No code-style meta-knob (ktlint's
`ktlint_official/intellij_idea/android_studio` axis — which alone gates ~10 of its wrapping
rules — is deliberately erased; this is the biggest simplification the one-formatter decision
buys). Default *values* remain tweakable until Phase C ships; the *set* is fixed. Deliberately
conceded: the "fix my spacing but preserve my line breaks" niche — those users keep ktlint.

**Internals** (Phase C, module `wrasse-format`, zero kotlinc deps), consuming the same single walk:

1. **`Doc` types** — the layout vocabulary: `Text`, `Break` (a point where a line break *may*
   go), `Group` ("keep together if it fits"), `Indent`. ~150 lines, written once.
2. **`DocBuilder`** — *the one place*. A privileged stream consumer (registered alongside rules,
   receiving the same leaf/enter/exit events) translating the token stream into Doc
   instructions — one `when (ctx.type)` branch per syntax node. All layout decisions about a node
   kind live in its branch. Every F-bucket "rule" is written here — as a branch, never as a
   `WRule`. This component grows throughout Phase C; most of the F bucket ("spacing" ×13,
   blank-line policies) isn't even a branch — it's a property of how the printer emits whitespace
   at all.
3. **`Layout`** — the single deterministic pass over the Doc: thread the current column; for each
   `Group`, "does the flat form fit in the remaining width?" — break or don't. Nested groups
   compose because the decision flows top-down. ~200 lines, written once.

The Doc IR is unavoidable and should not be resisted: in `outer(inner(a, b, c), d)` the outer
wrap decision needs the inner's rendered width, and the inner's indentation depends on the
outer's decision. Per-engine "local fits checks" cannot resolve that nesting; a whole-tree layout
pass is the only correct shape. (This reverses an earlier draft's leaning — recorded so it isn't
re-litigated.)

**Cooperation with fixes — one direction, content → layout, no cycles:** Path-1 edits (lint
fixes, T fixes, ImportEngine output) are *content* decisions; the printer owns *layout*. After
the walk, remaining EditPlan edits are spliced into the Doc (doc leaves reference original spans,
so they are addressable by offset; spliced text is an atomic run with measurable width;
engine-owned regions like the import list are laid out by their engine, not re-wrapped). Then
layout runs. Layout never changes tokens, so it can never re-trigger a rule — **no cycle is
possible by construction.**

**Check mode:** formatter enabled, fixing not — render, diff, report **one** diagnostic per
unformatted file at the first divergence ("file is not wrasse-formatted"). No per-difference rule
attribution — that would be re-deriving à-la-carte rules from a printer.

**Residual lint:** lines the printer cannot break (long string literals, URLs) get a report-only
`max-line-length` finding.

### 5.4 Patch format and the apply step

The existing offset-edit patch format is **kept** (a previously-drafted length-prefixed
whole-file "bundle" format is rejected — D18) and extended in Phase C with a whole-file record
type for printer output.

```
# wrasse-fixes v1
file:<absolute-path>
hash:<sha256-hex-of-source-as-the-walk-saw-it>
edit:<startOffset>:<endOffset>:<escaped-replacement>     (Path 1; descending offset order)
...                                                      (Phase C adds a whole-file record type)
```

- **One patch file per module** in its build dir — never global (modules compile in parallel
  processes; a shared file would need flaky cross-process locking). Apply globs and merges.
- **Hash-guarded, idempotent, self-cleaning:** apply skips any record whose hash no longer
  matches the file; a stale patch is a no-op; after a successful fix the next compile emits an
  empty patch. For whole-file records the guard is *more* load-bearing: a stale whole-file write
  would destroy hand-edits made between compile and apply — hash-check immediately before write,
  abort that file loudly on mismatch, never silently overwrite.
- **Hash/offset consistency rule:** offsets and the hash must both be defined against the same
  text **the walk actually saw** (the compiler's in-memory buffer), never re-read from disk. If
  on-disk bytes differ from that buffer (CRLF normalization, generated sources), apply must
  detect and refuse loudly rather than splice at shifted offsets. (Verifying kotlinc's CRLF
  behavior + a CRLF fixture is a Phase A.5 task — see §13.)
- Mid-write safety: temp file + atomic rename. Gated behind `wrasse.fix`; apply is an
  **explicit** step (`wrasseApply`), never auto-run in a normal build.
- Escaping (edit records): `\n` → literal `\n`, `\` → `\\`; empty replacement = deletion.
- Not fuzzy unified/IntelliJ diff — git diff is an optional *export* for review, never storage.

---

## 6. Rule scope — what gets built, in five buckets

Full per-rule classification (all 255 ktlint/detekt/diktat-unique rules, one row each) lives in
[autoformat-scope.md](autoformat-scope.md); consult it before porting any rule. Summary:

| Bucket | Count | Becomes |
|---|---|---|
| **F** — formatter | 92 | **One printer** (§5.3). Not rules. Not 92 implementations. |
| **S** — semantic | 4 | **One ImportEngine** (unused removal, star expansion, FQN→import, ordering) |
| **T** — targeted fixes | 21 (~15 unique) | Individually-toggleable `WRule`s emitting edits via EditPlan |
| **L** — lint-only | 128 | Report-only rules; the mechanical bulk of Phase B |
| **X** — dropped | 10 | Not ported (niche dogma, compiler-covered, outbound) |

Load-bearing observation: 76 of ktlint's 105 rules are pure layout. Ported ktlint-style that is
76 interacting rewriters (the fixed-point trap, forever); as a printer they are branches of one
component. The "200-rule port" therefore collapses to: **1 printer + 1 import engine + ~15 small
fixes + ~128 report-only rules**.

Notable classification calls (the printer contract decides all of them):

- **Brace insertion** (`if-else-bracing`, detekt `braces-on-*`, …) → **T**, not F: the printer
  never inserts tokens the author didn't write.
- **`modifier-order`** → **T** (token reordering, not layout).
- **Redundant-syntax deletions** (empty `{}` bodies, useless parens, redundant `public`,
  `: Any()`, `: Unit`, useless backticks, `it ->`, trivial accessors, numeric underscores,
  `.rangeTo` → `..`) → **T**.
- **Semicolons and trailing commas** → **F** (the explicitly whitelisted token exceptions, same
  family as prettier's semicolon handling in JS).
- **Import ordering** → owned by the **ImportEngine** (it must re-sort after removal/expansion
  anyway); the printer treats the import list as engine-owned output.
- **Naming rules, metrics, smells, "use X instead of Y" suggestions** → **L**, always. Renames
  need cross-file changes; judgment-shaped rewrites are never autofixed (user stance, D9 spirit).

Seven genuinely-debatable "hard calls" (brace-insertion-by-fiat, comment interiors, raw-string
indent safety, Explicit-API-mode interaction, …) are listed at the bottom of
autoformat-scope.md — resolve during implementation, don't re-derive.

**ImportEngine notes (Phase B.3):** the flagship. Requires collecting every resolved reference's
fully-qualified target per file from FIR (the compiler does not hand over a used-imports set).
Must **bail on ambiguity** rather than guess: shadowing, extension-function imports,
operator/`componentN` imports, KDoc references. A wrong import fix permanently burns trust —
budget this as a mini-project, not a rule.

---

## 7. Configuration

`wrasse.json` / `wrasse.jsonc`, discovered by walking up from the source root, parsed by the
hand-rolled zero-dependency JSONC reader, loaded once at registration from
`CompilerConfiguration.javaSourceRoots`. Malformed config fails fast.

- **Effective config via `extends`** — child values override base; an absent rule is
  off/inherited, not an error. Circular/deep chains are caught. (Replaces the old
  "every rule must be listed" mandate — that broke every config on every release.)
- **`level: off | warn | error` per rule** — one tri-state axis, not separate enable+severity.
  Global `warnOnly` CLI flag layers on top as a blanket error→warn downgrade for rollout.
- **Formatting** is a single `format` on/off plus the five style parameters (D21). No à-la-carte
  formatting family (D17). Targeted fixes are ordinary tri-state rules.
- **Exclude-only** path filtering, globs precompiled to `PathMatcher`; global `exclude` unions
  with per-rule `exclude`. No `include` (avoids precedence ambiguity). *(Currently parsed but not
  enforced — Phase A.5 bug, §13.)*
- **No presets; new rules default off** (D6) — reproducibility across upgrades; discoverability
  via `--list-rules` / effective-config dump (Phase A remainder), not a `recommended` set.
- **No baseline** (D7, re-confirmed). Adoption = `level` + `exclude`.
- **Suppression:** `@Suppress("rule-id")` only, expression and declaration scope (Phase A
  remainder). No comment directives, ever.
- **No `.editorconfig` support** (D12) — native rules + migration path instead.
- `$schema` (`wrasse-schema.json`) hosted for editor autocomplete (D13).

---

## 8. Resolution and `SemanticWRule` (Phase B.3)

The event model today is purely syntactic. The `checkCall(...)` hook on `WrassePlugin` is a
stubbed, separate FIR entry point, not yet unified into `WRule`. Closing the gap:

- **`SemanticWRule` family** — resolution-aware rules receiving both the `WContext` and a
  **resolution facade**, *inside the same single walk* (widened per-node context, not a second
  walk). Facade built once per file before the walk; FIR elements indexed by offset.
- The hard part is the **adapter** — correlating a walk position with its FIR element (offset
  mapping, or building the semantic view from FIR) — not the visitor shape.
- Resolution is free because Host A rides a compile that already resolved everything. Inbound
  only (§2.4).

---

## 9. Performance

### Design (what makes it fast)

- **LightTree, not PSI.** ktlint and detekt build PSI (heavyweight). Wrasse reads the flyweight
  LightTree kotlinc already built. Host B never parses at all.
- **SAX walk, no tree** — no node objects, no source-string materialization; one mutable
  `WContext` per file; primitive-array ancestor stack; O(1) `column()` via tracked newline
  offsets; ordinal-indexed dispatch (array index, no hashing); disabled rules cost zero.
- **Incremental compilation is free filtering** — kotlinc only recompiles changed files, so lint
  fires only on what changed.
- **Single-pass fix/format** — the printer's two bounded in-memory stages vs. ktlint's
  multi-pass fixpoint.

### Honest claims (what to say publicly, and what not to)

- **Lint vs ktlint+detekt: decisively faster in any real build** — structural, not tuning. They
  pay full parses (detekt-with-type-resolution pays ~a second compile); wrasse pays a walk that
  is a rounding error on the compile it rides. The gap widens in the incremental inner loop.
- **The conceded scenario, stated plainly:** lint-only on a cold repo with no build — ktlint
  parses in seconds, wrasse needs an actual compile. Concede it explicitly in docs; never claim
  it. The pitch is *"free when you build — and every real project builds."*
- **Format vs ktfmt: say "free during builds," never "faster than ktfmt."** Interactive latency
  (format-on-save, ms-per-file CLI) is structurally conceded with D16. A standalone syntactic
  host on LightTree would be the only credible path to beating ktfmt head-to-head — evaluated,
  feasible, and explicitly out of scope (D16 re-confirmation).
- **Perceived speed ≠ throughput:** without an IDE story some users will call wrasse "slow"
  regardless of benchmarks. Known, accepted.

### Budget and tripwire

The walk overhead budget at full rule count is **low single-digit % of compile time** — this is
what keeps "lint is free" true, and it will creep silently as 200 rules land unless measured.
Therefore (Phase A.5): a JMH benchmark over a real corpus (e.g. Kotlin stdlib sources) as a
regression tripwire, **before** rule porting starts. Keep `WStreamRule` count small; keep
disabled-rule cost at zero.

Known walk-level punch list (fix in A.5, verify with the benchmark):

1. `childArray.copyOfRange(0, count)` allocates one array per interior node — replace with a
   per-depth array pool; also call `disposeChildren` per the `FlyweightCapableTreeStructure`
   contract (currently never released, defeating the tree's own recycling).
2. Buffered path recomputes `WNodeTypeMapping.map` and `child.text` per child after the recursion
   already computed them — restructure to reuse.
3. `WNodeTypeMapping` is a HashMap lookup per node — index a flat array by `IElementType.index`.
4. `trackLastNewline` scans every leaf's full text backwards — gate by token type (only
   whitespace/comments/strings can contain `\n`).
5. Hash source from the compiler's in-memory buffer (not a disk re-read); hex via lookup table,
   not `"%02x".format` per byte.
6. Measure whether `LighterASTTokenNode.text` allocates a subsequence per leaf; if real, prefer
   offsets + the shared buffer.

---

## 10. Kotlin version matrix & distribution

- **JVM 8 bytecode**, single JAR — loads into the kotlinc daemon classloader like any bundled
  compiler plugin (D14).
- Compiled against the latest supported Kotlin (2.4, `gradle/libs.versions.toml`); cross-version
  FIR API differences absorbed by runtime-selected registrar shells
  (`wrasse-kotlinc-internal-k20`/`-k22`) + the adapter. Rule code and `wrasse-model` never see
  version-specific kotlinc APIs.
- The same fixture set replays against every supported minor (2.1–2.4) via the per-minor test
  modules — the primary mitigation against FIR API instability. Per-patch tasks
  (`testPatch_<version>`) extend coverage.
- **Known fragile surfaces** (mitigate with tests, §13/§14): `WNodeTypeMapping`'s direct
  `KtTokens.*`/`KtNodeTypes.*` constant references (a rename breaks compile or silently maps to
  `UNKNOWN` — rules just stop firing); registrar selection probes internal compiler class names;
  the three near-identical checker shells must be edited in triplicate on signature changes. Add
  the next Kotlin EAP to the patch harness early — breakage appears there first.

Kotlinc surface (adapter-only; rule code never imports these): LightTree
(`KtLightSourceElement`, `FlyweightCapableTreeStructure<LighterASTNode>`, `LighterASTNode`,
`LighterASTTokenNode`, `IElementType`); FIR registration (`CompilerPluginRegistrar`,
`CommandLineProcessor`, `CompilerConfiguration`, `FirExtensionRegistrar(Adapter)`,
`FirAdditionalCheckersExtension`, `FirFileChecker`, `FirFunctionCallChecker`); diagnostics
(`KtDiagnosticsContainer`, `DiagnosticReporter`/`reportOn`, `error1`/`warning1`).

---

## 11. Testing strategy

- **Fixture auto-discovery:** tests are `.kt` files under
  `testing/wrasse-test-harness/src/main/resources/fixtures/<dir>/`, each dir paired with a
  `wrasse.json` (or inheriting via `extends`). Directives drive expectations:
  `// expect-error <line>:<col> <rule-id> "<message>"`, `// expect-warning`, `// expect-clean`,
  `// fixture-option: ...`. Adding a test = adding a file. Always assert **full** error messages.
- **Version matrix:** the same fixtures run against Kotlin 2.1–2.4 (fixtures are
  version-agnostic by construction).
- **Idempotence invariant (D19, Phase A.5):** for every autofix fixture — apply → re-lint →
  zero diagnostics → second fix emits zero edits. Phase C: `format(format(x)) == format(x)`;
  formatted fixtures re-format to themselves.
- **Mapping-completeness test** (A.5): walk representative fixtures per Kotlin minor and assert
  zero `UNKNOWN` node-type mappings.
- **CRLF fixture** (A.5): guards the hash/offset consistency rule (§5.4).
- Rules are unit-testable without a compiler (`wrasse-model` has no kotlinc dep); fixture tests
  exercise the full plugin path.

---

## 12. Decision log

The point of this section is to **stop re-litigating settled questions**. Reopen only with new
information. Statuses: Accepted · Rejected · Superseded.

### Architecture

- **D1 — Compiler plugin, not a Gradle plugin or embedded compiler · Accepted.** Ride kotlinc's
  FIR and read its LightTree. Avoids the redundant parse and Gradle API churn. Cost: lint only
  runs when a compile runs; config isn't automatically a build input (mitigated by an optional
  thin Gradle plugin, Phase D).
- **D2 — Own intermediate model behind an adapter · Accepted.** Rules depend only on wrasse's
  event model, never kotlinc types. Isolates the unstable K2 surface; rules testable without a
  compiler, portable to other hosts.
- **D3 — Two-host model · Accepted.** Read-only lint plugin (Host A) + separate explicit
  read-write apply (Host B). Dissolves "how do we write during compile."
- **D15 — Inbound rules now; outbound deferred · Accepted/Deferred.** Outbound needs whole-program
  and breaks under incremental; if ever built, full-build-only — no persistent cross-ref index, no
  reading kotlinc's incremental caches.
- **D16 — Host B is build-mediated only; no standalone parse · Accepted, re-confirmed 2026-07-18.**
  Every real usage runs through a real compile; applying is pure byte-splicing. A standalone
  syntactic-only "Host C" (embedded LightTree parser — parsing needs no classpath, only
  resolution does; would enable fast CLI format/lint and a ktfmt head-to-head speed story) was
  evaluated as feasible and **explicitly kept out of scope**; no spike. The design preserves the
  option for free (rules and `wrasse-format` are kotlinc-free). Accepted consequence: the
  formatter is batch/build-mediated; perf claims say "free during builds," not "faster than
  ktfmt." Revisit only if a build-independent entry point becomes an actual goal.

### Config

- **D4 — Effective config via `extends`; not mandatory-all-rules · Accepted** (supersedes the
  earlier list-every-rule mandate, which broke every config on every release).
- **D5 — Per-rule `level: off|warn|error` · Accepted** (reverses an earlier global-severity
  design; separate tasks would mean separate compiles — the exact double work wrasse avoids).
- **D6 — No `recommended` preset; new rules default off · Accepted.** Presets silently change CI
  results on upgrade. Discoverability via `--list-rules`/effective-config dump.
- **D7 — No baseline file · Accepted, re-confirmed 2026-07-18** with the tension explicitly on
  the table (drop-in adoption on legacy repos is where baselines shine — considered, still
  rejected). Revisit only with concrete adoption feedback.
- **D8 — Suppression via `@Suppress("rule-id")` only · Accepted.** No comment directives, ever.
  Annotation-granular suppression is the accepted trade.
- **D12 — No `.editorconfig` support · Accepted.** Parsing it + ktlint's property semantics is a
  tar pit.
- **D13 — `$schema` hosted statically for editor autocomplete · Accepted.**

### Fixing & formatting

- **D9 — Autofix off by default, opt-in, behavior-preserving; no SAFE/SUGGESTED tiers ·
  Accepted.** The user owns the risk; the one implementation constraint: a fixer must be
  behavior-preserving and **bail when uncertain**.
- **D10 — Fixes as a per-module edit-list; exact offsets, hash-guarded · Accepted.** Details §5.4.
- **D11 — `formatting-` prefix + `formatting-opinionated` escape hatch · Superseded by D17.** The
  à-la-carte formatting family no longer exists; config is `format` on/off + style parameters.
- **D17 — One opinionated printer; the printer contract · Accepted 2026-07-18.** §5.3. Rejected
  alongside: clang-format-scale option surfaces (real-world variance concentrates in ~5 axes);
  porting the 76 ktlint layout rules as independent rewriters (the fixed-point trap).
- **D18 — EditPlan + keep edit-list format; bundle format rejected · Accepted 2026-07-18.** §5.2,
  §5.4. The bundle's claimed benefit (no overlap detection) was backwards — overlap detection is
  an invariant checker, now run earlier (compile time), not removed.
- **D19 — Idempotence as a harness-enforced invariant, gating Phase B · Accepted 2026-07-18.** §5.1.
- **D20 — Rules instantiated per file · Accepted 2026-07-18.** §4. Removes the data race if
  kotlinc parallelizes checkers; decided now because EditPlan adds more per-file state.
- **D21 — Style parameters locked; no code-style meta-knob · Accepted 2026-07-18.** §5.3.

### Build & distribution

- **D14 — JVM 8 bytecode, single JAR across a Kotlin range · Accepted.** §10.

### Rejected outright

- **R1 — ktfmt byte-for-byte compatibility ("win Google/Meta off ktfmt").** Bug-for-bug fidelity
  against a moving target, forever — unwinnable treadmill. Formatting is a stable "wrasse style"
  with one-time migration.
- **R2 — Nursery checked-exception rule.** Heuristic, viral function-coloring, against Kotlin's
  grain, off the locked focus. (The errors-as-values preference still informs wrasse's own code.)
- **R4 — SAFE_FIX/SUGGESTED_FIX two-tier taxonomy.** Superseded by D9 — behavior preservation is
  an implementation constraint, not a config axis.

> Working principle behind several rejections: don't cargo-cult eslint/prettier/biome
> conventions. Minimal, single-axis config; the user owns autofix risk.

---

## 13. Roadmap

Scope discipline is the point: anything not in a phase below is **parked** or **dropped** and
should not creep into earlier work.

### Done — Foundation

Plugin loads; FIR checker fires; diagnostics with file/line/col; SAX rule engine + ordinal
dispatch; JSONC config + `extends` + tri-state `level`; runtime version shells (k20/k22), single
JAR; 3 rules; fixture harness + 2.1–2.4 matrix; MVP offset-patch autofix end-to-end
(`wrasseFix`/`wrasseApply`); repo self-lint.

### Phase A remainder — config & severity polish

- `@Suppress("rule-id")` at expression and declaration scope.
- `--list-rules` / effective-config dump (the discoverability story replacing presets).
- Optional CLI override for config path.

**Exit:** a new rule ships without editing any existing user config; warn and error coexist in
one run; `@Suppress` silences one rule.

### Phase A.5 — Foundation hardening (gate: complete before B)

1. **Trust-burning bug fixes** (found in the 2026-07 review; details §14):
   - CRLF/normalization verification + fixture — does kotlinc normalize `\r\n`? If yes, today's
     disk-based hash + normalized offsets **silently corrupt Windows files** on apply. Hash and
     offsets must both refer to the walk's text (§5.4); apply refuses loudly on mismatch.
   - `exclude` config is parsed but never enforced (silent no-op) — wire it up; requires passing
     the real absolute path into `WContext` (currently gets just the file *name*).
   - `no-semicolons`: class-body false negative (any semicolon with `CLASS_BODY` parent treated
     as required) and consecutive-`;;` miss; the known statement-separator false positive.
   - `trailing-newline` on an empty file reports span `0..1` (past EOF).
   - Patch-file writer: unsynchronized `patchFileInitialized` check-then-truncate; cross-module
     clobber if modules ever share a fix dir; relative-path resolution against the applier's CWD.
   - MPP: confirm `FirFileChecker(MppCheckerKind.Common)` doesn't double-fire per file
     (double-appended edits); dedup or switch kind if it does.
2. **Idempotence harness invariant (D19)** — before any rule porting.
3. **Per-file rule instantiation (D20)** — includes fixing `WNodeStack.clear()` not resetting its
   counts array (latent trap for pooling).
4. **EditPlan + `takeEditsIn` (D18)** — proven end-to-end with one nested rule pair; overlap
   check moved to compile time; expose the source buffer on `WContext`.
5. **Walk perf punch list** (§9). The JMH benchmark is deferred by owner decision (2026-07-19) —
   revisit before Phase B volume porting begins, since the walk-overhead budget still needs a
   tripwire eventually.
6. **Mapping-completeness test** per Kotlin minor (zero `UNKNOWN` on representative fixtures);
   map `KW_TYPEALIAS` (currently silently unmapped — the claimed soft-keyword fallback doesn't
   exist).

### Phase B — Parity port (the bulk)

Scope per §6 / [autoformat-scope.md](autoformat-scope.md):

- **B.1 — lint-only rules (~128, bucket L).** Report, never fix. Mechanical volume; no new infra.
- **B.2 — targeted fixes (~15, bucket T).** Braces family, `modifier-order`, redundant-syntax
  deletions. Each gated by the idempotence harness; born-clean discipline.
- **B.3 — ImportEngine (bucket S).** `SemanticWRule` + FIR resolution facade + LightTree↔FIR
  correlation adapter. One engine, several config keys. Bail on ambiguity. A mini-project.

Within a tier: complexity 1 → 3; implement overlapping ktlint/detekt/diktat rules once under a
single wrasse id.

**Exit:** a representative real project lints under wrasse with parity-equivalent findings to its
ktlint + detekt setup (minus parked outbound rules) at measurably lower wall-clock.

### Phase C — The printer

- `wrasse-format`: `Doc` + `DocBuilder` + `Layout` per §5.3; style parameters per D21.
- Whole-file record type in the patch format; hash-guarded apply (§5.4).
- Edit splicing (content → layout); check mode; residual `max-line-length` report.
- Harness: `format(format(x)) == format(x)`; formatted fixtures re-format to themselves.
- Resolve the 7 hard calls in autoformat-scope.md as they come up.

**Exit:** the formatter reformats a real module idempotently via `./gradlew wrasseFix`;
`wrasse.fix` expands a star import correctly on a real module; the compile-riding fix pass beats
a separate `ktlint -F` invocation on the same files.

### Phase D — Hardening & release

- Extended version matrix (per-patch, next EAP early); fuzz on real-world Kotlin repos.
- Published benchmarks vs ktlint + detekt (lint) and ktlint -F (fix) — framed per §9's honest
  claims.
- SARIF / checkstyle reports from Host B for CI dashboards.
- Optional thin Gradle plugin: declare `wrasse.json` as a compile input; wire tasks — while
  keeping the compiler plugin host-agnostic. Amper setup example (verify Amper can pass compiler
  plugin args — do this early in D).
- Publish to Maven Central; `$schema` hosted; README.

**Exit:** another team adopts wrasse from published artifacts and docs without our help.

### Parked (deferred, not rejected)

- **Outbound / whole-program rules** — dead code, architecture/layering, cycles, dependency
  analysis (D15).
- **Novel semantic rules** — no-recursion, split-compound-boolean, function line limits,
  explicit-library-defaults, restricted-API. Later differentiation, not parity.
- **Standalone Host C** (CLI / IDE format-on-save / pre-commit without a build) — feasible,
  explicitly not a goal (D16). The only credible path to a ktfmt head-to-head speed claim, if
  ever wanted.

### Dropped (do not revisit)

- Nursery checked-exception rule (R2). ktfmt byte-compatibility (R1). Baseline file (D7).
  Comment-based suppression (D8). À-la-carte formatting rule family (D17 supersedes D11).

### Load-bearing risks

1. **FIR API instability** across Kotlin versions — adapter isolation + version matrix; a
   permanent maintenance tax, and the real competitive risk (the perf moat is structural; the
   tax is what erodes capacity).
2. **Import-optimization correctness** — bail on ambiguous/shadowed resolution rather than
   guess; one wrong autofix permanently burns trust.
3. **Walk overhead creep** at 200 rules — held by the A.5 benchmark tripwire and the
   low-single-digit-% budget.

---

## 14. Known issues & tech debt (beyond the A.5 list)

- **WARN-severity diagnostics are silently dropped on Kotlin 2.1 and 2.2** (ERROR works; 2.3/2.4
  fine). Confirmed pre-existing against the pre-A.5 baseline: the same 5 warn-level fixtures fail
  on both minors (`mixed-levels/warn-only-downgrades-all`,
  `extends-exclude-union/child-overrides-level-from-base`, `no-semicolons-warn/*`,
  `no-semicolons-warn-only/*`), so `testMinorHarness` is currently red on 2.1/2.2. Root-cause
  hypothesis: `WrasseErrors20` (used by the k20 shell) does not extend `KtDiagnosticsContainer`,
  unlike the k22/main containers — a cross-version FIR diagnostics-registration difference. Needs
  its own investigation in the k20 registrar internals.
- `ctx.childIndex` is stale during `exitNode` (holds the last child's index, not the exiting
  node's own) — restore before exit dispatch or document loudly.
- `ActiveNodeEntry.depth` is dead — remove or use.
- `ChildBuffer` allocated per `WBufferedNodeRule` enter — pool when engines land.
- Registrar selection probes internal compiler class names (`classExists` markers); checker
  shells exist in triplicate (k20/k22/main) — any signature change must be mirrored. Version
  matrix is the safety net.
- Patch file stores absolute paths — not portable across machines/CI. Not solved, tracked.
- `wrasseApply` task registered for all subprojects (`onlyIf`-guarded noise in `./gradlew tasks`).
- Incremental-compilation DX: warnings in files that didn't recompile don't reappear in output;
  `-PwrasseCheck`/`-Pwrasse.fix` changing compiler args forces full recompilation — currently
  accidental, should be documented as the intended "full sweep" mechanism.
- Rules with per-file state on shared instances (pre-D20 state) — removed by A.5 item 3.
- `FirSyntacticChecker` allocates a `KtLightSourceElement` per violation — fine (violations are
  cold), noted for completeness.
