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
  wrasse-model/            WContext, WNodeStack, WNodeType, WRule, StreamDispatch, WRuleSet, WConfig,
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
  `no-wildcard-imports` (`WStreamRule`, `requiresResolution`, resolution-powered star-import
  expansion autofix — package-stars only, see §8), `trailing-newline` (`WFileRule`, autofix).
- **Offset-patch autofix pipeline working end-to-end, emission rides check mode (D22):** rules
  attach `WEdit`s to reports → `WrassePlugin.checkFile` collects them → whenever `fixOutputDir` is
  set (no separate fix flag), merges them into that compilation's own
  `build/wrasse/<compilation>/wrasse-fixes.txt` (file + SHA-256 + descending-offset edits),
  loading the existing patch once per compilation and upserting/removing (self-cleaning) each
  recompiled file's entry, atomically rewriting the whole file every time →
  `wrasseApply` (`WPatchApplier`) walks `build/wrasse/` recursively, hash-checks, overlap-checks,
  applies via temp file + atomic rename. `./gradlew wrasseFix` wires it together (same check
  compile as `wrasseLint` + `wrasseApply`); the repo lints itself (`wrasseLint`/`wrasseFix`).
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
  `lastNewlineOffset` making `column()` an O(1) subtraction; `sourceText`, a `CharSequence` view
  of the compiler's source buffer set once per walk by the adapter (D18) — the same value the
  file hash is computed from, never fetched twice; and the per-file `editPlan` (`EditPlan`, D18).
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
(`pendingStart` etc.) is race-free if kotlinc ever parallelizes file checkers. Per compilation,
`WRuleSet` fixes once which rule IDs are enabled and their configs (as
`(WUninitializedRule, WrasseRuleConfig)` pairs); per file, `WRuleSet.dispatchForFile` calls
`initRule` on the surviving (non-excluded) rules and builds a fresh `StreamDispatch` from the
result. A rule whose `exclude` matches the current file is skipped at this step — never
instantiated for that file at all, replacing an earlier report-time filter. Rebuilding
`StreamDispatch`'s ordinal-indexed arrays costs `O(WNodeType.SIZE)`, fixed regardless of
active-rule count and negligible next to walking the file itself, so this stays a plain
per-file rebuild rather than a cached dispatch shape until profiling says otherwise — the
`dispatchForFile` contract would not need to change if that optimization ever lands.
`EditPlan` and `WContext` share the per-file lifecycle.

**Multi-id engines:** `WUninitializedRuleGroup` (`wrasse-model`) is the sibling contract for a
fused engine backing several user-facing ids behind one implementation (§5.1, "fighting rules get
fused"; §8 has `ImportEngine`, the first and so far only one). It declares `ids: Set<String>` and
`initGroup(configs: Map<String, WrasseRuleConfig>): WRule`, called per file with exactly the
enabled, non-excluded-for-this-file subset of its ids — an id missing from `configs` behaves as
if that rule does not exist for this file. `WRuleSet` holds `(WUninitializedRuleGroup,
Map<id, WrasseRuleConfig>)` pairs alongside its `(WUninitializedRule, WrasseRuleConfig)` ones and
filters each group's config map independently per file before deciding whether to instantiate it
at all. `WReporter`'s contract is untouched: a group reports under each surviving id's own
severity by passing a small per-id `WRule` facade (`id`/`config` only, never dispatched) as the
`rule` argument — `.report(...)`'s reader never sees the difference between a facade and a real
top-level rule.

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

**EditPlan** (implemented, `libs/wrasse-model`; per file, replaces the flat `collectedEdits`
list `WrassePlugin.checkFile` used to accumulate):

- Entries are `(ruleId, WEdit, collection sequence)`, kept ordered by span — start ascending,
  then end ascending, then sequence descending for exact-span ties — the single order that
  keeps the overlap scan below correct *and* reproduces collection order in the applier output
  (see the same-offset bullet below, formerly a known hazard, now fixed). `takeEditsIn(start,
  end)` returns and removes every entry whose span lies *within* `[start, end]` inclusive, as
  the attributed `Entry` (not a bare `WEdit`): a composing engine can read which rule an inner
  edit came from for its own bail-out logic without a second lookup, at no extra allocation cost
  over what ordered storage already needs.
- **Post-order composition:** the walk exits children before parents, so when an engine rewrites
  a region at `exitNode`, every edit inside its span already exists. It calls
  `editPlan.takeEditsIn(start, end)`, applies those edits textually to its copy of the original
  span (descending offsets, purely local), performs its own transform (wrap, re-indent each line —
  a pure text operation), and emits **one composed edit** for the outer span. Consumed inner edits
  leave the plan. This is Wadler-style indent-threading done SAX-style: no rule ordering, no
  priority system, no fact-passing protocol, no observing another rule's rendered text.
- **Invariants (enforced, loud):**
  - After the walk, remaining edits must be pairwise disjoint. Overlap is a rule bug and fails
    **at compile time with rule attribution** (both rule ids, both spans, both replacements) —
    never at apply time, where the user would lose fixes. (The applier keeps a cheap sanity
    re-check.) The disjointness scan needs only adjacent pairs under the span order above; a
    same-span pair where both edits are zero-width inserts (the sequence tiebreak case) never
    trips it — the formula (`next.start >= current.end`) already passes for two equal points,
    so no special case was needed.
  - An edit's span must lie within the currently-open ancestor chain when emitted. Implemented:
    `WrassePlugin.checkFile` now constructs the per-file `WContext` itself and hands it to
    `LightTreeStreamAdapter.walk`, so the `WReporter` it builds closes over that same `ctx` and
    can read `ctx.ancestors`' innermost open span at the exact moment a rule's edits arrive —
    no widened `WReporter.report` signature needed. Deferred emissions (e.g. `no-semicolons`'
    forward-lookup, `afterFile`/`WFileRule` reports) are fine — the ancestor stack is empty by
    then, which is treated as FILE always being open, vacuously satisfying the check.
  - Same-offset insertions apply in collection order (deterministic tiebreak): ties break by
    *descending* sequence (later-collected first) in the entry order, because
    `StringBuilder.replace` on an unchanged offset pushes whatever is already there to the
    right — applying the later insert first leaves room for the earlier one to land leftmost.
    `WPatchWriter`/`WPatchApplier` are unchanged (still a stable descending-offset sort each); it
    is exactly this input order that makes their existing sort reproduce collection order in the
    output. Locked by a real writer→reader→applier round-trip test.

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

**Rule ID and message conventions (owner-approved, locked):** lowercase-kebab ids; `no-<thing>`
names a prohibition, a bare noun phrase names a requirement (`import-ordering`, `trailing-newline`).
One wrasse id per **concept** across the ktlint/detekt/diktat overlaps it replaces — never one id
per source tool. Messages are one short declarative sentence: capitalized, no trailing period,
stating what is wrong, with parameterized facts inlined where they help fixing (e.g. "Function has
7 parameters (max 5)"). All shipped rules already conform; every future port must too. Where
ktlint/detekt/diktat chose a conservative exemption over an upstream shape, wrasse matches it rather
than going further just because a broader fix is provably safe, unless the owner explicitly
approves extending scope. **KDoc states the code's contract only** — behavior, parameter/return
semantics, non-obvious caller-facing invariants, in a few lines; development history, bug narratives,
review-round stories, and phase/track references belong in this document (tersely), not in KDoc.

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
  with per-rule `exclude`. No `include` (avoids precedence ambiguity). Enforced at
  rule-instantiation time (D20, §4): a rule whose `exclude` matches the current file is simply
  never instantiated for it; the global `exclude` short-circuits the whole file before any rule
  is built.
- **No presets; new rules default off** (D6) — reproducibility across upgrades; discoverability
  via `--list-rules` / effective-config dump (Phase A remainder), not a `recommended` set.
- **No baseline** (D7, re-confirmed). Adoption = `level` + `exclude`.
- **Suppression:** `@Suppress("rule-id")` only, file/declaration/expression scope — shipped, see
  §7.1. No comment directives, ever.
- **No `.editorconfig` support** (D12) — native rules + migration path instead.
- `$schema` (`wrasse-schema.json`) hosted for editor autocomplete (D13).

### 7.1 Suppression (`@Suppress`) — shipped 2026-07-19

A violation is suppressed iff its reported `[startOffset, endOffset)` span lies within the span of
an element annotated `@Suppress(...)`/`@file:Suppress(...)` whose arguments name that rule —
uniform positional containment, no config surface (annotation-driven only, D8).

- **Three region kinds, one containment rule.** `@file:Suppress(...)` → region = the whole file
  (see the import-family caveat below). A declaration's own `@Suppress(...)` (class, function,
  property, primary/secondary constructor, parameter, type parameter, or a local declaration — a
  local `val`/`var` is a `PROPERTY` node like any other) → region = that declaration's own
  `[start, end)` span, read directly off `WContext.ancestors` at `ANNOTATION_ENTRY` enter time: its
  immediate parent is `MODIFIER_LIST`, whose own immediate parent (one level further up the already-
  pushed ancestor stack) is the annotated declaration — no exhaustive per-declaration-kind switch
  needed, since a `MODIFIER_LIST` is always a direct child of whatever it modifies. An annotated
  expression (`@Suppress(...) expr`, parsed as `ANNOTATED_EXPRESSION` with the entry as a direct
  child, no modifier list involved) → region = that node's own span.
- **Argument matching is syntactic, not resolved, and conservative.** Only a directly-written,
  non-interpolated string literal counts (`VALUE_ARGUMENT`'s sole direct child is a `STRING_TEMPLATE`
  whose own direct children are at most one `LITERAL_STRING_TEMPLATE_ENTRY` — no escape entries, no
  interpolation entries, no named arguments). Concatenation, a `const val` reference, an
  interpolated string (even one built entirely from compile-time constants) — anything else — is
  never evaluated, just ignored; that argument suppresses nothing (locked by
  `suppress-non-literal/`, exercising all three shapes). Exact match, case-sensitive, against real
  rule ids. **Wildcards:** the literal strings `"all"` and `"wrasse"` match **case-insensitively**
  (`"ALL"`, `"Wrasse"`, ... all match) and suppress every wrasse rule in the region; this is the
  one deliberate case-insensitive exception — everything else about argument matching is exact.
  Unknown/foreign entries (`"UNCHECKED_CAST"`, `"unused"`, a detekt id, ...) are inert: never an
  error, never an accidental suppression (`suppress-file-scope/foreign-id-inert-error`,
  `suppress-multi-entry/none-match-error`).
- **The annotation itself is matched by simple name, syntactically** — `Suppress` (one identifier
  segment under `CONSTRUCTOR_CALLEE`) or exactly `kotlin.Suppress` (two segments). This is
  deliberately not resolution-verified: a user-defined class also named `Suppress` (or shadowing
  `kotlin.Suppress`) would false-suppress under this same syntactic match. Accepted and documented
  here rather than fixed — resolution-verified matching (checking the annotation's FIR-resolved
  callee against the real `kotlin.Suppress` `ClassId`) is engine-scope future work, not required for
  this feature to be correct on any real codebase. The bracket multi-annotation form (`@[A B]`,
  `KtNodeTypes.ANNOTATION`) is left unmapped in `WNodeTypeMapping` and therefore never resolves a
  scope at all for entries written that way — inert, not wrong, and not expected to matter (nobody
  writes `@Suppress` inside a bracket group in practice).
- **Import-family rules have no per-import granularity** — an import directive is never nested
  inside any declaration (`IMPORT_LIST` sits at the file's top level, alongside, never inside, any
  class/function), so a declaration-scoped `@Suppress("no-unused-imports")` (or any other
  `ImportEngine`-backed id) can *structurally never* contain an import's own span: it is always a
  no-op there (`suppress-declaration-scope/class-level-suppress-of-import-rule-is-noop-error` locks
  this explicitly). Only `@file:Suppress(...)` (region = the whole file) can suppress an import
  rule at all. This is the direct cost of D8's "annotation-granular suppression is the accepted
  trade" — there is no per-import-directive annotation target in Kotlin's grammar to hang a finer
  scope off.
- **Collection & filtering design (framework-level, chosen over the alternatives considered):**
  `SuppressionCollectorRule` (`wrasse-rules`) is a `WStreamRule` injected as an always-on,
  non-user-configurable rule via `WRuleSet.dispatchForFile`'s `alwaysOn` param — it rides the same
  single walk as every real rule, has no `wrasse.json` entry, and is never excluded. It accumulates
  every `@Suppress` region into `SuppressionIndex` (pure containment matching, unit-tested standalone,
  zero kotlinc dependency) as it walks. `WrassePlugin.checkFile`'s `WReporter.report` gate is
  checked **synchronously, at report time** — before a violation becomes a `ViolationReport` and
  before its `WEdit`s ever reach `EditPlan.add` — rather than as a post-walk filter over an already-
  built report list. This works because of an ordering property verified structurally, not just
  empirically: an annotation always sits, textually, before whatever it can suppress (a modifier
  list precedes its declaration's body; a file annotation list is the file's first possible
  construct; an annotated expression's entry precedes its base expression), and the LightTree is
  already fully parsed before the walk starts, so an ancestor's *final* span is known the instant it
  is pushed onto `WContext.ancestors` — not only once its children are later visited. So by the time
  `report()` is called for any offset, from any rule (an ordinary leaf-driven one, or a
  `WFileRule`/`afterFile`-deferred one like the import engine, which only ever reports once the
  *entire* file — and therefore every annotation in it — has been walked), every region that could
  cover that offset is already in the index. No two-phase pre-scan of the file-annotation region was
  needed to make file-level suppression fast at rule-instantiation time (the D20 exclusion path,
  `WRuleSet.dispatchForFile`'s `isExcluded`) — that optimization is **deliberately deferred**:
  correctness came first, and a file wholly suppressed for one rule id still pays for walking that
  rule during the same pass today. Revisit only if profiling ever makes that walk cost visible.
  Because the gate short-circuits before any edit reaches the plan, a suppressed fixable violation
  is D22-self-cleaning for free — no `EditPlan` removal machinery was needed (locked by
  `SuppressionEditsDroppedSpec`, a genuine two-compile sequence: an unsuppressed violation emits a
  real patch entry, then the same file recompiles with the annotation added and the entry is gone).

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

### 8.1 Resolution facade (`WResolvedUsage`)

**As-built (resolution facade spike):** `WResolvedUsage` (`wrasse-model`, zero kotlinc deps) is a
file-level, not per-node, facade: `classifiers: Set<String>` (dot-separated FQNs, type arguments/
annotation types/qualifiers included), `callables: Set<WCallableUsage>` (package/class/name triple,
`classFqName == null` for top-level), `hasResolutionErrors: Boolean`. Held nullable on
`WContext.resolvedUsage`, null meaning "not collected". `internal/ResolvedUsageCollector` builds it
from the checker's `FirFile` via a `FirVisitorVoid` walking only `declarations` + file-level
`annotations` (imports and the package directive are separate `FirFile` fields, never visited, so
they never self-justify). Local declarations (`CallableId.packageName ==
CallableId.PACKAGE_FQ_NAME_FOR_LOCAL`) are filtered out — they can never be import targets.
Collection is lazy and gated: `WrassePlugin.checkFile` only invokes the provider when
`dumpResolvedUsage` is on or `WRuleSet.requiresResolution` (computed once at construction from
`WUninitializedRule.requiresResolution`, default false) is true — zero FIR walk otherwise. A
`dumpResolvedUsage` plugin option (wired like `fix`/`warnOnly` through `Constants`,
`WrasseCommandLineProcessor`, and all three registrars) emits one synthetic `RuleLevel.ERROR`
diagnostic per file: `resolved-usage: classifiers=[a.B, c.D] callables=[a/foo, a.B/bar] errors=false`
(ASCII-sorted, `packageFqName/name` for top-level, `classFqName/name` for members). The
`SemanticWRule` family and LightTree↔FIR offset correlation are still future — this spike only
proves the FIR surface is stable 2.1–2.4 and gets the facade onto `WContext`.

### 8.2 The import engine (`no-unused-imports` / `no-wildcard-imports` / `import-ordering`, fused into `ImportEngine`)

**As-built (`no-unused-imports`, first `requiresResolution` consumer):** a `WStreamRule` in
`wrasse-rules` (`NoUnusedImportsRule`). It assembles each explicit import
directive off the leaf stream (`IMPORT_DIRECTIVE`'s `KW_IMPORT`/`IDENTIFIER`/`DOT`/alias
`IDENTIFIER`, distinguished from the main path via `ctx.hasAncestor(IMPORT_ALIAS)`) and separately
records every `EOL_COMMENT`/`BLOCK_COMMENT`/`KDOC` leaf's offset span; the actual verdict is pure
decision logic (`UnusedImportDecision`, unit-tested without a compiler) run at `afterFile`. An
import counts as used, per the bail-on-ambiguity mandate, if *any* of: a classifier equals its FQN
or starts with `FQN.` (covers nested classes); a callable's `classFqName` equals the FQN or starts
with `FQN.` (covers constructors, companion/static-like members, enum entries — the same shape
noted above for `WCallableUsage`); a callable's `classFqName` equals the FQN's *parent* and its
`name` equals the FQN's simple name (covers `import a.b.Obj.member` — object vals/funs, enum
entries, Java statics — where the import FQN itself names the member, so `classFqName` can never
equal the full FQN); a top-level callable (`classFqName == null`) matches package+simple-name; or —
the conservative fallback that kills ktlint's known KDoc-reference false positive — the import's
visible name (alias if present, else simple name) occurs as a whole word
(`\bname\b`) inside any recorded comment/KDoc span of `ctx.sourceText`, checked textually rather
than via any KDoc-aware resolution FIR does not provide. Whole-file bail (report nothing) when
`ctx.resolvedUsage == null` or `hasResolutionErrors`. **Star imports are explicitly out of
scope for this rule** — a directive containing `MUL` is skipped entirely (never even recorded as
a candidate); `no-wildcard-imports` owns star syntax end-to-end itself now, including the
attribution-driven expansion fix (below) — this rule never needs to reason about which names a
star covers. **Retired 2026-07-19:** the description below (through "Fixtures" at the end of this
section) is kept as the historical record of the attribution rules, bails, and fixtures — all
still accurate — but the three-`WStreamRule` shape, the `afterFile`-registration-order dependency,
and the `EditPlan.takeEditsIn` self-composition it describes no longer exist. `ImportEngine` (the
as-built paragraph at the end of this section) folds all three into one decision-maker.

**As-built (`no-unused-imports` removal fix):** every unused-import report carries a deletion
`WEdit` *when one can be emitted safely* — computed by a small pure function (`ImportRemovalSpan`,
unit-tested without a compiler, returns `WEdit?`) from `ctx.sourceText` and the directive's own
`[startOffset, endOffset)` — no re-reads, no tree. Deletion-span policy: if the directive is the
only non-whitespace content on the line(s) it spans (checked by scanning back to the enclosing
line start and forward to the enclosing line end), the whole line is deleted including its
terminating `\n` (or to end-of-text if it is the file's last line with no trailing newline) —
otherwise the deletion would leave an empty line behind. This also covers a directive whose own
statement-terminating semicolon sits inside its own node span (the Kotlin grammar's
`KotlinParsing.parseImportDirective` calls `consumeIf(SEMICOLON)` *before* closing the
`IMPORT_DIRECTIVE` marker) when that directive is alone on its line — `import x.y;` alone still
gets whole-line removal, semicolon included.

**Otherwise — a semicolon-separated sibling import or a trailing comment shares the line — the
rule bails: no edit at all, report-only for that directive (D9, "behavior-preserving and bail
when uncertain").** This is a reversal of an earlier draft of this policy, which deleted only the
directive's own span in this branch; that broke the D19 idempotence invariant across rules once
`no-semicolons` is also enabled (this repo's own `wrasse.json` does exactly that). Deleting the
*right-hand* directive in `import a.b; import c.d` leaves `import a.b; ` behind: the *left*
directive's own separator semicolon is now statement-trailing, which `no-semicolons` did not
flag before the deletion and does flag after it — pass 2 of `fix(fix(x))` then emits a brand-new
`no-semicolons` diagnostic+edit that pass 1 never reported. Extending the deletion backward to
also consume the separator would fix that specific case but reaches into the *left* directive's
own span — an outright EditPlan overlap when *both* directives on the line are unused. Deciding a
neighbor's semicolon fate from inside `no-unused-imports` is exactly the cross-rule coupling the
design confines to a real fused engine (§5.1); absent that engine, the correct move is to bail,
not guess. The same-line case stays a lint-only finding until the ImportEngine fuses import
removal and statement-separator cleanup into one decision-maker.

Two adjacent whole-line-removable unused imports fall out as two disjoint whole-line `WEdit`s
(their spans touch but never overlap), satisfying the EditPlan disjointness invariant without any
extra bookkeeping in the rule. Locked by fixtures with `.fixed.kt` companions (§11) covering:
first/middle/last position in a multi-import list plus an adjacent unused pair; an unused import
as the file's last line with no trailing newline; a semicolon-shared-line import list (the
same-line side bails with no edit and survives the D19 cycle unchanged, alongside a second,
whole-line-removable unused import so the fixture still exercises a real fix); an unused import
whose removal empties the import list entirely with no stray blank line; and a dedicated
`no-unused-imports-with-semicolons/` fixture dir with **both** `no-unused-imports` and
`no-semicolons` enabled, proving end-to-end that round 2 of the D19 cycle reports exactly the
surviving (unedited) diagnostics and never a newly-introduced `no-semicolons` finding.

**Typealias imports need the abbreviation, not just the expansion:** `collectConeType` in
`ResolvedUsageCollector` records `coneType.abbreviatedType` (the `ConeKotlinType.abbreviatedType`
extension in `org.jetbrains.kotlin.fir.types`, from `AbbreviatedTypeAttribute` on `ConeAttributes`
— stable, byte-identical across 2.1.21/2.2.21/2.3.21/2.4.0, javap-verified) recursively alongside
the expanded classifier for every cone type, including type arguments. Without it, an import of a
typealias (`import a.b.Alias`, e.g. dogfooding surfaced `FirFileChecker`/`FirFunctionCallChecker`,
which are typealiases over generic FIR checker base classes) never appears in `classifiers` at
all — only the alias's *expansion* does — so the alias import was always misreported unused.

**Hazard for the next FIR subtype added here:** `FirVisitorVoid` dispatches on a node's *exact*
declared type, not via inheritance — a direct subtype of an overridden type (e.g.
`FirResolvedCallableReference`, `FirPropertyWithExplicitBackingFieldResolvedNamedReference` under
`FirResolvedNamedReference`; `FirErrorResolvedQualifier` under `FirResolvedQualifier`) falls through
to `visitElement` (recurse-only) unless it has its own override, silently dropping the reference
rather than erroring — check every direct subtype of a handled type, not just the type itself, when
touching this visitor. `FirBackingFieldReference`/`FirDelegateFieldReference` (also direct
`FirResolvedNamedReference` subtypes) are deliberately left unhandled — they are self-references to
a property's own `field`/delegate storage and can never be import targets.

**As-built (`no-wildcard-imports` star expansion — the flagship resolution-powered fix):**
`NoWildcardImportsRule` is now a `WStreamRule` (`requiresResolution = true`, converted from its
original flag-only `WNodeRule` shape) assembling every import directive (star and explicit) off
the leaf stream via the shared `ImportDirectiveAssembler` (extracted from `NoUnusedImportsRule`
rather than duplicated — both rules need the same `IMPORT_DIRECTIVE`/`IMPORT_ALIAS`/`MUL`
bookkeeping), plus the file's own package FQN (`PACKAGE_DIRECTIVE` leaves) and every `KDOC` leaf's
span. The report for a star import fires unconditionally, exactly as before; only the attached
`WEdit` is conditional — the pure decision function `WildcardExpansionDecision.decide`
(unit-tested without a compiler) returns `null` for "report only" and a `WEdit` replacing the
directive's own `[start, end)` span with the ASCII-sorted, newline-joined explicit imports
otherwise (no trailing newline in the replacement — the directive's own line ending is untouched).

**Attribution** — a used symbol attributes to `import P.*` iff resolving it needs exactly one
top-level name under `P`: a classifier `P.X[.Y...]` attributes `P.X` (nested-class access is
always written qualified through its top-level owner — `Outer.Nested` in source only requires
`Outer` to be in scope, member navigation resolves `.Nested` from there); a top-level callable
(`classFqName == null`) with `packageFqName == P` attributes `P.name`; a member callable with
`classFqName == P.X[.Y...]` attributes `P.X` (this is also how constructor calls attribute —
a constructor's `WCallableUsage` has `classFqName`/`name` equal to the class's own FQN/simple
name, so `Widget()` attributes `P.Widget` via the same rule as any other member). A symbol already
covered by an existing **non-aliased** explicit import of the same FQN is excluded from the
replacement (that import already brings its plain simple name into scope; the star's other
attributed symbols still expand). Symbols reachable only via Kotlin's default imports still
attribute if their resolved parent is `P` — no special-casing for default-imported packages
(`kotlin`, `kotlin.collections`, ...): expanding a redundant star of one into explicit imports is
compile-preserving and harmless.

**Syntactic gate (dogfooding finding, added after the two correctness-bug fixes described
below):** classifier and
member-callable attribution additionally require the attributed symbol's own simple name to
appear as a written `IDENTIFIER` leaf somewhere in the file body (outside import directives and
the package directive — `NoWildcardImportsRule` collects this set off the same leaf stream it
already walks). `WResolvedUsage` records every type the compiler's inference touches, not just
the ones the author typed — a member chain (`a.b.doSomething()`) resolves through `b`'s type
without `b`'s class ever appearing as an identifier, and an implicit loop/lambda variable type is
a real classifier reference with no token in the source at all. Dogfooding on
`ResolvedUsageCollector.kt` itself (collapsing its seven `fir.types` imports to a star and running
`wrasseFix`) found exactly this: the fix reconstructed all seven original imports correctly but
added two superfluous ones, `ConeClassLikeLookupTag` and `ConeTypeProjection`, for types never
named in the source — one via a member chain (`coneType.lookupTag.classId`), the other via a `for`
loop variable's implicit type (`for (typeArgument in coneType.typeArguments)`). Compilable and
idempotent, so not trust-burning like the two correctness bugs described below, but importing a
name that is never written is over-expansion no IDE would produce. Top-level callable attribution
is **not** gated: operator conventions (`+` desugars to a member named `plus`, destructuring to
`componentN`, `()` call syntax to `invoke`) legitimately need an import whose name never appears
as a written identifier at all — gating those would trade over-expansion for a broken compile,
strictly worse. A constructor call needs no special case here: writing `Widget()` writes the
identifier `Widget`, so the gate passes naturally. The gate and the alias-exclusion fix below are
independent and either is sufficient alone: a symbol reachable only through an alias (never
written under its plain name) is now dropped by the gate regardless of the alias-exclusion logic,
which only matters when the plain name *is* separately written (via some other qualified
reference) — locked by two distinct fixtures (`alias-attribution-error`, gate drops the alias-only
symbol; `alias-plus-qualified-attribution-error`, both mechanisms coexist and the symbol survives
because it's also separately written).

**Post-ship review found two real correctness bugs — both producing wrong code, not just a wrong
report — fixed before this shipped further:**

1. **Alias-blind exclusion.** The exclusion above originally matched *any* explicit import's FQN,
   alias or not. `import a.b.X as Y` binds only the name `Y` — it never brings plain `X` into
   scope — so excluding `a.b.X` from the replacement just because an aliased import of it existed
   could drop the plain import a bare `X` elsewhere in the file still needed, an outright broken
   compile. Fixed: only a **non-aliased** explicit import of the same FQN excludes it now. A plain
   `import a.b.X` and an aliased `import a.b.X as Y` of the same target were confirmed to legally
   coexist (harness-driven real-compile probe: zero diagnostics), so including both is always
   safe, never a bail. Locked by `alias-attribution-error` (aliased import of one attributed
   symbol used only via the alias, plus a second, star-only symbol — the alias's own explicit
   import never satisfies a *bare* reference to its target, a genuine Kotlin resolution quirk
   confirmed empirically alongside the fix, so no fixture can combine a bare and an aliased
   reference to the same target under one star without the file already failing to compile on its
   own; the fixture instead proves the FQN is still correctly attributed and expanded).
2. **Simple-name collision from qualified-only usage (new 7th bail).** `WResolvedUsage`'s
   classifier set cannot distinguish "resolved because this star brought the name into scope" from
   "resolved via full qualification, needing no import at all" — `FirResolvedQualifier` records a
   classifier for `P.X.member()` (fully qualified) exactly like it would for a bare `X` the star
   actually provides. Emitting `import P.X` for a qualified-only usage can (a) conflict with an
   existing explicit import of some other `Q.X` — confirmed empirically: "Conflicting import:
   imported name 'X' is ambiguous" followed by "Unresolved reference" at every use — or (b)
   silently *flip* what bare `X` already resolves to, the worst outcome: confirmed empirically with
   a same-named non-generic class shadowing `kotlin.collections.List` once explicitly imported,
   turning `List<Int>` into "No type arguments expected" with no diagnostic pointing back at the
   fix. Fixed with a new bail: build a simple-name → FQNs map from *every* used classifier (last
   segment) and callable (top-level by name; member by `classFqName`'s last segment) in the whole
   file — not just this star's attribution. If an attributed symbol's simple name maps to any FQN
   other than itself, bail the whole star. This also catches cross-star collisions (two stars
   attributing the same simple name from different packages) for free, since the map spans the
   whole file's usage, not one star's. Over-bailing here is intentional — a wrong expansion is the
   one outcome this rule may never produce. Locked by `qualified-usage-conflict-bail-error` and
   `qualified-usage-flip-bail-error` (both report-only, no companion — confirmed to fail-first via
   a dedicated real-compile spec, `WildcardExpansionAmbiguitySafetySpec`, since the standard
   fixture assertions check only the diagnostic's message/location, which is identical whether an
   edit is wrongly attached or correctly withheld). All four pre-existing expansion fixtures with
   `.fixed.kt` companions were re-verified to still expand normally — no existing case had a
   genuine collision.

Reproducing bug 2 exposed a real gap in the harness itself: `IdempotenceCycle`'s round-2 recompile
only ever checked `wrasse:`-prefixed diagnostics, so a wrong edit that breaks compilation via a
*non*-wrasse compiler error (conflicting import, unresolved reference, type mismatch) passed
silently — nothing in the standard cycle would have caught either bug's fixture without this. Added
`IdempotenceCycle.assertPatchedFileCompiles` (filters to non-`wrasse:` `ERROR`-severity
diagnostics) but did **not** wire it into `runIfFixEmitted`'s default path: several existing
fixtures compile with `noJdk = true` and trip unrelated, pre-existing `Cannot access '...'`/
`Unresolved reference 'java'` diagnostics from that classpath choice alone (confirmed while first
trying the global wiring — enum supertypes need JDK classes noJdk doesn't provide), which would
have been flagged as false positives across the whole suite. Called explicitly instead, only by
`WildcardExpansionAmbiguitySafetySpec`, which drives compile → fix → reapply → recompile directly
for exactly the two ambiguity shapes above (per-Kotlin-minor, like every other hand-written
compile-driven spec in this suite).

**The eight bails** (report fires, no edit — every ambiguity resolves toward "don't touch it"),
each locked by a fixture in `no-wildcard-imports-expansion/`. **Bail 2 superseded 2026-07-19** —
see "Member-star expansion" below for the current, generalized replacement (an authoritative
resolved-import classification instead of an outright bail); bails 1 and 3–8 are unchanged:
1. **Whole-file.** `ctx.resolvedUsage == null` or `hasResolutionErrors` — the rule never even
   calls the decision function, every star in the file reports with no edit.
2. ~~**Class/object-star** — superseded 2026-07-19 by member-star expansion (below); previously an
   unconditional bail whenever a used callable's `classFqName` equalled `P` exactly.~~
3. **Zero attribution.** An unused star is `no-unused-imports`/engine territory, not expansion.
4. **Shared line.** Reuses `ImportRemovalSpan`'s same-line check, now extracted into a shared
   `ImportLineSpan.isAloneOnLine` (both rules need the identical blank-prefix/blank-suffix scan;
   this is the one extraction judged to genuinely reduce duplication, not gold-plated further).
5. **Own package.** `P == filePackageFqName` is a degenerate star (everything already resolves
   without it).
6. **Duplicate stars.** Two identical `import P.*` directives in one file both bail — which one is
   "the" import of `P` is ambiguous.
7. **Simple-name collision.** See "post-ship review" above — an attributed symbol whose simple
   name is also reachable, under a different FQN, from somewhere else in the file's usage (a
   qualified-only reference, a default import, another star) bails the whole star.
8. **KDoc bracket references.** KDoc `[Name]`/`[qualified.Name]` links resolve through imports
   invisibly to FIR — a name mentioned only in a doc comment never appears in `resolvedUsage` at
   all, so it can silently lose its only path to resolution if the star is expanded without it.
   A reference's leading segment (before its first `.`) must be covered by explicit imports'
   visible names (alias if present, else simple name), this star's own attributed simple names, or
   any *other* star's own attributed simple names, else that star bails. Coverage deliberately
   excludes the file's own top-level declaration names — a second declaration-collecting pass is
   not "cheaply available" from this rule's leaf-stream assembly, so that source is skipped
   outright rather than approximated; skipping it only ever produces *more* bails, never a false
   "covered". **Bug found and fixed 2026-07-20 (wave-2 installment-3 backfill):** the cross-star
   half of this (folding in *other* stars' attribution, not just this star's own) was missing
   entirely — `WildcardExpansionDecision.decide` only ever checked its own star's `coveredNames`,
   unlike `UnusedStarDecision.decide`'s already-correct `allStars`-aware loop (§8.2's "Fixtures"
   list below). This was not just an inconsistency: with two stars in a file and a KDoc reference
   covered only by the *other* star's attribution, pass 1 would bail this star (uncovered by its
   own attribution alone) while the *other* star still expanded into explicit imports — at which
   point the reference becomes covered by an explicit import file-wide, so pass 2 no longer bails
   and expands the first star too, an outright D19 idempotence violation
   (`fix(fix(x)) != fix(x)`) caught by a fixture built to probe exactly this shape
   (`no-wildcard-imports-expansion/cross-star-kdoc-coverage-shared-error`) before it shipped.
   Fixed by threading `allStars` into `WildcardExpansionDecision.decide` and folding every other
   star's own attribution into `coveredNames`, mirroring `UnusedStarDecision` exactly — both stars
   now correctly expand in one pass once the fix lands.

**Fixtures:** `no-wildcard-imports-expansion/` (own `wrasse.json`, only `no-wildcard-imports`
enabled) covers all four expansion shapes with `.fixed.kt` companions — a package star over an
aux package with a subset of symbols used (class, top-level fun, nested-class access attributing
the outer, constructor call, enum entry access), a star alongside a pre-existing explicit import
of one of its own attributed symbols (excluded from the replacement), two independent stars over
two different aux packages expanding in one pass, and a single-file stdlib case
(`import kotlin.math.*` with `abs`/`PI`, locking top-level-callable and property attribution
without any aux file), the alias-attribution shape above (an aliased explicit import plus a
second, star-only symbol), the alias-plus-qualified shape (same alias, plus a separate fully-
qualified reference that writes the plain name, so the gate lets it survive), a member-chain and
loop-variable inference case (`inference-only-classifier-gated-error` — one aux class accessed
only through another's property, one only through a `for` loop variable's implicit type; the
expansion includes only the class whose constructor is actually written, dropping both
inference-only ones), and an operator/destructuring regression guard
(`operator-convention-ungated-error` — a starred package's extension `operator fun plus` and two
extension `componentN` functions used only via `a + b` and `val (x, y) = a`; the expansion
includes all three even though none of their names are ever written, pinning that top-level
callable attribution stays ungated) — plus one report-only fixture per bail, including both
simple-name-collision shapes above, no companion (a bail fixture makes no edit, so the harness's
idempotence cycle never triggers for it — nothing to re-verify). A
separate `imports-full/` dir runs `no-wildcard-imports` + `no-unused-imports` + `no-semicolons`
together on one file (star expansion + an unrelated unused explicit import + a trailing
unnecessary semicolon), proving the three rules' edits stay disjoint and compose correctly without
an `ImportEngine` — every inserted import is used by construction, so the expansion output never
trips `no-unused-imports`. Multi-file fixtures needed a harness fix: `IdempotenceCycle`'s round-2
recompile previously passed only the patched primary file, never the fixture's aux sources — fine
for `no-unused-imports` (the removed import's usage always vanishes with it) but wrong here, since
an expanded star's explicit imports are of symbols the file *still uses*; `runIfFixEmitted` now
takes an `auxSources` parameter and recompiles round 2 with them present.

**Fusion status (2026-07-19):** the description below through "Fixtures" (end of this subsection)
records `import-ordering` as it shipped originally, standalone, before the `ImportEngine` fusion
(further below) folded it together with `no-unused-imports`/`no-wildcard-imports` into one
decision-maker — the sort key, clean-list check, and composition mechanics described here are
unchanged by that fusion; only *how* the three rules reach each other's edits changed (see
"Retired" note below). Import re-sorting after expansion, and zero-attribution star removal (bail 3
above), were never deferred: `import-ordering` composes over `no-wildcard-imports`'s edits and
`no-unused-imports` owns star removal directly (reusing this rule's attribution computation), both
from the original three-rule shape onward. Genuinely new capability at the time this fusion
shipped — member-star expansion, FQN-shortening/insertion, the KDoc same-package coverage gap — is
covered where each stands today: member-star below; the other two in the "Extension points" note
closing the `ImportEngine` paragraph further down.

`import-ordering` is plain ASCII-alphabetical order on the file's import directives, no grouping, no
config knob, purely syntactic (never sets `requiresResolution`). Originally a fourth independent
`WStreamRule` (`ImportOrderingRule`) recording each `IMPORT_DIRECTIVE`'s own span during the walk
(via `IMPORT_LIST`/`IMPORT_DIRECTIVE` enter/exit) and whether any `EOL_COMMENT`/`BLOCK_COMMENT`/
`KDOC` leaf was seen while `IMPORT_LIST` was still open; the verdict and every composition step are
pure, compiler-free functions (`ImportOrderingDecision`), unit-tested without a compiler. The **sort
key** is a directive's own node text with the leading `import` keyword and its following whitespace
stripped (`ImportOrderingDecision.sortKeyOf`) — `import a.b.C` sorts by `a.b.C`, `import a.b.C as D`
sorts by `a.b.C as D` (so aliased duplicates of the same FQN order deterministically by their
alias), `import a.b.*` sorts by `a.b.*`. Report fires once per file, at the first out-of-order
directive's own span, message `Imports are not sorted`; no report at all for zero/one directive or
an already-sorted list. **Bug found and fixed 2026-07-20 (wave-2 installment-3 backfill):** every
backtick character is now also stripped from the sort key (`` import a.b.`when` `` sorts by
`a.b.when`), matching ktlint's own `ImportSorter` comparator
(`import.toString().replace("\`", "")`) — found porting ktlint's real
`ImportOrderingRuleAsciiTest` suite, whose one backtick case (`org.mockito.Mockito.\`when\`` vs.
`...verify`) failed against wrasse's original raw-text `sortKeyOf`: the backtick character's own
ASCII value (0x60) sorts *before* every lowercase letter, so a backtick-quoted identifier sorted
earlier than intended relative to plain ones, silently treating an actually-unsorted list as
already sorted. Locked by `import-ordering/backtick-identifier-order-error`.

**Why this can't compose at its own `exitNode`:** `no-unused-imports`' removal and `no-wildcard-
imports`' expansion both decide their edits in `afterFile`, *after* `IMPORT_LIST` has already exited
the walk — their decisions need whole-file data (resolved usage), not just the list's own subtree.
D18's post-order composition (`exitNode` consumes inner edits already in the plan) assumes the inner
edit exists by the time the outer node exits; that assumption fails here, so `import-ordering` also
defers its own decision to `afterFile`. This is still why `ImportEngine` computes the whole import
family in one `afterFile` call today.

**The composition mechanics (unchanged by the fusion):** first, a purely textual "is this region
safe to touch" check (`ImportOrderingDecision.isCleanList`) — the list must be exactly `directive\n
directive\n...\ndirective` with no leading/trailing slack and no comment leaf recorded anywhere
inside it. A comment (ownership of which directive it documents is ambiguous once reordered), a
blank line, or two directives sharing one line (semicolon-separated) all fail this check — no inner
edits are ever taken in that case, so any edits `no-unused-imports`/`no-wildcard-imports` already
placed inside the region are left completely untouched and flow through to `finalEdits()` on their
own (locked by `imports-full/comment-blocks-reorder-error`: a star expansion and an unused-import
removal both apply standalone while a comment sitting between two other, already-sorted imports
keeps `import-ordering` from touching anything at all — not even a report, since those two are
already in order). When the region *is* clean, taking pulls out whatever inner edits exist within
`[listStart, probeEnd)` (see the containment predicate below); if none, `import-ordering` behaves
like any other rule (report + a single re-sort edit, only if the original order was wrong). If it
took any edits, it **always** emits its own composed edit — replacing the whole taken span with the
edits applied and the result re-split-and-sorted — even when the original, pre-edit order happened
to be sorted already, because the post-edit content (e.g. a star's multi-line expansion landing at
the star's old position) might not be.

**The containment predicate and the last-directive hazard (still exact today, only where it runs
changed):** an edit is taken into the composed replacement only when its own span sits fully inside
the probed region — `start <= edit.startOffset` and `edit.endOffset <= end`, inclusive of the
boundary, but an edit whose `endOffset` runs past `end` is left untouched, no partial taking.
`ImportRemovalSpan`'s whole-line deletion consumes a directive's own trailing `\n` — for every
directive except the list's own last one, that trailing `\n` sits comfortably inside `[listStart,
listEnd)` because another directive follows it. For the *last* directive, that same trailing `\n`
separates the import list from whatever comes after it — **outside** `IMPORT_LIST`'s own node span
(confirmed empirically, not assumed: `IMPORT_LIST`'s reported `endOffset` equals its last child
directive's own `endOffset`, never reaching into trailing whitespace). Probing only `[listStart,
listEnd)` would therefore strand that one deletion edit outside the taken region, overlapping the
composed whole-list replacement at apply time. Fixed by `probeEnd`, extending `listEnd` to the end of
the line it sits on (reusing the shared `ImportLineSpan.indexOfNewlineFrom` line-boundary utility)
whenever such a line exists, so the last directive's trailing-newline-inclusive deletion is captured
too; the composed replacement then preserves that trailing `\n` when the probed region had one. The
probe only ever extends into text that is unambiguously either blank or exactly this one hazard, so
it never over-reaches into an unrelated rule's edit. Locked by `imports-full/last-directive-removed-
error`: the file's *last* import is the unused one, and the composed fix correctly consumes its
whole-line-including-newline deletion without any overlap, collapsing to the single surviving,
correctly re-sorted import.

**Bail after taking, when it can't be trusted:** if the reconstructed region doesn't parse as "zero
or more clean `import ...` lines" after applying the taken edits (a future, not-yet-imagined
composing rule producing something unexpected — none of today's rules can actually trigger this),
every taken entry goes back untaken (in reverse of the order it was taken, so descending-sequence
tie-breaking among equal-span entries is restored exactly as it was) and `import-ordering` reports
only if the *original* order was wrong, with no edit — never guessing. `ImportOrderingDecisionSpec`
locks this directly against a fabricated overlapping-edit input and a fabricated non-import
reconstructed line, without needing a real compile to trigger either.

**Retired 2026-07-19: three independent rules composing over the shared `EditPlan` in registration
order.** `no-unused-imports`, `no-wildcard-imports`, and `import-ordering` used to be three separate
rules; the taking step above ran as `EditPlan.takeEditsIn`, called by standalone `ImportOrderingRule`
against the shared, cross-rule `EditPlan` (an actual "self-consumption" of another rule's already-
reported edit), which only worked because `afterFile` fired in literal registration order —
`LightTreeStreamAdapter.walk` calls `rule.afterFile(...)` in `dispatch.allRules` order, itself
`StreamDispatch`'s construction order, itself `WRuleSet.activeRules`' order, itself `wrasseMain`'s
iteration of `config.rulesConfigs.idToConfig` (a `LinkedHashMap` whose insertion order traces back to
the literal `listOf(...)` in `WrasseKotlincPluginMain.kt`) — so `ImportOrderingRule` had to stay
registered after `NoWildcardImportsRule` and `NoUnusedImportsRule` for its `takeEditsIn` call to see
their edits (locked, at the time, by a dedicated `RuleRegistrationOrderSpec` test). `ImportEngine`
(below) is one object with one `afterFile` computing all of the above directly against its own
in-memory pending-report list before anything is reported — nothing to register relative to itself,
no shared plan to consume, no ordering dependency left to violate. `RuleRegistrationOrderSpec` still
exists but now locks something unrelated to ordering — the engine's declared id set (§4's "Multi-id
engines").

**Fixtures:** `import-ordering/` (rule alone) covers unsorted → sorted, already-sorted → clean,
aliased imports (same FQN, different alias, ordering by alias), a comment inside the list
(report, no edit), a blank line inside the list (report, no edit), and a single import (clean).
`imports-full/` extends its existing three-rule showcase with `import-ordering` — one `wrasseFix`
pass now expands the star, removes the unused import, and re-sorts the surviving imports, alongside
the pre-existing `no-semicolons` fix, all in one pass — plus the two edge-case fixtures described
above (comment blocks reordering while removal/expansion still apply; the dangling last-directive
deletion).

**As-built (`no-unused-imports` star removal, closing the last import-autofix gap):** a
zero-attribution `import P.*` — a star providing nothing — used to be flagged by
`no-wildcard-imports` but never auto-removed (`no-unused-imports` skipped stars entirely, §8 above).
`NoUnusedImportsRule` now also tracks star directives, the file's package FQN, and every written
identifier — the same bookkeeping `NoWildcardImportsRule` already does — and calls a new pure
decision function, `UnusedStarDecision.decide` (unit-tested without a compiler). Its attribution
computation is *reused, not duplicated*: `WildcardExpansionDecision`'s attribution logic (the
written-identifier gate, the ungated top-level-callable/operator-convention rule, the member-star
check) was extracted into a shared `StarAttribution` object both rules call. A star is removed iff:
its attributed set — filtered by the same non-aliased-explicit-import exclusion expansion applies —
is empty (**superseded 2026-07-19**: a member-star's attributed set is `StarAttribution
.attributedMembers`'s return value directly — see the member-star expansion as-built above for why
a skipped non-static member usage correctly leaves this empty rather than disqualifying the star;
a package-star's is unchanged); its own
package is not the file's own package (redundancy, not unusedness — deferred to the engine, see
below); and no KDoc bracket reference is left uncovered by every *other* source (explicit imports
plus every *other* star's own attribution — this star contributes nothing itself, so it cannot cover
anything). Whether an edit is attached is unchanged: `ImportRemovalSpan`'s existing alone-on-line
policy — report always fires, edit `null` when the directive shares its line with something else,
the same D19 survivor shape as an unused explicit import. Duplicate identical zero-attribution stars
are *not* bailed here (unlike expansion's duplicate-package bail, which exists only to pick one
expansion target) — each is decided independently, so both come back removed with their own disjoint
whole-line edits.

With both rules enabled, an unused star now produces two reports (`no-wildcard-imports`'s
unconditional one plus this rule's "Unused import") but only one edit — expansion's own
zero-attribution bail never emits a competing edit for the same span, so there is no `EditPlan`
overlap; `import-ordering` consumes the removal via `takeEditsIn` like any other whole-line deletion.

**Still out of scope, deliberately (a growth site for `ImportEngine`, not yet built):** an
own-package star (`P == filePackageFqName`) is always redundant but is left untouched here —
that's a different judgment call (harmless-but-pointless vs. "provides nothing") the engine is
meant to own eventually, not something either half of `no-unused-imports`/`no-wildcard-imports`
decides today even though they are now one object. A star that is redundant only because every
attributed symbol already has an explicit import (a "default-redundant" star, as opposed to a
*zero*-attribution one) also stays untouched by `no-unused-imports` — its attribution is non-empty,
so it is correctly out of this rule's scope by construction, and remains a `no-wildcard-imports`
report-only finding until the engine grows this capability.

**No exclude/allowlist knob, by design — confirmed against upstream 2026-07-20 (wave-2
installment-3 backfill):** ktlint's `ij_kotlin_imports_layout`-adjacent
`ij_kotlin_packages_to_use_import_on_demand` editorconfig property defaults to allowing
`java.util.*`/`kotlinx.android.synthetic.**` unflagged outside `ktlint_official` style, and
detekt's own `WildcardImport` rule ships an `excludeImports` config defaulting to
`listOf("java.util.*")` — both mean neither upstream tool flags `java.util.*` out of the box.
Wrasse has no such knob at all and flags every star unconditionally, matching only ktlint's
strictest (`ktlint_official`) mode and diverging from both tools' actual defaults — a deliberate
consequence of the project's minimal-config stance (no per-package exclude lists, no
`ktlint_official`-style presets), not an oversight.

**Fixtures:** `no-unused-imports-star/` (own `wrasse.json`, only `no-unused-imports` enabled) covers
a zero-attribution star alongside a used explicit import (removed, `.fixed.kt` companion), a star
attributed only through an ungated operator convention from the starred package (out of scope,
`expect-clean`), a zero-attribution star with an uncovered KDoc reference (out of scope,
`expect-clean`), a same-line zero-attribution star (report fires, no edit — D19 survivor), two
duplicate zero-attribution stars (both removed, companion), and an own-package star (out of scope,
`expect-clean`). `imports-full/` gained one more fixture: an unused star alongside an unused
explicit import and an unsorted surviving pair — one `wrasseFix` pass removes both and re-sorts,
and the star's own line pins the two-reports-one-edit interaction directly (`expect-error` lines
for both `no-wildcard-imports` and `no-unused-imports` at the same span).

**Known practical limitation (dogfood-observed, deliberate):** the KDoc-coverage bail cannot vouch
for a bracket reference to a *same-package* symbol declared in a sibling file (e.g. `[WContext]`
inside `WNodeStack.kt`'s KDoc) — such a name appears in no coverage source at all when the file's
own code never uses it, so any star in that file stays report-only. Real-world files KDoc-reference
same-package types constantly, so this bail suppresses star removal often. Closing it needs
package-member knowledge beyond the current file — a session-backed symbol-provider query (or a
compile-wide package→declarations view) — which is ImportEngine-scope facade work, not a rule-side
heuristic. Tracked as an explicit engine requirement.

**As-built (`ImportEngine`, the fusion — 2026-07-19, a pure refactor of everything above, zero
fixture changes):** `no-unused-imports`, `no-wildcard-imports`, and `import-ordering` are now one
`WStreamRule` behind `WUninitializedRuleGroup` (§4, "Multi-id engines"), replacing the three
independent rules described throughout this section. Model: `ImportEngine.ids` declares all three
ids; `WRuleSet` gives `initGroup` exactly the enabled, non-excluded-for-this-file subset as a
`Map<id, WrasseRuleConfig>`; an id absent from that map behaves as if its rule does not exist for
this file, preserving today's independent per-id enable/exclude exactly (the `no-unused-imports-star/`,
`no-wildcard-imports-expansion/`, and `import-ordering/` fixture dirs each enable only one id and
still pass unmodified). `requiresResolution` is true iff `no-unused-imports` or
`no-wildcard-imports` is among the enabled ids — `import-ordering` alone still never triggers FIR
usage collection, matching the old per-rule gating bit for bit. Severity stays per-id: the engine
builds one small facade `WRule` per surviving id (`id`/`config` only, never registered with
`StreamDispatch`) and passes the matching one to `WReporter.report(...)` for each diagnostic, so
`WReporter`'s contract needed no change — the smallest honest fix for "one engine, several
severities" the design constraints asked for. One walk-side assembly now collects import
directives (explicit and star), every directive's own span (for ordering), comment/KDoc spans,
written identifiers, and the package FQN exactly once; the pure decision objects
(`ImportDirectiveAssembler`, `UnusedImportDecision`, `ImportRemovalSpan`, `UnusedStarDecision`,
`StarAttribution`, `WildcardExpansionDecision`, `ImportOrderingDecision`, `ImportLineSpan`) are
unchanged — the engine calls each once in `afterFile`, in the same relative order the three rules
used to run in (this only matters for the one fixture with two diagnostics at an identical span,
`imports-full/unused-star-with-unsorted-remainder-error`: `no-wildcard-imports`' report must still
precede `no-unused-imports`' at a zero-attribution star's position). Composition: the engine
collects every explicit-unused/star-unused/expansion decision into an in-memory pending-report
list first (message, span, and a possibly-null `WEdit`, not yet handed to `WReporter`), then — if
`import-ordering` is enabled and the list is clean (`ImportOrderingDecision.isCleanList`) — applies
`EditPlan.takeEditsIn`'s exact containment predicate to that local list instead of the shared
`EditPlan` (no rule-to-rule bus needed inside one object) to decide which pending edits fold into
one composed replacement versus stay individual, then reports every pending decision plus, if
composition ran, one extra report for `import-ordering` itself — reproducing the old
report-then-`takeEditsIn`-consumes-the-edit two-step as a single decide-then-report step with
byte-identical diagnostics and patch output. `RuleRegistrationOrderSpec` (`app/wrasse-kotlinc-plugin`)
now locks `ImportEngine.ids` instead of a registration order that no longer exists.
**Extension points named at the time of this fusion:** FQN-shortening/import insertion (adds text a
user never wrote, unlike everything above) and closing the KDoc same-package-sibling coverage gap
(needs a session-backed package→declarations query the engine would own as a facade, not a
rule-side heuristic — the "Known practical limitation" above) were both still open; member-star
(class/object) expansion, the third item on this list, shipped 2026-07-19 — see below.
FQN-shortening/insertion is done too now (the FQN→import track, D.1–D.3, further below); the KDoc
gap is the one item still outstanding — see that track's own conclusion for current status.

**As-built (`WResolvedImport` facade extension and member-star expansion — 2026-07-19):**
`WResolvedUsage.resolvedImports: List<WResolvedImport>` (`wrasse-model`, zero kotlinc deps) adds
the file's own import directives as FIR resolved them, one entry per directive in source order
(duplicates included, no alias — rule code already tracks that syntactically): `fqn` (the star's
own target with no trailing `.*`, or the full imported name), `isStarImport`, and
`resolvedParentClassFqName` — non-null iff the import's parent resolves to a class/object. For a
*star* import this "parent" is empirically the star's own target (`FirImportResolveTransformer`
calls the same `packageFqName`/`relativeParentClassName` split on the star's own FQN, not its
parent, confirmed by reading the transformer directly) — so `resolvedParentClassFqName == fqn`
whenever a star is a member-star, giving an authoritative, non-inferred package-vs-member answer.
`internal/ResolvedUsageCollector` builds this from `FirFile.imports`; an import whose
`importedFqName` is null or root is dropped (nothing to key on); one that never became a
`FirResolvedImport` is carried through with `resolved = false` and `resolvedParentClassFqName =
null` — javap-confirmed byte-identical API surface (`FirImport`, `FirResolvedImport`, `FirFile
.imports`) across 2.1.21/2.2.21/2.3.21/2.4.0. `dumpResolvedUsage`'s message gained an ASCII-sorted
`imports=[...]` segment (e.g. `sample.aux.*`, `sample.aux.Status.*(parent=sample.aux.Status)`,
`p.Q.member?unresolved`).

`StarAttribution.classify` uses this to decide package-vs-member per star, cross-checked against
the pre-existing usage-based `isMemberStar` inference (a callable whose `classFqName` equals the
star's own FQN) as a cheap defensive check: no matching resolved import, an unresolved match, or a
disagreement between the two signals (authoritative says package but the cross-check says member)
all collapse to `UNRESOLVED_OR_AMBIGUOUS` — bail, never guess. Agreement in the other direction (a
member-star with the cross-check finding nothing) is not a contradiction — it is a member-star with
zero, or classifier-only, attribution.

**Legality matrix (empirically probed against a local kotlinc build before locking any of this
— the reference for every shape this feature claims legal or illegal):**

| Star shape | `import Owner.*` itself | Explicit `import Owner.member` |
| --- | --- | --- |
| Enum class (entries) | legal | legal |
| Enum class (ordinary instance member/property) | legal | **illegal** — "Functions and properties can only be imported from packages or objects" |
| Plain class (nested classifier) | legal | legal |
| Plain class (instance member) | legal (syntax) but brings nothing in bare | **illegal** |
| Plain Java class (static member) | legal, brings statics in bare | legal |
| Kotlin `object` (any member) | **illegal** — "cannot import on demand from object" | legal |
| Companion object (any member, via `Owner.Companion.*`) | **illegal** — same "on demand from object" error | legal, but only via `import Owner.Companion.member` (`import Owner.member` alone does **not** resolve a companion member) |

Two structural consequences follow directly, so `attributedMembers` needs no `ClassKind` facade
data at all: (1) since `import Object.*`/`import Companion.*` never compiles, a member-star's own
target can never be an object/companion in a file whose resolution didn't already error — the
"object star" fixture (`object-star-bail-error`) locks this the same way `resolution-error-bail-
error` does, via the existing whole-file bail, not a new mechanism; (2) since a companion member's
own `classFqName` is the companion's FQN, not the outer class's, it can never attribute to a plain
`import Outer.*` star's exact-match check regardless. That leaves exactly one per-member legality
question `attributedMembers` (`StarAttribution`) must decide: is a used callable member (`classFqName
== C` exactly) an enum entry or Java static, or an ordinary instance member? `WCallableUsage`
gained `isStatic: Boolean` (from `FirCallableSymbol.isStatic`, `org.jetbrains.kotlin.fir
.declarations.utils`, javap-confirmed identical across all four minors) precisely because the FIR
raw-fir builder marks enum entries `isStatic = true` at construction (`PsiRawFirBuilder
.toFirEnumEntry`, read directly, not inferred) — the same bit Java statics carry, and the same bit
the compiler's own `getImportStatusOfCallableMembers` checks for a non-singleton owner. A nested
classifier (`classifier` starting with `C.`) is always legal (no `isStatic` check needed — nested-
class import never depends on static-ness). An attributed callable with `isStatic == false` is
**skipped**, not disqualifying: import-on-demand from a classifier only ever exposes
statics/enum-entries/nested-classifiers (empirically confirmed above — a non-static member never
legally reaches bare scope through any member-star, under any circumstance), so a usage recorded
against `C` that isn't one of those *by construction* resolved some other way — a receiver
(`x.instanceMember()`, needing no import of `C` at all) or a same-`classFqName` constructor call
(`C()`, needing `C` itself in scope via some other mechanism, never this star) — and is therefore
simply not this star's business, expanded or not. `WildcardExpansionDecision` and
`UnusedStarDecision` both just exclude it from the attributed set; a star whose *only* associated
usage is such a skipped member ends up with an empty attributed set — correctly expansion's "zero
attribution" bail, and correctly `no-unused-imports`' "removable" — since that usage never needed
the star at all. Locked by `enum-star-with-instance-method-error` (entries still expand; a custom
instance method called on one is untouched) and `unused-star-instance-only-usage-error`
(instance-member-only usage → the star is removed as unused).

**Fixtures:** `no-wildcard-imports-member-star/` (own `wrasse.json`, `no-wildcard-imports` only) —
an enum star with a used subset of entries (expansion, `.fixed.kt`), an illegal object star
(whole-file bail via the pre-existing resolution-error mechanism, no companion), an enum star with
entries used alongside a receiver-called custom instance method (expansion of the entries only, the
method call untouched, `.fixed.kt`), a class star whose only attributed usage is a nested classifier
(expansion, `.fixed.kt`), and a member-star attribution colliding in simple name with an unrelated
fully-qualified usage (bail, reusing the existing collision check unmodified). `no-unused-imports-star/`
gained `unused-member-star-error` (a zero-attribution enum star, removed — `.fixed.kt`) and
`unused-star-instance-only-usage-error` (a star whose only usage is a skipped instance member,
also removed — `.fixed.kt`); the pre-existing `member-star-used-clean` fixture (a *used* member-star,
`no-unused-imports` leaves it alone) needed no change. `imports-full/` gained
`member-star-with-ordering-error`: a member-star expansion composing with `import-ordering`'s
re-sort in one `wrasseFix` pass, same composition mechanism as a package-star's. Zero existing
package-star fixture changed.

### 8.3 The FQN→import track (D.1–D.3: `no-unnecessary-fqn`)

**As-built (LightTree↔FIR offset-correlation spike — D.1 of the FQN→import track, 2026-07-19):**
first of a three-part feature (D.1 this spike / D.2 a report-only rule, done below / D.3 a fix,
still ahead); this part proves the offset-correlation bet with zero user-visible change — no rule
logic, no rewrites. `WResolvedUsage.qualifiedUsages: List<WQualifiedUsage>` (`wrasse-model`, zero
kotlinc deps) adds `WQualifiedUsage(startOffset, endOffset, targetFqName, packageFqName, kind:
WQualifiedUsageKind)` (`QUALIFIER` | `TYPE_REF`; `packageFqName` is a D.2 addition, see below), one
entry per `FirResolvedQualifier`/`FirResolvedTypeRef` (class-like
cone type only) whose own `source` is a **real** element —
`source.kind === KtRealSourceElementKind`, javap-confirmed byte-identical bytecode surface
(`KtSourceElement.getKind()`, `AbstractKtSourceElement.getStartOffset()`/`getEndOffset()`,
`KtRealSourceElementKind` as a singleton object) across kotlin-compiler-embeddable
2.1.21/2.2.21/2.3.21/2.4.0 — no per-minor branching needed. `ResolvedUsageCollector`'s
`UsageVisitor` records these alongside its existing classifier/callable collection
(`recordQualifierUsage`/`recordTypeRefUsage`, both funneling through one defensive `recordUsage`
that also skips a null/negative/inverted span rather than throwing, matching the collector's
existing whole-file `runCatching` posture). `FirFile.imports`/`packageDirective` are still separate
fields the visitor never visits (unchanged from the resolution-facade spike above), so import/
package-directive text self-evidently never contributes a qualified usage. **The bet holds, with
zero divergence found:** a single fixture (`QualifiedUsageCorrelationSpec`, `tests-base` + one
subclass per minor, the established per-minor pattern) compiles one source file plus an aux
cross-package file and asserts the **exact** `qualified=[...]` dump segment byte-for-byte identical
on all four minors — same spans, same order, same targets — including a hand-verified span
(`17..29:TYPE_REF:sample.aux.A` for `@sample.aux.A`, confirmed by counting UTF-16 code units from
file start: `"package sample\n\n@sample.aux.A\n..."`, offset 17 lands exactly on the `s` of
`sample.aux.A` after the `@`) proving these are the same raw LightTree offsets the SAX walk itself
reports (`ViolationReport`'s own offset convention), not a separately-mapped coordinate space. The
same fixture locks the four **hazard** constructs (a `for` loop over a range, a destructuring
declaration, a string template, an `if` used as an expression) as producing **zero** qualified-usage
entries — their desugared machinery (`kotlin.collections.IntIterator`, `Pair.component1`/
`component2`, `_synthetic/WHEN_CALL`) still shows up in the pre-existing `classifiers`/`callables`
sets exactly as before, but every one of those FIR nodes carries a fake, not real, source, so
`recordUsage`'s kind check drops them by construction — no special-casing needed in the collector.
One honesty note carried over from the resolution-facade spike, restated because this is the first
consumer-facing use of it: `TYPE_REF` fires for **every** resolved class-like type ref with a real
source, written qualified or not (a bare `Regex` parameter type gets a `TYPE_REF` entry exactly like
a qualified `sample.aux.C` one) — judging "was this actually written with a dot in it" is explicitly
D.2's job (walk-side syntax), not this facade's; existing `ResolvedUsageDumpSpec` cases were updated
to their real, mostly-small `qualified=[...]` segments (a bare parameter/return type still yields a
`TYPE_REF` entry) rather than approximated. **Gating**, exactly mirroring `requiresResolution`'s
existing shape: `WUninitializedRule.requiresQualifiedUsages` / `WUninitializedRuleGroup
.requiresQualifiedUsages(enabledIds)` (both default false) aggregate into `WRuleSet
.requiresQualifiedUsages` (computed once, OR across rules and groups); `WrassePlugin.checkFile`
gates the collector call itself on `dumpResolvedUsage || requiresResolution ||
requiresQualifiedUsages` as before, and separately passes a `collectQualifiedUsages: Boolean`
(`dumpResolvedUsage || requiresQualifiedUsages`) through the now-parameterized `resolvedUsage`
lambda so the extra visitor work (and the list itself) is skipped entirely unless actually wanted —
at the time this spike shipped, nothing set `requiresQualifiedUsages` yet except dump mode, locked
by `WRuleSetSpec`'s aggregation tests plus the pre-existing `ResolvedUsageDumpSpec` "collect nothing
when dumpResolvedUsage is off and no rule requires resolution" case, which continues to assert zero
diagnostics (and so, by construction, an uncollected `qualifiedUsages`) unchanged (`no-unnecessary-fqn`,
below, is now the first real consumer). `dumpResolvedUsage`'s message gained a
`qualified=[start..end:kind:fqn, ...]` segment, ASCII-sorted by start offset then end.

**As-built (`no-unnecessary-fqn` — D.2 of the FQN→import track, originally report-only, 2026-07-19;
D.3 below attaches the fix the same day this document was next revised):** a fourth id on
`ImportEngine`, reporting a fully-qualified usage `a.b.C...` whose qualifier prefix `a.b.` could be
dropped given an existing or addable `import a.b.C`. Message: `"Unnecessary fully qualified name"`.
This section (through "Fixtures" below) describes the detection decision exactly as D.2 shipped it
and exactly as D.3 still uses it unchanged — precision-first, silent-skipping (no report at all) on
any ambiguity rather than guessing, per the same "bail-on-ambiguity" mandate that governs the rest
of the import family; D.3 (further below) adds no new detection, only the edits.

**Facade fidelity fix, found failing-first while starting this rule.** `recordTypeRefUsage`
recorded the typealias **expansion**'s `classId` for a `coneType.abbreviatedType`-bearing type
ref, while the span it recorded still covered the **written alias name** — a real span/target
mismatch, not just an imprecision. Caught by the pre-existing `ResolvedUsageDumpSpec` "dump both
the abbreviated (typealias) classifier and its expansion for a supertype-position usage" case,
which had been silently asserting the bug: `class Impl : BaseAlias()` (written) recorded
`qualified=[58..67:TYPE_REF:sample.aux.Base]` (the expansion) before the fix; the fix — using
`coneType.abbreviatedType ?: coneType` to pick the classId, matching `collectConeType`'s existing
recursion into the same attribute — changes that to `...:sample.aux.BaseAlias` (the abbreviation,
matching what is actually spelled at that span) with zero other change to the message, confirmed
by re-running the existing test before/after. A dedicated case was also added to
`QualifiedUsageCorrelationSpec` pinning the same fix on a fresh example (`sample.aux.WidgetAlias`
over `class Widget`).

**Second facade addition: `WQualifiedUsage.packageFqName`.** A flat dotted `targetFqName` string
cannot distinguish a package segment from a nested-class segment (`a.b.C.Nested` vs. a flat
`a.b.c.D` are indistinguishable by dots alone) — exactly the boundary this rule must never guess.
`packageFqName` (`ClassId.packageFqName`, empty string for the root package) gives the real split
straight from FIR, so the class actually worth importing is always `packageFqName` plus the first
segment of the FQN's relative-class part — the *outermost* class of the chain — never the nested
class itself. This is a deliberate deviation from "no new facade state, substring analysis
suffices" (which governs the *syntactic* proof below, not this FIR-side ground truth): guessing the
package/class boundary from string-splitting alone was judged less honest than the project's
existing precedent of reading the real split from `ClassId` (the same posture as every other
FIR-facade addition in this section).

**Detection semantics — all of the following must hold for a given `WQualifiedUsage`, else silent
skip, no report at all** (`QualifiedUsageDecision`, `libs/wrasse-rules`, compiler-free, unit-tested
without kotlinc):
1. **Syntactic proof.** The walk-collected `sourceText` substring at the usage's own span must
   literally spell the target: a `QUALIFIER` usage's span must equal `targetFqName` exactly (FIR's
   qualifier always stops at the class, D.1's finding, so span and target agree character for
   character whenever this holds — a backtick-quoted segment, an alias spelled differently,
   whitespace/a comment inside the chain, or partial qualification all fail this literal
   comparison); a `TYPE_REF` usage's span must *start with* `targetFqName` followed by end-of-span,
   `<` (generic args), or `?` (nullability) — confirmed against a real compile that a parameterized
   type's own resolved-type-ref span does include its generic argument list (`sample.aux.Box<Int>`
   as one 19-character span, hand-verified by offset), not just the raw classifier name.
2. **Package-prefix only, one canonical proposal per target.** The dropped span is always exactly
   `packageFqName + "."`; the kept remainder starts at the outermost class's own simple name. A
   nested chain (`a.b.C.Nested`) proposes `import a.b.C`, keeping `C.Nested` — never proposing to
   import the nested class itself. A member-qualified chain (`a.b.C.member`) is covered by the same
   mechanism naturally, confirmed empirically while building this rule's fixtures: FIR only ever
   records a `QUALIFIER` usage for an **object-like** reference (a bare object reference, or a
   member access through one) — a fully-qualified constructor call of a plain, non-object class
   (`a.b.Widget()`) produces **no** `WQualifiedUsage` at all (its qualifier/callable resolution is
   tracked elsewhere, invisibly to this facade), so there is nothing to bail on and nothing to
   propose for that shape; only a declared-type position (`TYPE_REF`) or an object-like reference
   (`QUALIFIER`) is ever a candidate.
3. **Import viability — the inverted collision analysis, in priority order** (`SimpleNameCollisionIndex`,
   extracted from `WildcardExpansionDecision`'s bail-7 logic and now shared by both):
   - a non-aliased explicit `import a.b.C` for the exact candidate already exists → report
     unconditionally, the cleanest case (pure redundancy, nothing else to check);
   - otherwise, skip **unconditionally** — same-package target or not, see below — if the
     candidate's simple name collides with either: another used FQN's own simple name anywhere in
     the file; or an explicit import's visible name (alias or plain) bound to a *different* FQN;
   - the candidate's package equals the file's own package → report, skipping only the next check
     (same-package needs no import in D.3, but is *not* otherwise a separate unconditional branch —
     see below for why);
   - otherwise (a new import is genuinely needed), also skip if the candidate's simple name is a
     written `IDENTIFIER` occurrence anywhere in the file *outside* every span this same target is
     known to occupy. This check needed a real fix, found dogfooding this rule's own fixtures, not
     hypothetically: a fully-qualified constructor call's own trailing segment (per point 2) is a
     written identifier at a position no `WQualifiedUsage` covers, so the extremely common
     `val x: pkg.Type = pkg.Type()` shape would otherwise self-trigger this bail against its own
     constructor call. Fixed by also treating every literal, word-bounded textual occurrence of the
     candidate FQN in `sourceText` as "this target's own span" (`QualifiedUsageDecision
     .literalOccurrences`) — still a written-text check, not a resolution claim, so it does not
     reopen the precision point 1 established.
   - otherwise, report (import-needed variant).

   **Same package is not a separate, unconditional branch — two bugs found in high-supervision
   review before this ever shipped, neither hypothetical.** A first draft returned "safe"
   unconditionally for any same-package candidate (Kotlin resolves same-package classes unqualified
   with no import needed, and a package cannot declare two top-level classes with the same simple
   name — both true, but incomplete on their own). **Bug 1:** an *explicit import* can still shadow
   a same-package sibling for bare-name resolution — confirmed empirically via a dedicated
   real-compile probe (package `p` with a sibling-file class `p.C`, plus `import q.C` and a bare
   `C().qOnly()` call in the same file): `dumpResolvedUsage` showed `callables=[q.C/C, q.C/qOnly]`,
   `errors=false` — the bare call resolved to the *imported* `q.C`, the same-package `p.C` never
   considered. Unconditionally trusting same-package would let an unrelated `import q.C` elsewhere
   in the file silently rebind what a shortened `C` means once D.3 writes it — exactly the
   wrong-fix-from-a-right-report outcome this whole track exists to prevent. Fixed by making the
   simple-name-collision checks above apply to same-package candidates too, never skipped for them.
   **Bug 2, the opposite direction, surfaced immediately once bug 1 was fixed:** a same-package
   class *declared in the current file itself* writes its own simple name as a written `IDENTIFIER`
   at the declaration site, a position no usage span covers — so the written-identifier-elsewhere
   check (previous bullet) flagged a class's own declaration of itself as a foreign collision,
   wrongly bailing a same-package usage that used to report correctly. Structurally, only a
   same-package target can ever have its own declaration living in the current file (a
   cross-package "new import needed" target never can, since a class's package is fixed by its own
   file's package declaration), so this false positive is only possible for same-package and never
   for the general case. Fixed by having same-package skip *only* that one check, once the two
   simple-name-collision checks above already passed — an asymmetric exemption, not a blanket one.
   Locked by `same-package-shadowed-by-import-skip-clean` (bug 1: skip) alongside
   `same-package-unshadowed-still-reported-error`/`same-package-redundant-error` (bug 2's own
   regression fixture: report, no conflicting import) as the paired control, plus three
   `QualifiedUsageDecisionSpec` unit tests pinning the same shapes directly.
4. **Deduplication.** Every usage sharing the same candidate import FQN is one "target" — the
   viability decision runs once per target, but every surviving usage still gets its own report at
   its own span.
5. **File-level bails**, shared with the rest of the engine: `ctx.resolvedUsage == null` or
   `hasResolutionErrors` skips the whole file (no `WResolvedUsage`, nothing to judge); KDoc spans
   are never scanned because they structurally can't be — `qualifiedUsages` is built from real FIR
   elements only, and KDoc text is never part of the FIR tree, so a name mentioned only in a doc
   comment can never produce a `WQualifiedUsage` in the first place (locked by a regression fixture
   rather than left implicit).

**Registration.** Fourth id on `ImportEngine` (`no-unnecessary-fqn`), gated by a new
`requiresQualifiedUsages(enabledIds)` override (true iff this id is enabled) — the first id to
actually set it, closing the loop the D.1 spike left open. `requiresResolution` is unchanged (the
gate gets `ctx.resolvedUsage` populated with `classifiers`/`callables` either way, per
`WrassePlugin.checkFile`'s existing three-way `dumpResolvedUsage || requiresResolution ||
requiresQualifiedUsages` gate). The walk-side assembly gained one more piece, gated on this id being
enabled: every written `IDENTIFIER`'s own offset (`IdentifierOccurrence`), the position-aware
sibling of the flat `writtenIdentifiers` set the other ids already collect. Not enabled in this
repo's own `wrasse.json` yet (dogfooding is a separate, deliberate step).

**Fixtures.** `no-unnecessary-fqn/` (own `wrasse.json`, only this id enabled): the import-needed
variant for a plain `TYPE_REF`, an object `QUALIFIER`, a member access through an object, a nested
class (package-only prefix dropped), and a parameterized type (generic-arg-inclusive span); the
already-imported and same-package (unshadowed) report variants; two usages of the same target both
reported independently; every skip condition — whole-file resolution-error bail, a backtick-quoted
segment, a root-package usage, a colliding classifier elsewhere, a colliding aliased import, a
colliding written identifier from the file's own unrelated declaration, an aliased import correctly
*not* counting as already-imported, a same-package usage shadowed by an unrelated explicit import
(the bug-1 regression fixture, above), and a KDoc-only mention never scanned at all. `imports-full/`
gained the id (report-only at the time, so no edit-composition risk with the other three then — D.3
below changes this) and one fixture proving its report coexists with the other ids' machinery
without interference. `QualifiedUsageDecisionSpec` (`libs/wrasse-rules`) unit-tests the pure
decision logic directly, compiler-free, including the same-package shadowing and self-declaration
shapes above. **All of the above is unchanged by D.3** — every fixture in this paragraph gained a
`.fixed.kt` companion (or stayed report-only/clean, for the bails) without a single expectation
line changing; D.3 automates exactly what D.2 already decided, adding no new detection.

**As-built (`no-unnecessary-fqn` fix — D.3 of the FQN→import track, closing it, 2026-07-19):**
`UnnecessaryFqnReport` gained `newImportFqn: String?` — non-null on exactly one usage per distinct
target (the one with the smallest `dropStart`, so the choice is deterministic regardless of FIR
traversal order) when that target needs a genuinely new `import` directive, `null` for every other
usage and for every target needing no import change. `ImportEngine.collectUnnecessaryFqn` now
attaches a body-drop `WEdit(dropStart, dropEnd, "")` to *every* `no-unnecessary-fqn` report
unconditionally (the report firing at all already means D.2 proved it safe — "every report D.2
makes is already provably rewritable") and separately records `(newImportFqn, itsReport)` pairs as
**import-insertion anchors** for `resolveImportListChanges` to resolve into that report's second
edit, `PendingImportReport.extraEdit`.

**The three import variants (D.3's own decision, inside `QualifiedUsageDecision.decideAll`):**
1. **Already imported** (a non-aliased explicit import of the exact candidate exists) or
   **same package** — no import edit, body edit only.
2. **The candidate's package is a [`DefaultImportPackages`] target** — no import edit either (a
   bare name there already resolves via the compiler's own defaults; adding one would be pure
   noise — the `kotlin.Unit` dogfood case, commit cf6d939). `DefaultImportPackages.ALL` is the ten
   packages `kotlin`, `kotlin.annotation`, `kotlin.collections`, `kotlin.comparisons`, `kotlin.io`,
   `kotlin.ranges`, `kotlin.sequences`, `kotlin.text`, `kotlin.jvm`, `java.lang` — javap-confirmed
   against `kotlin-compiler-embeddable:2.3.21`, not assumed from documentation: the first eight are
   every `ImportPath` string constant loaded in `org.jetbrains.kotlin.resolve
   .DefaultImportsProvider`'s own constructor; `kotlin.jvm`/`java.lang` are the two JVM-platform
   additions read directly off `org.jetbrains.kotlin.resolve.jvm.platform
   .JvmDefaultImportsProvider`'s class file (`javap -c -constants`).
3. **Otherwise** — add `import <candidateImportFqn>`, attached to the one earliest usage.

**A generalization found while building the default-import variant, not shipped as first
written.** D.2's step 4 (the "written elsewhere" bail, §8 above) was scoped to skip only for
*same-package* candidates, reasoning that step 4 exists purely to protect a **new** import from
being silently shadowed — logic that is moot once no new import is happening at all. D.3 initially
kept that same-package-only scoping and immediately failed its own `kotlin.Unit` dogfood-style
fixture: `fun f(): kotlin.Unit = Unit` bails (wrongly reports nothing) because the *body*'s bare
`Unit` is a written-identifier occurrence outside the type position's own span, and step 4 doesn't
know `kotlin.Unit` needs no new import at all. Generalized the exemption from `isSamePackage` to
`needsNoNewImport = isSamePackage || packageFqName in DefaultImportPackages.ALL` — the same
reasoning that justified skipping step 4 for same-package applies identically to a default-import
target, since in both cases there is no *new* import for step 4 to protect. Locked by
`QualifiedUsageDecisionSpec`'s "report a default-import-package usage even when its simple name is
also written bare elsewhere" case and the `default-import-package-error` fixture (the exact
`kotlin.Unit` shape, `.fixed.kt` companion).

**Insertion ownership — three placements, one pure decision object (`ImportInsertionDecision`,
`libs/wrasse-rules`, compiler-free) plus a fourth, engine-side path that needs no new object at
all:**
1. **Import-ordering enabled and the list is clean.** `ImportOrderingDecision.composeRegion` gained
   an `extraLines: List<String>` parameter — brand-new `import <fqn>` lines that never correspond
   to a span in the original text (so they cannot be a `WEdit` against it, unlike the `edits` the
   composed rewrite already splices) are appended to the post-edit, pre-sort line list before the
   final ASCII sort, so a new import's position "falls out naturally" from the identical composed
   whole-list rewrite ordering already performs — no offset-based insertion logic needed for this
   path at all. `resolveImportListChanges` (renamed from `decideOrdering`, same function, now also
   the single place both decisions are made — see below) enters this branch whenever ordering is
   enabled, the list is clean, and *either* `taken` (edits from other ids in-region) or the new-FQN
   list is non-empty — previously only `taken` could trigger composition; a pure insertion with
   nothing else to compose used to be unreachable and now is.

   **Truthfulness invariant (a real bug, found in high-supervision review, not shipped as first
   written): running composition is never itself evidence of disorder.** The first version of this
   change reported `import-ordering`'s "Imports are not sorted" diagnostic *whenever composition
   ran at all* — including a pure insertion or a pure fold with no genuine violation, e.g. a single,
   trivially-sorted pre-existing import plus one new `no-unnecessary-fqn` addition. That is a
   fabricated diagnostic: in pure lint mode (no fix applied), a user with a perfectly sorted import
   list and one unrelated FQN finding would see a false "Imports are not sorted" error that no edit
   ever corrects for them to see the falsehood. Diagnostics must be true statements about the code,
   not artifacts of *how* an edit happened to get produced. Fixed: the `import-ordering` **report**
   fires if and only if `ImportOrderingDecision.firstOutOfOrder` finds a genuine violation in the
   file's own, pre-edit directive order (`records`) — composing and reporting are decoupled. When
   composition runs and the list *is* genuinely out of order, behavior is unchanged: the ordering
   report carries the composed edit, exactly as it always has. When composition runs on an
   already-sorted list (a pure insertion, and — this was already a latent instance of the identical
   falsehood predating D.3 entirely, for a plain fold of other ids' edits with no insertion at all —
   a pure removal/expansion fold too), no `import-ordering` diagnostic is emitted at all; the
   composed whole-list edit instead rides an existing, genuinely-true report's `extraEdit`
   (`carrierFor`): the earliest-sorting new target's own `no-unnecessary-fqn` anchor when an
   insertion is involved, otherwise the first folded (`no-unused-imports`/`no-wildcard-imports`)
   report in span order — either way, a report whose own message was already true regardless of
   which physical edit object ends up attached to it. Locked by
   `imports-full/sorted-list-fqn-insertion-no-false-ordering-error` (a genuinely sorted two-import
   list, one new import inserted via composition, exactly one `no-unnecessary-fqn` diagnostic and
   **zero** `import-ordering` diagnostics, `.fixed.kt` proving the insertion still lands correctly
   sorted); `imports-full/file-annotation-rewrite-with-ordering-error` and
   `imports-full/unnecessary-fqn-coexists-error` (both single-pre-existing-import, trivially-sorted
   fixtures) had their bogus `import-ordering` expectations removed — their `.fixed.kt` companions
   are unchanged (the composed edit is byte-identical, only its carrier report changed).
   `imports-full/combined-fix-error`, `grand-slam-error`, `last-directive-removed-error`,
   `member-star-with-ordering-error`, and `unused-star-with-unsorted-remainder-error` all have a
   genuinely out-of-order pre-existing list and keep their `import-ordering` expectation unchanged.
2. **No existing import directives at all** (`directiveSpans.isEmpty()` — note the `IMPORT_LIST`
   node itself is *always* present, zero-width, even with zero directives, probe-confirmed via
   `LightTreeStreamAdapter.walk` on `package sample\n\nval x = 1\n`; "no import list" means no
   directives, not no node). `ImportInsertionDecision.emptyListInsertion` replaces the
   whitespace-only gap between `listStart` and the first non-whitespace content with a canonical,
   born-clean rendering: a blank line before the new import block when something (a package
   directive and/or file annotations) precedes `listStart` (`listStart > 0`), a blank line after
   when something follows, neither when there is nothing on that side — regardless of the
   *original* gap's width (a stray extra blank line from source formatting is silently normalized
   away along with the insertion, since the whole gap is replaced, not patched around). This path
   runs whether or not `import-ordering` is enabled — there is nothing to "compose" with zero
   directives, so it is always this dedicated placement. Locked by six `no-unnecessary-fqn/`
   fixtures whose target needed a new import with no pre-existing list (`duplicate-usage-both-
   reported-error`, `generic-type-report-error`, `nested-class-report-error`, `qualifier-member-
   access-report-error`, `qualifier-object-report-error`, `typeref-report-error` — six, all
   `.fixed.kt`), plus `ImportInsertionDecisionSpec` unit tests for both sides of the gap
   (preceding-only, following-only, both, neither) and multi-import sorting within one block.
3. **Import-ordering disabled, or the list isn't clean, or composition itself bails**
   (`composeRegion` returning `null`) — `ImportInsertionDecision.standaloneEdits`, a **zero-width**
   `WEdit` per seam (the patch machinery has handled zero-width same-offset insertions since D18's
   same-offset-insertion work, §5.2). Each new import's `ImportOrderingRecord.sortKey`-driven
   insertion index picks one of two seam shapes: immediately before the first existing directive
   sorting after it (only when the pairwise gap to its predecessor, or to `listStart` for the very
   first directive, is a single comment-free `\n` — the identical "clean pairwise gap" fact
   `ImportOrderingDecision.isCleanList` already establishes for the *whole* list, checked here for
   just the one seam), or after the last directive (`listEnd`, always exactly the last directive's
   own `endOffset`, §8 above) — used both when the new import sorts last and as the universal
   fallback whenever the earlier seam isn't clean. A dirty pairwise gap (most commonly a comment
   documenting the *next* directive) is never risked with a mid-list insertion — falling back to
   "after the last directive" can never sever a comment from what it documents, at the cost of a
   not-strictly-alphabetical position for that one import, the same bail-toward-safety posture as
   the rest of the import family. Several new imports landing at the identical seam merge into one
   edit, sorted among themselves, rather than relying on `EditPlan`'s same-offset tie-break at all.
   Locked by `no-unnecessary-fqn-standalone/mid-list-insertion-error` (a clean mid-list seam,
   `.fixed.kt`), `no-unnecessary-fqn-standalone/comment-gap-fallback-error` (a comment sitting
   exactly at the natural seam, falling back to end-of-list, `.fixed.kt`), and
   `ImportInsertionDecisionSpec` unit tests for every seam shape including the multi-import
   same-seam merge and the split-across-two-seams case.

**A same-offset hazard found while building the standalone path, mirroring `import-ordering`'s own
`probeEnd` fix (§8 above) exactly.** "After the last directive" seams land at `listEnd`, which is
always exactly the last directive's own `endOffset` — if that same directive is *also* being
removed as unused, its whole-line deletion consumes past `listEnd` into the directive's own
trailing `\n` (outside `IMPORT_LIST`'s own span, same fact `import-ordering`'s `probeEnd` exists to
handle), so a zero-width insert exactly at `listEnd` would land *inside* that deletion's span — an
`EditPlan` overlap, caught only at apply time without a targeted fixture. `ImportEngine
.adjustForSwallowingEdit` pushes such an insertion past whatever pending edit's span already
swallows `listEnd`, dropping the insertion's own leading `\n` (the swallowing edit's `endOffset`
already lands at the start of the next line, so one isn't needed). Every *other* seam shape is
boundary-safe by construction and needs no such adjustment: a zero-width insert whose offset
exactly equals another edit's `startOffset` or `endOffset` is a boundary, not an overlap (`EditPlan`
sorts equal-start entries by end, so `next.start >= current.end` always holds at a shared boundary
point) — only the last-directive-trailing-newline shape reaches *past* its own nominal end into
territory a naive anchor would treat as free. Locked by `no-unnecessary-fqn-standalone/last-
directive-removed-standalone-error`: the last existing import is simultaneously unused (removed)
and the seam for a new, later-sorting import, both `no-unused-imports` and `no-unnecessary-fqn`
firing, one composed-free `wrasseFix` pass producing the correct, non-overlapping result
(`.fixed.kt`).

**The reconciliation case — verified a non-issue by construction, not fixed with new suppression
code.** The design brief for this track worried about a specific interaction: an import whose
*only* usage is fully qualified (`import a.b.C` alongside a written `a.b.C`, never a bare `C`)
being flagged unused by `no-unused-imports` independently of whatever `no-unnecessary-fqn` decides
about the same usage — two rules reaching individually-correct, jointly-wrong conclusions unless
reconciled. Checked empirically (a dedicated harness probe, not assumed) before writing any
reconciliation logic: `no-unused-imports`' classifier/callable matching (`UnusedImportDecision
.isUnused`, §8 above) keys purely on **FQN membership** in `WResolvedUsage.classifiers`/`callables`
— sets the compiler populates identically whether a reference was written bare or fully qualified
(`visitResolvedTypeRef`/`visitResolvedQualifier` record a classifier for *any* resolved reference
with a real source, §8's D.1 section). A qualified-only usage of an explicitly imported class is
therefore already, unconditionally, seen as "used" by `no-unused-imports` — there is no scenario in
which the same directive is simultaneously flagged unused and targeted by a same-target FQN
rewrite, so no suppression is needed and none was added. This was confirmed, not assumed: a probe
fixture (`import sample.aux.Widget` + `val w: sample.aux.Widget = TODO()`, both
`no-unused-imports` and `no-unnecessary-fqn` enabled) produces exactly one diagnostic
(`no-unnecessary-fqn`) both before and after this investigation — locked permanently by
`imports-full/already-imported-not-unused-error` (`.fixed.kt`) and by
`FqnImportInsertionSafetySpec`'s "reconciliation case" real-compile proof, rather than left as a
one-off manual check.

**Compile-safety.** `FqnImportInsertionSafetySpec` (`testing/wrasse-kotlinc-plugin-tests-base`,
subclassed per Kotlin minor exactly like `WildcardExpansionAmbiguitySafetySpec`) drives a real
compile → fix → reapply → recompile cycle and asserts
`IdempotenceCycle.assertPatchedFileCompiles` for the four trickiest shapes: import-into-empty-list,
a file-annotation rewrite plus its import addition, the reconciliation case, and the default-import
variant — the stronger guarantee the standard fixture cycle doesn't apply globally (§8 above,
`no-wildcard-imports` section: several fixtures compile `noJdk = true` and trip unrelated classpath
diagnostics that would false-positive across the whole suite if wired in by default).

**Fixtures, in full.** `no-unnecessary-fqn/`: every existing fixture from D.2 above gained a
`.fixed.kt` companion where it now fixes (ten of them; the bail/skip fixtures stay report-only or
clean, unchanged), plus `default-import-package-error` (new, `.fixed.kt`). A new directory,
`no-unnecessary-fqn-standalone/` (`no-unnecessary-fqn` + `no-unused-imports`, `import-ordering`
deliberately *not* enabled, to exercise the standalone placement path independent of composition):
`mid-list-insertion-error`, `comment-gap-fallback-error`, `last-directive-removed-standalone-error`
(all `.fixed.kt`). `imports-full/` (all four ids plus `no-semicolons`, so `import-ordering` is
always enabled there): `unnecessary-fqn-coexists-error` gained a `.fixed.kt` (no `import-ordering`
expectation — its single-import list is trivially sorted, see the truthfulness invariant above);
`already-imported-not-unused-error` (the reconciliation case, `.fixed.kt`);
`file-annotation-rewrite-with-ordering-error` (a real `@file:OptIn(a.b.Marker::class)` shape,
mirroring the exact dogfood construct from commit cf6d939, `.fixed.kt`, likewise no
`import-ordering` expectation); `grand-slam-error` (unused-import removal + star expansion + FQN
rewrite/import addition + ordering, all five ids firing in one file — this list *is* genuinely out
of order, so its `import-ordering` expectation stays, one composed `wrasseFix` pass, `.fixed.kt`);
`sorted-list-fqn-insertion-no-false-ordering-error` (the truthfulness-invariant regression lock: a
genuinely sorted two-import list plus one new insertion, exactly one `no-unnecessary-fqn`
diagnostic and zero `import-ordering` ones, `.fixed.kt`). `QualifiedUsageDecisionSpec` gained cases
for all three import variants plus the earliest-usage tie-break and the default-import
generalization above; `ImportInsertionDecisionSpec` (new) unit-tests `ImportInsertionDecision`
directly; `ImportOrderingDecisionSpec` gained `composeRegion` `extraLines` cases (with and without
other edits, with and without a pre-existing trailing newline).

**The FQN→import track is complete.** D.1 (offset correlation) → D.2 (report-only detection) → D.3
(the fix, this section) shipped across three sessions with zero detection changes after D.1 — D.3
added no new ambiguity handling beyond the default-import generalization above, which closes a gap
D.2's own report-only shape could never have surfaced (there was no "is a new import needed"
question to get wrong until D.3 asked it). Remaining in the engine's own growth-site bucket,
unchanged by this track: own-package/default-redundant star removal, and the KDoc same-package-
sibling coverage gap (needs a session-backed package→declarations query, §8 above).

**Remediation (found dogfooding kryptoid, an external codebase): already-imported bypassed the
collision checks.** `QualifiedUsageDecision.isSafeToDrop` returned safe unconditionally for an
exact, non-aliased already-imported candidate, before the simple-name collision checks ran. Real
shape: a file imports `kotlin.time.Instant` (so bare `Instant` means the Kotlin type) and also
writes several `java.time.Instant` usages fully qualified, deliberately disambiguating the two
worlds at each call site — kryptoid's `LbankFeedParser`. The old ordering flagged the
already-imported `kotlin.time.Instant` occurrences as "unnecessary": compile-safe (bare `Instant`
already resolves to it) but style-destructive, undoing the file's own deliberate disambiguation
that the collision index already knows about (both FQNs share the simple name `Instant`). Fixed by
running the collision checks (index + explicit-import-visible-name) unconditionally, before every
variant including already-imported and same-package; only the written-elsewhere check (step 4) keeps
its existing same-package/default-import exemption. Locked by
`already-imported-collision-skip-clean` (the dual-`Widget` shape mirroring the real `Instant` one,
failing before this fix and clean after) and a matching `QualifiedUsageDecisionSpec` case. Recompiling
kryptoid's `feed-parsers` module before/after: 10 findings → 8 (the two `LbankFeedParser`
already-imported-`Instant` false positives gone; the other 8, all genuine, unaffected).

**Ground-truthed against detekt's `UnnecessaryFullyQualifiedName`** (`dev.detekt.rules.style`,
`RequiresAnalysisApi`, real semantic resolution via the Kotlin Analysis API — not a PSI heuristic).
Read its source plus its full test suite, and probed its real engine (`lintWithContext`) on the
kryptoid shapes above plus the dual-`Widget`/dual-`List` collision pair. Its collision check
(`hasNameCollision`) resolves the file's real lexical scope at the usage (imports, local/package
declarations, type parameters), excluding only the two default-importing scopes, and bails iff a
*different* symbol than the one actually resolved already binds that simple name there — this is
asymmetric: for the dual-`Instant` shape, detekt still flags the already-imported `kotlin.time.Instant`
occurrence (no self-collision) while skipping the unimported `java.time.Instant` one (collides with
the import) — probe-confirmed, not assumed. Wrasse's fix above is deliberately narrower here on
purpose (an explicit brief from the owner, not upstream parity): it skips *both* occurrences,
treating "two FQNs share a simple name anywhere in this file" as a file-wide fact about deliberate
disambiguation, not a per-occurrence safety check. Same-package handling needed no adjustment: it
already matches detekt's scope-based result exactly (confirmed by re-reading the source against the
already-shipped `same-package-shadowed-by-import-skip-clean` / `same-package-unshadowed-still-
reported-error` pair) even though wrasse gets there via an explicit `isSamePackage` branch rather
than real scope resolution.

Two genuine, confirmed scope gaps found (wrasse narrower than upstream, kept narrower per the
owner's scope-⊆-upstream directive, not fixed — both structural facade limitations, not detection
bugs): (1) a fully-qualified **constructor call** with no accompanying type-position usage of the
same target produces no `WQualifiedUsage` at all (FIR only records a `QUALIFIER` for an object-like
reference — a plain-class constructor call is tracked only as callable resolution elsewhere,
invisible to this facade) — locked clean by `constructor-call-only-skip-clean`, covering detekt's
"constructor calls" and "empty and null selector expressions" cases, and explaining why
`generic-type-report-error` only ever reports the type position, never the accompanying
same-target constructor call, at one report per target rather than two. (2) a **package-qualified
top-level function or property** (`kotlin.io.println(...)`, `kotlin.run { }`, `kotlin.math.max(...)`)
produces no `WQualifiedUsage` either — the qualifier chain resolves to a package, not a class/object,
so there is no object-like reference for FIR to record — locked clean by
`package-qualified-toplevel-call-skip-clean`, covering detekt's "fully qualified function calls"
(`kotlin.io`/`kotlin.collections`/`kotlin.math` cases) and "reports single-segment package qualified
calls" cases; detekt's parallel case through an actual class (`java.lang.System.currentTimeMillis()`)
*is* covered, confirmed by probe, via the same member-access-through-a-qualifier mechanism as
`qualifier-member-access-report-error`. Detekt's three "property named same as kotlin package"
tests and its "does not report function call when shadowed" case all use this same
package-qualified-call shape, so they hold for wrasse too, incidentally, for the structural reason
above rather than any shadowing awareness — the observable behavior (no report) still matches.

`type-parameter-shadow-skip-clean` locks a case with no existing coverage: a type parameter whose
name shadows a used FQN's simple name (mirroring detekt's "shadowed by enclosing type parameter"
test) — confirmed clean, not because wrasse models type-parameter scoping at all, but because the
type parameter's own declaration token is itself a written `IDENTIFIER` the step-4 written-elsewhere
check already catches; the identical reasoning applies to detekt's declaration-based shadowing cases
(a nested class/object whose name shadows an FQN) without a dedicated fixture per variant.

Every other detekt test case maps onto wrasse's existing mechanism directly (one `QUALIFIER`/
`TYPE_REF` per resolved class-like reference, syntactic-proof-gated, outermost-class-only,
collision-checked) and is already exercised by the pre-existing `no-unnecessary-fqn/` fixtures —
return/variable/parameter/catch-clause/generic/nullable/vararg/secondary-constructor type positions,
object and static-member-through-a-class access, class literals, annotations, type aliases,
supertypes, casts, `when`-branch `is` checks, and nested-chain dedup — no new fixture needed since
none of these exercise a decision path the existing suite doesn't already cover; detekt's own
message text (parameterized with the FQN) is not matched — wrasse's message stays the fixed,
non-parameterized string per §6's message convention, not a gap.

**Owner decision, adopting detekt's asymmetry on a dual same-simple-name collision (recorded
verbatim):**

> Dual same-simple-name FQN usage handling adopts detekt's asymmetry:
> - If one colliding target has an exact non-aliased import: its qualified usages ARE flagged
>   (rewrite = body-edit only, bare name already binds; D.3 attaches no import edit — variant (a)
>   already behaves so); the OTHER target's qualified usages stay silent (mandatory
>   disambiguation).
> - If NEITHER colliding target is imported: everything stays silent (never invent an import amid
>   ambiguity).

This narrows the remediation two paragraphs above, which had gone further than detekt on purpose
(deliberately silencing *both* sides of a dual collision, already-imported or not) — the probe cited
there had already found detekt's real behavior asymmetric; this decision brings wrasse in line with
it instead of staying narrower. `QualifiedUsageDecision.isSafeToDrop` moves the already-imported
check back to the front, returning safe unconditionally before either collision check runs (the
already-imported branch's own unconditional green light — bare name provably binds to the candidate
regardless of what else in the file shares its simple name); the two collision checks now run only
for the not-already-imported paths (same-package and import-needed alike), unchanged from the
remediation above. Locked by flipping `already-imported-collision-skip-clean` (renamed
`already-imported-collision-error`, `.fixed.kt` companion added: the imported target's usage is
rewritten to the bare name, the other, unimported target's usage untouched) from clean to reporting,
and `classifier-simple-name-collision-clean` (already the exact both-unimported dual shape, dual
`Widget` usages with neither imported — verified, not a new fixture) staying clean unchanged.
Recompiling kryptoid's `feed-parsers` module: 8 findings → 10 (the two `LbankFeedParser`
already-imported-`Instant` occurrences report again, owner-endorsed; the other 8 unaffected).

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
Therefore (Phase A.5): a JMH benchmark over a fixed corpus as a regression tripwire, **before**
rule porting starts. Keep `WStreamRule` count small; keep disabled-rule cost at zero. The corpus
is a deterministic synthetic generator (`BenchmarkCorpusGenerator`, versioned via
`CORPUS_VERSION`), not this repo's own sources — those grow every session, which made
cross-session comparison meaningless; JMH settings are hardened to 2 forks / 5 warmup + 5
measurement iterations to keep error bars under ~5% of score (2 warmup + 3 measurement, 1 fork
was noise-dominated, occasionally inverting which variant looked faster).

**Measured (2026-07-19, post-import-chain, 6 rules incl. the resolution facade and
qualified-usage collection):** wall-clock A/B on a real external 134-file multi-module JVM
project (kryptoid; interleaved rounds, `--rerun-tasks --no-build-cache`, warnOnly so codegen
runs in both modes, symmetric init scripts): base mean 3784ms vs wrasse-enabled 3850ms over
four warmed rounds — **≈1.7% overhead**, partially inside the base build's own ±5% round
variance. Within budget. The same measurement on wrasse's own repo shows 5–8%: a 17-tiny-module
micro-build amortizes per-module fixed costs (plugin load, config parse, patch init) badly —
per-file walk cost is not the driver there. Bonus finding from the same run: 12 genuine
findings, zero false positives, on a codebase wrasse had never seen.

Known walk-level punch list (fix in A.5, verify with the benchmark):

1. ~~`childArray.copyOfRange(0, count)` allocates one array per interior node~~ — done: replaced
   with a per-depth `ChildArrayPool` owned per `walk` call (no shared mutable statics — safe for a
   future concurrent `checkFile`), grown geometrically, reused across siblings at the same depth
   (only one node per depth is ever mid-loop over its own children at a time). `disposeChildren` is
   now called on the tree's own borrowed array once its children are fully processed, per the
   `FlyweightCapableTreeStructure` contract (verified against the actual `MyTreeStructure`
   implementation: it recycles the small `TokenRangeNode`/`SingleLexemeNode` wrapper objects back
   into kotlinc's own pools; the backing array itself is not retained by the tree structure across
   calls, so our own pool copy is the only defensible cross-implementation-safe reuse).
2. ~~Buffered path recomputes `WNodeTypeMapping.map` and `child.text` per child~~ — done: the parent
   loop now computes each child's type/isLeaf/text once and passes them into the recursive
   `walkNode` call (which no longer recomputes them for its own node) and into the `ChildBuffer.add`
   call — one computation, two uses.
3. ~~`WNodeTypeMapping` is a HashMap lookup per node~~ — done: a `WNodeType?` array indexed by
   `IElementType.index` (a JVM-process-local `short`, stable for the JVM's lifetime but not
   portable across JVMs/compiler versions — verified via `IElementType`'s bytecode: `myIndex` is
   `final`, assigned once under a lock from a global static counter with a soft 15000 warn threshold,
   nowhere near the `short` ceiling in practice). Built lazily on first use, sized to the highest
   index among the existing `map`'s own keys (kept as the source of truth), so every mapped key
   provably fits; out-of-range/negative indices fall back to the map. Mapping-completeness spec
   still passes on all four minors.
4. ~~`trackLastNewline` scans every leaf's full text backwards~~ — done: gated by token type, and
   the safe set is wider than "whitespace/comments/strings vs everything else" as first guessed —
   verified directly against kotlinc's `Kotlin.flex` lexer grammar (not assumed) rather than
   hardcoded from memory. Provably `\n`-free and skipped: keywords and punctuation/operators
   (fixed literal token text), quote/template delimiters (`"`, `"""`, `$`, `${`, `}`), `IDENTIFIER`
   (`ESCAPED_IDENTIFIER = `[^`\n]+`` excludes it explicitly), numeric literals and
   `CHARACTER_LITERAL` (digit/hex/escape-sequence charsets exclude a raw `\n`), and `EOL_COMMENT`
   (`EOL_COMMENT="/""/"[^\n]*`). Left in the "always scan" (conservative, unchanged) set because
   they demonstrably *can* contain `\n`: `WHITE_SPACE`, `BLOCK_COMMENT`, `KDOC`, and
   `REGULAR_STRING_PART` (a raw/triple-quoted string's lexer literally emits a lone `\n` as its own
   `REGULAR_STRING_PART` token mid-string).
5. ~~Hex via lookup table, not `"%02x".format` per byte~~ — done: `HexEncoding.lowerCase` in
   `wrasse-lang`, used by both `WrassePlugin.computeSourceHash` and `WPatchApplier.sha256` (source
   hashing already read from the walk's in-memory `ctx.sourceText`, not a disk re-read, since D18).
6. Measure whether `LighterASTTokenNode.text` allocates a subsequence per leaf; if real, prefer
   offsets + the shared buffer. *(Not measured — out of this pass's scope; still open.)*

A JMH benchmark module (`testing:wrasse-benchmarks`, §11) now exists as the tripwire described
above; smoke numbers for this pass are in the A.5 roadmap entry below.

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
- **Test-harness classpath skew is a real failure mode, not just a plugin one**: kotlinc's own
  `GroupingMessageCollector` drops every WARNING-severity diagnostic from a compile that has *any*
  ERROR (regardless of source), unless `-Xreport-all-warnings` is passed — a stock CLI behavior,
  not a wrasse bug. Per-minor test modules must resolve a `kotlin-stdlib` matching the forced
  `kotlin-compiler-embeddable` version for the sources under compilation; letting Gradle's default
  "highest version wins" conflict resolution hand the fixture compile a newer toolchain stdlib
  produces a real "incompatible metadata version" error that then silently swallows unrelated
  WARN-level assertions. Any future per-minor/per-patch harness wiring must keep the compiled
  sources' library classpath pinned to the same minor as the compiler, separately from the test
  JVM's own classpath (which does need the toolchain's stdlib for kotest/JUnit to run at all).

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
- **Multi-file fixtures:** `// fixture-aux-file: aux/Foo.kt` (repeatable) compiles a companion
  source alongside the fixture — physically at `<rule-dir>/aux/Foo.kt`, wired into the harness as
  `sample/aux/Foo.kt`. Aux files carry no directives/expectations of their own; discovery
  explicitly excludes `aux/` so a companion source can never be mistaken for its own fixture.
  Locks cross-file resolution axes (e.g. `no-unused-imports`' classifier/callable FQN-prefix
  matching) that a single-file fixture cannot exercise under `noJdk`.
- **Version matrix:** the same fixtures run against Kotlin 2.1–2.4 (fixtures are
  version-agnostic by construction). Per-minor task wiring (patch-config resolution, `testMinor`,
  `testPatch_<version>`, stdlib pinning, fixture-dir system property) is centralized in
  `internal-convention-plugin` behind the `wrasseKotlinMinorMatrix { minor.set(...);
  patches.set(listOf(...)) }` extension — adding a new supported minor is now: create a module with
  a two-line matrix declaration, add it to `settings.gradle.kts`, and add it to the root
  `testMinorHarness`/`testPatchHarness` aggregate tasks.
- **Idempotence invariant (D19, Phase A.5):** for every autofix fixture — apply → re-lint →
  zero diagnostics → second fix emits zero edits. Phase C: `format(format(x)) == format(x)`;
  formatted fixtures re-format to themselves.
- **Byte-exact expected output (`.fixed.kt` companions):** a fixture may have a sibling
  `<fixtureId>.fixed.kt` holding the exact expected post-fix content of `sample/test.kt` (raw
  bytes, no directives). `FixtureLoader` excludes `*.fixed.kt` from fixture discovery and attaches
  the companion's content to the `Fixture`; a companion naming no matching fixture is a loud
  `require` failure (catches typos). After the idempotence cycle applies the patch,
  `WrasseFixtureSpec` asserts the patched file equals the companion exactly (`shouldBe`, so a
  mismatch renders a full diff) when one is present; a fixture with edits but no companion stays
  legal (opt-in, not required for every autofix fixture); a companion whose fixture emits no
  edits is a loud failure (dead companion = spec rot).
- **Mapping-completeness test** (A.5): walk representative fixtures per Kotlin minor and assert
  zero `UNKNOWN` node-type mappings.
- **CRLF fixture** (A.5): guards the hash/offset consistency rule (§5.4).
- Rules are unit-testable without a compiler (`wrasse-model` has no kotlinc dep); fixture tests
  exercise the full plugin path.
- **Walk-throughput benchmark** (A.5, §9): `testing:wrasse-benchmarks` (`me.champeau.jmh`) — run
  via `./gradlew :testing:wrasse-benchmarks:jmh`; not part of `build`/`test`/`check`. Corpus is a
  fixed, versioned synthetic generator, not this repo's own sources (§9).

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
- **D8 — Suppression via `@Suppress("rule-id")` only · Accepted, shipped 2026-07-19.** No comment
  directives, ever. Annotation-granular suppression is the accepted trade — most visibly, import-
  family rules have no per-import granularity (§7.1) since imports have no declaration to hang a
  finer-scoped annotation off.
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
- **D22 — Patch emission rides check mode; apply is compile-free · Accepted 2026-07-19.**
  Supersedes D10's wiring detail (emission gated behind `wrasse.fix`) and §14's
  "accidental full sweep" note. Rules compute `WEdit`s during every lint anyway; only the
  patch-file write was gated, so the old fix flow re-ran a full compile purely to re-derive
  known information. New model: (1) whenever the plugin is active, the patch is emitted —
  no separate fix flag, so lint and fix compiles have identical compiler args and never
  invalidate each other; (2) `wrasseFix` = the same check compile (UP-TO-DATE when check
  already ran → zero extra compile in the common flow) + `wrasseApply`; (3) patch files are
  **per compilation** (`build/wrasse/<compilation>/wrasse-fixes.txt`, distinct
  `fixOutputDir` per compile task) so parallel main/test compiles never race one file —
  apply walks the whole `build/wrasse/` tree; (4) **merge-on-write**: a compile replaces
  entries for the files it actually recompiled — including *removing* entries for
  recompiled files with zero edits (self-cleaning) — and preserves entries for files the
  incremental compile didn't touch; the per-file hash guard already makes preserved-stale
  entries safe no-ops at apply time. Entries for since-deleted files are retained and
  surface as loud `Skipped` results at apply. Config edits still don't invalidate compile
  tasks (D1 cost, unchanged) — a config change wants an explicit full re-check before
  fixing; the thin Gradle plugin (Phase D) remains the real fix for that.

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

Retroactive upstream-test backfill, wave 2 installment 1 (2026-07-20): `no-semicolons`
(ktlint `no-semi`, detekt's own id is a bare `KtlintRule` wrapper around the same ktlint rule —
zero additional cases) and `trailing-newline` (ktlint `final-newline` + detekt
`NewLineAtEndOfFile`) ported against their real upstream test suites — found and fixed three real
false-positive/negative bugs (a redundant-semicolon-before-a-trailing-lambda-literal
misdetection that was an unsafe autofix, a missing `companion object`-without-body exemption, and
an enum entries-list terminator that was never flagged even when nothing followed it before the
closing brace); one confirmed-unsafe-autofix gap left as an open blocker, not papered over: a bare
`;` standing in as a `for`/`while`/`if` construct's entire (empty) body is structurally
indistinguishable, in wrasse's LightTree mapping, from an ordinary trailing statement semicolon —
flagging and autofixing it as "unnecessary" is a pre-existing, newly-confirmed bug (deleting it
is a genuine compile break, confirmed via `IdempotenceCycle.assertNoNewCompileErrors`), needs a
backward-scan (or grammar-level) fix, not attempted here.

Retroactive upstream-test backfill, wave 2 installment 2 of 4 (2026-07-20): `no-empty-class-body`
(ktlint `no-empty-class-body` + detekt's own, separately implemented `EmptyClassBlock`),
`no-unit-return` (ktlint `no-unit-return`; detekt's own id is a bare `KtlintRule` wrapper around the
same ktlint rule — zero additional cases), and `no-empty-parens-before-trailing-lambda` (ktlint
`unnecessary-parentheses-before-trailing-lambda`; detekt's wrapper test reuses two of ktlint's own
three cases verbatim — zero additional cases) ported against their real upstream test suites. Found
and fixed one real bug: `no-empty-class-body` was reporting (without an edit) on an anonymous object
expression's empty body (`object : Foo {}`, `object {}`), reasoning only from kotlinc's grammar
(the body is syntactically mandatory there); both upstream ktlint and detekt's independently
implemented `EmptyClassBlock` agree this shape is never flagged at all — corrected to a full
exemption (see B.2 above for the detail). `no-unit-return`'s and
`no-empty-parens-before-trailing-lambda`'s existing fixture sets already matched their real upstream
suites case-for-case (comment-adjacency and newline-gap bails both confirmed as deliberate,
narrower-than-upstream autofix scopes, not gaps); two small coverage gaps closed anyway —
`no-unit-return` gained `other-return-type-clean` (upstream's own `fun foo(): String = "foo"` case,
the one shape the existing suite hadn't exercised directly), and
`no-empty-parens-before-trailing-lambda` gained `invoke-chain-multi-level-error` locking upstream
issue #3016's full three-call-deep invoke chain verbatim (the existing `invoke-chain-clean` only
exercised the minimal two-level case). No open blockers from this installment.

Retroactive upstream-test backfill, wave 2 installment 3 of 4 (2026-07-20): the import family — the
largest dedup job, ~90 pre-existing fixtures — `no-unused-imports` (ktlint `no-unused-imports` +
detekt's own, independently implemented `UnusedImport` in `detekt-rules-style`, plus a thin
`NoUnusedImports` ktlint-wrapper with near-zero additional cases), `no-wildcard-imports` including
its resolution-powered expansion autofix (ktlint `no-wildcard-imports` + detekt's own
`WildcardImport` + its ktlint-wrapper), and `import-ordering` (ktlint's four-file `importordering`
suite — only `ImportOrderingRuleAsciiTest` is in scope per D21, ASCII-only, no layout config;
`Custom`/`Idea` are confirmed pure editorconfig-grouping variants, out of scope — plus detekt's
thin `ImportOrdering` wrapper) ported against their real upstream test suites. wrasse's
resolution-powered semantics deliberately diverge from ktlint's syntactic false positives/negatives
throughout (KDoc-reference FPs, shadowing, same-simple-name-different-package precision, same-
package-import handling) — every such upstream case was treated as a documented divergence, not a
gap, with wrasse's resolution-correct behavior locked instead. Two real bugs found and fixed, both
small and contained (mechanical/one-line, not engine-shape changes — reported separately below
where a fix would have been engine-shape): the `import-ordering` sort key not stripping backticks
(§8.2's `import-ordering` paragraph) and `WildcardExpansionDecision`'s KDoc-coverage bail missing
the cross-star fold `UnusedStarDecision` already had, an actual D19 idempotence violation, not just
an asymmetry (§8.2's bail-8 paragraph — both have full detail, including the exact fixtures that
lock each). 16 new fixtures added across the family, closing gaps upstream porting surfaced that
no prior installment's fixture set had considered: KDoc `@see`/reference-link
syntax, operator-convention imports (`combine` infix, `plusAssign`, `rangeTo`/`rangeUntil`),
`componentN` destructuring via real extension functions, property-delegate providers
(`provideDelegate`), annotation-argument-only references, class-literal (`::class`) references,
cross-package same-simple-name overload resolution (two same-named functions in different
packages, only one call-shape actually resolving — a genuine precision win over both upstream
tools' simple-name-based matching), same-package explicit-import handling (a real three-way split:
ktlint always keeps it, detekt always flags it, wrasse is usage-driven and agrees with ktlint only
when the import is genuinely unused), a no-package-directive star-import shape, and a
cross-star KDoc-coverage shape (see the bug above). **Two blockers surfaced and deliberately not
fixed in-task, both requiring an owner call before any implementation — tracked in §14: (1)** an
alias-identity blind spot in `UnusedImportDecision` (two same-FQN imports under different aliases,
only one used — the unused one is silently never flagged, since `WResolvedUsage`'s classifier/
callable sets are keyed by resolved target, not by which import directive brought it into scope);
**(2)** no mechanism anywhere in `ImportEngine` detects or removes exact-duplicate import
directives at all (ktlint's own, long-standing "Duplicate 'import ...' found" behavior has no
wrasse equivalent, for either explicit imports or stars whose target is actually used). Both are
facade/engine-shape changes, not contained fixes — flagged for the owner, not attempted here.

Retroactive upstream-test backfill, wave 2 installment 4 of 4 (2026-07-20): `modifier-order`
(ktlint's own `ModifierOrderRuleTest` + detekt's own, independently implemented `ModifierOrderSpec`
— its ktlint-wrapper `ModifierOrdering` is a thin re-export, zero additional cases, per B.2 above)
ported against their real upstream test suites. One genuine bug found, not a wrasse logic defect —
escalated as an engine-shape blocker rather than papered over in-task (full detail in §14): a real
Kotlin 2.1.x `K2JVMCompiler` crashes during Fir2Ir lowering (`IllegalStateException` in
`Fir2IrDeclarationStorage.findContainingIrClassSymbol`) compiling the *fixed* output of detekt's own
`data internal class Test(val test: String)` case — i.e. wrasse's own correct reorder,
`internal data class ...`, not the reported-as-wrong input — confirmed deterministic (reproduced
twice) via the real `testMinorHarness` idempotence check, absent on Kotlin 2.2/2.3/2.4, and
unaffected by renaming every identifier in the fixture, so it is specific to the `data`+`internal`
modifier pair's *textual order*, not incidental to the fixture's naming choices. Every other ported
case passed against the shipped engine on the first try. ktlint's context-receiver/context-
parameter cases (`Issue 3027`) map to the already-documented annotations/context-lists-untouchable
divergence (B.2 above): each case has only one real comparable keyword once the context list and
its annotation are excluded, so it is trivially non-violating under wrasse regardless, and — like
the `expect`/`actual` shapes — isn't real-compilable in the fixture matrix without an experimental
flag; no new test needed beyond the existing generic fewer-than-two-keywords coverage. Three detekt
cases (`private actual class Test`, `annotation expect class Test`, both `compile = false` in
detekt's own harness, plus the `data`/`internal` pair above once its real-compile crash was found)
are real-compile-inexpressible across the full supported matrix and were ported as six new
`ModifierOrderDecisionSpec` cases instead (violation + already-ordered clean for each pair),
following the same real-compile-inexpressible-goes-to-the-decision-spec precedent B.2 itself set
for `expect`/`actual`. Eight new fixtures added, closing gaps neither upstream case-by-case porting
nor the original ship-time probing had locked as end-to-end fixtures: a visibility/`tailrec` swap
(ktlint's `protected`/`tailrec` and detekt's `private`/`tailrec` cases deduped to one shape, same
canonical pair), an `override`-centric 3-violation member soup (visibility/`override`,
`suspend`/`override`, `tailrec`/`override`, ktlint's own real test verbatim), an `open`/`override`
swap on a real overriding function (detekt's own real test shape), a 3-keyword swap with two
annotations interspersed among real keywords on a real overriding+suspend function (locks the
same same-span-edit/annotations-never-move behavior the decision spec already unit-tested, now
end-to-end against a real compile) paired in the same file with the single-real-keyword-plus-
annotation clean shape (ktlint flags this, wrasse doesn't — divergence, by construction, since one
keyword is always trivially ordered), a multi-line array-valued-annotation prefix before a
`suspend`/visibility swap (stresses report-span/offset tracking isn't confused by a large
annotation blob before the modifier list), a `const`/`internal` swap on a companion object member
(as opposed to the existing `companion-object-error` fixture's swap on the `companion` keyword
itself), and a comment-adjacent-but-already-ordered clean shape (detekt's own real assertion that a
comment between two correctly-ordered keywords never reports — trivially implied by
`isAlreadyOrdered`'s check running before the comment bail, but locked end-to-end anyway for
upstream parity). `vararg-parameter-error`, `fun-interface-keyword-ignored-clean`, and
`value-class-keyword-ignored-clean` were confirmed verbatim matches of detekt's own real
`a vararg argument`/`fun interface`/`value class` tests (already ported at ship time); detekt's
`a kt parameter with modifiers` (`lateinit`/`internal`) and `an overridden function` clean case were
confirmed subsumed by the existing multi-modifier-soup and generic fewer-than-two-keywords coverage
respectively — no new fixture needed for either. Both upstream checkouts left byte-clean (no
probing needed this round; upstream sources were only read, never modified).

Retroactive upstream-test backfill, wave 2 installment 5 (2026-07-20): `if-else-bracing` (ktlint's
own `IfElseBracingRuleTest` + `MultiLineIfElseRuleTest` — both feed the same wrasse concept, per B.2
above — plus detekt's own `BracesOnIfStatementsSpec`) ported against their real upstream test
suites. detekt's suite is overwhelmingly config-permutation coverage (`singleLine`/`multiLine` ×
`always`/`never`/`necessary`/`consistent`) that D21 already puts out of scope (no code-style knobs);
only the cases run under the actual shipped default combination (`singleLine=never`,
`multiLine=always`) were ported, closing two gaps the original ship-time probing hadn't fixtured:
nested single-line `if`-expressions used as a chain's own condition/then/else content (`nested-if-
condition-then-else-multiline-error`, `nested-if-single-branch-content-multiline-error`) and a
locked assertion that an already-braced, fully single-line chain is never touched for removal
(`single-line-if-else-already-braced-clean` — detekt's default would flag it for brace *removal*,
but this rule only ever inserts, never removes, an asymmetry the original KDoc already stated but
had never been fixtured). ktlint's own suite surfaced one genuine divergence, ground-truthed by
reading detekt's real `BracesOnIfStatements.walk()` source directly rather than probing (both
checkouts left byte-clean, read-only): an `else` whose content is a bare `if` starting on a *new*
line (`else\n    if (...)`, as opposed to `else if (...)` on one line) is, per ktlint's own real
formatted output, wrapped in its own explicit braces around the whole nested `if` — a shape ktlint
treats differently from a same-line `else if`. detekt's `walk()` makes no such distinction: it
excludes *any* `else` branch whose content `is KtIfExpression` from consideration unconditionally,
regardless of same-line or different-line placement, deferring to that nested `if`'s own visit
either way — structurally identical to wrasse's own `looksLikeBareIf` text check, which also doesn't
distinguish the two. Since wrasse's scope is the strict intersection of both engines' defaults and
detekt's default never wants this brace added, wrasse correctly matches detekt (not ktlint) here —
confirmed against the deepest, most convoluted case in either suite, ktlint's own "Issue 727 - Given
a deep nested if-else-if-statement" (four-level-deep nested `else if`/dangling-if mix, ported
verbatim as `deep-nested-else-if-chains-error`, 13 real diagnostics fired against a real compile,
matching ktlint's own 14 minus exactly this one documented divergence). Also confirmed as designed,
not a bug, on the same fixture: the existing "bail whenever the branch's own bare-statement text
already spans multiple lines" rule (§13 above) correctly bails on the two outermost chain levels
(each one's own bare content is an entire multi-line nested chain) while still firing real edits on
the innermost single-statement branches — a previously-untested recursive interaction, now locked.
One genuine, contained bug found and fixed in-task (not a divergence): `columnOf`'s original
algorithm used the chain head's own column directly, which silently misindents every brace in a
chain whose head sits mid-line — `fun foo() = if (...)`, ktlint's own real "Issue 1560" shape,
reproduced first via `property-assignment-midline-if-error` (a `fun` expression body) and confirmed
independently via `else-chained-call-same-line-error` (a top-level `val` initializer, ktlint's own
"Issue 2057" shape) before either fixture's expected output was written by hand — both failed
against the *actual* algorithm before the fix, not a hypothetical. Fixed to compute the indentation
of the chain head's own *physical line* instead (detail in B.2 above); regression-free against every
pre-existing fixture, since none of them has a chain head mid-line. Twelve new fixtures ported
overall, also covering ktlint's own "if inside a lambda" (`if-inside-lambda-last-expression-error`),
"Issue 1079" (`if` as a multi-line call argument, `if-inside-call-argument-error`), "Issue 945"
(comment on its own line before both branches' content, `else-own-line-comment-bail-error` — the
existing suite only had same-line-as-condition and same-line-trailing comment shapes), the blank-
line-preceded shape (`blank-line-preceded-if-error`, confirming no spurious blank line is
introduced), and ktlint's own "Issue 2135" empty-`THEN`-branch null-pointer regression test
(`empty-then-branch-clean`, confirming the existing empty-branch skip never throws in a genuine
multi-line chain, not just the trivially-skipped single-line case). ktlint's own consistency-forcing
`IfElseBracingRule` behavior (any-branch-already-braced forces the rest) and its unconditional
`else-if`-chain bracing are unchanged, already-documented, out-of-scope divergences (B.2 above) — not
re-fixtured. No open blockers from this installment.

### Phase A remainder — config & severity polish

- ~~`@Suppress("rule-id")` at expression and declaration scope.~~ **Done 2026-07-19** — shipped at
  file/declaration/expression scope, wildcards (`all`/`wrasse`), see §7.1.
- `--list-rules` / effective-config dump (the discoverability story replacing presets).
- Optional CLI override for config path.

**Exit:** a new rule ships without editing any existing user config; warn and error coexist in
one run; `@Suppress` silences one rule — **satisfied**.

### Phase A.5 — Foundation hardening (gate: complete before B) — **COMPLETE 2026-07-19**

All items done (details per item below). The Phase B gate is satisfied: matrix green on all
four minors and all tracked patch versions, idempotence invariant enforced in the harness,
per-file rule lifecycle, EditPlan composition, walk perf punch list + JMH tripwire, per-minor
build wiring centralized, MPP double-fire ruled out.

Done:
1. ~~Trust-burning bug fixes~~ — CRLF hash/offset consistency (bug empirically confirmed and
   fixed: LightTree offsets are LF-normalized, hash now computed from the walk's text, apply
   refuses loudly on line-ending divergence); `no-semicolons` class-body false negative +
   consecutive-semicolon miss (the literal `;;` form is a parse error, `"; ;"` was the real
   case; the documented statement-separator false positive did not reproduce — regression
   fixtures kept); `trailing-newline` empty-file span; patch-writer synchronization + absolute
   paths; `WNodeStack.clear()` counts reset. Remaining sub-item moved to item 6 below (MPP).
2. ~~Idempotence harness invariant (D19)~~ — implemented; tripwire proven end-to-end (a
   deliberately non-idempotent rule fails 16 fixtures with the D19 assertion).
3. ~~Per-file rule instantiation (D20)~~ — `WRuleSet.dispatchForFile`; per-rule exclude enforced
   at instantiation time (interim report-time filter deleted).
4. ~~EditPlan + `takeEditsIn` (D18)~~ — implemented in `libs/wrasse-model`; proven end-to-end
   with a nested inner/outer rule pair over an in-process parse+walk (no full compile needed);
   overlap check moved to compile time with full rule attribution; `WContext` exposes the
   walk's source text and the per-file `EditPlan`; same-offset insertion order fixed and locked
   with a writer→reader→applier round-trip test (§5.2, §14).
Also fixed en route (was not on this list): WARN-severity diagnostics dropped on Kotlin 2.1/2.2 —
a test-harness classpath-skew issue, not a plugin bug (see §10).

Remaining:
5. ~~Walk perf punch list + JMH benchmark~~ (§9) — punch list items 1-5 done (item 6, measuring
   `LighterASTTokenNode.text` allocation, is still open — out of scope for this pass); `ctx.childIndex`
   staleness and dead `ActiveNodeEntry.depth` from §14 fixed alongside it. JMH landed as
   `testing:wrasse-benchmarks` (§11): standard `me.champeau.jmh` + the repo's own convention plugin,
   no band-aid wiring — `./gradlew :testing:wrasse-benchmarks:jmh` is the tripwire, not wired into
   `build`/`test`/`check`. Hardened later the same day (2 forks, 5 warmup + 5 measurement
   iterations) over the fixed synthetic corpus (`BenchmarkCorpusGenerator`, `CORPUS_VERSION=1`:
   100 files, 223544 bytes) instead of this repo's own growing sources: zero-rules walk
   8.760 ± 0.160 ms/op (1.8% error) vs the current shipped rule set (`no-semicolons`,
   `trailing-newline`, plus the full `ImportEngine` — `no-unused-imports`/`no-wildcard-imports`/
   `import-ordering`/`no-unnecessary-fqn`) 9.827 ± 0.162 ms/op (1.6% error) — both comfortably
   under the ~5%-of-score target this tripwire needs to be useful; the original 2+3/1-fork numbers
   above predate the hardening and are superseded.
6. ~~MPP double-fire check~~ — **no repro; confirmed safe by design, not just by test.** Read the
   K2 CLI pipeline sources (`JvmFrontendPipelinePhase`, `FirSessionConstructionUtils`,
   `fir/pipeline/firUtils.kt`/`convertToIr.kt`, identical across 2.1–2.4): each source file is
   assigned to **exactly one** FIR module session (legacy `-Xcommon-sources` partitions files into
   disjoint `commonFiles`/`platformFiles`; `-Xfragments`/HMPP partitions via a single
   `hmppModuleName` per file). `MppCheckerKind.Common` checkers run once per session, over only
   that session's own files, inside `resolveAndCheckFir` — never touched by the later
   `runPlatformCheckers` pass (that pass unions all files but only runs `Platform`-kind checkers,
   filtered by `session.checkersComponent.commonDeclarationCheckers` vs `platformDeclarationCheckers`
   in `DeclarationCheckersDiagnosticComponent`). So a `Common`-kind `FirFileChecker` (what wrasse's
   `FirSyntacticChecker`/`20`/`22` all are) structurally fires exactly once per file regardless of
   MPP shape. Empirically verified with a real in-process `K2JVMCompiler` compile
   (`-Xmulti-platform` + `-Xcommon-sources`, one common file with an `expect` decl + a stray
   semicolon, one platform file with the matching `actual`): exactly 1 wrasse diagnostic and
   exactly 1 patch entry (1 edit) for the common file, both with fix output enabled and without.
   A control run with MPP disabled confirmed the shape was genuinely exercised (compiler rejected
   the same sources with "'expect' and 'actual' declarations can be used only in multiplatform
   projects" — proving the enabled run really went through the MPP session-splitting path, not an
   accidental single-session fallback). Regression guard: `MppCommonCheckerDoubleFireSpec`
   (`testing/wrasse-kotlinc-plugin-tests-base/src/test`), plus an additive
   `multiPlatformCommonSources` param on `WrasseTestHarness`. Only the legacy `-Xcommon-sources`
   CLI shape was driven end-to-end; `-Xfragments`/HMPP was not separately compiled (source reading
   shows the same one-file-one-session invariant via `fileBelongsToModule`/`sourcesByModuleName`,
   so no separate empirical pass was judged necessary — revisit only if HMPP-specific checker
   behavior is ever reported).
7. ~~Mapping-completeness test~~ — done: per-minor zero-`UNKNOWN` tripwire; 46 node/token kinds
   mapped including `KW_TYPEALIAS`; KDoc internals explicitly allowlisted.

### Phase B — Parity port (the bulk)

Scope per §6 / [autoformat-scope.md](autoformat-scope.md):

- **B.1 — lint-only rules (~128, bucket L).** Report, never fix. Mechanical volume; no new infra.
- **B.2 — targeted fixes (~15, bucket T) — chain started 2026-07-20 (6/15).** Braces family,
  `modifier-order`, redundant-syntax deletions. Each gated by the idempotence harness; born-clean
  discipline. `no-empty-class-body` shipped first: `WBufferedNodeRule` on `CLASS_BODY` (and
  `OBJECT_DECLARATION`, tracked via a stack to detect a `companion` modifier), deletes a
  whitespace-only body (any comment/KDoc inside makes it non-empty, never reported) plus the
  whitespace back to the preceding token (`EmptyClassBodyDeletionSpan`, the `ImportRemovalSpan`
  idiom). Empirically verified compile-legal after deletion for class/interface/object/enum
  class/nested-class/primary-constructor-class bodies. Matches upstream ktlint's own conservatism
  rather than going further: companion object bodies are exempt entirely (not reported, not fixed),
  same as ktlint, even though deleting one is provably compile-safe — extending scope beyond
  upstream needs explicit owner approval, not just "it's safe." An anonymous object expression's
  body (`object : Foo {}`, `object {}`) is exempt entirely too (not reported, not fixed) —
  **corrected 2026-07-20 during the wave-2 installment-2 backfill**: the rule originally reported
  (without an edit) on this shape, reasoning only from kotlinc's grammar (the body is syntactically
  mandatory there, so deletion is a compile error). Porting upstream ktlint's own real test suite
  (`Given an object declaration with empty body of an abstract class`) and detekt's own, separately
  implemented `EmptyClassBlock` (`does not report the object if it is of an anonymous class`) found
  both upstream tools agree: this shape is never flagged at all, not merely never autofixed —
  reporting it was an unapproved widening past upstream's line, the same category of thing the
  companion-object exemption above explicitly guards against, so it's corrected to match, not kept
  as a hand-rolled exception. Locked by `object-literal-clean`,
  `object-literal-with-generic-supertype-clean` (the exact upstream abstract-class-supertype shape),
  and `object-literal-in-function-clean` (detekt's local-scope shape), all with no `.fixed.kt`
  companion since nothing is emitted at all; the dedicated real-compile `EmptyClassBodySafetySpec`
  across all four Kotlin minors now asserts zero diagnostics instead of one. The idempotence
  harness itself gained a general guard here too: `IdempotenceCycle.assertNoNewCompileErrors` now
  compares round-1 vs. round-2 non-wrasse `e:`-severity diagnostic messages on every fixture's
  cycle, so a fix that silently breaks compilation (proven at original ship time by temporarily
  forcing the anonymous-object-literal shape to compute a deletion edit anyway: the file still
  applied, D19's own bookkeeping still reported success, and only this new guard caught the
  resulting syntax error) fails loudly instead of passing.
  `no-unit-return` shipped second: a `WBufferedNodeRule` on `FUN` finds the return-type `COLON`
  among the node's direct children, checks the following significant child is a `TYPE_REFERENCE`
  whose own source span is the exact literal text `"Unit"` (no semantic resolution — the same
  syntactic check upstream ktlint itself uses, and the reason `Unit?`, `kotlin.Unit`, an annotated
  return type, and a `Unit`-typed parameter are never even candidates, all empirically verified
  against upstream's own real behavior via a probe harness built against the actual ktlint
  ruleset-standard/rule-engine jars), then checks the next significant child after that is a
  `BLOCK` (a block-body function) rather than an `EQ` (an expression-body function, which upstream
  ktlint's own shipped/stable rule does not flag either — only a disabled, not-yet-shipped test
  exists upstream for that shape, confirmed by reading ktlint's own source and test file directly).
  Deletion (`NoUnitReturnDeletionSpan`) is a single contiguous splice from the colon's start to the
  type reference's end — empirically verified against upstream's real formatted output for the
  plain, no-comment shape (`fun foo(): Unit {}` → `fun foo() {}`, including multiline/blank-line-
  before-brace variants) to confirm no extra whitespace is introduced or removed on either side.
  Reported but never autofixed whenever a comment (`EOL_COMMENT`, `BLOCK_COMMENT`, or `KDOC`) sits
  between the colon and `Unit`, or between `Unit` and the block: upstream ktlint autocorrects some
  of these (a block comment either side), but probing upstream's actual formatter output surfaced
  a real, upstream-native corruption bug for one such shape — an `EOL_COMMENT` immediately before
  `Unit` — where deleting the newline trapped under that comment merges the following `{` onto the
  comment's own line, breaking the file. Rather than replicate upstream's per-shape comment
  handling (safe for block comments/KDoc, unsafe for EOL comments) at the risk of getting the
  distinction wrong, wrasse bails on autofix uniformly for any comment adjacency — narrower than
  upstream's autofix scope, same as upstream's *report* scope, and provably safe. Locked by
  `block-comment-after-bail-error`/`block-comment-before-bail-error`/`kdoc-between-bail-error`
  (report-only, no `.fixed.kt`) plus a dedicated real-compile `NoUnitReturnSafetySpec` across all
  four Kotlin minors for the EOL-comment shape specifically, mirroring `EmptyClassBodySafetySpec`.
  `no-empty-parens-before-trailing-lambda` shipped third, porting upstream ktlint's
  `unnecessary-parentheses-before-trailing-lambda` (whose own shipped test suite covers only three
  shapes — ground-truthed instead with a probe harness built against the real
  ktlint-ruleset-standard/rule-engine jars, plus a real `K2JVMCompiler` run for the one
  compile-breaking shape upstream's own tests never exercise). A `WBufferedNodeRule` on
  `CALL_EXPRESSION` finds the direct-child `VALUE_ARGUMENT_LIST`, requires its own source span be
  the literal two characters `"()"` (empirically confirmed both `foo(   )` and `foo(/* x */)` are
  never candidates at all upstream either, since either shape's extra child alone already fails a
  plain "children besides the parens" check — no special-casing needed), and requires the next
  significant sibling be a `LAMBDA_ARGUMENT`. Two shapes are exempt entirely (not reported, not
  fixed), both confirmed against upstream's real behavior and both because the empty parens are
  load-bearing, not redundant: whenever the significant sibling *before* the argument list is
  itself a `CALL_EXPRESSION` — an invoke-operator chain (`foo()() { }`, upstream issue #3016) or a
  call already ending in its own trailing lambda (`fooBar { "Hello" }() { "world" }`, upstream
  issue #2884) — removing the parens would silently resolve to a different call entirely. Reported
  but never autofixed whenever any whitespace/comment token between the parens and the lambda
  contains a newline: probing a real compile of the naively-fixed output surfaced a second
  upstream-native corruption bug in this same rule family (upstream applies the deletion anyway) —
  losing the call syntax marker `()` turns "call with trailing lambda" into "bare reference, then a
  lambda literal", which either fails to reparse (a property initializer at file scope) or fails to
  recompile with a genuine compiler error, `error: Function invocation '<name>(...)' expected`
  (verified with a real `K2JVMCompiler` run, not just ktlint's own lenient re-parse check — the
  latter alone did not surface the function-body case). Since an `EOL_COMMENT` between the parens
  and the lambda always forces a newline before the next token, this single newline check also
  subsumes that shape without a separate comment-type enumeration, simpler than `no-unit-return`'s
  equivalent bail. Locked by `newline-gap-bail-error`/`blank-line-gap-bail-error`/
  `eol-comment-gap-bail-error` (report-only, no `.fixed.kt`) plus a dedicated real-compile
  `NoEmptyParensBeforeTrailingLambdaSafetySpec` across all four Kotlin minors for both the
  plain-newline and blank-line shapes, mirroring `NoUnitReturnSafetySpec`.
  `modifier-order` shipped fourth, one wrasse id covering ktlint's `modifier-order` and detekt's own
  `dev.detekt.rules.style.ModifierOrder` (not the separate `dev.detekt.rules.ktlintwrapper.wrappers.
  ModifierOrdering`, a thin re-export of ktlint's own engine under a different id — its own KDoc
  says as much — not a second, independent implementation, so it never enters the intersection
  calculation below). The canonical order itself is not invented or taken from memory: both
  upstreams' own source comments say "subset of kotlinc's `KtTokens.MODIFIER_KEYWORDS_ARRAY`", read
  directly from the compiler's own sources jar, whose KDoc warns the array is stub-serialization-
  load-bearing and must never be reordered casually — the strongest possible confirmation this
  really is *the* canonical order, not a per-tool opinion. Ground-truthed by adding temporary probe
  cases directly to each upstream's own real test file (`ModifierOrderRuleTest.kt`,
  `ModifierOrderSpec.kt`), running them against the real engines, then reverting — both checkouts
  left byte-clean (`git status` verified) — covering full modifier-soup permutations, annotations in
  every position (before/between/after), `expect`/`actual`, `companion`, vararg/crossinline/noinline
  parameters, property-accessor modifiers, and enum entries.

  A `WBufferedNodeRule` on `MODIFIER_LIST` classifies each non-whitespace direct child: the 25
  canonical keyword types (visibility, `expect`/`actual`, modality, `const`/`external`/`override`/
  `lateinit`/`tailrec`/`vararg`/`suspend`/`inner`/`enum`/the `annotation`-class keyword/`companion`/
  `inline`/`infix`/`operator`/`data`) participate in the comparison; everything else — an
  `@Annotation`, a context-parameter/receiver list, the `fun`/`value` declaration keywords, or any
  future/unrecognized node — is inert, contributing nothing and never edited. A violation exists
  only when two or more participating keywords are out of relative canonical order (`no-unit-return`'s
  sibling precedent: fewer than two is always trivially ordered). Each out-of-place keyword gets its
  own same-span replacement edit — its own token's text swapped for the correct keyword's spelling —
  rather than one edit spanning the whole list, so anything physically interspersed among the
  compared keywords is never touched, wherever it sits, with no explicit prefix/span-bail logic
  needed at all (`ModifierOrderDecision`, unit-tested standalone). This generalizes to accessors,
  enum entries, and value parameters for free, since a `MODIFIER_LIST` is always a direct child of
  whatever it modifies — the same structural property `@Suppress`'s own region computation (§7.1)
  already relies on. Five new `WNodeType` keyword entries this port needed that no earlier rule had
  touched: `KW_FINAL`, `KW_INNER`, `KW_EXTERNAL`, `KW_EXPECT`, `KW_ACTUAL`.

  **Ground-truthed upstream disagreements, resolved by conservatism (intersection of what both
  engines actually fix):**
  1. *Annotations and context-parameter/receiver lists.* ktlint's own `ORDERED_MODIFIERS` includes
     `ANNOTATION_ENTRY`/`CONTEXT_RECEIVER_LIST` and repositions them (its own real, run test:
     `override @Ann fun foo()` is flagged and fixed, moving `@Ann` before `override`); detekt's own
     rule filters to `KtModifierKeywordToken` children only and never even looks at them (probe-
     confirmed: the same shape produces zero detekt findings, since a single real keyword is
     trivially ordered on its own) and has no autofix mechanism at all regardless. Wrasse matches
     detekt's narrower scope exactly: annotations and context lists are never part of the comparison
     and never touched by any edit, in any position (before, between, or after the real keywords) —
     a structural guarantee from the classification above, not a per-shape bail.
  2. *`fun`/`value` declaration keywords.* ktlint's own `tokenSet` omits both (probe-confirmed:
     `value private fun interface Foo`/`value private class Foo` are never flagged — the keyword is
     invisible to ktlint's own comparison); detekt's `order` array includes both (probe-confirmed:
     `companion private fun interface Foo`/`companion private value class Foo` are flagged). Since
     ktlint — the only one of the two with a real autofix — never considers these tokens at all,
     wrasse matches ktlint's narrower scope here (detekt never fixes anything regardless, so nothing
     is lost from the intersection).
  3. *Comments.* Unlike `no-unit-return`/`no-empty-parens-before-trailing-lambda`, no upstream-native
     corruption bug was found: probing confirmed ktlint's own `replaceChild`-based swap safely
     reorders modifiers around an inline comment, position preserved. Wrasse still bails (report-only)
     on any comment anywhere in the modifier list regardless, matching the project's established
     comment-bail precedent over the actual-safety finding, for uniformity with the other two rules.

  Real-compile note: several textbook ktlint/detekt test shapes are PSI-parseable but fail a real
  `K2JVMCompiler` frontend check (bare `expect class`/`actual class` at file scope outside a real
  multiplatform module, two simultaneous visibility keywords, `open` on a property setter, a getter
  whose visibility doesn't match its property) — both upstreams' own harnesses skip real compilation
  by default for exactly this reason (detekt's own `expect`/`actual` test passes `compile = false`
  explicitly). Every wrasse fixture was hand-verified against a real `kotlinc` invocation before being
  committed; `expect`/`actual` reordering is therefore locked only by the compiler-free
  `ModifierOrderDecisionSpec` (no fixture — a standalone pair does not compile outside a real
  multiplatform module), and the context-parameter/receiver-list exclusion above is ground-truthed by
  probe only, for the same reason (the Kotlin 2.1–2.4 fixture matrix compiles single-target JVM
  without the relevant experimental language feature enabled). Real modifier keywords are never legal
  directly on an enum entry either (confirmed against kotlinc's own `ModifierCheckerHelpers` target
  table and a real-compile probe) — only annotations realistically occur there, locked by
  `enum-entry-annotation-clean`. Fixtures otherwise cover a top-level 2-keyword swap, a 3-keyword
  member soup that is also the multiple-in-file case (3 violations in one file, ported verbatim from
  ktlint's own real test), a constructor `vararg`/visibility parameter swap (detekt's own real test
  shape), a property-accessor `inline`/visibility swap, a `companion object` visibility/modality
  swap, the annotation-interspersed narrower-than-ktlint shape (wrasse's real fixed output,
  `open @Deprecated(...) abstract class`, deliberately differs from ktlint's own, which would
  additionally relocate the annotation to the front), a comment-bail (report-only), an
  `@Suppress`-clean, an already-ordered-clean, and `fun`-interface/`value`-class clean shapes
  confirming both keywords are inert.
  `if-else-bracing` shipped fifth — the brace-insertion family opener, and the first T-bucket fix
  that *inserts* tokens rather than deleting/reordering them. One wrasse id for ktlint's
  `multiline-if-else` + `if-else-bracing` and detekt's `BracesOnIfStatements`. Ground-truthed both
  engines directly (temporary probe cases added to each upstream's own real test file — `KtLint
  AssertThat`-based probes against the real `ktlint-rule-engine`/`ktlint-ruleset-standard` 1.8.0
  jars for ktlint, source + `default-detekt-config.yml` reading for detekt — both checkouts left
  byte-clean, `git status` verified). The two upstreams disagree far more than they agree:
  - ktlint's `if-else-bracing` (restricted to `RuleV2.OfficialCodeStyle`, but `ktlint_official` is
    itself `CodeStyleValue`'s documented default, so it is active out of the box) forces
    *consistency* — any branch already braced forces the rest braced too, even on an otherwise
    single-line statement (probe-confirmed: `if (true) { a() } else b()` → `else { b() }`, an
    asymmetric single/multi-line result).
  - ktlint's `multiline-if-else` (unconditional, no code-style gate) braces every unbraced branch
    whenever the branch does not start on the same physical line as its condition/`else` keyword —
    *and*, separately, unconditionally braces every branch of any `else if` chain (3+ branches)
    regardless of line count, even a chain sitting entirely on one physical line (probe-confirmed:
    `if (true) a() else if (false) b() else c()`, fully single-line, still gets fully braced by
    this rule alone).
  - detekt's `BracesOnIfStatements` ships `active: false` in `default-detekt-config.yml` — ground-
    truthed its *own* default option values regardless (`singleLine = "never"`, `multiLine =
    "always"`), matching the project's practice of ground-truthing an upstream's decision logic
    under its own defaults rather than its default enablement (mirrors how every T-bucket rule so
    far is itself off-by-default in wrasse, D9). Its policy is decided **once per outermost
    `KtIfExpression`** (the whole chain, including any `else if` descendants, evaluated together)
    from whether *that whole statement's own text* contains a newline anywhere — never per-branch,
    never per-`else if`-segment. Its own real shipped test suite explicitly accepts a fully single-
    line `else if` chain with zero braces (`"no braces are accepted"`, `singleLine = "never"`) —
    directly contradicting ktlint's unconditional-`else if`-bracing quirk above. detekt has no
    autocorrect mechanism at all (`BracesOnIfStatements` implements no `Correctable`-style
    interface) — lint-only, every finding above is a "would flag", never a real fix to compare
    output shape against.
  Resolved by strict intersection, per the assignment's brief: wrasse only inserts braces where
  *both* engines' defaults would actually want them added. Since detekt never fixes anything, only
  its *lint* verdict constrains scope; ktlint's real, byte-exact formatter output is what wrasse's
  edits are grounded in wherever both agree bracing belongs. Concretely: a currently-unbraced
  branch gets braced if and only if (a) it is not itself an `else if` continuation (an `ELSE` whose
  sole content is a bare `IF` — `else { if ... }` is never attempted, the branch is handled when
  that nested `IF` is visited on its own instead) and (b) the *enclosing if/else-if/else chain's
  own full source span* (walked up through consecutive `ELSE`/`IF` ancestor pairs to the true chain
  head — this is what makes a locally single-line `else if` tail still get braced when some other
  part of the same chain is multi-line, probe-confirmed against both engines) contains a newline
  anywhere. Purely consistency-driven bracing (any-branch-already-braced forces the rest) and
  ktlint's unconditional-`else if`-chain quirk are both deliberately **out of scope** — detekt's own
  default never wants either, so neither is in the intersection; a fully single-line `if`/`if-else`/
  `else if` chain of any depth is left completely untouched (no report, no fix), matching detekt's
  own real, shipped test assertions for that shape.

  Mechanically: a `WBufferedNodeRule` on `IF` reads its own direct children (`RPAR`, `THEN`,
  `KW_ELSE`, `ELSE`, plus whitespace/comment siblings between them — confirmed via a direct dump of
  the real LightTree structure that leading/trailing whitespace and comments around `THEN`/`ELSE`
  are siblings at the `IF` level, never children of `THEN`/`ELSE` themselves) to locate each
  branch's own bare-content span and the gaps around it. A branch already wrapped in `BLOCK` (first
  content character `{`) is left alone entirely; an empty branch (`if (false) else { ... }` is
  legal Kotlin and must never throw) is skipped the same way. For a genuine bare branch, born-clean
  indentation is computed purely from source facts, never guessed: `baseIndentColumn` is the
  indentation of the chain head's own *physical line* — the count of leading whitespace before that
  line's first non-whitespace character, not the chain head's own column — shared by every brace in
  the chain (locked by the `else-if-tail-single-line-in-multiline-chain-error` fixture, where the
  locally-mid-line `else if` tail's new closing braces still align to the outermost `if`'s own
  column, not its own). **Corrected 2026-07-20 during the wave-2 installment-5 backfill**: the
  original algorithm used the chain head's own column directly (distance back to the previous
  newline), which coincides with the line's leading indentation whenever the chain head is itself
  the first token on its line (every fixture at ship time), but diverges when the chain head sits
  mid-line — `fun foo() = if (...)`, ktlint's own real Issue-1560 shape — misaligning every brace in
  the chain to that arbitrary mid-line position instead of the enclosing statement's real
  indentation depth; ported directly from real, reproduced failures (`property-assignment-midline-
  if-error`, `else-chained-call-same-line-error`) rather than papered over, since both are ordinary,
  unremarkable Kotlin shapes with no other reason to be out of scope. The fix is a three-line change
  to the same column-computation function (scan back to the line start, then scan forward past
  leading whitespace) with no other rule-shape change, and does not affect any existing fixture,
  since none of them has a chain head sitting mid-line. The wrapped body sits at
  `baseIndentColumn + indentWidth` (D21's default,
  4, hardcoded — not yet wired as config, no rule has needed it before this one). Two edits per
  fixed branch: the leading gap (condition's `RPAR`-end or `KW_ELSE`-end through the branch's own
  start) becomes `" {\n" + bodyIndent`; the trailing point (or, for a `THEN` immediately followed
  by `else`, the whole gap up to `KW_ELSE`) becomes `"\n" + closeIndent + "}"` (with a trailing
  space folded in for the `THEN`-followed-by-`else` case, so `"} else"` lands on one line exactly
  as ktlint's own real output does).

  Bails (reported, never autofixed) whenever a comment sits anywhere in the gap around the branch
  (established uniform-bail precedent — same posture as `no-unit-return`/`no-empty-parens-before-
  trailing-lambda`/`modifier-order`, chosen over replicating ktlint's own per-shape comment handling
  which a real probe showed is *not* uniformly safe: a leading same-line-as-condition or own-line
  comment before the body gets its indentation corrupted by ktlint's real autocorrect, though a
  trailing same-line comment after the body does not — wrasse does not attempt the distinction) or
  whenever the branch's own bare-statement text already spans multiple lines on its own (a chained
  call split across lines, ktlint's own real formatter output for this exact shape leaves the
  newly-nested continuation line's indentation completely uncorrected — a genuine upstream
  formatting gap, not something safe to replicate byte-for-byte). Both bail categories report a
  zero-width point at the branch's own content start rather than its full span: the branch can
  contain an independent nested `if` this same rule fixes on its own subsequent visit, and a wider
  span would spuriously overlap that inner fix's edits under the idempotence harness's overlap-
  based "did this diagnostic get fixed" heuristic, wrongly predicting the outer bail's diagnostic
  should vanish in D2 when it never had an edit and is expected to persist unchanged (caught by the
  `dangling-else-nested-error` fixture, the dedicated grammar-risk case per the assignment brief —
  the `else` there keeps binding to the *inner* `if` after bracing, never appearing to shift toward
  the outer one, locked by `IfElseBracingSafetySpec` across all four Kotlin minors alongside an
  `else if`-chain compile/re-lint-clean check).

  This port surfaced a real pre-existing framework bug, invisible until a fix's replacement text
  finally carried significant leading/trailing whitespace around an escaped newline: `WPatchReader`
  called `.trim()` on every raw patch line before parsing, silently eating that whitespace on
  round-trip (every prior T-bucket edit's replacement was either a bare token swap or had no
  whitespace adjacent to its field boundary, so this never fired). Fixed by dropping the `trim()`
  entirely (`CharSequence.lineSequence()` already strips line terminators; `WPatchWriter` never
  indents a structural line), locked by a dedicated round-trip case in `WPatchWriterReaderSpec`.
  `when-entry-bracing` shipped sixth — the brace-insertion family's second and, per the
  ground-truthed disagreement inventory below, its narrowest-scoped member. One wrasse id for
  ktlint's own `when-entry-bracing` and detekt's `BracesOnWhenStatements`. Ground-truthed both
  engines directly (both checkouts read only — `WhenEntryBracingTest.kt`/`WhenEntryBracing.kt` for
  ktlint, `BracesOnWhenStatements.kt`/`BracesOnWhenStatementsSpec.kt` plus
  `default-detekt-config.yml` for detekt's shipped defaults — no probe cases added this time, both
  upstream test suites already cover every shape needed; both checkouts left byte-clean, `git status`
  verified). The two upstreams disagree even more sharply here than in the if-family:
  - ktlint's single `when-entry-bracing` rule (`RuleV2.OfficialCodeStyle`, active by ktlint's own
    default code style) braces every currently-bare entry in a `when` as soon as *either* some entry
    already has a block body (`hasAnyWhenEntryWithBlockAfterArrow`) *or* some entry's body doesn't
    start on the same line as its own `ARROW` (`hasAnyWhenEntryWithMultilineBody`) — either
    condition alone is sufficient, and once triggered, *every* bare entry in that `when` gets braced,
    including ones that are themselves single-line.
  - detekt's `BracesOnWhenStatements` ships `active: false` in `default-detekt-config.yml` (ground-
    truthed its own default *option* values regardless, per the if-else-bracing precedent) with
    `singleLine = "necessary"`, `multiLine = "consistent"` — a materially different pair of defaults
    from `BracesOnIfStatements`' own `multiLine = "always"`. Its policy is chosen **per `when`
    expression**, from whether *any* entry's own body starts on a line after its `ARROW` (the same
    underlying grammar fact as ktlint's `hasAnyWhenEntryWithMultilineBody`): `singleLine =
    "necessary"` never examines bare entries at all — reading its own real shipped test suite
    (`existing braces are flagged` under `=necessary`) confirms it only ever flags an *already-braced*
    entry for **removal** when unnecessary, the opposite direction from bracing; `multiLine =
    "consistent"` flags a mix of braced and bare entries (`inconsistent braces are flagged`), but its
    own `no braces are accepted` case (verified directly in `BracesOnWhenStatementsSpec`) shows a
    fully-bare `when` — single-line entries or multi-line alike — is accepted outright, never
    flagged, even though ktlint alone would brace it.
  Resolved by strict intersection, same methodology as if-else-bracing: wrasse only braces a bare
  entry where *both* engines' defaults actually want a change there. Concretely, an entry is a
  candidate only when, within its own `when`, (a) some entry (any entry, braced or bare) has a body
  that doesn't start on the same line as its own `ARROW` — the trigger that flips detekt from
  `singleLine` to `multiLine` mode — **and** (b) some entry already has a non-empty block body — the
  only way `multiLine = "consistent"` ever flags anything, since a fully-bare `when` is accepted
  regardless of (a). Requiring both is what excludes two shapes ktlint alone would fix: a fully-bare
  `when` with a multiline entry but nothing already braced (locked clean by
  `fully-bare-multiline-clean` — ktlint's own real
  `Given a when-statement with a multiline body not contained in a block then add braces to all
  entries` test, reproduced and confirmed to disagree with detekt's `no braces are accepted`), and a
  fully single-line `when` mixing a braced and a bare entry with no entry ever spanning multiple
  lines (locked clean by `mixed-braced-bare-all-single-line-clean` — ktlint's own real `Given a
  when-statement containing an entry with braces and an entry without braces then add braces to all
  entries` test, where detekt's `singleLine = "necessary"` would want the *opposite* — removing the
  existing braces, never adding to the bare one). An empty block (`1 -> {}`) never counts as "already
  braced" for gate (b) either way — matches detekt's own `hasUnnecessaryBraces` exemption for an
  empty block, which excludes it from the consistency tally too (locked by
  `empty-block-sibling-not-counted-clean`). A single-entry `when` can never satisfy gate (b) at all
  (locked by `single-entry-when-clean`).

  Mechanically: a `WBufferedNodeRule` targeting both `WHEN` and `WHEN_ENTRY` (`ctx.type` distinguishes
  which fired, the same idiom `NoEmptyClassBodyRule` uses for its `companionStack`) pushes a small
  per-`when` accumulator (`anyEntryHasBlockBody`, `anyEntryHasMultilineBody`, the list of bare
  candidates) on `WHEN` enter and pops it on `WHEN` exit, evaluating the two gates and reporting only
  then; nesting (a `when` in another's subject, condition, or entry body) composes for free since the
  accumulator is a stack, not a single field. Each `WHEN_ENTRY`'s own direct children (found via its
  own `ChildBuffer`, confirmed via the real fixture harness rather than a separate dump probe —
  `ARROW` is a direct child regardless of how many comma-separated conditions precede it, unaffected
  by a `when` subject's presence or absence) locate the body's start precisely: the first non-
  whitespace-non-comment child after `ARROW`, whose own `WNodeType` is checked for `== BLOCK` (more
  precise than if-else-bracing's own first-character check, and avoids that check's known imprecision
  for a bare-lambda body, since a `WHEN_ENTRY`'s `ChildBuffer` — unlike `IF`'s `THEN`/`ELSE` — hands
  back the body's real node type directly). An empty block is recognized by blanking the text between
  its own braces (any comment or KDoc inside still counts as non-empty, same `NoEmptyClassBodyRule`
  convention). Indentation reuses [BraceInsertion] (extracted from `if-else-bracing`'s own
  `physicalLineIndentColumn`/edit-construction during this port — the two rules are close enough
  cousins that duplicating the mid-line-construct fix from the if-else-bracing backfill would have
  been a straight copy-paste, so it is now one shared helper instead) with `baseIndentColumn` shared
  by every entry in the `when`, computed once from the *enclosing `WHEN`'s* own physical line plus one
  `indentWidth` level — **corrected 2026-07-20 during the wave-2 backfill** from the original per-entry
  computation (each entry's own physical line, no shared base); see the dated backfill paragraph below
  for the mid-line-`WHEN` bug this replaced.

  Bails (reported, never autofixed) whenever a comment sits anywhere in the gap around the entry's
  body — between the `ARROW` and the body, or trailing the body on its own line before the next
  sibling (same uniform-bail precedent as every other T-bucket rule; leading case locked by
  `comment-before-body-bail-error`, trailing case by `eol-comment-after-body-bail-error` — a comment
  sitting *before* the whole entry, outside the `ARROW`-to-body gap, is untouched and unaffected,
  locked by `comment-before-entry-preserved-error`) or whenever the entry's own bare-expression text
  already spans multiple lines (a chained call split
  across lines, locked by `chained-call-multiline-body-bail-error` — ktlint's own real test suite
  confirms this shape needs a *second*, separate `IndentationRule` pass to reindent correctly, so
  replicating `when-entry-bracing`'s own real output byte-for-byte on its own would not itself be
  born-clean, same finding as if-else-bracing's own chained-call bail). An entry whose body is a
  single-line `if`/`when` expression is not exempt from this shape and gets braced normally (locked by
  `if-expression-body-single-line-error`) — only an embedded newline triggers the bail, not the
  presence of a nested conditional as such.

  **Cross-rule interaction with `if-else-bracing` (the assignment's key risk):** when a bare entry's
  body is itself a bare, multi-line `if`/`else` (`1 -> if (big) "big" else "small"`, physically
  spanning several lines), `when-entry-bracing`'s own embedded-newline bail fires on that entry —
  report-only, zero edits — while `if-else-bracing`, visiting the nested `IF` node on its own
  subsequent (post-order, child-before-parent) visit, braces its `THEN`/`ELSE` branches
  independently. No `EditPlan` composition (`takeEditsIn`, §5.2) is needed at all: the two rules are
  disjoint by construction, not by coincidence — whenever `when-entry-bracing` would otherwise succeed
  (no embedded newline in the entry's own content), any `if`/`else` inside that entry is, by the same
  token, itself confined to one physical line, so `if-else-bracing`'s own `chainSpansMultipleLines`
  gate never fires there either; whenever that gate *does* fire (the chain spans multiple lines),
  `when-entry-bracing`'s own bail has already fired first, emitting zero edits for that span. Proven
  both by the dedicated `when-if-bracing-combined/entry-bare-if-body-error` fixture (both rules
  enabled together, one `wrasseFix` pass, D19's idempotence cycle green) and by
  `WhenEntryBracingSafetySpec`'s own second real-compile case across all four Kotlin minors, which
  additionally confirms the bail's diagnostic persists unchanged into D2 as an expected survivor
  (`assertExpectedSurvivors`) rather than vanishing, since it never had an edit of its own — the exact
  same reasoning if-else-bracing's own `dangling-else-nested-error` fixture already established for a
  bail report's span never spuriously overlapping an inner fix's edits.

  Retroactive upstream-test backfill, wave 2 installment 6 (2026-07-20): `when-entry-bracing`
  (ktlint's own `WhenEntryBracingTest` — 7 real test cases — plus detekt's own
  `BracesOnWhenStatementsSpec`, restricted per D21 to the two config-combination nested classes that
  actually exercise the shipped default (`singleLine=necessary`, `multiLine=consistent`); the rest of
  detekt's suite is config-permutation coverage fixed at other `singleLine`/`multiLine` values and is
  out of scope) ported against both real upstream suites independently. Six of ktlint's seven cases
  and both in-scope detekt nested classes were already subsumed by the ship-time fixtures (`all-bare-
  single-line-clean`, `mixed-braced-bare-all-single-line-clean`, `fully-bare-multiline-clean`,
  `chained-call-multiline-body-bail-error`, `comment-before-entry-preserved-error`); one new fixture,
  `all-braced-single-line-clean`, closes a gap symmetric to if-else-bracing's own `single-line-if-
  else-already-braced-clean` (detekt's `singleLine=necessary` wants these single-statement braces
  *removed*; this insert-only rule stays silent, matching the sibling rule's already-documented
  asymmetry).

  Two real, contained bugs found and fixed, both from probing shapes neither upstream suite's own
  fixed test inputs happened to combine, in the same spirit as the if-else-bracing wave's mid-line-
  head bug: (1) ktlint's own seventh case (a bare entry with an EOL comment trailing its body on the
  same line, `BAR1 -> "bar1" // comment`) exposed that the leading/trailing comment-bail was only
  ever checking the gap *before* the body (between `ARROW` and the body), never the gap *after* it —
  unlike `if-else-bracing`'s own sibling check, which already covers both sides. A bare entry with a
  trailing same-line comment was silently braced with the comment left stranded after the closing
  brace (`"two"\n} // trailing`) instead of bailing, an asymmetry with the sibling rule the KDoc's own
  "same established uniform-bail precedent" claim didn't actually hold. Fixed by scanning the
  enclosing `WHEN`'s own children (available at the `WHEN` exit, once all entries are already
  recorded) for a comment immediately following each candidate entry, before any intervening newline;
  locked by `eol-comment-after-body-bail-error`. (2) Constructing a `WHEN`-entries-inside-a-lambda-
  and-mid-line-expression probe (per the assignment's own hunt list) surfaced the if-else-bracing
  wave's exact class of bug recurring here: `baseIndentColumn` was computed from *each entry's own*
  physical line, which coincides with the entry's own column only when the entry itself starts its
  line — false whenever an earlier entry on that `WHEN` sits mid-line (`= when (x) { 1 -> "one"` on a
  function's expression-body line, reproduced as `midline-when-expression-error`), misaligning that
  one entry's inserted braces to the enclosing statement's own column while every sibling entry (each
  starting its own line normally) stayed correctly indented. Fixed analogously to the if-else-bracing
  precedent: `baseIndentColumn` is now computed once per `WHEN` (not per entry) from the *enclosing
  `WHEN` node's* own physical line plus one `indentWidth` level, shared by every entry in that `when` —
  correct both in the common case (identical to the old per-entry value, since a normally-formatted
  entry's own physical line already equals the `WHEN`'s line indent plus one level) and in the mid-
  line case. Regression-free against every pre-existing fixture, since none has an entry sitting
  mid-line.

  Four more fixtures close shapes from the assignment's own hunt list that neither upstream suite's
  ship-time-adjacent probing had combined: a self-nested `when` (a bare entry's own body is itself a
  `when` expression, `nested-when-entry-body-error` — confirms the per-`when` accumulator stack
  composes correctly for a rule nesting inside itself, not just against `if-else-bracing`, exactly as
  already documented for the cross-rule case); a multi-line condition list before a single-line bare
  entry (`multiline-condition-list-error` — confirms the leading condition list, which sits entirely
  before `ARROW`, never leaks into the multiline-body gate check); an already-multi-line block as the
  *sole* trigger for bracing bare single-line siblings, with no bare entry itself ever starting on a
  new line (`multiline-block-triggers-bracing-error` — the broad `arrowEnd..contentEnd` newline check
  picks up a block's own internal newlines exactly as detekt's real `isMultiLine` sibling-text check
  does, a previously-implicit-but-untested code path); and a `when` used as a constructor-call argument
  (`when-inside-constructor-call-error`, closing the "whens inside constructor calls" hunt item with a
  normally-indented case once the mid-line fix above covers the misindentation risk). A `WHEN_ENTRY`
  body written as a lambda literal (`1 -> { { 1 } }`, the one genuinely tricky shape in detekt's own
  suite) was considered and ruled out as a risk without a new fixture: Kotlin's own grammar always
  parses a `{` immediately after `ARROW` as a `BLOCK`, never a lambda value, so this shape is already
  indistinguishable from any other already-braced entry to this rule's existing `== BLOCK` check —
  there is no code path where a bare `LAMBDA_EXPRESSION` could ever reach this rule as a when-entry's
  direct body. Both upstream checkouts left byte-clean, `git status` verified.
- **B.3 — ImportEngine (bucket S) — fusion complete 2026-07-19.** `no-unused-imports`,
  `no-wildcard-imports`, and `import-ordering` shipped independently first (all three ahead of any
  engine — resolution-facade spike, `no-unused-imports`' unused-import detection and removal
  autofix, `no-wildcard-imports`' package-star expansion and its seven bails, `import-ordering`'s
  ASCII re-sort, and `no-unused-imports`' zero-attribution star removal — all still described in
  full in §8 as the historical record), each gated by the idempotence harness as it landed. They
  are now one `WStreamRule` behind `WUninitializedRuleGroup`, `ImportEngine` (§4, §8's closing
  as-built paragraph) — a pure refactor: same fixtures, same diagnostics, same patch output,
  registration-order dependency and `EditPlan.takeEditsIn` self-consumption both retired along with
  the three separate rules. `SemanticWRule` unification and LightTree↔FIR offset correlation
  remain unbuilt (the engine still reads `WContext.resolvedUsage`, the file-level facade, not a
  per-node one). Member-star (class/object) expansion shipped 2026-07-19 (§8's closing as-built
  paragraph: authoritative `WResolvedImport`-based classification, an `isStatic`-gated legality
  check for enum entries/Java statics, whole-star bail on any instance-member usage). The
  LightTree↔FIR offset-correlation spike (D.1), the report-only `no-unnecessary-fqn` rule built on
  it (D.2, a fourth `ImportEngine` id), and the fix attached to that same decision (D.3) all shipped
  2026-07-19 — §8's closing as-built paragraphs. **The FQN→import track (D.1–D.3) is complete.**
  Still unbuilt, tracked as the engine's own remaining growth sites: own-package/default-redundant
  star removal, and closing the KDoc same-package-sibling coverage gap via a session-backed
  package→declarations query.

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

- `ChildBuffer` allocated per `WBufferedNodeRule` enter — pool when engines land.
- Registrar selection probes internal compiler class names (`classExists` markers); checker
  shells exist in triplicate (k20/k22/main) — any signature change must be mirrored. Version
  matrix is the safety net. **This bit in practice:** `WrasseCompilerPluginRegistrar20`/`22`
  (the delegate shells for non-current minors) reconstructed their own `WrassePlugin` via
  `wrasseMain(...)` without forwarding `fixEnabled`/`fixOutputDir` from the compiler
  configuration — autofix silently never wrote a patch file on any minor routed through those
  shells (2.1–2.3 as configured today), while diagnostics still reported correctly, so
  `assertMatchesExpectations` never caught it. `IdempotenceCycle.runIfFixEmitted` no-ops
  silently when the patch file is absent, so no existing assertion caught it either, on any
  rule, the whole time — found only when the `.fixed.kt` companion mechanism (§11) added an
  assertion that requires a patch to actually exist. Fixed by threading the same two config
  keys through both delegate shells; regression coverage is now every autofix fixture with a
  companion replayed via `testMinorHarness`/`testPatchHarness`.
- Patch file stores absolute paths — not portable across machines/CI. Not solved, tracked.
- `wrasseApply` task registered for all subprojects (`onlyIf`-guarded noise in `./gradlew tasks`).
- `:app:wrasse-kotlinc-internal-k20` and `:testing:wrasse-benchmarks` print Gradle's "Kotlin Gradle
  plugin was loaded multiple times" warning on every build (confirmed pre-existing, harmless so far
  — build/test output is unaffected). Root cause: `internal-convention-plugin` bundles
  `kotlin-gradle-plugin` as an ordinary dependency and applies it via `pluginManager.apply(String)`
  from the included build, which resolves through a different classloader than projects that pull
  Kotlin in via the `plugins { }` DSL / an externally-resolved plugin (e.g. `me.champeau.jmh`, which
  itself touches Kotlin Gradle Plugin classes). Tried swapping the bundled dependency for the Kotlin
  plugin's marker-artifact coordinate (`org.jetbrains.kotlin.jvm:org.jetbrains.kotlin.jvm.gradle.plugin:<ver>`)
  hoping to hook into Gradle's plugin-classloader cache — no effect, confirming that cache is only
  keyed through actual `plugins{}`/`PluginDependenciesSpec` resolution, not a plain `dependencies {}`
  declaration consumed by `pluginManager.apply`. A real fix means restructuring which layer applies
  `org.jetbrains.kotlin.jvm` (e.g. every module applying it directly via `plugins{}` instead of
  `internalConvention` doing it on their behalf) — touches all ~17 modules, out of scope for a
  contained chore.
- Incremental-compilation DX: warnings in files that didn't recompile don't reappear in output;
  `-PwrasseCheck`/`-Pwrasse.fix` changing compiler args forces full recompilation — currently
  accidental, should be documented as the intended "full sweep" mechanism.
- `FirSyntacticChecker` allocates a `KtLightSourceElement` per violation — fine (violations are
  cold), noted for completeness.
- **`wrasseFix` converges layer-by-layer, not in one sweep, when `level: error` is in effect
  (a direct consequence of error-level + fix mode, not a bug).** kotlinc's own compile fails the
  whole compilation unit as soon as it hits an `ERROR`-severity diagnostic, so a module with a
  wrasse violation never lets `-PwrasseFix` reach modules *downstream* of it in the same compile
  batch (e.g. `compileKotlin`/`compileTestKotlin` stop at the first failing module; anything that
  depends on it, or a later source set in the same module like `compileTestKotlin` after
  `compileKotlin`, is never even reached that pass). Each `./gradlew wrasseFix` run therefore
  fixes only the violations visible up to the first still-failing module, then `wrasseApply`
  writes that one patch and the run ends (`BUILD SUCCESSFUL` — the compile's failure is expected
  and swallowed by design, `failOnError = false` on that specific fork; see `forkGradle` in root
  `build.gradle.kts`). The *next* run reaches one layer further. Confirmed dogfooding
  `import-ordering` repo-wide (D18 chain finale): converged in three `./gradlew wrasseFix` passes
  — pass 1 fixed `wrasse-kotlinc-plugin-tests-base`'s main source set (which had blocked every
  module after it), pass 2 then reached its test source set (compiled separately, after main) and
  fixed a violation there, pass 3 found nothing left. **`warnOnly` is the intended one-sweep
  mechanism**: with `-PwarnOnly` (or the global `warnOnly` config flag), violations report but
  never fail the compile, so every module gets visited and every fix gets written in a single
  `-PwrasseFix` pass — `wrasseFix` should default to (or document) `warnOnly` semantics for
  first-time/large-scale adoption sweeps, reserving the repeat-until-clean workflow above for the
  steady-state case of a handful of new violations. Not implemented as a `wrasseFix` default yet
  — tracked here.
- **`wrasseApply`'s classpath used to depend on the consuming module's own dependency
  declarations (fixed).** `configureWrasseApply` (`internal-convention-plugin`) built the
  `JavaExec` classpath from `project.files(jar, runtimeClasspath)` — fine for modules that pull
  wrasse-lang in via `implementation`, but `app/wrasse-kotlinc-internal-k20`/`-k22` declare it
  `compileOnly` (the compiler-plugin-loading model: their code must not leak kotlinc/wrasse
  classes into the *compiled artifact*, since they run inside kotlinc's own classloader), so
  `WPatchApplierKt` was never on their `runtimeClasspath` at all —
  `wrasseApply` failed there with `ClassNotFoundException` every time those modules had a fixable
  violation, found via dogfooding `import-ordering` repo-wide. Fixed: a dedicated, unconditionally
  resolvable `wrasseApplyClasspath` configuration, seeded by the convention plugin itself from the
  `wrasse-compiler-plugin` catalog coordinate (the same one `configureWrasse` already resolves for
  `kotlinCompilerPluginClasspath`) — its published POM carries `wrasse-lang` as a transitive
  runtime dependency, so this is independent of whatever the consuming module itself declares.
  Regression-locked by a TestKit spec (`internal-convention-plugin`) applying the plugin to a
  throwaway module with only `compileOnly` wrasse-unrelated deps (and a second with none at all),
  pre-seeding a patch, and asserting `wrasseApply` still applies it.
- **`forkGradle` (root `build.gradle.kts`) failure propagation, audited and hardened.** Suspected
  (from the same dogfooding session) that a failing nested `wrasseApply` fork could leave
  `wrasseFix` reporting `BUILD SUCCESSFUL`. Direct reproduction with the pre-fix classpath bug
  above (a real `wrasseApply` failure) showed `forkGradle`'s existing `failOnError` check already
  propagates correctly (`BUILD FAILED`, `"Forked gradle task failed"`) — could not reproduce a
  silent swallow. Hardened anyway per standing policy (dogfood-reported risk still gets a test):
  `forkGradle("wrasseApply", ...)`'s `failOnError` is now passed explicitly (`true`) rather than
  relying on the default, and a TestKit probe (`ForkGradleFailurePropagationSpec`) reproduces the
  exact fork-and-check pattern verbatim in a throwaway fixture project (its own copy of this
  repo's Gradle wrapper, so the nested `./gradlew` reuses the already-downloaded distribution)
  against a task that deliberately fails — locking that `failOnError = true` propagates,
  `failOnError = false` suppresses (the by-design behavior for the compile-with-fix fork), and a
  successful nested build never trips the check. Chose this over a full TestKit run of the real
  `wrasseFix` task because root `build.gradle.kts` has no test source set of its own and the real
  task spans the whole multi-module build — disproportionate for what is a generic
  fork/propagate question, decoupled from any wrasse-specific logic.
- **Blocker, flagged not fixed (wave-2 installment-3 backfill, 2026-07-20): alias-identity blind
  spot in `UnusedImportDecision` — a silent false negative.** `matchesClassifier`/`matchesCallable`
  key purely on the import's own FQN; `WResolvedUsage.classifiers`/`callables` are whole-file,
  deduplicated `Set`s keyed by resolved target, not by "which import directive brought this into
  scope." Two import directives of the identical target FQN with different (or no) aliases are
  legal, compiling Kotlin (confirmed elsewhere in this same section — the alias-blind-exclusion
  fix in `no-wildcard-imports` expansion), e.g. `import foo.Bar as Bar1` / `import foo.Bar as
  Bar2` with only `Bar1()` ever called: `matchesClassifier("foo.Bar", classifiers)` returns `true`
  for **both** records, since the classifier set only records the resolved target, not which
  alias reached it — `Bar2`'s dead import is silently kept forever, never reported. Only the
  KDoc/comment textual fallback is alias-aware; the two primary classifier/callable branches are
  not. Not a fixture-only gap: fixing it for real needs per-occurrence usage data (which import
  directive a given reference actually went through), not the current whole-file `Set` facade —
  the same shape of facade work `no-unnecessary-fqn`'s `qualifiedUsages`/`identifierOccurrences`
  already carries, extended to `no-unused-imports`. Flagged as a blocker rather than fixed
  in-task: touches `WResolvedUsage`'s facade shape, high blast radius, an owner call.
- **Blocker, flagged not fixed (wave-2 installment-3 backfill, 2026-07-20): no mechanism anywhere
  in `ImportEngine` detects or removes exact-duplicate import directives.** Ktlint's own
  `ImportOrderingRule` treats a literal duplicate `ImportPath` (identical FQN and alias) as
  unconditionally reportable and auto-removable — `"Duplicate 'import a.b.C' found"` — regardless
  of whether the shared target is used; this is core, long-standing upstream behavior (ktlint
  issue 1243's regression test), found porting `ImportOrderingRuleIdeaTest`/`-AsciiTest`. Wrasse
  has no equivalent anywhere in the fused engine: `UnusedImportDecision` loops directives
  independently with no cross-directive "have I seen this exact FQN+alias already" check, and
  `UnusedStarDecision`/`WildcardExpansionDecision`'s star-duplicate bail only covers the
  zero-attribution case, never "duplicate stars/explicit imports whose target *is* actually
  used." A realistic, common pattern (merge-conflict duplicate imports, copy-paste) that ktlint
  actively cleans up and wrasse currently cannot touch at all. Flagged as a blocker rather than
  fixed in-task: a genuinely new decision path (which duplicate survives, the message, how it
  composes with `import-ordering`'s own re-sort and `no-unused-imports`' removal), not a contained
  bug fix — an owner call on scope, not an implementation detail.
- **Blocker, flagged not fixed (wave-2 installment-4 backfill, 2026-07-20): `modifier-order`'s
  autofix can produce code that crashes Kotlin 2.1.x's own backend for at least the `data`+
  `internal` reorder.** Porting detekt's own real `data internal class Test(val test: String)`
  case (`kt classes with modifiers`) into an end-to-end fixture, `testMinorHarness`'s idempotence
  check (§11, D19) failed *only* on Kotlin 2.1 (2.2/2.3/2.4 all green): round 1 (the reported-as-
  wrong `data internal class ...`) compiles cleanly, but round 2 — the applied fix, wrasse's own
  *correct* reorder to `internal data class ...` — crashes the real `K2JVMCompiler`'s Fir2Ir
  lowering phase with `java.lang.IllegalStateException` at
  `Fir2IrDeclarationStorage.findContainingIrClassSymbol`. Confirmed deterministic (reproduced
  running the same task twice) and confirmed specific to the two keywords' textual order, not
  incidental to the fixture: renaming every identifier in the file (class name, constructor
  parameter name) left the crash unchanged. This is a genuine Kotlin 2.1.x compiler defect, not a
  wrasse logic bug — modifier order carries no FIR/IR semantics upstream, so kotlinc's own backend
  should be insensitive to it — but wrasse's single JAR ships one behavior across the entire
  2.1–2.4 matrix (§10) with no mechanism for a rule to know, or condition its edits on, the exact
  Kotlin patch version compiling the host project. Concretely: a real project still on Kotlin 2.1.x
  running `wrasseFix` on a `data`+`internal`-ordered class today would have its build broken by
  this specific fix. Not fixed in-task: there is no contained change available in `modifier-order`
  or `ModifierOrderDecision` itself (the reorder logic is correct; the target compiler is buggy),
  and adding target-Kotlin-version awareness to a rule's edit decision would be new, cross-cutting
  infrastructure with no precedent anywhere in the rule model — an owner call on whether/how to
  build it (a version-conditioned edit suppression facade), not an implementation detail. Locked
  instead as a compiler-free `ModifierOrderDecisionSpec` pair (violation + already-ordered clean),
  matching the same real-compile-inexpressible precedent used for `expect`/`actual`, since it
  cannot be an end-to-end fixture without breaking the very version matrix `testMinorHarness`
  exists to guard.
