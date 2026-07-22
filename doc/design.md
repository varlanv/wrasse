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
fused"; §8 has `ImportEngine`, the first one; §13's Phase B has `ModifierEngine`, the second,
fusing `modifier-order`/`redundant-visibility-modifier` after a real cross-rule edit-overlap crash
between them surfaced, §14). It declares `ids: Set<String>` and
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

Small, surgical, behavior-preserving edits: the **T** bucket (~14 fixes: brace insertion,
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
| **T** — targeted fixes | 20 (~14 unique) | Individually-toggleable `WRule`s emitting edits via EditPlan |
| **L** — lint-only | 129 | Report-only rules; the mechanical bulk of Phase B |
| **X** — dropped | 10 | Not ported (niche dogma, compiler-covered, outbound) |

Load-bearing observation: 76 of ktlint's 105 rules are pure layout. Ported ktlint-style that is
76 interacting rewriters (the fixed-point trap, forever); as a printer they are branches of one
component. The "200-rule port" therefore collapses to: **1 printer + 1 import engine + ~14 small
fixes + ~129 report-only rules**.

Notable classification calls (the printer contract decides all of them):

- **Brace insertion** (`if-else-bracing`, detekt `braces-on-*`, …) → **T**, not F: the printer
  never inserts tokens the author didn't write.
- **`modifier-order`** → **T** (token reordering, not layout).
- **Redundant-syntax deletions** (empty `{}` bodies, useless parens, redundant `public`,
  `: Unit`, useless backticks, `it ->`, trivial accessors, numeric underscores,
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
7 parameters (max 5)"). An autofix-capable rule's report that declines to attach an edit for this
particular occurrence gets `" (no autofix for this shape)"` appended (§14's "Report-only bails"
entry); report-only rules never carry it. All shipped rules already conform; every future port must
too. Where
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
- **D23 — Compiler-wide facts reach a rule via `WrasseRuleConfig`, not just `wrasse.json` ·
  Accepted 2026-07-20.** `WrasseRuleConfig` gained `explicitApiActive` (§13's `redundant-
  visibility-modifier` entry): a fact about the current compile
  (`CompilerConfiguration.languageVersionSettings`'s explicit-API flag), not something a user sets
  in `wrasse.json`, threaded uniformly onto every rule's config from `WrasseCompilerPluginRegistrar`
  rather than gated behind a config key. Precedent for any future rule needing a compiler-wide,
  non-user-configurable fact (a language-version gate, a target-platform check): add a field to
  `WrasseRuleConfig` with a safe default, populate it once in the registrar, thread it through
  `wrasseMain`/`loadConfig`/`WConfig.from`/`buildConfig` uniformly — never branch by rule id at
  config-construction time; the consuming rule alone decides what the field means.

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
  - **D24 — amends D18: an invariant violation is now a per-file warning, not a compile-killing
    throw · Accepted 2026-07-20.** D18's "fail loudly at compile time" was implemented as an
    uncaught `check(...)` in `EditPlan.finalEdits()` (and a sibling one in
    `WrassePlugin.checkFile`'s ancestor-containment guard), which propagates out of the FIR
    checker; kotlinc's own top-level handler turns that into an opaque `INTERNAL_ERROR` diagnostic
    and kills the *entire* compile, including the compiler's own unrelated diagnostics for that
    file and every other file in the module (reproduced and recorded in §14's now-superseded
    wave-2-installment-7 entry). That is strictly worse than any other wrasse bug class: a rule
    that reports a wrong offset or a bad edit at worst corrupts one file's fix; this killed the
    build. `WrassePlugin.checkFile` now wraps its entire per-file body (walk, rule dispatch, edit
    collection, printer/`DocBuilder` render, and `EditPlan.finalEdits()` itself) in one
    `runCatching`, so this invariant check is caught exactly like any other wrasse bug: reported
    as a single `RuleLevel.WARN` `ViolationReport` naming the exception type and message, this
    file's diagnostics and edits are discarded wholesale (never partially applied), and the compile
    proceeds — kotlinc's own checkers still run for this file and every other file. The loud-
    failure *intent* survives in the message, not in killing the build: the overlap check's
    existing rule-attributed text (`"EditPlan: overlapping edits from rule '<id>' (...) and rule
    '<id>' (...)"`) is the exception's own message, so it flows into the warning verbatim — nothing
    about *which* two rules collided is lost, only the ability to take down the user's build over
    it. Rule/engine attribution beyond what the triggering exception's own message happens to carry
    is deliberately not attempted: the SAX walk (`LightTreeStreamAdapter`) dispatches many rules per
    leaf/node with no per-call bookkeeping today, and adding a "currently dispatching rule" write
    before every dispatch call, across every rule kind, purely to attribute a hopefully-rare crash,
    is overhead on the hot path for a benefit the exception's own message/type/stacktrace already
    covers well enough to diagnose — guessing an attribution from stale state would risk being
    wrong, which is worse than omitting it.
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

- **B.1 — lint-only rules (~129, bucket L) — first installment, the NAMING family, shipped
  2026-07-21.** Report, never fix — a rename is cross-file and never automatic, so `canAutofix` is
  false everywhere in this family and no rule attaches a `WEdit`. Dedupe map (28 catalog rows
  across ktlint/detekt/diktat → 7 wrasse ids):

  | wrasse id | dedupes |
  |---|---|
  | `class-naming` | ktlint `class-naming`, detekt `class-naming`, diktat `identifier-naming` (class sub-check) |
  | `function-naming` | ktlint `function-naming`, detekt `function-naming`, diktat `identifier-naming` (function sub-check) |
  | `property-naming` | ktlint `property-naming`, detekt `object-property-naming`/`top-level-property-naming`, diktat `identifier-naming` (property sub-check) |
  | `enum-entry-naming` | ktlint `enum-entry-name-case`, detekt `enum-naming` (entries, not the enum class itself), diktat `identifier-naming` (enum sub-check) |
  | `package-naming` | ktlint `package-name`, detekt `package-naming`, diktat `package-naming` (unique sub-checks) |
  | `backing-property-naming` | ktlint `backing-property-naming`; diktat's `implicit-backing-property` is a related consistency check, not separately ported |
  | `filename` | ktlint `filename` |

  Deferred out of scope for this installment (documented, not silently dropped): detekt's
  `constructor-parameter-naming`/`function-parameter-naming`/`lambda-parameter-naming`/
  `variable-naming` (parameter and local-variable naming — a different scope-resolution shape than
  member/top-level declarations, held for a follow-up batch); `forbidden-class-name` (a
  user-supplied blocklist, a policy-config shape, not a casing check); `function-name-max-length`/
  `function-name-min-length`/`variable-max-length`/`variable-min-length` (length metrics, a
  different config axis than casing); diktat's `parameter-name-in-outer-lambda` (bespoke
  `it`-naming judgment, a refactoring suggestion more than a casing check).

  Per-rule semantics, exemptions, and where the three catalogs disagree:

  - **`class-naming`** — PascalCase for `class`/`interface`/`object`. Exemptions: a backtick-
    wrapped Kotlin keyword identifier (e.g. `` `data` ``) always; any backtick-wrapped name when the
    file imports `org.junit.jupiter.api` (JUnit 5) — an import-based "this is test code" heuristic,
    since wrasse's streaming model has no test-source-set concept and an import is the only
    reachable signal (mirrors how the upstream rule this id derives from decides the same thing).

  - **`function-naming`** — lowerCamelCase. Exemptions: an override (the name may be fixed by a
    supertype outside this project); a factory function — the union of "declared return type equals
    the function's own name" and "no declared return type, single-expression body is an unqualified
    call to the same name" (two different catalogs' own factory carve-outs, both kept to minimize
    false positives); a backtick-wrapped keyword always; in a file importing a known test library
    (`io.kotest`, `junit.framework`, `kotlin.test`, `org.junit`, `org.testng`) any backtick-wrapped
    name, or a plain name that may also contain underscores. No Compose `@Composable` carve-out —
    not a documented exemption in any of the three source catalogs, so not invented here.

  - **`property-naming`** — `const val` must be SCREAMING_SNAKE_CASE (`serialVersionUID` excepted
    outright regardless of casing); otherwise lowerCamelCase, except: a leading-underscore backing
    property (owned by `backing-property-naming` instead), a property with a custom getter, a
    top-level `val`, or an object-member `val` — all three skipped because immutability, and thus
    whether SCREAMING_SNAKE_CASE was actually intended, cannot be determined without resolution.
    **Catalog disagreement**: one catalog's own rule leaves top-level/object-member `val` unchecked
    entirely (its stated reason: can't reliably tell if the value is meant to be immutable);
    another checks them anyway with a permissive pattern. Adopted the narrower, fewer-report
    reading. A top-level or object-member `var` is not exempt either way (only `val` triggers the
    exemption), so still needs plain lowerCamelCase.

  - **`enum-entry-naming`** — PascalCase or SCREAMING_SNAKE_CASE, the union of the two conventions
    checked side by side rather than picking one. **Catalog disagreement**: the three catalogs'
    defaults range from "both allowed" to "either style, chosen by config, never both at once" to
    "very permissive — arbitrary mixed casing after the first letter, not a real convention."
    Adopted the two-real-conventions union over both the arbitrary-mixed-case permissiveness and
    the single-style-only default (narrower than needed, would report the other convention).

  - **`package-naming`** — no underscores anywhere; each dot-separated segment starts with a
    lowercase letter followed by letters or digits (digits and mixed case allowed after a segment's
    first letter). **Catalog disagreement**: one catalog requires a segment's first run of
    characters to be pure lowercase letters only (no digits, no mixed case ever); the adopted
    pattern is the more permissive of the two, reporting strictly fewer files. A corporate reverse-
    domain-prefix policy (one catalog's own distinct check) is out of scope here — project-specific
    policy, not a general naming convention.

  - **`backing-property-naming`** — a leading-underscore *member* property (never top-level or
    local, see narrowing below) must be `_` followed by lowerCamelCase, never on an override. When a
    same-`CLASS_BODY` sibling property, or a single-empty-parameter-list getter function named
    `get<Capitalized>`, correlates by name, that sibling must be public. **Narrowed from the
    upstream rule this id derives from**: that rule also requires the property itself to carry
    `private`, and reports an unconditional "no matching member" violation whenever none is found
    in scope at all — including for *every* top-level or local underscore-prefixed identifier,
    since no correlated-member concept even applies there. wrasse only ever looks for a sibling
    within the same `CLASS_BODY` (the one reachable unit in the streaming model — no companion-
    object indirection, no cross-class-body search); when no sibling is found there, the check is
    skipped rather than reported, and top-level/local identifiers are never targets at all. Both
    narrowings trade upstream's report volume for zero false positives from context this model
    can't see. The standalone "must itself be `private`" requirement is dropped too — a visibility
    convention, not a naming one, and out of this batch's scope.

  - **`filename`** — a file with exactly one non-private top-level `class`/`interface`/`object`
    must be named after it (exact, case-sensitive match); otherwise the file name must be
    PascalCase. Folds a single-top-level-`typealias`-or-`object` file, and a file with several
    top-level declarations where only one "doesn't extend" the sole class, into the same plain
    PascalCase fallback rather than upstream's more particular per-shape branches — converges to
    the same outcome whenever the object/typealias name is already properly cased (which
    `class-naming` already enforces separately), and never reports *more* files than upstream's
    finer branching would.

  Casing itself is checked with `Char.isUpperCase`/`isLowerCase`/`isLetterOrDigit`
  (`IdentifierCasing`, shared by all seven decisions) rather than porting an ASCII regex plus a
  diacritic-normalization utility — Kotlin identifiers admit arbitrary Unicode letters, and
  character-class checks handle that natively without a bespoke helper used nowhere else.
- **B.2 — targeted fixes (~14, bucket T) — chain started 2026-07-20 (11/14).** Braces family,
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

  `redundant-visibility-modifier` shipped seventh, porting detekt's own `RedundantVisibilityModifier`
  — ktlint ships no equivalent rule at all, confirmed by a full grep of its real checkout (no
  `RedundantVisibility`-anything, no visibility-redundancy concept in any ruleset, standard or
  experimental) and its own docs, so this is a single-upstream port, not an intersection. Both
  checkouts were read-only for ground-truthing (`git status` verified clean before and after, no
  probe cases needed — detekt's own `RedundantVisibilityModifierSpec`, 13 real cases, already covers
  every shape needed). Detekt's own rule is two hand-written PSI visitors: `ClassVisitor.visitClass`
  flags `public` on a `KtClass` (covers `class`/`interface`/`enum class`/`annotation class`/`sealed
  class`, all one PSI type — confirmed identically true of LightTree's own `CLASS` node, ground-
  truthed via a direct dump of the real tree: `interface`/`enum`/etc. are just a different keyword
  child of the same node type), and `ChildrenVisitor.visitNamedFunction`/`visitProperty` flag `public`
  on a function or property **only when not itself `override`**
  (`isExplicitlyPublicNotOverridden`). Neither visitor exists for `KtObjectDeclaration` (a plain
  `object` or `companion object`), `KtPrimaryConstructor`/`KtSecondaryConstructor`, `KtTypeAlias`, or a
  property accessor — all four are silently never candidates upstream, by omission rather than an
  explicit exemption, and wrasse matches that scope exactly rather than extending it: `MODIFIER_LIST`
  is only ever inspected when its own immediate parent is `CLASS`, `FUN`, or `PROPERTY` (ground-
  truthed to be the *only* three parent types that can carry the keyword worth checking, via the same
  tree dump). Detekt's rule bundles a second, unrelated check into the same id (a redundant `internal`
  on a member of a `private`/local class) — out of scope here per the assignment's own framing
  ("delete redundant `public`"), tracked as a possible follow-up, not built.

  **The hard call (autoformat-scope.md #4): Explicit API mode.** Detekt's own rule already gates on
  this — `isExplicitApiModeActive()` reads
  `languageVersionSettings.getFlag(AnalysisFlags.explicitApiMode) != DISABLED` and skips both visitors
  entirely when active (its own `Explicit API mode` nested test class proves `STRICT`/`WARNING` →
  zero findings, `DISABLED` → 2, for identical input) — the strongest possible confirmation this is
  reachable, since detekt needed the exact same fact for the exact same reason. Verified independently
  for wrasse's own use as a compiler plugin (not a standalone detekt CLI reading source): decompiled
  `kotlin-compiler-embeddable` sources jars for all four supported minors (2.1.21, 2.2.21, 2.3.21,
  2.4.0) plus a direct `javap` check against the real 2.4.0 jar on this machine confirm
  `CompilerConfiguration.languageVersionSettings` (the `CommonConfigurationKeys.
  LANGUAGE_VERSION_SETTINGS`-backed extension property) is fully populated by
  `CLICompiler.setupCommonArguments` **before** `K2JVMCompiler.doExecute` loads plugins and calls
  `CompilerPluginRegistrar.registerExtensions` — so the flag is unconditionally readable at plugin-
  registration time on every supported Kotlin version, no version skew. There is no dedicated
  `CommonConfigurationKeys`/`JVMConfigurationKeys` entry named anything like `EXPLICIT_API`; the
  `AnalysisFlags.explicitApiMode`-inside-`LanguageVersionSettings` path is the only route, identical
  across all four minors (2.2+ moved the CLI-arguments-to-flag copy into a separate
  `CommonCompilerArgumentsConfigurator`, a refactor with no behavioral or key-name difference).
  Threaded registrar → plugin → rule config, matching the assignment's own framing:
  `WrasseCompilerPluginRegistrar.registerExtensions` reads
  `configuration.languageVersionSettings.getFlag(AnalysisFlags.explicitApiMode) != ExplicitApiMode.
  DISABLED` and passes it as `wrasseMain`'s new `explicitApiActive` parameter, threaded through
  `loadConfig`/`WConfig.from`/`buildConfig` onto every `WrasseRuleConfig` uniformly (a compile-wide
  fact, not a per-rule-id concept — every other rule ignores the field). `RedundantVisibilityModifierRule.
  initRule` checks `config.explicitApiActive` first and, when true, returns a `WBufferedNodeRule` with
  an empty `targetTypes` — a genuine self-disable (zero dispatch-array registration, not merely
  "never reports"), exactly as the assignment demanded. **Real bug caught by the four-minor harness,
  not the 2.4-only unit test:** the top-level registrar's `explicitApiActive` computation alone was
  insufficient — `WrasseCompilerPluginRegistrar.registerExtensions` builds one `WrassePlugin` via
  `wrasseMain` for its own direct (`K22Registrar`) path, but for older/other API shapes it instead
  *delegates* to a completely separate `WrasseCompilerPluginRegistrar20`/`22.registerExtensions`
  (`app/wrasse-kotlinc-internal-k20`/`-k22`, reflection-loaded, same `CompilerConfiguration` instance
  passed through), each of which independently calls `wrasseMain` a second time — a pre-existing
  duplication `dumpResolvedUsage` et al. already had to satisfy, that this port initially missed for
  the new parameter. `testMinorHarness --rerun-tasks` failed on Kotlin 2.1/2.2/2.3 (`2.4` alone, run
  via `test`, is the one minor whose delegation path stays in the top-level registrar and so passed
  by coincidence) with `RedundantVisibilityModifierExplicitApiSpec` still emitting 2 diagnostics under
  `strict`/`warning` — both delegate registrars needed the identical `explicitApiActive` computation
  added independently. Locked by a dedicated `RedundantVisibilityModifierExplicitApiSpec` (subclassed
  per Kotlin minor, the `SafetySpec` convention) proving three things against a real `K2JVMCompiler`
  invocation: the same source is flagged (2 diagnostics) with the mode off — proving the rule is
  genuinely wired, not just inert by accident — and silent under both `-Xexplicit-api=strict` and
  `-Xexplicit-api=warning`, with the real compile still succeeding either way, across all four minors.
  The test harness gained one new additive constructor parameter for this,
  `WrasseTestHarness(explicitApiMode: String?)`, following the `multiPlatformCommonSources` precedent
  exactly (a raw `K2JVMCompilerArguments.explicitApi: String` field set directly — no
  plugin-option/`CliOption` machinery, since this mirrors a real compiler flag rather than a
  wrasse-specific setting) rather than a `dumpResolvedUsage`-style plugin option, since explicit API is
  a compiler-wide concern, not something wrasse itself defines.

  A `MODIFIER_LIST` containing `KW_OVERRIDE` anywhere is skipped entirely — no report, not just no
  fix — mirroring detekt's own `isExplicitlyPublicNotOverridden` exactly: overriding can legally
  *widen* visibility from a more restrictive base member (`protected` → `public`, which Kotlin
  explicitly permits, narrowing being the only illegal direction), and deciding whether a given
  `public override` is a genuine widening or truly redundant needs the base declaration's own
  visibility — never resolvable syntactically. Rather than add resolution to what is otherwise a
  purely syntactic `T`-bucket rule, wrasse matches detekt's own blanket exclusion (locked clean by
  `override-clean`, matching detekt's own real override test shapes over both an abstract-class base
  and an interface base). Deletion span (`RedundantVisibilityModifierDeletionSpan`, compiler-free,
  unit-tested standalone): the `public` keyword's own span plus whatever whitespace directly follows
  it collapses to nothing, never touching anything before it (an annotation, indentation, an
  unrelated leading comment — ground-truthed via a direct LightTree dump that a *bare* leading comment
  with no preceding annotation sits **outside** `MODIFIER_LIST` entirely, as a sibling of the parent
  declaration, while a comment interposed between an annotation and `public` sits **inside** the list
  — the parser's own marker only backs off trailing whitespace, never leading trivia) or, past the
  trailing whitespace, whatever real token or comment comes next. Reported but never autofixed
  (uniform comment-bail precedent, same family as `no-unit-return`/`modifier-order`) whenever a
  comment sits anywhere in the modifier list itself, or immediately follows the trailing whitespace —
  applied here even though, unlike those two rules, no actual upstream-native corruption risk was
  found: the forward whitespace scan always halts at the comment's own start byte, so the comment is
  never swallowed by the deletion either way; bailing is pure uniformity with established precedent,
  not a safety finding. Locked by `list-internal-comment-bail-error` and
  `trailing-comment-bail-error`, with `bare-leading-comment-untouched-error` proving the converse — a
  leading comment *outside* the list is never a reason to bail, since the edit never reaches it.
  Fixtures otherwise cover a top-level class, a top-level interface with a member (detekt's own real
  "reports interface with public modifier" two-finding shape), a class member function and property, a
  nested class member, a multi-modifier list (`public open fun`, confirming only `public` itself is
  ever touched), an annotation-interspersed member (confirming the deletion span never touches an
  earlier annotation in the same list), and clean shapes for every excluded parent type (object/
  companion object, primary/secondary constructor, typealias, property accessor) plus an
  already-non-public clean and an `@Suppress` clean. `expect`/`actual` is deliberately not
  fixture-tested — mirroring `modifier-order`'s own established limitation, a standalone `expect`/
  `actual` pair does not compile outside a real multiplatform module — but is inert to this rule's
  detection logic regardless (`KW_ACTUAL`/`KW_EXPECT` never participate in the `KW_PUBLIC`/
  `KW_OVERRIDE` scan either way, so no special-casing was needed or added).

  **As-built (`ModifierEngine`, the fusion — 2026-07-20, fixing the `modifier-order`/`redundant-
  visibility-modifier` crash recorded in §14 as the wave-2-installment-7 blocker):** the two rules
  above are now one `WBufferedNodeRule` behind `WUninitializedRuleGroup` (§4, "Multi-id engines"),
  mirroring `ImportEngine`'s own shape (§8.2) at a much smaller scale — no resolution, one node
  type, one pure decision object per id. Model: `ModifierEngine.ids` declares both ids; `WRuleSet`
  gives `initGroup` exactly the enabled, non-excluded-for-this-file subset as a
  `Map<id, WrasseRuleConfig>`; an id absent from that map behaves as if its rule doesn't exist for
  this file, preserving each id's own standalone behavior exactly when the other is off/excluded —
  the pre-existing `modifier-order/` and `redundant-visibility-modifier/` fixture dirs, each
  enabling only their own id, pass unmodified (the refactor gate, same bar `ImportEngine`'s own
  fusion was held to). `redundant-visibility-modifier`'s explicit-API self-disable (D23) is
  unchanged: `initGroup` simply omits its facade from the group when `configs[REDUNDANT_
  VISIBILITY_MODIFIER_ID]?.explicitApiActive == true`, exactly as the old `initRule` did.

  One walk-side `exitNode` on `MODIFIER_LIST` replaces the two independent `exitNode`s: it
  classifies every non-whitespace child exactly as the old `ModifierOrderRule` did (canonical-index
  lookup against the same 25-entry `ORDERED_MODIFIER_TYPES` list, `hasComment` set by any
  comment/KDoc child), while separately tracking whether `KW_PUBLIC` and `KW_OVERRIDE` are present.
  `redundant-visibility-modifier`'s own decision (parent-type gate, `KW_OVERRIDE` bail,
  `RedundantVisibilityModifierDeletionSpan`) runs first, exactly as before, and reports under its
  own id. **The crash fix itself:** whenever that decision fires — reports, regardless of whether an
  edit actually attaches (a comment-forced report-only fire still counts) — the `public` keyword's
  own `ModifierKeywordOccurrence` is dropped from the list `ModifierOrderDecision.decide` sees for
  this same list, before `modifier-order`'s own decision runs. Its position is moot: the other id is
  deleting it outright, so `modifier-order` reorders only what will still be there afterward,
  computing `expectedOrder`/edits/report-span from the survivors alone — never touching `public`'s
  own span, so the two ids can never emit overlapping `WEdit`s for the same list again. When
  `redundant-visibility-modifier` doesn't fire on a list (disabled, excluded, no `public` present,
  `KW_OVERRIDE` present, wrong parent type), `modifier-order` sees the full keyword list exactly as
  its old standalone `exitNode` did — bit-for-bit the pre-fusion behavior. Both pure decision
  objects (`ModifierOrderDecision`, `RedundantVisibilityModifierDeletionSpan`) are unchanged and
  still unit-tested standalone (`ModifierOrderDecisionSpec`, `RedundantVisibilityModifierDeletion
  SpanSpec`) — the fusion touches only which rule *calls* them and with what keyword list, never
  their own logic.

  Locked by a new `modifier-order-visibility-combined/` fixture dir (both ids at `level: error`,
  mirroring `when-if-bracing-combined/`'s own precedent for a cross-rule fixture): the exact crash
  shape from §14 (`suspend public fun bar() {}`, now a passing `.fixed.kt` case emitting only the
  `redundant-visibility-modifier` report/edit, since the sole surviving keyword after deletion is
  trivially ordered — reproduced failing first, via a throwaway probe against a real
  `K2JVMCompiler` invocation confirming the documented `IllegalStateException` before any fix code
  was written, then as this same fixture against the pre-fusion rules, both discarded after
  confirmation); a redundant-`public`-only and an order-violation-only case (each firing only its
  own id, confirming independence when one problem is absent); a combined file exercising both
  problems on one declaration alongside a redundant-only and an order-only declaration elsewhere in
  the same file; a three-keyword case (`inner public open class Foo`) where the deletion still
  leaves two survivors genuinely out of order, so both a deletion edit and a reorder-of-survivors
  edit land on the same list without overlapping; and a suppression pair (`@Suppress` on one id but
  not the other, on separate declarations) confirming each id's suppression stays independent inside
  the fused engine, exactly as `WReporter.report`'s pre-existing offset-scoped suppression check
  already guaranteed for any two ids sharing a report path. `RuleRegistrationOrderSpec`
  (`app/wrasse-kotlinc-plugin`) now locks `ModifierEngine.ids` alongside `ImportEngine.ids` instead
  of `modifier-order`/`redundant-visibility-modifier` as standalone single-id entries.

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

  Retroactive upstream-test backfill, wave 2 installment 7 (2026-07-20): `redundant-visibility-
  modifier` ported against detekt's own real `RedundantVisibilityModifierSpec` (13 cases)
  independently — ktlint ships no equivalent rule at all, re-confirmed by a fresh grep of its real
  checkout at the start of this installment (no `RedundantVisibility`-anything, no visibility-
  redundancy concept anywhere in its standard or experimental rulesets or docs), so there is only one
  upstream to intersect, not two to reconcile. Of detekt's 13 cases: the two "reports ..." function/
  class/interface/field cases were already exact-matched ship-time (`member-function-error`, `top-
  level-class-error`, `interface-with-member-error`, `member-property-error`); "does not report
  overridden function of abstract class with public modifier" was already covered by `override-
  clean`; "does not report overridden function of abstract class without public modifier" and "does
  not report overridden field without public modifier" are trivially subsumed regardless of override
  status (no `KW_PUBLIC` in the modifier list at all means `publicIndex` never leaves `-1`, the same
  code path as `already-non-public-clean`) and were not re-fixtured; "does not report overridden
  function of interface" is subsumed by `override-clean`'s own unconditional `KW_OVERRIDE`-anywhere
  bail, which never inspects what the base declaration actually is (abstract class or interface) —
  same code path, not re-fixtured; the `Explicit API mode` nested class (3 cases: strict/warning/
  disabled) was already covered more thoroughly ship-time by the dedicated, real-compile
  `RedundantVisibilityModifierExplicitApiSpec` (all three modes, not just the one the assignment
  flagged as a possible gap — checked directly, no gap found). Two literal 1:1 gaps closed with new
  fixtures: "does not report field without public modifier" and the function analogue, both folded
  into one clean fixture (`no-modifier-member-clean`, a bare `fun`/`val` pair with no modifier at
  all); and "does not report overridden field with public modifier" — `override-clean` only had a
  function-override shape, not detekt's own dedicated property-override one, closed by
  `property-override-clean` (parity with the function case, same unconditional bail). Detekt's own
  bundled second check (a redundant `internal` on a member of a `private`/local class,
  `visitDeclaration`'s `isInternal()` branch, 2 more cases) remains the already-documented,
  deliberate out-of-scope divergence from ship time (rule KDoc and this section, above) — not
  re-examined.

  Beyond the ported spec, the assignment's own hunt list (nested/inner classes, companion objects,
  annotated declarations, KDoc, expect/actual, enum entries, modifier-order interaction, explicit-API
  mode, property accessors) was worked case by case, each ground-truthed against a real compile
  (`./gradlew :testing:wrasse-kotlinc-plugin-tests-2-4-x:test`, not inferred from reading the rule).
  **Nested/inner classes**: `public` on a `class` nested inside another `class`, and co-occurring with
  `inner`, both fire correctly (`inner-class-error`) — the rule's `MODIFIER_LIST`-parent-is-`CLASS`
  check has no notion of nesting depth, so this was never actually at risk, but wasn't locked before.
  **Companion objects**: object-clean (ship-time) already proved the companion *declaration* itself is
  never a candidate (`OBJECT_DECLARATION` is never `CLASS`); the open question was whether a *member*
  declared inside a companion object still gets flagged — confirmed yes (`companion-object-member-
  error`), since the parent-type check only ever looks at the immediate `MODIFIER_LIST` parent (`FUN`
  here), never the grandparent container, exactly mirroring detekt's own `ChildrenVisitor` traversal
  (which recurses into every container uniformly, `KtObjectDeclaration` included). **Annotated
  declarations**: ship-time only exercised an annotated function (`annotation-interspersed-error`);
  `annotated-class-error` extends the same shape to a class-level annotation on its own line, closing
  it. **KDoc**: two new fixtures answer the question the ship-time KDoc (design.md, above) left
  implicit for `KDOC` specifically rather than only `EOL_COMMENT`/`BLOCK_COMMENT` — a bare KDoc with no
  preceding annotation sits **outside** `MODIFIER_LIST` exactly like a bare `//` comment (parser marker
  reasoning identical either way), so the fix still applies untouched (`kdoc-leading-untouched-error`);
  a KDoc interposed between a preceding annotation and `public` sits **inside** the list exactly like an
  interposed `//` comment, so the rule correctly bails with the `" (no autofix for this shape)"` marker
  (`kdoc-interspersed-bail-error`) — parity confirmed for the third comment-shaped node type the
  `hasComment` scan already listed but had never actually fixture-exercised. **`expect`/`actual`**: no
  new work — already deliberately not fixture-tested at ship time (a standalone `expect`/`actual` pair
  doesn't compile outside a real multiplatform module, and `KW_EXPECT`/`KW_ACTUAL` never participate in
  the `KW_PUBLIC`/`KW_OVERRIDE` scan regardless), re-verified correct on re-reading rather than
  re-derived. **Enum entries**: ground-truthed directly against the real Kotlin compiler sources (a
  local read-only checkout, `git status` verified clean before and after) rather than guessed —
  `KotlinParsing.parseEnumEntry` calls the general `parseModifierList`, so `public RED` parses into an
  `ENUM_ENTRY`'s own `MODIFIER_LIST`; `ModifierCheckerHelpers.kt`'s `defaultVisibilityTargetPredicate =
  always(...)` for `PUBLIC_KEYWORD`, and the absence of any `possibleParentTargetPredicateMap` entry
  for `PUBLIC_KEYWORD` restricting it against `KotlinTarget.ENUM_ENTRY_LIST`, together mean `public` on
  an enum entry is **semantically legal, if pointless** — not a compile error, so (unlike the `expect`/
  `actual` and modifier-order-2.1-backend-crash precedents) this shape *is* fixture-expressible.
  `enum-entry-clean` confirms it end-to-end: the source compiles clean and wrasse stays silent, since
  `ENUM_ENTRY` is never one of the three parent types this rule ever inspects — matching detekt's own
  scope exactly (`ClassVisitor`/`ChildrenVisitor` never visit `KtEnumEntry` either). **Modifier
  position**: ship-time's only multi-modifier shape (`multi-modifier-error`) had `public` first, already
  in canonical order; `modifier-position-middle-error` (`open public suspend fun`) and
  `modifier-position-last-error` (`suspend public fun`) confirm the `KW_PUBLIC` scan and the deletion
  span's forward-whitespace walk are both positionally independent — correct regardless of where
  `public` sits in the list. **Property accessors**: already correctly excluded ship-time
  (`property-accessor-clean`), re-verified, no gap. **Explicit API mode**: already more thorough than
  the assignment expected (see above), no gap.

  One genuine bug found, not a wrasse rule-logic defect — escalated as an engine-shape blocker
  rather than papered over in-task (full detail in §14): enabling `redundant-visibility-modifier`
  together with `modifier-order` crashes the entire compile with `INTERNAL_ERROR` whenever a
  redundant `public` also participates in an out-of-order modifier list (e.g. `suspend public fun
  bar() {}`) — `EditPlan`'s cross-rule overlap check throws, uncaught, during plain linting, no
  `-Pwrasse.fix` required. Reproduced directly against a real compile, independently confirmed a
  second time via a forked sub-agent's own separate reproduction, matching exactly.
  `unnecessary-inheritance` ported next, a lint-only rule (bucket L — see autoformat-scope.md),
  porting detekt's own `UnnecessaryInheritance` — ktlint ships no equivalent, confirmed by a full
  grep of its real checkout (no `inherit`/`supertype` concept anywhere). Detekt's own engine (read
  directly, then ground-truthed with eight temporary probe cases added to its real test file and run
  against the real `detekt-rules-style` module, reverted after — `git status`/`git diff` verified
  byte-clean) turns out to be a **naive literal text comparison**, not a semantic one:
  `visitClassOrObject` flags a `superTypeListEntries` entry only when its own PSI text is exactly
  `"Any()"` or `"Object()"`. Confirmed empirically: a qualified `kotlin.Any()`, an aliased `MyAny()`
  (`typealias MyAny = Any`), and even whitespace inside the call (`Any ()`) are all silently **not**
  flagged by the real engine, since none of their spans spell the literal text; a comment between
  the colon and the entry does not block detection (the check only ever looks at the entry's own
  span); an anonymous object expression (`object : Any() {}`) is flagged exactly like a named
  declaration, since `KtObjectDeclaration` is a `KtClassOrObject` too. Wrasse's own detection matches
  this exactly — `SUPER_TYPE_CALL_ENTRY`'s own source span must literally equal
  `"Any()"`/`"Object()"`, no FIR resolution at all — the narrowest interpretation that is provably a
  subset of detekt's real behavior on every axis (semantic resolution would in fact go *wider* on
  the alias case, a real superset risk, so plain text is the correct choice here, not merely the
  simplest one). `SUPER_TYPE_LIST` is targeted directly regardless of declaring kind, covering
  class/named-object/companion-object/anonymous-object-literal uniformly with no parent-type
  branching. Interface and enum-class declarations are never fixture-tested, not because of any
  exclusion in the rule but because neither shape compiles with an explicit `Any()`/`Object()`
  supertype in the first place — ground-truthed against the real compiler frontend rather than
  assumed: an interface cannot extend a class at all (only another interface may appear in its
  supertype list), and `enum class Foo : Any()` fails a real compile with FIR's
  `CLASS_IN_SUPERTYPE_FOR_ENUM` (`FirEnumClassSimpleChecker`, read directly from a local kotlin
  checkout) — the same "considered, ground-truthed, excluded as uncompileable" category as
  `modifier-order`'s enum-entry note and `no-unit-return`'s `expect`/`actual` note. Matching detekt's
  own `UnnecessaryInheritance`, which ships no autofix at all, wrasse's port is report-only —
  narrower than the T-bucket redundant-syntax-deletion family it otherwise resembles, and the
  correct default per §6's conservatism principle absent an explicit owner call to go further.

  Fixtures cover a sole entry with and without a body, `Object()` (via `import java.lang.Object`),
  an anonymous object literal, a named object, a companion object, a leading/trailing/middle
  position among multiple supertypes, a multi-line supertype list (both the sole-entry and
  multiple-entry shapes), four comment-adjacency positions (before/after the shared comma on both
  the leading and trailing side, and between the colon and a sole entry — confirming a comment never
  blocks detection), and three clean shapes proving the naive-text-match boundary: `kotlin.Any()`,
  `Any ()` (whitespace inside the call), and a plain interface supertype.

  `unnecessary-backticks` shipped eighth, porting detekt's own `UnnecessaryBackticks` — ktlint ships
  no equivalent, confirmed by a full grep of its real checkout, so this is a single-upstream port.
  Detekt's rule ships no autocorrect mechanism at all (no `Correctable`/similar mixin), unlike
  `no-unit-return`/`if-else-bracing` where an upstream fixer exists to ground an output shape
  against; wrasse's autofix here is an independent addition, licensed only by autoformat-scope.md's
  own T-bucket classification ("removing useless backticks is safe mechanical"), not by an upstream
  precedent to match byte-for-byte. Necessity mirrors detekt's own `hasUnnecessaryBackticks` check
  exactly: the backtick-quoted text is redundant only when its unquoted form is (a) a syntactically
  valid plain identifier — first character a Unicode letter or `_`, every following character a
  letter, digit, or `_` — confirmed against the compiler's own lexer grammar (`Kotlin.flex`'s
  `IDENTIFIER` rule) and its `isIdentifier()`/`HARD_KEYWORDS` extension, read directly from a
  decompiled `kotlin-compiler-embeddable` sources jar; (b) not one of the 28 *hard* keywords
  (`KtTokens.KEYWORDS`) — `typealias` and `typeof` are hard keywords alongside the more familiar
  `fun`/`val`/`class`/... set, confirmed by a real `K2JVMCompiler` invocation that `val typealias =
  5`/`val typeof = 9` fail to parse unquoted while `val public = 1`/`val data = 1`/`val get = 1`
  (all *soft*/modifier keywords, `KtTokens.SOFT_KEYWORDS`, contextual rather than reserved) compile
  cleanly — so a backtick-quoted soft keyword used as a plain name is reported and fixed the same as
  any other identifier, never exempted; and (c) not made up entirely of `_` characters (Kotlin's own
  placeholder-name convention: `` `_` ``/`` `__` `` compile as real declared names only when
  backtick-quoted, and stay backtick-quoted). No test-method-name carve-out is needed as a special
  case: a name containing a space (`` `Foo Bar` ``) already fails the plain-identifier check on its
  own, so ktlint-style backtick-quoted test method names are naturally exempt as a structural
  consequence of the identifier check, never a dedicated rule branch.

  Matched by a `WLeafRule` on `IDENTIFIER` — every occurrence (declaration, call site, callable
  reference, import) is checked independently, since each is its own `IDENTIFIER` token; the fix is
  a single edit replacing the whole backtick-quoted span with the bare unquoted text
  (`UnnecessaryBacktickDecision`, unit-tested standalone). Reported but never autofixed (bail, same
  posture as every other T-bucket rule's own comment/adjacency caution) when the identifier sits
  inside a string template's short-form entry (`` "$`name`" ``): the entry's own text is directly
  adjacent, character-for-character, to whatever literal text follows it in the same string once the
  backticks are gone, with no delimiter of its own — unlike a long-form entry (`` "${`name`}" ``),
  whose explicit closing brace makes removal always safe. Wrasse's own bail is intentionally coarser
  than detekt's own `canPlaceAfterSimpleNameEntry` check (which only bails when the following
  character would actually extend the identifier): the coarser rule is strictly safe and avoids
  building lookahead machinery this rule has no other need for. Fixtures (16 error/clean, 9 with a
  `.fixed.kt` companion): class/function/property/import declarations and their call-site/callable-
  reference usages, a hard-keyword clean set (`typealias`/`typeof`/`when`/`is`/`fun`), a
  soft/modifier-keyword error case (`` `public` ``, autofixed), an all-underscore clean set, a
  name-with-spaces clean set, a leading-digit clean case, a Unicode-letter error case, both
  string-template shapes (long-form fixed, short-form bail), and `@Suppress` happy/negative cases.

  `explicit-it-lambda-parameter` shipped ninth, porting detekt's own `ExplicitItLambdaParameter` —
  again ktlint ships no equivalent and detekt's own rule ships no autocorrect at all, so as with
  `unnecessary-backticks` the autofix here is wrasse's own addition under the T-bucket
  classification, not an upstream byte-for-byte target. Detekt flags a lambda whose own
  `valueParameters` list has size exactly 1 and whose sole parameter's name is the identifier `it`,
  regardless of whether that parameter carries an explicit type (two different messages, same
  finding); a lambda where `it` is one of *several* named parameters is a different upstream id
  (`ExplicitItLambdaMultipleParameters`, bucket L per autoformat-scope.md — a fix would require
  inventing a new parameter name, never mechanical) and stays entirely out of this rule's scope.
  Autofix scope is narrower than detection scope, split on a real semantic hazard rather than a
  syntactic one: an **untyped** `it ->` is always safe to delete outright — whether the parameter is
  named explicitly or left implicit, it still binds the exact same single slot, so removing the
  declaration changes neither what `it` means inside this lambda nor whether some nested lambda's
  own implicit `it` shadows it (shadowing depends only on the name `it` itself, identical either
  way) — reasoned directly from Kotlin's own implicit-`it` binding rule, not from probing an upstream
  fixer, since none exists. A **typed** `it: Type ->` is never autofixed, only reported: an explicit
  parameter type can be safe to drop only when the lambda's own use site target-types it well enough
  for the compiler to re-infer the identical type from an implicit `it` alone — a call-site fact no
  syntactic, resolution-free check can establish, so wrasse bails uniformly rather than risk emitting
  a type-inference-breaking edit (the same "bail when uncertain" doctrine as `no-unit-return`'s
  comment-adjacency bail, applied to a genuinely different hazard).

  Mechanically, a `WBufferedNodeRule` targets both `FUNCTION_LITERAL` and `VALUE_PARAMETER`: a small
  per-`FUNCTION_LITERAL` stack frame (the `WhenEntryBracingRule`/`WHEN`-`WHEN_ENTRY` idiom, reused at
  a smaller scale) accumulates the enclosing lambda's own parameter count and whichever parameter is
  named `it`, populated as each `VALUE_PARAMETER` exits (gated on its own immediate parent being
  `VALUE_PARAMETER_LIST` and that list's own parent being the current `FUNCTION_LITERAL`, so a nested
  local function's own unrelated parameter list is never mistaken for the lambda's); the verdict
  fires only at the `FUNCTION_LITERAL`'s own exit, once the final parameter count is known, using
  that same node's direct children to locate `{`/`->` for the edit. The deletion span deliberately
  starts at the opening `{`'s own end, not at the parameter's own start: it swallows the whitespace
  between `{` and the parameter along with the parameter and arrow themselves, while leaving every
  character from the arrow's end onward — including a multiline lambda's own leading newline and
  indentation before its body — completely untouched, so a fix never needs re-indenting
  (`ExplicitItLambdaParameterDecision`, unit-tested standalone with a real string-splice check).
  Reported but never autofixed (uniform comment-bail precedent) when a comment sits anywhere between
  `{` and `->`.

  A nested pair of lambdas each explicitly naming their own parameter `it` (`list.map { it ->
  it.map { it -> it.plus(1) } }`, the classic shadowing-hazard shape) is fixed independently and
  safely at each level: neither fix changes which lambda's implicit `it` a nested body resolves to,
  since both lambdas already spell their bound name `it` before and after the fix — proven by the
  dedicated `nested-lambdas-both-explicit-error` fixture (both diagnostics fire, both fix, the
  post-fix source stays semantically identical to the original). Fixtures (11 error/clean, 4 with a
  `.fixed.kt` companion): an untyped single-line case, an untyped multiline case (proving the
  arrow-to-body gap survives untouched), a typed bail, two comment-bail shapes (before and after the
  parameter), three clean shapes (fully implicit, a differently-named parameter, a two-parameter
  lambda where one happens to be `it`), the nested-lambda safety case, and `@Suppress`
  happy/negative cases.

  Both new ids composed with `format` are proven in
  `format-with-fixes/unnecessary-backticks-and-format-error` and
  `format-with-fixes/explicit-it-lambda-parameter-and-format-error`: each fixture's own edit is the
  *only* divergence from the file's otherwise-canonical layout, so the printer's whole-file render
  and the targeted edit compose without any reindentation — the same low-risk shape
  `if-else-bracing-and-format-error` already established for the brace-insertion family.

  `empty-default-constructor` and `redundant-constructor-keyword` shipped tenth and eleventh,
  porting detekt's own `EmptyDefaultConstructor` and `RedundantConstructorKeyword` — ktlint ships no
  equivalent to either, confirmed by a full grep of its real checkout (no `EmptyDefaultConstructor`,
  no `RedundantConstructor`-anything, no primary-constructor-syntax concept anywhere), so both are
  single-upstream ports. Neither upstream rule ships an autocorrect mechanism, so as with
  `unnecessary-backticks`/`explicit-it-lambda-parameter` both autofixes here are wrasse's own
  addition under the T-bucket classification, not a byte-for-byte upstream target.

  `empty-default-constructor` mirrors detekt's own `hasSuitableSignature`/`isNotCalled`/
  `isExpectedOrActualClass` checks exactly: a primary constructor is only ever a candidate when its
  own value-parameter list is empty, it carries no annotation, and its visibility is either absent
  or explicitly `public` (`private`/`protected`/`internal` bail entirely, matching detekt's own
  `hasPublicVisibility`); the containing class carrying `expect`/`actual` bails entirely
  (`isExpectedOrActualClass`); and a sibling secondary constructor delegating to it via a
  zero-argument `this()` bails entirely too (`isNotCalled`) — removing the empty parameter list
  would delete the only constructor that delegation call could still resolve to, since Kotlin's own
  implicit-delegation rule only ever targets the primary constructor when one is written down.
  Detection is mechanically a `WBufferedNodeRule` targeting `CLASS`, `PRIMARY_CONSTRUCTOR`,
  `MODIFIER_LIST` (dual-gated on its own immediate parent — `CLASS` for the `expect`/`actual` scan,
  `PRIMARY_CONSTRUCTOR` for the annotation/visibility scan, the same parent-type-gating idiom
  `redundant-visibility-modifier` already established), `VALUE_PARAMETER_LIST` (gated to a
  `PRIMARY_CONSTRUCTOR` parent, giving both the empty-parameter check and the `(`/`)` deletion span),
  and `CONSTRUCTOR_DELEGATION_CALL`; a small per-class and per-constructor stack (mirroring
  `ExplicitItLambdaParameterRule`'s own pending-frame idiom) correlates facts gathered at different
  nesting depths, finalizing the verdict only once the whole class — including every secondary
  constructor nested in its body — has closed. The zero-argument `this()` check itself needs no
  dedicated node type for the delegation reference or its argument list: `onChildLeaf` already
  forwards every descendant leaf regardless of depth, so a `KW_THIS` leaf found outside any
  `VALUE_ARGUMENT_LIST` ancestor identifies the reference itself, and any non-paren, non-whitespace,
  non-comment leaf found inside one means the call carries a real argument — both read directly off
  `WContext.hasAncestor`, no extra buffering required.

  Autofix scope is deliberately narrower than detection scope, on a different axis than either prior
  T-bucket rule's own bail: the edit is *only* ever "delete the `(`...`)` span" — never anything
  before it. When the constructor spells its own `constructor` keyword (`class Foo constructor()`),
  deleting just the parameter list would leave that keyword dangling with nothing left to attach to,
  a syntax error; rather than also deleting the keyword (which would risk an edit-overlap crash with
  `redundant-constructor-keyword` firing independently on the exact same span whenever both ids are
  enabled together — the same crash shape §14/D24 already recorded for
  `modifier-order`/`redundant-visibility-modifier`, avoided here by construction rather than papered
  over after the fact), this rule bails the fix and reports only, leaving the keyword's own removal
  entirely to `redundant-constructor-keyword`; the two ids' own edits are always adjacent, never
  overlapping, with no coordination between them required. A comment sitting inside the otherwise-
  empty parameter list (`class Foo(/* c */)`) bails the fix the same way, the uniform comment-
  adjacency posture every other T-bucket rule already takes. The `expect`/`actual` exemption has no
  end-to-end fixture: an `expect class Foo()` needs a real multiplatform target to compile at all,
  and the fixture harness's per-fixture directives have no multiplatform knob (only a handful of
  dedicated non-fixture specs drive `WrasseTestHarness`'s own `multiPlatformCommonSources`) — the
  same "considered, ground-truthed, excluded as uncompilable in a single fixture file" category as
  `unnecessary-inheritance`'s interface/enum-class note; `EmptyDefaultConstructorDecisionSpec`
  exercises the `isExpectOrActual` branch directly instead. Fixtures (12, 2 with a `.fixed.kt`
  companion): a bare empty constructor with and without a following class body, one with a
  supertype-constructor call directly after it (proving the deletion span never touches what
  follows), an explicit-`public`-plus-keyword bail and a bare-keyword bail (both report-only), a
  comment-inside-parens bail, an annotation clean shape, a non-empty-parameter clean shape, a
  combined private/internal/protected clean shape, a called-via-zero-arg-`this()` clean shape, and
  `@Suppress` happy/negative cases.

  `redundant-constructor-keyword` mirrors detekt's own `hasConstructorKeyword() && hasNoModifier()`
  check, where `hasNoModifier()` is `modifierList == null && !hasPreviousComment()`: the keyword is
  redundant only when the constructor's own `MODIFIER_LIST` child doesn't exist at all — no
  annotation and no visibility modifier of its own, checked by existence alone
  (`ChildBuffer.hasChildOfType`), never by inspecting what such a list would contain, since detekt's
  own `modifierList` is null precisely when nothing was ever written there — and no comment sits
  anywhere in the class header's own trivia between its name (or type parameter list) and the
  keyword. Unlike every other T-bucket rule's own "report but decline the fix" bail, this comment
  check is a **full bail — no report at all**, matching detekt's own detection-level bail exactly
  (`hasNoModifier()` returns `false`, so the whole condition is `false`, not merely the correctable
  half of it); there is no upstream fixer to narrow away from here; the discovery is upstream's own.
  Mechanically a `WBufferedNodeRule` targets `CLASS` and `PRIMARY_CONSTRUCTOR` only — no dedicated
  `MODIFIER_LIST` target is needed, since existence of the constructor's own list is already visible
  on `PRIMARY_CONSTRUCTOR`'s own direct `ChildBuffer` — with a small pending-keyword stack keyed to
  the enclosing class: the comment sits as a *sibling* of `PRIMARY_CONSTRUCTOR` inside `CLASS`'s own
  children, not inside the constructor itself, so the backward scan for both the comment check and
  the deletion span's own start walks `CLASS`'s own buffer from the `PRIMARY_CONSTRUCTOR` entry
  backward over `WHITE_SPACE`/comment children until a real token is found. The deletion itself
  collapses that entire run of leading trivia — including any newline crossed along the way, the
  same `class Foo\n{\n}` → `class Foo` precedent `EmptyClassBodyDeletionSpan` already established —
  through the keyword's own end, leaving whatever follows (`(`, always directly adjacent) untouched.
  An annotation on a value *parameter* rather than the constructor itself (`class AnnotatedParam
  constructor(@Ann x: Double)`) does not suppress the report: that annotation's own `MODIFIER_LIST`
  is two levels below `PRIMARY_CONSTRUCTOR` (inside `VALUE_PARAMETER_LIST` → `VALUE_PARAMETER`), never
  a direct child of it, so `hasChildOfType` correctly never sees it — matching detekt's own
  `modifierList` (the constructor's own, not any parameter's) — proven by a dedicated fixture rather
  than assumed. Fixtures (11, 4 with a `.fixed.kt` companion): a same-line keyword, a keyword pushed
  to its own line by a newline with no comment (collapsing across the line break), the annotated-
  parameter-still-reported case, a constructor's-own-annotation clean shape, a private-visibility
  clean shape, a no-keyword-at-all clean shape, a comment-before-the-keyword full-bail clean shape,
  an annotation-class shape (proving `CLASS` covers every class-like kind uniformly, no
  `data`/`annotation`/`sealed` branching needed), and `@Suppress` happy/negative cases. A `data class`
  shape was deliberately not fixture-tested end-to-end, despite being detekt's own primary test
  case: ground-truthed via a throwaway reproduction entirely outside wrasse (a bare
  `WrasseTestHarness` double-compile into the same `workDir`, no wrasse rule involved at all,
  reverted after — `git status` verified byte-clean) to a real Kotlin 2.1.21-only backend crash
  (`Fir2IrDeclarationStorage.findContainingIrClassSymbol` throwing `IllegalStateException: IR class
  for Serializable not found`) whenever a `data class` is recompiled into the same destination
  directory across two structurally-differing sources — the exact shape the idempotence harness's
  own round-2 recompile always performs, and reproduced identically whether or not the keyword or
  wrasse itself is involved. Not a rule-logic defect (the annotation-class fixture already proves
  `CLASS`-node uniformity across every declared kind), so the fixture set simply avoids `data class`
  as the declared kind rather than route around a kotlinc-version-specific backend bug in-task;
  `testPatchHarness` confirms every other 2.1.x patch (`2.1.0`/`2.1.10`/`2.1.20`) is unaffected, so
  this is narrower than a blanket 2.1.x regression.

  Both new ids composed with `format` are proven in
  `format-with-fixes/empty-default-constructor-and-format-error` and
  `format-with-fixes/redundant-constructor-keyword-and-format-error`, the same low-risk composition
  shape as every prior T-bucket id.

  `trivial-accessors`, `long-numerical-values`, and `range-conventional` shipped twelfth,
  thirteenth, and fourteenth, closing out the T bucket: all three port diktat's own
  `TrivialPropertyAccessors`, `LongNumericalValuesSeparatedRule`, and `RangeConventionalRule` — a
  full grep of both real checkouts turned up no ktlint equivalent for any of the three, and no
  detekt equivalent for the first; detekt does ship its own, differently-shaped
  `UnderscoresInNumericLiterals` (lint-only, no autocorrect, default acceptable length 4, an
  explicit `serialVersionUID`-in-a-`Serializable`-type exemption, hex/binary excluded entirely) and
  `RangeUntilInsteadOfRangeTo` (lint-only, recommends the `..<` operator for the exact same `b - 1`
  shape); both were read and considered but not adopted as the port target, since the scope doc
  attributes this trio to diktat and diktat is the only one of the three shipping an actual
  autocorrect for any of them.

  `trivial-accessors` narrows diktat's own detection to four exact shapes rather than its looser
  "exactly one reference expression named `field` anywhere in the subtree" heuristic: a bare `get`
  with no parameter list and no body at all (already identical to no accessor), `get() = field`,
  `get() { return field }`, and `set(value) { field = value }` — a setter's block must be exactly
  one statement, a plain-`=` assignment of the parameter to `field` verbatim; a compound-assignment
  operator (`+=` and similarly) is never trivial, a deliberate correctness fix over diktat's own
  check, which inspects only the assignment's left/right text and never its operator, so it would
  misclassify `field += value` as removable. Diktat's own algorithm affords annotated getters only
  accidental protection (an annotation entry's own type reference is itself a second
  `REFERENCE_EXPRESSION`, which silently defeats its single-reference count) and setters none at
  all; wrasse makes the guard explicit and uniform instead: any accessor carrying its own
  `MODIFIER_LIST` (an annotation or a visibility modifier) is still reported — this is a real
  trivial body, worth flagging — but never autofixed, since either may carry behavior (JVM
  interop, restricted visibility) a plain deletion would silently drop. Deletion collapses leading
  whitespace back to the prior real token,
  the same precedent `EmptyClassBodyDeletionSpan` established, and swallows any comment living
  inside the accessor's own body along with it (the whole construct is being removed, not merely
  reformatted, so there is no separate "which side does this comment belong to" question the way
  there is for a token-level deletion). Fixtures (17, 7 with a `.fixed.kt` companion): the four
  trivial shapes (plus both accessors trivial on the same property at once, proving the two
  deletions compose without overlap), four non-trivial clean shapes (a wrapping call, a transformed
  setter parameter, the compound-assignment safety fix, a returned literal), two operand-mismatch
  clean shapes for the setter, a bare `private set` full-silent-bail clean shape (matching diktat's
  own behavior for that exact shape, since its setter check has no bare-form fallback at all), two
  report-only bail occurrences (an annotated getter, a visibility-carrying setter), a
  comment-swallowed-by-deletion shape, and `@Suppress` happy/negative cases.

  `long-numerical-values` hardcodes diktat's own default thresholds (no configurable knob, per the
  project's own no-per-rule-config-beyond-level stance): a digit run longer than three characters
  is grouped in blocks of three, counted from the right for an integer literal's digits (and a
  float literal's real part) and from the left for a float literal's fractional part; a hex (`0x`)
  or binary (`0b`) literal's own prefix, and an `L`/`f`/`F` suffix, are preserved untouched outside
  the grouped run. A literal already containing an underscore anywhere is skipped entirely — no
  report at all — narrower than diktat's own behavior of still emitting a second, differently
  worded warn-only diagnostic for any individual block that's still too long; dropped as
  inconsistent with every other wrasse rule's one-message-per-occurrence convention. A float
  literal in scientific notation (`e`/`E`) is out of scope entirely, sidestepping a real latent bug
  in the ported algorithm: splitting on `.` alone for a dot-less exponent literal (`1e10`) leaves
  nothing to index as the fractional part. `serialVersionUID` — detekt's own exemption — was
  evaluated and deliberately not ported: getting it right needs either a name-only heuristic with
  real double-counting risk across nested local properties in wrasse's own event-stream model, or
  actual resolution, out of scope for a purely lexical T-rule; the existing `@Suppress` mechanism
  already covers this one narrow, known false positive at zero extra engineering cost. Fixtures
  (13, 8 with a `.fixed.kt` companion): a plain long decimal integer, one with an `L` suffix, a hex
  literal, a binary literal, a float grouped on its real part only, one grouped on its fractional
  part only, one with an `f` suffix, a short-literal clean shape, an already-underscored clean
  shape for both an integer and a float, a scientific-notation clean shape, and `@Suppress`
  happy/negative cases.

  `range-conventional` fuses two independent rewrites under one id, mirroring diktat's own single
  rule: a qualified, single-argument `rangeTo` call (`a.rangeTo(b)`) becomes `a..b`; a `..` range
  whose upper bound is `<expr> - 1` (any single layer of parentheses around the subtraction left
  exactly where they were) becomes `until <expr>` — reproducing diktat's own fixer's residual
  `1 until (4)` shape byte-for-byte, verified directly against its own fix-test fixtures, rather
  than the cleaner `1 until 4` a fresh design might prefer. Both matches are purely syntactic — no
  resolution — so a locally-declared `rangeTo` overload is rewritten the same as the standard
  library's, matching diktat's own scope exactly; a two-argument `rangeTo` call is untouched, the
  same single-argument gate diktat's own rule applies. A subtraction wrapped in two or more layers
  of parentheses is out of scope entirely — diktat's own fixer unwraps arbitrarily deep nesting via
  a dedicated chain-unwrap helper; this port narrows to zero or one layer, comfortably past every
  realistic case and far simpler to get right without that helper's own recursive-unwrap machinery.
  detekt's own `RangeUntilInsteadOfRangeTo` recommends `..<` instead of `until` for the identical
  `b - 1` shape; both operators are equally legal everywhere in wrasse's whole 2.1–2.4 support
  matrix (`..<` stabilized well before Kotlin 2.1), so this was a real choice, not a version
  constraint — `until` was kept both because the scope doc attributes this rule to diktat and
  because it needs no version-gating logic to justify, an infix stdlib function present since
  Kotlin's earliest releases. Both rewrites decline the fix (report only) whenever a comment sits
  anywhere inside the matched span, the same posture as every other T-bucket id: the replacement
  text is synthesized from sub-expression spans alone, with nowhere to relocate a comment found in
  between. Fixtures (12, 5 with a `.fixed.kt` companion): the `rangeTo`-call rewrite, three shapes
  of the `until` rewrite (no parens, one paren layer, pre-existing spaces on both sides of `..`), a
  plain-range clean shape, a `- 2` (not `- 1`) clean shape, a two-argument `rangeTo` clean shape, a
  double-paren clean shape proving the deliberate depth narrowing, one report-only comment-bail
  occurrence per rewrite, and `@Suppress` happy/negative cases.

  Each new id's own composition with `format` is proven in
  `format-with-fixes/trivial-accessors-and-format-error`,
  `format-with-fixes/long-numerical-values-and-format-error`, and
  `format-with-fixes/range-conventional-and-format-error` — the same low-risk shape as every prior
  T-bucket id.

  T-rule test backfill (2026-07-21): the seven newest T-bucket ids — `unnecessary-backticks`,
  `explicit-it-lambda-parameter`, `empty-default-constructor`, `redundant-constructor-keyword`,
  `trivial-accessors`, `long-numerical-values`, and `range-conventional` — ported against their real
  upstream test suites (detekt's `UnnecessaryBackticksSpec`, `ExplicitItLambdaParameterSpec`,
  `EmptyDefaultConstructorSpec`, `RedundantConstructorKeywordSpec`; diktat's
  `TrivialPropertyAccessorsWarnTest`/`FixTest`, `LongNumericalValuesSeparatedWarnTest`/`FixTest`,
  `RangeConventionalRuleWarnTest`/`FixTest`; detekt's `UnderscoresInNumericLiterals` and
  `RangeUntilInsteadOfRangeTo` evaluated for classification only, per each rule's own paragraph
  above, since neither was the adopted port target). ktlint re-confirmed to ship none of the seven
  by a fresh grep of its real checkout. Coverage table (already-covered / new-fixture /
  out-of-scope-with-citation / upstream-config-specific, upstream case count in parens):

  | rule | already-covered | new fixture | out-of-scope | config-specific | upstream cases |
  |---|---|---|---|---|---|
  | `unnecessary-backticks` | 9 | 2 | 1 | 0 | 12 |
  | `explicit-it-lambda-parameter` | 7 | 0 | 0 | 0 | 7 |
  | `empty-default-constructor` | 7 | 0 | 4 | 0 | 11 |
  | `redundant-constructor-keyword` | 7 | 0 | 1 | 0 | 8 |
  | `trivial-accessors` | 5 | 0 | 0 | 0 | 5 |
  | `long-numerical-values` (diktat) | 15 | 1 | 2 | 16 | 34 |
  | `long-numerical-values` (detekt, classification only) | — | 0 | 10 | 28 | 38 |
  | `range-conventional` (diktat) | 12 | 2 | 1 | 2 | 17 |
  | `range-conventional` (detekt, classification only) | — | 0 | 7 | 0 | 7 |

  Five new fixtures closed real gaps: `unnecessary-backticks/type-annotation-usage-error` (a
  backtick-quoted class name used in type-annotation position, detekt's own `class` test combo, not
  previously exercised end-to-end even though the rule's own per-`IDENTIFIER` mechanism already
  covered it structurally) and `unnecessary-backticks/string-template-short-form-adjacent-merge-error`
  (locks the rule's own documented coarser-than-detekt string-template bail — detekt's own
  `canPlaceAfterSimpleNameEntry` suppresses detection entirely when the following character would
  extend the identifier, e.g. `` "$`foo`bar" ``; wrasse's own bail is deliberately coarser and still
  reports, only declining the fix, per this rule's own paragraph above); `unnecessary-backticks/
  import-with-spaces-clean` (a spaced backtick-quoted name in import position, detekt's own clean
  case, not previously locked in that position); `long-numerical-values/short-hex-literal-clean` (a
  short hex literal, `0xF`, implicitly clean in diktat's own "test bad" suite but never asserted
  there, so never locked here either) and `long-numerical-values/already-underscored-oversized-block-
  clean` (locks the rule's own documented divergence from diktat: a literal already containing an
  underscore is skipped entirely regardless of block size, where diktat still emits a second,
  differently-worded warn-only diagnostic per oversized block — per this rule's own paragraph above);
  and `range-conventional/until-identifier-operand-error` (the `until` rewrite's left/right operands
  need not be numeric literals — diktat's own `1..(b - 1)` → `1 until (b)` case, proving the rewrite
  reads operand text verbatim rather than assuming a literal shape) and `range-conventional/
  until-followed-by-step-error` (the `until` rewrite fires correctly when the range expression is
  itself the left operand of an outer `step` infix call, diktat's own `1..(4 - 1) step 3` case,
  proving detection isn't confused by an enclosing binary expression). No real bugs found — every
  ported case either matched an existing fixture, was already a documented divergence from this
  rule's own paragraph above (`empty-default-constructor`'s four `expect`/`actual` shapes, real-
  compile-inexpressible per its own paragraph; `redundant-constructor-keyword`'s `data class` shape,
  the already-recorded Kotlin 2.1.x `Fir2IrDeclarationStorage` crash; `range-conventional`'s
  double-parens shape, the already-recorded depth-narrowing divergence — none needed a new
  quarantine fixture), or fell to an upstream-configurable knob wrasse's own no-per-rule-config
  stance never exposes (diktat's `isRangeToIgnore`/custom `maxNumberLength`/`maxBlockLength`
  fix-test configs; detekt's `acceptableLength`/`allowNonStandardGrouping` on the non-adopted
  `UnderscoresInNumericLiterals`). All three upstream checkouts (`ktlint`, `detekt`, `diktat`) left
  byte-clean — read-only throughout, `git status` verified. Full ladder green: `build`, `test
  --rerun-tasks`, `testMinorHarness --rerun-tasks`, `testPatchHarness`, `wrasseLint -Prepublish`.
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
- **B.4 — lint-only rules (bucket L), second installment, the COMPLEXITY/METRIC family — shipped
  2026-07-21.** Report, never fix — `canAutofix` is false everywhere in this family; a metric
  threshold breach is a design smell, not a mechanical rewrite. All thresholds are hardcoded (no
  per-rule config knob exists or will exist, D-config-stance): every one below is the sole
  upstream catalog's own default where only one catalog carries the rule at all (true for every
  id in this batch — none of ktlint/detekt/diktat disagree on a shared metric rule the way the
  naming batch's catalogs did), so "most permissive on disagreement" never had to be invoked here;
  it would be if a future batch ports a rule two catalogs both define with different defaults.
  Dedupe map (13 catalog rows, all from detekt except `file-size` from diktat, → 13 wrasse ids —
  no collapsing needed; unlike the naming batch, no two catalogs define the same metric rule):

  | wrasse id | dedupes | threshold | provenance |
  |---|---|---|---|
  | `long-parameter-list` | detekt `LongParameterList` | function >5 params, constructor >6 params | detekt default (`allowedFunctionParameters=5`, `allowedConstructorParameters=6`), sole catalog |
  | `long-method` | detekt `LongMethod` | >60 distinct code lines | detekt default (`allowedLines=60`), sole catalog |
  | `large-class` | detekt `LargeClass` | >600 distinct code lines | detekt default (`allowedLines=600`), sole catalog |
  | `too-many-functions` | detekt `TooManyFunctions` | >11 functions (file, class, interface, object, enum — one uniform number) | detekt default (`allowedFunctionsPer{File,Class,Interface,Object,Enum}=11`, already uniform across all five scopes), sole catalog |
  | `nested-block-depth` | detekt `NestedBlockDepth` | depth >4 | detekt default (`allowedDepth=4`), sole catalog |
  | `cyclomatic-complexity` | detekt `CyclomaticComplexMethod` | McCabe complexity >14 | detekt default (`allowedComplexity=14`), sole catalog |
  | `return-count` | detekt `ReturnCount` | >2 `return` statements | detekt default (`max=2`), sole catalog |
  | `throws-count` | detekt `ThrowsCount` | >2 `throw` statements | detekt default (`max=2`), sole catalog |
  | `destructuring-declaration-with-too-many-entries` | detekt `DestructuringDeclarationWithTooManyEntries` | >3 entries | detekt default (`maxDestructuringEntries=3`), sole catalog |
  | `complex-condition` | detekt `ComplexCondition` | ≥3 `&&`/`\|\|` operators in one condition | detekt default (`allowedConditions=3`), sole catalog |
  | `function-name-max-length` | detekt `FunctionNameMaxLength` | name >30 chars | detekt default (`maximumFunctionNameLength=30`), sole catalog |
  | `function-name-min-length` | detekt `FunctionNameMinLength` | name <3 chars | detekt default (`minimumFunctionNameLength=3`), sole catalog |
  | `file-size` | diktat `FileSize` | >2000 total lines | diktat default (`maxSize=2000`), sole catalog |

  **The uniform nesting policy (stated once, applies to every id above):** a nested declaration —
  a local function inside a function, a nested/inner class inside a class, an anonymous object's
  own member — gets its own independent frame/accumulator and is checked entirely on its own
  terms; nothing it contains ever merges upward into the enclosing declaration's own count, not
  even a flat +1 for "a nested thing existed here." This is a deliberate simplification, and for
  `cyclomatic-complexity` a deliberate **deviation** from detekt's own default (`ignoreLocalFunctions
  = false`, under which a nested function's *entire* internal complexity folds into the enclosing
  function's total by default — an inconsistency in detekt's own complexity family, since
  `LongMethod`/`LargeClass` already subtract nested extents from the parent while
  `CyclomaticComplexMethod` does not by default). Adopting one coherent rule — nested declarations
  are always independent, never additive — across every metric in this batch trades a small amount
  of upstream fidelity for internal consistency and strictly fewer reports (the permissive-leaning
  bias this whole batch follows), and is what "verify, upstream usually says no" resolved to for
  every rule here after checking each one's actual source (§ the rule notes below).

  Per-rule implementation notes, nesting/exemption decisions, and where a rule's own semantics
  needed a documented simplification:

  - **Counting rides the streaming walk two ways.** Five ids share one `WStreamRule` fused engine,
    `FunctionMetricsEngine` (`return-count`, `throws-count`, `nested-block-depth`,
    `cyclomatic-complexity`, `long-method`): a single stack of `FunctionMetricsFrame`s, pushed on
    `FUN` enter and popped on exit, accumulates all five metrics for that function in the same
    pass — cheaper than five separate walks each re-deriving the same function-nesting boundary,
    the same "one decision-maker" reasoning `ImportEngine`/`ModifierEngine` already established.
    Two more (`too-many-functions`, `large-class`) share a second engine, `ClassMetricsEngine`,
    with the identical frame-stack shape keyed to `CLASS`/`OBJECT_DECLARATION` instead of `FUN`.
    `WStreamRule` was picked over `WNodeRule`/`WBufferedNodeRule` deliberately: the adapter fires
    `onChildLeaf` once per *active* matching entry in `activeNodeRules`, so a `WNodeRule` targeting
    a self-nestable type (e.g. `FUN` inside `FUN`) gets its `onChildLeaf` invoked multiple times
    for one real leaf once nesting is two or more deep — the same rule instance is on the active
    list twice. `WStreamRule.visitLeaf`/`enterNode`/`exitNode` are called exactly once per node
    unconditionally (outside that mechanism entirely), so a hand-rolled frame stack keyed off them
    never double-counts regardless of nesting depth — the only kind that is both correct and cheap
    here. The remaining ids (`long-parameter-list`, `destructuring-declaration-with-too-many-entries`,
    `complex-condition`, and the `function-name-max-length`/`function-name-min-length` pair fused
    into `FunctionNameLengthEngine`) don't need frame-stack nesting tracking at all — each is a
    single `WBufferedNodeRule`/`WNodeRule` reading one node's own direct children or source span —
    so they stay standalone rather than joining a fused engine.
  - **`long-method`/`large-class`** count *distinct* source-code lines, matching detekt's own
    `linesOfCode()` definition exactly (a line with only whitespace or a comment/KDoc on it never
    counts) rather than a naive line-span subtraction — implemented as "current line number,
    updated on every leaf's own newline count; record it against the active frame only when a
    non-whitespace/non-comment leaf lands on a new line," an O(1)-per-leaf technique needing no
    line-number table. Counted over the whole declaration (signature/modifiers included), not just
    the body/class-body — simpler than detekt's own split (which tracks body-only lines for
    non-nested functions but whole-function lines for nested ones, solely to compensate for its
    own nested-subtraction bookkeeping) and made unnecessary by this batch's uniform
    never-merge-upward policy.
  - **`cyclomatic-complexity`** sums: the function's own baseline (+1), each `if` (unless it is an
    unbraced `else if` continuation — detected as "this `IF`'s own immediate parent is `ELSE`,"
    exactly mirroring detekt's `KtContainerNodeForControlStructureBody` check but purely
    syntactically), each loop (`for`/`while`/`do-while`), each `when` entry (including `else`),
    each `catch` clause, each `continue`/`break`, and each `&&`/`\|\|`/`?:` operator token. Unlike
    detekt's own default, a scope-function call with a trailing lambda (`run`/`let`/`apply`/
    `with`/`also`/`use`/`forEach`) is never treated as an extra decision point — detecting "is this
    `CALL_EXPRESSION`'s callee one of these names and does it have a lambda argument" needs its own
    small side-tracking machinery for a syntactic, name-only heuristic (detekt's own version is
    equally unresolved, just already built); omitting it only *lowers* the computed complexity
    (fewer reports), so it's dropped rather than built for marginal value. `nested-block-depth`
    drops the same scope-function extension for the identical reason, and both rules were verified
    against detekt's actual source (not assumed) before making this call.
  - **`return-count`/`throws-count`** count `RETURN`/`THROW` nodes whose nearest enclosing `FUN` is
    the frame being checked; a lambda literal is transparent (pushes no frame of its own), so a
    `return`/`throw` inside a lambda passed to another call counts toward the *enclosing* function
    — broader than detekt's own default, which excludes only a *labeled* lambda return
    (`return@foo`) via `excludeReturnFromLambda=true`. Telling a non-local (inlined, unlabeled)
    lambda return from a genuinely local one needs inlining knowledge this syntax-only pass does
    not have; treating every lambda uniformly as transparent is the simpler, single coherent rule.
    `return-count` keeps detekt's own `excludedFunctions=["equals"]` default verbatim (a function
    literally named `equals`, override or not, never triggers) since it costs nothing extra to
    check a function's own name.
  - **`long-parameter-list`** exempts an override (its parameter list is fixed by the supertype, not
    a local decision — the same reasoning `function-naming`'s override exemption already
    established) and a data class's primary/secondary constructor (matching detekt's own
    `ignoreDataClasses=true` default: a data class's parameters are its whole public shape by
    design). Reports at the `VALUE_PARAMETER_LIST`'s own span (the parens), matching detekt's own
    `Entity.from(parameterList, function)` — not the declaration's name, since detekt itself
    doesn't point there for this one rule.
  - **`complex-condition`** mirrors detekt's own algorithm exactly, including its crudeness: a
    plain, non-overlapping substring count of `&&`/`\|\|` over the condition's own raw source
    text (not an AST walk over `BINARY_EXPRESSION`/operator tokens) — detekt's own implementation
    is text-based too (`frequency(text, "&&")`), so matching it exactly rather than "improving" it
    with token-level counting keeps wrasse's report volume identical to upstream's own known
    behavior on this rule.
  - **`destructuring-declaration-with-too-many-entries`** is a direct `DESTRUCTURING_DECLARATION`
    child count (`DESTRUCTURING_DECLARATION_ENTRY` children) — destructuring declarations never
    nest inside one another, so no frame stack is needed at all.
  - **`function-name-max-length`/`function-name-min-length`** exempt an override (name fixed by the
    supertype) and an `operator` function (name fixed by the language — `plus`, `get`, `invoke`,
    ...) either way, matching detekt's own exemptions on both rules. In practice the operator
    exemption can never be organically exercised for `-max-length` (every Kotlin operator function
    name is short) and barely for `-min-length` (the shortest, `get`/`set`/`inc`/`dec`/`not`, are
    already 3 characters, at the minimum threshold) — kept for parity with detekt's own rule shape
    regardless, since it costs nothing to check.
  - **`too-many-functions`** counts only *directly*-declared member functions per
    class/interface/object/enum (never a nested class/object's own functions) plus a separate
    file-level top-level-function count, matching detekt's own scope exactly — detekt itself never
    looks past direct children either, so no subtraction was ever needed upstream or here. detekt's
    own default config additionally excludes `**/test/**` source-set paths for this rule (a signal
    that test classes are a common, expected source of noise for it) — wrasse has no source-set
    concept in the rule layer to replicate that automatically, but the existing per-rule `exclude`
    glob every wrasse rule already supports (§7) is the same escape hatch a user would reach for
    with any other rule; no new mechanism was built for this one specifically.

  **Deliberately skipped, with reasons** (the "8 defensible rules over 13 arbitrary ones" bias):

  - **`throws-count`/`return-count`'s upstream siblings that are genuinely off-by-default or
    narrow-scope, not merely uncommon:** detekt's `CognitiveComplexMethod` (inactive by default
    upstream, `ActiveByDefault` absent; a second, more subjective complexity metric that heavily
    overlaps `cyclomatic-complexity`'s coverage — shipping both invites two thresholds disagreeing
    about the same function), `MethodOverloading` (inactive by default; a same-name-overload-count
    smell that overlaps `too-many-functions`' coverage), `NestedScopeFunctions` (inactive by
    default; a narrower variant of `nested-block-depth` scoped only to scope-function lambdas,
    which this batch already declined to special-case), `ComplexInterface` (inactive by default;
    overlaps `too-many-functions`' interface scope), `NamedArguments` (inactive by default; a style
    suggestion about positional-vs-named call sites, not a size/nesting/count design smell — out
    of this batch's actual scope, not merely skipped for noise reasons).
  - **`string-literal-duplication`** (detekt `StringLiteralDuplication`) — inactive by default
    upstream, and its own default config carries four separate tunable knobs
    (`allowedDuplications`, `ignoreAnnotation`, `allowedWithLengthLessThan`, `ignoreStringsRegex`)
    just to keep it usable at all — the clearest case in this batch of "a rule whose usefulness
    depends entirely on being tunable"; hardcoding any one combination makes it either a no-op
    (permissive knobs) or noise (strict ones), so it's skipped outright per the task's own
    stated skip criterion, not merely deprioritized.
  - **diktat `lambda-length`** — a `long-method`-shaped rule but scoped only to lambda literals
    that use the implicit `it` parameter (diktat's own `doesLambdaContainIt` gate), at a default of
    only 10 lines. That narrow scope plus an aggressive threshold generalizes poorly across common
    Kotlin idioms (DSL builders, `apply`/`also` blocks, Gradle build scripts, test setup blocks) —
    another rule whose real-world value is inseparable from per-project tuning, so it's skipped
    rather than hardcoded at a value guaranteed to be wrong for large swaths of idiomatic code.
  - **`labeled-expression`/diktat `custom-label`/the task's own tentative "label-count"** — neither
    upstream rule is actually count-shaped: `LabeledExpression` (inactive by default) flags *any*
    custom label at all (an allowlist of exempted label names is its only config axis, not a
    threshold), and diktat's `custom-label` is the same presence check, not a count. There is no
    natural number to hardcode here without inventing a threshold neither catalog itself uses —
    skipped as not actually belonging to this batch's threshold-shaped scope, rather than shipped
    with a made-up cutoff.
  - **detekt naming-length siblings `variable-max-length`/`variable-min-length`** — same length-metric
    shape as the two function-name-length ids shipped here, but scoped to local
    variables/properties, the same "different scope-resolution shape than member/top-level
    declarations" reason B.1 already gave for deferring `variable-naming`'s casing check; held for
    that same follow-up batch rather than split across two unrelated installments.
  - **Rules needing FIR resolution or cross-file knowledge:** none of this batch's candidates
    actually need either — every one above is syntax-only, including `complex-condition`'s
    scope-function detection (name-based, exactly as detekt's own unresolved heuristic is) and
    `long-parameter-list`'s override/data-class checks (both syntactically visible: an `override`
    modifier keyword, a `data` modifier keyword). No candidate was dropped for this reason in this
    batch; noted here only because the task asked the question explicitly.

  Fixture coverage: 69 fixtures across the 13 rule directories (at-threshold-clean,
  over-threshold-error, a nesting/independence case demonstrating a nested declaration's own
  separate frame, `@Suppress` happy/negative pairs, per rule; `too-many-functions` additionally
  covers its interface/file scopes, `nested-block-depth` additionally covers the unbraced-`else
  if`-is-not-extra-depth case). Unit specs: `FunctionMetricsFrameSpec`/`ClassMetricsFrameSpec`
  exercise the two pure, kotlinc-free counting/nesting accumulators directly (push/pop sequences,
  repeated-line dedup) — the highest-value tests in this batch, since the counters are where
  off-by-one bugs hide — plus one Decision spec per rule family covering the exact threshold
  boundary and message text.

- **B.5 — lint-only rules (bucket L), third installment, the POTENTIAL-BUGS/CORRECTNESS-SMELL
  family — shipped 2026-07-21.** Report, never fix — `canAutofix` is false everywhere in this
  family. Candidates were the detekt-style rows autoformat-scope.md classifies as L that the
  naming (B.1) and metrics (B.4) installments hadn't yet covered. Triage table (verify-first, per
  the task's own directive — every candidate was checked against its actual upstream source
  before deciding, not assumed):

  | candidate | syntax-only feasible? | outcome |
  |---|---|---|
  | `equals-null-call` | yes — upstream's own check is pure PSI (`calleeExpression.text == "equals"`, single-arg text `"null"`) | shipped |
  | `safe-cast` | yes — upstream's own check is pure PSI (`KtIsExpression` + branch text comparison) | shipped |
  | `use-let` | yes — upstream's own check is pure PSI (`operationToken` + either-operand text `"null"`) | shipped |
  | `also-could-be-apply` | yes — upstream's own check is pure PSI (callee text + statement receiver text) | shipped |
  | `function-only-returning-constant` | yes — upstream's own check is pure PSI (body-expression node type) | shipped |
  | `may-be-constant` | yes, narrowed — upstream's own check is pure PSI, but its binary-expression/named-constant-reference extension needs whole-file forward-reference lookahead this single pass doesn't attempt | shipped, narrowed |
  | `unused-parameter` | yes — upstream's own check is pure PSI name-matching (still true today; detekt never moved this one to resolution) | shipped |
  | `unused-private-class` | yes, upstream's own check is a resolution-free heuristic (its own comment: "without type resolution it is hard to tell if this is really a class or part of a package") | shipped, further simplified |
  | `unused-private-member` | **no** — ground-truth detekt's current `UnusedPrivateFunction`/`UnusedPrivateProperty` both declare `RequiresAnalysisApi` and resolve each reference to a specific K2 symbol to disambiguate overloads/operator conventions/property delegates; `WResolvedUsage.callables` is a file-level, name-collapsed `Set` (dedupes by package/class/name, not declaration identity) built for import correctness, not per-declaration attribution — insufficient granularity | **skipped** |
  | `nested-classes-visibility` | yes — upstream's own check is pure PSI (modifier keywords only) | shipped |
  | `forbidden-comment` | yes; config-shaped (word list) — hardcoded to the one list every one of ktlint/detekt/diktat ships as its own out-of-box default (`TODO:`/`FIXME:`/`STOPSHIP:`), no wrasse-specific list invented | shipped |
  | `forbidden-suppress` | yes syntactically, but config-shaped (forbidden-rule-id list) with **no sensible hardcoded default** — unlike `forbidden-comment`'s markers, no rule-id blocklist is universally agreed, and upstream's own default (`rules: []`) is itself a no-op | **skipped** |
  | `string-should-be-raw-string` | yes, narrowed — upstream's own check is pure PSI, but its multi-piece concatenation "pivot element" analysis is dropped | shipped, narrowed |
  | `trim-multiline-raw-string` | yes, narrowed — upstream's own check is pure PSI; the chained-trim-call check here peeks raw text after the string rather than inspecting the parent node | shipped, narrowed |
  | `explicit-it-lambda-multiple-parameters` | yes — upstream's own check is pure PSI (parameter name list) | shipped |
  | `range-until-instead-of-range-to` | yes syntactically, but **already owned** by the shipped `range-conventional` engine (B.2, diktat): both target the identical shape (`a..b-1`) with the identical judgment, merely disagreeing on the preferred replacement spelling (`until` vs `..<`); shipping a second id reporting the same span with a competing suggested fix would violate this project's own "one wrasse id per concept, never one id per source tool" dedupe discipline and risk a same-span `EditPlan` collision once `range-conventional`'s own fix runs | **skipped** |

  No candidate in this batch needed FIR resolution to avoid false positives (unlike
  `unused-private-member`, genuinely a different case) — `WResolvedUsage`/`WQualifiedUsage` were
  consulted (per the task's own instruction to check `requiresResolution`/`requiresQualifiedUsages`
  and the facade's actual shape, §8.1) but none of the 13 shipped rules attaches either flag;
  every one is `WLeafRule`/`WNodeRule`/`WBufferedNodeRule`/`WStreamRule` over syntax alone, exactly
  matching how ktlint/detekt's own equivalents are themselves implemented (several of detekt's own
  KDoc comments say as much explicitly, e.g. `UnusedPrivateClass`'s uppercase-first-letter heuristic
  comment).

  Dedupe map (13 wrasse ids, no cross-catalog collisions — every candidate here is detekt's own,
  no ktlint/diktat sibling to fold in):

  | wrasse id | dedupes |
  |---|---|
  | `equals-null-call` | detekt `EqualsNullCall` |
  | `safe-cast` | detekt `SafeCast` |
  | `use-let` | detekt `UseLet` |
  | `also-could-be-apply` | detekt `AlsoCouldBeApply` |
  | `function-only-returning-constant` | detekt `FunctionOnlyReturningConstant` |
  | `may-be-constant` | detekt `MayBeConstant` |
  | `unused-parameter` | detekt `UnusedParameter` |
  | `unused-private-class` | detekt `UnusedPrivateClass` |
  | `nested-classes-visibility` | detekt `NestedClassesVisibility` |
  | `forbidden-comment` | detekt `ForbiddenComment` |
  | `string-should-be-raw-string` | detekt `StringShouldBeRawString` |
  | `trim-multiline-raw-string` | detekt `TrimMultilineRawString` |
  | `explicit-it-lambda-multiple-parameters` | detekt `ExplicitItLambdaMultipleParameters` |

  Per-rule semantics and narrowing notes:

  - **`equals-null-call`** — a call whose callee text is exactly `equals` with a single argument
    whose own trimmed text is exactly `null` (receiver-qualified or bare), reported at the call
    expression's own span — matches upstream exactly, no narrowing.
  - **`safe-cast`** — an `if`/`else` whose condition is exactly `<identifier> is T` (or `!is T`)
    and whose branches reduce (after stripping one layer of `{ }`) to the identifier on one side
    and `null` on the other, reported at the whole `if` expression — matches upstream exactly.
  - **`use-let`** — an `if`/`else` whose condition is `<expr> != null`/`null != <expr>` with an
    `else` reducing to `null`, or `<expr> == null`/`null == <expr>` with a `then` reducing to
    `null` — matches upstream's own algorithm precisely: neither side of the condition is
    inspected beyond "is one operand literally `null`," so the checked branch's own value is
    never correlated back to the condition's non-null operand.
  - **`also-could-be-apply`** — a single-lambda-argument `also` call whose block statements are
    all non-empty and each begins with `it.` or `it?.` (checked as a literal text prefix on the
    statement's own span, not a receiver-node inspection — `it` cannot be a prefix of any other
    identifier without a following letter/digit/underscore, so this is exact, not approximate),
    reported at the `also` callee's own span.
  - **`function-only-returning-constant`** — a function whose body is exactly `= <literal>` or
    `{ return <literal> }` (a bare numeric/character/boolean literal, or a non-interpolated string
    template), reported at the function's own name. Exempt (hardcoded, upstream's own defaults,
    no wrasse config surface beyond `level`): `override`/`open`, declared directly inside an
    interface, or `actual`. Upstream's own `excludedFunctions` regex-list knob has no wrasse
    equivalent — no per-rule config beyond `level` exists project-wide.
  - **`may-be-constant`** — **narrowed**: a top-level or direct object/companion-member `val`
    (never a plain class member, matching upstream's own scope restriction) whose own initializer
    is *directly* a literal constant, with no `var`, no existing `const`, no `actual`, no
    `override`, no getter, and no non-`@JvmField` annotation. Upstream's own extension — folding a
    binary expression of two already-constant-foldable operands, including a bare reference to
    another already-declared file-scope or companion constant — is dropped outright: correctly
    resolving a forward-declared named constant needs a whole-file pre-pass this single
    SAX walk does not attempt, and arbitrary-depth nested-binary-expression descent is a
    materially larger feature for a shape (`val x = 1 + 2`, `val y = A + "suffix"`) that is
    distinctly rarer than a bare literal initializer in practice. Strictly narrower — every report
    this rule emits, upstream would also emit — never a false positive relative to upstream.
  - **`unused-parameter`** — a function parameter whose name never occurs, in its own function's
    subtree, as a plain name reference or a local property's own declared name (the two removal
    triggers detekt's own visitor uses), reported at the parameter's own name. Every currently-open
    enclosing `FUN` frame is checked on each occurrence — not just the innermost — mirroring
    upstream's own whole-subtree, no-lexical-shadowing name matching exactly (upstream's visitor
    also doesn't stop at a nested local function's boundary); this can mark an outer parameter
    "used" by an unrelated nested function's own same-named parameter, a narrow false-negative
    both here and upstream. Also narrowed: a named-argument label (`foo(paramName = value)`) is
    not distinguished from a real reference and always counts as a use — the node model has no
    distinct shape for an argument's name position; permissive, not a false-positive risk. Exempt
    (hardcoded, upstream's own defaults): `abstract`/`open`/`override`/`operator`/`external`/
    `expect`/`actual`/`protected` functions, a function literally named `main`, any function
    inside an `expect`/`external` class or an interface, and a parameter name matching
    `ignored|expected`. **Resolved autoformat-scope.md's own open question** (hard call #6: "verify
    added value before porting" against kotlinc's own warnings) — confirmed empirically (compiled
    representative unused-parameter/unused-variable samples with the embeddable K2 compiler, zero
    extra flags) that kotlinc's own equivalent diagnostics live under FIR's `analysis/checkers/
    extra/` package, gated behind `-Xextra-checkers`, not run by an ordinary compile — porting this
    rule is not redundant with anything Host A's ride-along compile already gets for free.
  - **`unused-private-class`** — **deliberately simplified, not merely narrowed**: a `private`
    class (top-level or nested) whose simple name never occurs as a type reference (`USER_TYPE` —
    covering supertypes, parameter/return/property types, `is`/`as` targets, generic arguments) or
    a plain name reference (`REFERENCE_EXPRESSION` — covering constructor calls, qualifiers,
    callable references) anywhere in the file, reported at the class's own span. Upstream tracks a
    dozen distinct PSI node shapes one by one; every one of them, in this model, funnels through
    exactly one of these two node types, so the two-check version here is a strict superset of
    upstream's own tracked contexts — it only ever reports a class upstream would also flag, never
    one it wouldn't. Upstream's own import-FQN correlation is dropped outright: a private
    declaration cannot be imported from elsewhere by definition, so it has no bearing here.
  - **`nested-classes-visibility`** — a nested class/object carrying an explicit `public` modifier,
    declared directly inside a top-level, non-interface, `internal` class's own body, reported at
    the nested declaration's own span. An enum entry, an `enum`-modified nested class, and a
    companion object are exempt (matches upstream). Deeper-nested (grandchild) declarations are
    never considered — matches upstream's own `klass.declarations` (direct-children-only) scope.
  - **`forbidden-comment`** — a line comment, block comment, or KDoc whose raw text contains
    `TODO:`, `FIXME:`, or `STOPSHIP:` (matched directly over the comment's own delimited text,
    since none of the three markers can appear inside a comment delimiter itself), reported at the
    comment's own span. The marker list is hardcoded — every one of ktlint/detekt/diktat ships
    this exact three-marker default out of the box; wrasse has no `wrasse.json` surface for a
    project-specific list (`level` only), so inventing one was out of scope, not merely deferred.
  - **`string-should-be-raw-string`** — **narrowed**: a non-raw, non-empty string literal carrying
    more than two `\t`/`\"`/`\\`/`\n` escape sequences (upstream's own default threshold and
    exact character set), reported at its own span. Upstream's own multi-piece string-
    concatenation "pivot element" analysis — where several adjacent `+`-joined pieces are jointly
    counted and only the chain's own designated piece reports — is dropped; a string that is one
    operand of a `+` expression is skipped entirely rather than analyzed as part of a chain,
    narrower and strictly fewer reports. A string passed as an argument to `replaceIndent(...)`/
    `prependIndent(...)` is exempt (checked via the nearest enclosing call's own callee name),
    matching upstream's own carve-out for those two methods' own arguments.
  - **`trim-multiline-raw-string`** — a raw (`"""`) string literal containing an actual newline
    character with no `.trimIndent()`/`.trimMargin()` call chained onto it, reported at its own
    span. The chained-call check peeks at the raw source text immediately after the closing `"""`
    (spaces/tabs only skipped) rather than inspecting a parent qualified-expression node — a
    comment or newline between the string and its trim call reads as "not trimmed," narrower than
    upstream, never a false positive. Exempt (matching upstream): a `const val`'s own direct,
    unwrapped initializer, and any string nested inside an `@`-annotation's arguments — both
    contexts require a compile-time constant, so a trim call could never legally attach there.
  - **`explicit-it-lambda-multiple-parameters`** — a lambda declaring more than one parameter,
    one of them named `it`, reported at the lambda's own span — matches upstream exactly.

  Fixture coverage: 87 fixtures across the 13 rule directories (error cases, clean cases, every
  documented exemption, `@Suppress` happy/negative pairs per rule). One real, load-bearing
  discovery from the `@Suppress` negative fixtures: `may-be-constant`'s own "no non-`@JvmField`
  annotation" exemption fires for *any* annotation, `@Suppress` included — a wrong-id
  `@Suppress` directly on the property silences the rule's own report all by itself, with no
  suppression involved at all, so that fixture uses `@file:Suppress` instead (a file-level
  annotation never touches the property's own modifier list). Also discovered: a `CLASS` node's
  own span includes its leading `@Suppress` annotation when one is present, shifting the expected
  report line for `nested-classes-visibility`/`unused-private-class`'s own wrong-id fixtures by
  one line versus the no-annotation case. Unit specs: one Decision spec per rule (12 — `also-
  could-be-apply` through `explicit-it-lambda-multiple-parameters`) plus three specs for the
  shared syntax-only predicates introduced in this batch (`ConstantLiteralCheck`,
  `StringTemplateText`, `WordBoundaryScan`), reused across `function-only-returning-constant`,
  `may-be-constant`, `string-should-be-raw-string`, and `trim-multiline-raw-string` rather than
  duplicated per rule.

  **Known gap, out of this batch's scope by the task's own instruction:** `wrasse-schema.json`
  (editor-autocomplete only, D13) was not touched — the 13 new rule ids are valid, effective
  `wrasse.json` config today (the schema plays no role in what the plugin itself accepts, only in
  editor tooling), but an editor validating against the schema's `"additionalProperties": false`
  rule list would flag any of them as unrecognized until the schema is extended in a follow-up.

Within a tier: complexity 1 → 3; implement overlapping ktlint/detekt/diktat rules once under a
single wrasse id.

- **B.6 — lint-only rules (bucket L), fourth installment, the COMMENT/KDOC-POLICY and
  EXCEPTION-HANDLING families — done 2026-07-22.** Fifteen ids. KDoc/comment policy:
  `undocumented-public-class`/`-function`/`-property` (fused in `KdocEngine`, gated on the compile
  running under Kotlin explicit-API mode via D23 `explicitApiActive` — an ungated "KDoc on every
  public declaration" rule would fight this project's own contract-only-not-mandatory style, so
  outside explicit-API mode these three report nothing; members and non-public declarations are
  never candidates), `kdoc-tag-mismatch` (also fused; a `@param`/`@property` tag naming a parameter
  the signature does not have — never gated on explicit-API mode), `kdoc-deprecated-tag` (a
  `@deprecated` block tag, which Kotlin does not honour), `comment-over-private-declaration` (KDoc
  on a private function/property). Exception handling: `empty-catch-block`, `swallowed-exception`,
  `too-generic-exception-caught`, `too-generic-exception-thrown`, `print-stack-trace`,
  `rethrow-caught-exception`, `not-implemented-declaration` (`TODO()`/`throw NotImplementedError`),
  `instance-of-check-for-exception` (`is`/`as` on a caught exception), `exception-raised-in-
  unexpected-location`. All syntax-only (no resolution); the catch-family shares
  `AllowedExceptionName` (`_`/`ignore*`/`expected*` catch names are intentional-ignore signals) and
  `CatchParameterText`. **Suppression-scope note:** a rule reporting on a KDoc leaf (e.g.
  `kdoc-deprecated-tag`) reports at the KDoc's own offset, which under wrasse's positional
  (offset-containment) suppression sits *before* the following declaration's `@Suppress` span — so
  a declaration-level `@Suppress` does not cover it (unlike detekt's PSI-element containment).
  File-scope `@file:Suppress` is the correct suppression for such findings; the fixtures encode this.

- **Fixture-harness worker headroom (infrastructure, 2026-07-22).** The per-minor fixture modules
  run every fixture as a separate in-process `K2JVMCompiler.exec` in one long-lived Gradle test
  worker; repeated in-process kotlinc compiles accumulate IntelliJ-platform registry/classloader/
  metaspace state that `exec` does not fully tear down. Around ~105 fixture directories this crossed
  the worker's default stack/metaspace ceiling and surfaced as `StackOverflowError`s at random
  compiler frames, cascading across unrelated fixtures and hanging the run. `forkEvery` cannot help
  (Kotest emits one spec class of many dynamic tests, so there is nothing to fork between). Fix, in
  the convention plugin's `configureTests`, scoped to the `:testing:wrasse-kotlinc-plugin-tests-*`
  modules only: `maxHeapSize = "2g"`, `-Xss8m`, `-XX:MaxMetaspaceSize=1g`. Not a rule or fixture
  defect — every rule was green on old fixtures and every new fixture directory green in isolation;
  only the cumulative compile count tipped it over.

- **B.7 — lint-only rules (bucket L), fifth installment, the CONTROL-FLOW/EMPTY-BLOCK/POTENTIAL-BUG
  remainder plus the deferred VARIABLE-naming family — shipped 2026-07-22.** Eighteen new ids.
  Report, never fix — `canAutofix` is false everywhere in this batch. Triage table (every
  candidate checked against its actual current upstream source, not the possibly-stale
  `autoformat-scope.md` snapshot alone — several of this batch's real rules
  (`DoubleNegativeExpression`/`DoubleNegativeLambda`, `LoopWithTooManyJumpStatements`,
  `ConstructorParameterNaming`, `VariableNaming`, `VariableMinLength`/`VariableMaxLength`) postdate
  that catalog's own snapshot and only surfaced by reading ground-truth detekt/diktat directly):

  | candidate | syntax-only feasible? | outcome |
  |---|---|---|
  | `empty-if-block`/`empty-else-block` | yes — upstream's own check is pure PSI (`KtBlockExpression` with zero children) | shipped, fused (`EmptyBlockEngine`) |
  | `empty-for-block`/`empty-while-block`/`empty-do-while-block` | yes, same shape | shipped, fused |
  | `empty-finally-block`/`empty-try-block`/`empty-init-block`/`empty-secondary-constructor` | yes, same shape | shipped, fused |
  | `empty-function-block` | yes — modifier/interface-membership check is pure PSI | shipped, standalone (needs `FUN`'s own children, not reachable cheaply from a fused `BLOCK` walk) |
  | `empty-when-block` | yes — entry-count check is pure PSI | shipped, standalone |
  | `empty-kotlin-file` | yes — whole-file text-minus-package-directive check is pure PSI | shipped, standalone |
  | `unconditional-jump-statement-in-loop` | yes, narrowed — upstream's own check is pure PSI, but its multi-statement backward-scan "already conditional" exemption is dropped | shipped, narrowed to the single-statement-body case |
  | `loop-with-too-many-jump-statements` | yes — upstream's own check is pure PSI, a deep (non-nested-loop-crossing) descendant count | shipped |
  | `custom-label` | yes — upstream's (diktat) own check is name-based/ancestor-counting, no resolution; corrects B.5's own skip rationale (B.5 characterized it as "a presence check, not a count, no natural threshold to hardcode" — re-reading diktat's actual `CustomLabel.kt` shows a fixed, non-configurable `nestedCount == 1` check, not a user-tunable threshold at all) | shipped |
  | `double-negative` | yes, narrowed — detekt's own `DoubleNegativeExpression` declares nothing analysis-requiring for its prefix-`!`-chain branch, but does for its `.not()`/`not()` qualified-call branch (must resolve to know it is really `Boolean.not()`); `DoubleNegativeLambda`'s own negation-detection (token-type discrimination plus identifier camelCase-splitting across an arbitrary-depth lambda subtree) is real but meaningfully more complex for a narrow stylistic nit | shipped, narrowed to the prefix-`!`-chain form only; `DoubleNegativeLambda`'s own shape not folded in, left for a future batch |
  | `redundant-else-in-when` | **no** — "is this `when`'s own `else` actually reachable" needs knowing whether the subject type's own hierarchy is already exhaustive (sealed/enum resolution) | **skipped** |
  | `redundant-return`/`redundant-jump` | not present as a distinct rule in any of ktlint/detekt/diktat today | **skipped** |
  | `redundant-boolean-literal` (`== true`/`== false`) | not present as a distinct rule in any of the three catalogs today (detekt's own `NullableBooleanCheck` recommends the *opposite* direction, `?: false` → `== true`) | **skipped** |
  | `simplifiable-boolean-expression` | not present as a distinct rule in any of the three catalogs today | **skipped** |
  | `for-loop-over-range-index-instead-of-collection` | not present upstream as such; the closest sibling (detekt `ForEachOnRange`) is a different concept (boxing cost of `IntRange.forEach`) and itself needs the receiver's resolved type | **skipped** |
  | `nested-loop` (metric) | no such metric rule in any of the three catalogs today | **skipped** |
  | `variable-naming` | **already shipped** — `property-naming` (B.1) targets every `PROPERTY` node with no ancestor restriction beyond its own top-level-`val`/object-member-`val` exemptions, so it already enforces lowerCamelCase on a **local variable** inside a function body today; empirically verified (a temporary fixture asserting a local `val LocalBad = 1` inside a function reports `property-naming`, confirmed green, then removed). B.1's own deferral note assumed a disjoint scope that does not actually exist in the shipped code | **no new id — dedupe, not a skip** |
  | `constructor-parameter-naming` | yes — upstream's own check is pure PSI (name pattern + `override`) | shipped |
  | `variable-name-min-length` | present upstream, but detekt's own default (`minimumVariableNameLength = 1`) makes the check a permanent no-op (`length < 1` never holds for a real identifier) — hardcoding it ships a rule that can never fire; no cross-catalog signal exists for a different, meaningful default | **skipped** — no meaningful hardcoded default |
  | `variable-name-max-length` | yes — upstream's own default (`maximumVariableNameLength = 64`) is meaningful | shipped |

  Dedupe map (16 catalog rows across detekt/diktat → 18 wrasse ids; `empty-if-block` through
  `empty-secondary-constructor` are 9 ids fused into `EmptyBlockEngine`, no other collapsing
  needed — this batch's candidates never overlapped the way the naming batch's did):

  | wrasse id | dedupes |
  |---|---|
  | `empty-if-block` | detekt `EmptyIfBlock` |
  | `empty-else-block` | detekt `EmptyElseBlock` |
  | `empty-for-block` | detekt `EmptyForBlock` |
  | `empty-while-block` | detekt `EmptyWhileBlock` |
  | `empty-do-while-block` | detekt `EmptyDoWhileBlock` |
  | `empty-finally-block` | detekt `EmptyFinallyBlock` |
  | `empty-try-block` | detekt `EmptyTryBlock` |
  | `empty-init-block` | detekt `EmptyInitBlock` |
  | `empty-secondary-constructor` | detekt `EmptySecondaryConstructor` |
  | `empty-function-block` | detekt `EmptyFunctionBlock` |
  | `empty-when-block` | detekt `EmptyWhenBlock` |
  | `empty-kotlin-file` | detekt `EmptyKotlinFile` |
  | `unconditional-jump-statement-in-loop` | detekt `UnconditionalJumpStatementInLoop` |
  | `loop-with-too-many-jump-statements` | detekt `LoopWithTooManyJumpStatements` |
  | `custom-label` | diktat `CustomLabel` |
  | `double-negative` | detekt `DoubleNegativeExpression` (prefix-`!`-chain branch only) |
  | `constructor-parameter-naming` | detekt `ConstructorParameterNaming` |
  | `variable-name-max-length` | detekt `VariableMaxLength` |

  Per-rule semantics, narrowing, and provenance notes:

  - **The empty-block family** shares one convention, already established by `empty-catch-block`
    (B.6) and `no-empty-class-body` (B.2): a `BLOCK`'s own direct children must be nothing but its
    braces and whitespace (`EmptyBlockCheck`, extracted from `EmptyCatchBlockRule` in this batch and
    reused everywhere below); any comment or KDoc inside makes it non-empty. **`EmptyBlockEngine`**
    fuses nine ids into one `BLOCK`-targeted buffered walk, routed purely by the block's own
    immediate parent (`THEN`/`ELSE` for `empty-if-block`/`empty-else-block`; a loop's `BODY`,
    disambiguated one level further by which of `FOR`/`WHILE`/`DO_WHILE` owns it, for the three
    loop ids; `FINALLY`/`TRY`/`CLASS_INITIALIZER`/`SECONDARY_CONSTRUCTOR` directly) — one decision-
    maker instead of nine independent rules each re-deriving the identical emptiness check. A
    `BLOCK` whose parent is `FUN` is deliberately excluded from the engine's routing:
    `empty-function-block` needs the function's own `open`/interface-membership context, cheaper to
    read directly off `FUN`'s own children than to re-derive from a nested `BLOCK`'s ancestors, so
    it stays a standalone rule reading `BLOCK`'s span text directly (`EmptyBlockCheck.isEmptySpan`)
    rather than joining the engine.
  - **`empty-when-block`** deliberately applies the project's own "comment/KDoc inside exempts"
    convention to itself even though detekt's own `EmptyWhenBlock` does not (it only checks
    `entries.isEmpty()`, with no comment carve-out) — narrower, never a new false positive relative
    to upstream, and consistent with every sibling rule in this family.
  - **`empty-function-block`** exempts an `open` function (a subclass may still rely on the no-op
    default) and any function declared directly inside an interface (the common "optional callback
    with a no-op default" idiom) — both matching detekt's own `EmptyFunctionBlock` defaults
    (`isOpen()`, and its own `isDefaultFunction()` interface check, folded together here since this
    batch's model has no config surface for `ignoreOverridden` and detekt's own default already
    treats every interface member with a body as exempt regardless of `override`).
  - **`empty-kotlin-file`** strips only the file's own `PACKAGE_DIRECTIVE` span before checking
    blankness, matching detekt's own algorithm exactly (`file.text` minus the package-directive
    range). A real consequence, not a bug: any `@file:` annotation (including
    `@file:Suppress("empty-kotlin-file")`) is itself non-blank content, so a file carrying one is
    never considered empty in the first place, by either detekt's algorithm or this one — the
    fixtures document this rather than working around it.
  - **`unconditional-jump-statement-in-loop`** — **narrowed**: reports a loop (braced or bare body)
    whose entire body is exactly one statement that is itself a bare `break`, or a bare `return`
    whose own value is not an `<expr> ?: break`/`<expr> ?: continue` elvis fallback (that idiom
    always exits the loop one way or another regardless, a defensive pattern detekt's own rule
    also leaves alone). A bare `continue` is never flagged — deliberately narrower than upstream:
    a lone unconditional `continue` does not exit the loop the way `break`/`return` do, so including
    it would contradict this rule's own stated rationale ("the loop is only executed once").
    Upstream's own multi-statement case — flagging *any* top-level jump statement in a longer body,
    exempting one that is itself preceded by a sibling already containing a jump anywhere in its own
    subtree — needs a backward-scan heuristic this single-pass model does not attempt; every report
    this narrower version emits, upstream would also emit.
  - **`loop-with-too-many-jump-statements`** counts `break`/`continue` at any nesting depth of
    `if`/`when`/blocks inside a loop's own body, but never descends into a nested loop's own body
    (it gets its own independent count instead — the same never-merge-upward policy `B.4`'s metric
    family already established), matching detekt's own visitor exactly (`return` is not counted,
    matching detekt). Implemented as a standalone `WStreamRule` with a hand-rolled `LoopJumpFrame`
    stack (not `WNodeRule`'s `onChildLeaf`, which double-fires for a self-nestable target — the same
    reasoning `FunctionMetricsEngine` already documented). Threshold hardcoded at detekt's sole
    default (`maxJumpCount = 1`, i.e. more than one jump reports).
  - **`custom-label`** reports a `return@x`/`break@x`/`continue@x` naming a label other than
    `@loop` (the project-convention name for a manually-labeled loop) or a name matching its own
    enclosing call — Kotlin gives every trailing-lambda call an implicit label equal to its own
    name, so writing that name back out is never "custom" regardless of which function it is —
    when exactly one enclosing loop or matching-named call surrounds it, meaning the name was never
    needed to disambiguate. **Generalized beyond upstream's own narrower `@forEach`/
    `@forEachIndexed`-only allowance** after the own-codebase measurement below found a false
    positive on `return@runCatching` in `libs/wrasse-lang/FileWalkUp.kt`: diktat's own hardcoded
    three-name allowlist happens to cover the two most common higher-order functions but does not
    generalize to the actual language rule, so this batch checks "does the label's own name match
    any enclosing `CALL_EXPRESSION`'s own callee name" instead of a fixed list — a strict superset
    of diktat's own three names, never a new false positive relative to it. The `forEach`/
    `forEachIndexed`-shaped-call check (for the separate nesting-count, not the exemption) is
    still name-based, matching diktat's own unresolved heuristic. Ancestor counting rides
    `WContext.ancestors` directly (no hand-rolled stack needed): ordinary upward iteration over the
    label reference's own ancestor chain, counting `FOR`/`WHILE`/`DO_WHILE` and forEach-shaped
    `CALL_EXPRESSION` nodes for the count, and any name-matching `CALL_EXPRESSION` for the
    exemption.
  - **`double-negative`** — **narrowed**: reports a chain of two or more consecutive `!` prefix
    operators (optional whitespace between them), read directly off the outermost
    `PREFIX_EXPRESSION`'s own span text. Verified against kotlinc's actual parser source
    (`KotlinExpressionParsing.parsePrefixExpression`, which calls
    `myBuilder.disableJoiningComplexTokens()` specifically so `!!x` lexes as two separate `EXCL`
    tokens — i.e. two nested `PREFIX_EXPRESSION`s — rather than one `EXCLEXCL` token, confirmed
    against detekt's own `DoubleNegativeExpressionSpec` test corpus, e.g. `!!b`/`!!!b`) — so a plain
    consecutive-`!`-character scan from the outermost prefix's own start offset is exact, not
    approximate. The `.not()`/`not()` qualified-call forms detekt's own rule also recognizes are
    dropped: distinguishing a genuine `Boolean.not()` from a user's own identically-named function
    needs resolution, which is exactly why detekt's own current rule declares
    `RequiresAnalysisApi` for itself.
  - **`variable-naming`** needed no new code: `property-naming`'s existing `WNodeType.PROPERTY`
    target has no ancestor restriction beyond its own top-level/object-member-`val` exemptions, so
    it already reaches a local variable inside a function body — confirmed by temporarily adding a
    local-variable fixture to `property-naming`'s own suite, observing it pass, then removing it
    (never landed as a permanent fixture; the behavior itself already has full permanent coverage
    via `property-naming`'s own member/local/top-level fixture set). B.1's own stated deferral
    reason (a "different scope-resolution shape than member/top-level declarations") does not hold
    against the shipped code; this batch's own contribution is documenting the correction, not new
    behavior.
  - **`constructor-parameter-naming`** requires lowerCamelCase for a primary or secondary
    constructor's own value parameters (not a plain function's), exempt on `override` and on a
    backtick-wrapped keyword (the latter an addition over detekt's own rule, matching every other
    naming id in this project's own convention — strictly narrower, never a new false positive).
    detekt's own `excludeClassPattern` config knob has no wrasse equivalent (default value never
    excludes anything anyway, so this is a no-behavior-change omission, not a narrowing). A regular
    function's own parameter naming remains out of this batch's scope (`function-parameter-naming`/
    `lambda-parameter-naming`, still deferred, not requested here).
  - **`variable-name-max-length`** reports a property/variable name over 64 characters (detekt's
    sole default), exempt only on `override` (matching detekt's own `VariableMaxLength` exactly —
    no `isSingleUnderscore` carve-out the way `VariableNaming`/`VariableMinLength` have, since a
    length check has no reason to special-case a one-character name). Targets the identical
    `PROPERTY` population `property-naming` already visits — a different axis (length, not casing),
    the same coexistence already established between `function-naming` and
    `function-name-max-length`/`function-name-min-length` (B.4).

  Fixture coverage: 113 fixtures across the 18 new rule directories (error/threshold cases,
  every documented exemption, `@Suppress` happy/negative pairs per rule, plus a nested-loop-
  independence case per counting rule demonstrating the never-merge-upward policy). One documented
  exception to the usual `@Suppress` happy/negative pair: `empty-kotlin-file` has no wrong-id
  negative fixture, because *any* file annotation (right id or wrong) already disqualifies the file
  from being considered empty by the algorithm itself, before suppression is even consulted — there
  is no constructible "annotation present, still reported" case. Another: `variable-name-max-length`
  has no override-exemption fixture — an overriding declaration's name is, by Kotlin's own rules,
  identical in length to the declaration it overrides, so a self-contained fixture proving "long
  override name, no report" is not constructible without pulling in an external (Java/bytecode)
  supertype outside this module's own lint scope; the override check itself is still implemented and
  covered indirectly by the equivalent exemption already fixture-tested for
  `constructor-parameter-naming`. Unit specs: one Decision spec per rule (`EmptyBlockCheckSpec`,
  `UnconditionalJumpDecisionSpec`, `LoopWithTooManyJumpStatementsDecisionSpec`,
  `CustomLabelDecisionSpec`, `DoubleNegativeDecisionSpec`, `ConstructorParameterNamingDecisionSpec`,
  `VariableNameMaxLengthDecisionSpec`) plus `LoopJumpFrameSpec` for the shared counting accumulator.

- **B.8 — lint-only rules (bucket L), sixth installment, plus the remaining-L completeness
  inventory — shipped 2026-07-22.** Eight new ids: `magic-number`, `function-parameter-naming`,
  `lambda-parameter-naming`, `global-coroutine-usage`, `throwing-exception-in-main`,
  `invalid-range`, `missing-package-declaration`, `unnecessary-part-of-binary-expression`. Report,
  never fix — `canAutofix` is false everywhere in this batch.

  **Remaining-L inventory (the task's own required deliverable):** every ktlint/detekt/diktat L-
  bucket row from autoformat-scope.md was cross-checked against `registeredRules()`/
  `registeredRuleGroups()` in `WrasseKotlincPluginMain.kt` — the actual shipped-id list, not the
  prose in B.1–B.7 alone (which turned out to already have one gap: `unnecessary-inheritance` is
  shipped, described in a B.2-adjacent paragraph rather than its own L-bucket entry, and was
  nearly re-shipped by this batch before the registration file caught it). ktlint/detekt's own
  current sources were read directly for several rows the catalog doesn't carry at all
  (`MagicNumber`, `UseRequire`/`UseRequireNotNull`/`UseCheckNotNull`/`UseCheckOrError`,
  `SpreadOperator`, `UnnecessaryLet` — all real detekt-rules-style/performance classes missing from
  `detekt-rules-catalog.md`, the same stale-snapshot pattern B.7 already found for
  `DoubleNegativeLambda`/`ConstructorParameterNaming`). Headline count: **29 genuinely-remaining,
  syntax-only-feasible L candidates** identified across the three catalogs (yes/narrowed
  feasibility, not already shipped or deduped, not blocked by a resolution or config-shape need) —
  8 shipped this batch, 21 held for a later installment. A larger set of rows were checked and
  found **not** portable at all (needs resolution, needs a config surface with no sensible
  default, needs project-structure/build knowledge this rule layer doesn't have, or is already
  covered/deduped by a shipped id) — the full triage table below records every row, shipped and
  skipped alike, with its reason.

  **Triage table** (✓ = shipped this batch; every "skip" reason was checked against that rule's
  actual current source, not assumed):

  | candidate | catalog | concern | syntax-only feasible? | outcome |
  |---|---|---|---|---|
  | `magic-number` | detekt (sole catalog; diktat's own `MagicNumberRule` dedupes to it, see below) | numeric literal not declared as a named constant | yes — no `RequiresAnalysisApi` | ✓ shipped |
  | `function-parameter-naming` | detekt | casing on a plain function's own value parameters | yes — pure PSI | ✓ shipped (closes a B.1 deferral) |
  | `lambda-parameter-naming` | detekt | casing on a lambda's own parameters (destructured included) | yes — pure PSI | ✓ shipped (closes a B.1 deferral) |
  | `global-coroutine-usage` | detekt | `GlobalScope.launch`/`.async` usage | yes — pure PSI, no `analyze` call at all | ✓ shipped |
  | `throwing-exception-in-main` | detekt | `throw` anywhere in a top-level `main`'s own subtree | yes — pure PSI (`isMainFunction()` is a syntactic signature check) | ✓ shipped |
  | `invalid-range` | detekt | literal-bounded range that can never iterate (`2..1`) | yes, narrowed — only a bare-literal-vs-bare-literal comparison, matching upstream's own identical cast-based restriction | ✓ shipped |
  | `missing-package-declaration` | detekt | file with no `package` statement | yes — pure PSI (`packageDirective?.text.isNullOrBlank()`) | ✓ shipped (inactive by default upstream, but sole-catalog, no config surface, no overlap — safe despite that) |
  | `unnecessary-part-of-binary-expression` | detekt | duplicate operand in an `&&`/`\|\|` chain | yes — upstream's own check is a text-based, non-overlapping-flatten comparison, no resolution | ✓ shipped |
  | `use-require`/`use-require-not-null`/`use-check-not-null`/`use-check-or-error` | detekt (missing from the catalog entirely) | prefer `require`/`check`/`error`/`requireNotNull`/`checkNotNull` over manually throwing `IllegalArgumentException`/`IllegalStateException` | **no** — all four declare `RequiresAnalysisApi` today (resolving the thrown type, or resolving that the called `require`/`check` really is `kotlin.require`/`kotlin.check` and not a same-named user function) | **skipped**, same precedent as `unused-private-member` (B.5) |
  | `spread-operator` | detekt (missing from the catalog) | spread-operator array-copy cost | **no** — `RequiresAnalysisApi`; the one syntactically-visible exemption (`*arrayOf(...)` literal argument) is not the only one upstream grants (a vararg-forwarding case needs symbol resolution too), so a syntax-only port would report cases upstream's own resolution-backed version would not | **skipped** |
  | `unnecessary-let` | detekt (missing from the catalog) | redundant `.let { }` call | **no** — `RequiresAnalysisApi` (`analyze` used for parameter-reference counting and resolving that the callee really is `kotlin.let`) | **skipped** |
  | `redundant-visibility` on interface members | — | — | already shipped | **already covered** — `redundant-visibility-modifier`/`ModifierEngine` (T-bucket, B.2), not L; not re-portable here |
  | `no-wildcard-imports`-adjacent | — | — | already shipped | **already covered** — `ImportEngine` (S-bucket, B.3) |
  | ktlint `kdoc` (misplaced KDoc) | ktlint | KDoc not immediately before its declaration | yes | portable, **held for a later batch** |
  | ktlint `lambda-return` | ktlint | explicit labeled `return` as a trailing lambda's last statement | yes | portable, **held for a later batch** |
  | ktlint `mixed-condition-operators` | ktlint | `&&`/`\|\|` mixed at the same nesting level without disambiguating parens | yes — pure token scan | portable, **held for a later batch** |
  | ktlint `no-consecutive-comments` | ktlint | stacked `//` comments where one KDoc/block comment reads better | yes | portable, **held for a later batch** |
  | ktlint `no-single-line-block-comment` | ktlint | a `/* ... */` that fits on one line should be `//` | yes | portable, **held for a later batch** |
  | ktlint `string-template` (`.toString()` redundancy) | ktlint | `"${x.toString()}"` inside a template | yes | portable, **held for a later batch** |
  | ktlint `type-argument-comment`/`type-parameter-comment`/`value-argument-comment`/`value-parameter-comment` | ktlint | comment in a disallowed position inside a type/value argument or parameter list | yes, and naturally fuseable into one engine (the `EmptyBlockEngine` precedent) | portable, **held for a later batch** |
  | ktlint `no-empty-file` | ktlint | zero-content file | already covered | **dedupe, not a skip** — identical concept to shipped `empty-kotlin-file` (detekt `EmptyKotlinFile`, B.7); the catalog listed both sides of the same dedupe separately |
  | ktlint `function-expression-body` | ktlint | prefer expression-body over a single-`return`-statement block body | yes, narrowed (report-only "prefer expression body", no rewrite) | portable but judgment-flavored; **held for a later batch** |
  | detekt `unnecessary-inheritance` | detekt | redundant `: Any()`/`: Object()` | already covered | **already shipped**, undocumented gap in the B.1–B.7 prose closed by this batch's own registration-file audit |
  | detekt `function-parameter-naming`/`lambda-parameter-naming` siblings | detekt | — | — | ✓ shipped this batch (see above) |
  | detekt `kdoc-references-non-public-property` | detekt | KDoc `@property` tag naming a non-public property | yes, syntax-only (same shape as shipped `kdoc-tag-mismatch`) | portable, **held for a later batch** |
  | detekt `no-name-shadowing` | detekt | a name reintroduced in a nested scope | unresolved (hard call #6, still open) | **held — needs the "does kotlinc's own extra-checkers tier already cover this" verification `unused-parameter` (B.5) did for its own hard call, not yet done for this one** |
  | detekt `outdated-documentation` | detekt | KDoc drifted from the signature it documents | overlaps `kdoc-tag-mismatch`'s own coverage; the rest is prose-drift judgment | **skipped** — not a distinct, narrowly-portable concept once the tag-mismatch slice is already carved out |
  | detekt `cognitive-complex-method` | detekt | second complexity metric | already decided | **skipped**, already documented in B.4 (overlaps `cyclomatic-complexity`) |
  | detekt `labeled-expression` | detekt | any custom label present | already covered | **dedupe** — the same concept `custom-label` (B.7, diktat) already ships under; shipping detekt's own variant too would be a second id for one concept |
  | detekt `global-coroutine-usage` sibling checks | — | — | — | ✓ shipped (see above) |
  | detekt `throwing-exception-in-main` | — | — | — | ✓ shipped (see above) |
  | detekt `forbidden-public-data-class`/`forbidden-class-name`/`forbidden-suppress` | detekt | annotation/name/rule-id blocklist | config-shaped, no sensible hardcoded default (same reasoning `forbidden-suppress` was already skipped for in B.5) | **skipped**, all three |
  | detekt `library-entities-should-not-be-public` | detekt | library-API-surface policy | needs a "library module" concept this rule layer doesn't have | **skipped** |
  | detekt `array-primitive`/`for-each-on-range`/`last-index`/`useless-supertype`/`inverse-method` | detekt/diktat | boxing/receiver-type/supertype-member questions | all need resolution to avoid false positives | **skipped**, all five |
  | detekt `invalid-package-declaration` | detekt | package statement doesn't match directory structure | needs source-root/directory convention knowledge, a different axis than syntax | **skipped** |
  | detekt `useless-postfix-expression` | detekt | postfix `++`/`--` whose value is provably discarded | yes, pure PSI, but needs a correctly-scoped (not detekt's own flat, order-fragile) per-class property-name set plus per-return/binary-expression correlation — meaningfully more machinery than this batch's other seven | portable, **held for a later batch** on complexity grounds, not a feasibility gap |
  | diktat `debug-print` | diktat | bare `print`/`println`/`console.*` call | yes, pure PSI, same family as shipped `print-stack-trace` (B.6) | portable, **held for a later batch** |
  | diktat `collapse-if` | diktat | nested `if` with no `else` at either level, mergeable | yes, report-only | portable, **held for a later batch** |
  | diktat `extension-functions-same-name` | diktat | two unrelated extension functions with an identical signature on related classes | yes, whole-file two-pass (same shape as `unused-private-class`'s own whole-file scan), **though its own catalog description ("member-shadows-extension confusion") does not match what the rule's actual source does** — another stale-catalog-description finding | portable, **held for a later batch** |
  | diktat `getter-setter-fields` | diktat | accessor that recurses on itself instead of using `field` | plausible pure-PSI bug catcher, **current diktat source for the exact row this catalog name refers to was not re-derived in this session** (only `CustomGetterSetterRule`, a different, already-`X`-classified rule, was found under this area) | **verification incomplete — do not assume portable; re-derive from source before a future batch scopes it** |
  | diktat `sync-in-async` | diktat | `runBlocking` reached from inside a coroutine | yes, narrowed, ancestor-based (same idiom `custom-label`'s own ancestor counting already established) | portable, **held for a later batch** |
  | diktat `when-must-have-else`/`string-concatenation`/`boolean-expressions` | diktat | statement-`when` without `else`; `+`-chained string building; boolean-algebra simplification (including whether this is actually where `== true`/`== false` lives, correcting B.7's "not present in any of the three catalogs" note for that shape) | plausible, **not re-derived from current diktat source this session** | **verification incomplete — re-derive before scoping** |
  | every other diktat row already marked judgment/ABI/refactoring-shaped in autoformat-scope.md (`class-like-structures`, `data-classes`, `inline-classes`, `single-constructor`, `single-init`, `stateless-class`, `compact-initialization`, `overloading-default-values`, `lambda-parameter-order`, `type-alias`, `variable-generic-type`, `no-var-rule`, `null-checks`, `local-variables`, `nullable-type`, `comments` (commented-out code)) | diktat | — | judgment-shaped or heuristic-risky by the catalog's own description | **skipped**, matches this project's own established "judgment-shaped rewrites stay L-or-nothing, never guessed" stance; not re-verified individually this session since none looked like a plausible reclassification |

  Per-rule semantics, exemptions, and provenance:

  - **`magic-number`** — an `INTEGER_CONSTANT`/`FLOAT_CONSTANT` whose parsed value (see
    `NumericLiteralValue`, mirroring the sole catalog's own suffix/underscore/radix-prefix parsing
    exactly) is not one of `{-1, 0, 1, 2}` is reported, unless it sits in one of six structural
    exemptions: nested (at any depth) inside a property's own initializer — member, top-level,
    local, `const`, or companion, all folded into one check since detekt's own defaults exempt
    every one of them; a parameter's own default value; a named call argument; a `hashCode`
    function's own body; the receiver of a dot-qualified call (`5.toString()`); or a function's own
    bare-literal return value (an expression body, or a block body's `return <literal>` — narrowed
    to not require "sole statement in the block" the way the upstream check does, a documented
    simplification that only lowers the report count, matching this project's own may-be-constant
    precedent for accepting a narrower, single-pass-friendly shape). **Catalog disagreement**:
    diktat ships its own `MagicNumberRule` with several defaults that diverge from detekt's — it
    does *not* exempt property/local-variable declarations or an extension-function's own receiver
    by default, and it always checks named arguments (no such exemption axis at all) — on every
    one of these points detekt's own default is the more permissive (fewer-report) reading, so this
    id adopts detekt's algorithm and defaults wholesale, per the task's own narrowest-reading
    instruction; diktat's differently-shaped ignore-number list (explicit suffixed-string forms
    like `1UL`) is subsumed anyway by detekt's own normalize-then-compare-as-`Double` approach. Two
    of detekt's own unconditional exemptions (a parameter's own default value; a bare-literal
    function return) are ported as documented structural simplifications rather than detekt's own
    exact PSI-parent checks, both strictly narrower. Implemented as a `WStreamRule` (not a
    `WNodeRule` targeting the constant types directly, and not a leaf rule): kotlinc's LightTree
    wraps the literal token inside an interior `INTEGER_CONSTANT`/`FLOAT_CONSTANT` node, so the
    check fires from `enterNode`, and since `WStreamRule.enterNode` carries no reporter, every
    candidate is stashed and decided together in `afterFile` — this exact wiring mistake (routing
    the check through `visitLeaf`, which the constant node type never reaches, silently producing
    zero reports for every fixture) was caught by the fixture harness during this batch's own
    development, not a live risk in the shipped code. A hand-rolled `FunFrame` stack (`hasOverride`,
    `paramCount`, `name`, populated by watching `KW_OVERRIDE`/`IDENTIFIER`/`VALUE_PARAMETER` leaves
    against the innermost open frame) answers the `hashCode`-function exemption without the
    double-firing risk a self-nestable `WNodeRule` target would carry — the same reasoning
    `FunctionMetricsEngine`/`ThrowingExceptionInMainRule` already documented.
  - **`function-parameter-naming`** — a plain function's own direct value parameter (never a
    primary/secondary constructor's, `constructor-parameter-naming`'s exclusive territory) must be
    lowerCamelCase, exempt on `override` (the enclosing function's own fact, not the parameter's)
    and a backtick-wrapped keyword. Closes a deferral B.1 explicitly logged ("a different scope-
    resolution shape than member/top-level declarations, held for a follow-up batch").
  - **`lambda-parameter-naming`** — a lambda's own parameter (plain or, uniquely among this
    project's naming ids, each individual entry of a destructured one, `{ (a, b) -> } `) must be
    lowerCamelCase or a lone `_`; no override concept applies since lambdas cannot be overridden.
    Also closes a B.1 deferral.
  - **`global-coroutine-usage`** — a dot-qualified expression whose receiver's own text is exactly
    `GlobalScope` and whose selector call begins with `launch` or `async` is reported at the whole
    expression's span — matches upstream exactly (its own check is equally receiver-text/callee-
    name based, no resolution).
  - **`throwing-exception-in-main`** — a top-level, non-override, public `fun main` with 0 or 1
    parameters (Kotlin's only legal entry-point shapes) containing a `throw` anywhere in its own
    subtree (nested local functions/lambdas included) is reported at the function's own span.
    Narrowed to the common top-level case only: upstream's own second recognized shape (a
    `@JvmStatic`-annotated `main` inside an object) is not ported, a documented, strictly-narrower
    omission. The parameter-count check accepts any 0-or-1-arity `main`, not upstream's own exact
    `Array<String>`/`vararg String` type-text match — a narrow, documented widening whose only
    realistic false-positive shape (`fun main(x: Int)`, a legal-but-not-really-an-entry-point
    signature) is vanishingly rare in practice.
  - **`invalid-range`** — a `..`/`downTo`/`until`/`..<` binary expression whose left and right
    operands are both direct `INTEGER_CONSTANT` children is reported when the bounds can never
    produce an iteration, mirroring the sole catalog's own per-operator arithmetic exactly. A
    unary-minus-wrapped literal (`-1..1`) is never a candidate, matching upstream's own identical
    gap (its cast to a bare constant expression fails the same way) — not a narrowing, an exact
    match.
  - **`missing-package-declaration`** — a file whose `PACKAGE_DIRECTIVE` carries no dotted name
    (checked via the same `DOT_QUALIFIED_EXPRESSION`/`REFERENCE_EXPRESSION`-child test
    `PackageNamingRule` already uses to tell "has a package" from "does not") is reported at offset
    0. Inactive by default upstream, but sole-catalog, config-free, and non-overlapping — shipped
    anyway per this project's own "every rule ships off by default regardless of upstream's own
    default" stance (D6).
  - **`unnecessary-part-of-binary-expression`** — a chain of `&&`/`||` operands containing a
    whitespace-insensitive duplicate is reported at the whole chain's span, matching upstream's own
    crude text-comparison exactly (`a` and `(a)` count as different operands, same as upstream).
    Each `&&`/`||` `BINARY_EXPRESSION` stores its own flattened operand list at its own exit, keyed
    by its own offsets; a same-operator direct child's own list is merged in and removed from the
    map (never independently reported); a parenthesized sub-expression is never itself
    `BINARY_EXPRESSION`-typed, so it is always treated as one opaque operand, never flattened
    through — the same boundary upstream's own recursive descent respects. Whatever survives
    unconsumed in the map by `afterFile` is each chain's own true outermost node, decided and
    reported there.

  Fixture coverage: 61 fixtures across the 8 rule directories (error/threshold/exemption cases,
  `@Suppress` happy/negative pairs per rule; `magic-number` additionally covers a rise-then-fall
  case for its property-depth counter — a magic number after a local property's own scope closes
  is still reported, proving the depth counter actually decrements rather than leaking the
  exemption forward). Unit specs: one Decision spec per rule plus `NumericLiteralValueSpec` for the
  shared suffix/radix parser.

  **Own-codebase measurement** (isolated `rsync` copy, never this repo's own `wrasse.json`;
  `allWarningsAsErrors` disabled in the copy's convention plugin; `publishToMavenLocal` run in the
  copy before `compileKotlin compileTestKotlin -PwrasseCheck --continue`, so the mavenLocal plugin
  jar under test was freshly built from the code this batch actually ships): of the eight new ids,
  seven fire **zero** times anywhere in this repo's own `main`+`test` sources —
  `function-parameter-naming`, `lambda-parameter-naming`, `global-coroutine-usage`,
  `throwing-exception-in-main`, `invalid-range`, `missing-package-declaration`,
  `unnecessary-part-of-binary-expression`. `magic-number` alone fires 319 times, concentrated in
  test sources (kotest specs asserting on raw numeric expected values — 181 in
  `libs/wrasse-rules/src/test`, 51 in `libs/wrasse-lang/src/test`, 47 in `libs/wrasse-model/src/test`,
  11 in `testing/wrasse-kotlinc-plugin-tests-base/src/test`) but with a real, plausibly-fixable tail
  in production code too (15 in `libs/wrasse-rules/src/main`, 8 in `libs/wrasse-lang/src/main`
  — e.g. radix/offset literals in `HexEncoding.kt`/`WPatchReader.kt`/`ConfigValueJsonc.kt` — 2 in
  `libs/wrasse-format/src/main`, 4 in `testing/wrasse-kotlinc-plugin-tests-base/src/main`).
  **Enablement recommendation** (not applied to this repo's own `wrasse.json`, per the task's own
  instruction): the other seven ids are safe to enable at `error` immediately, zero-risk by this
  measurement. `magic-number` is real and correctly-scoped but too noisy for this repo's own test
  suites to enable uniformly today; enable it for `main` source sets only (via a per-rule
  `exclude` glob over `**/src/test/**`), or accept the one-time cost of naming the ~29 production-
  code constants it would flag across the whole repo, before turning it on repo-wide.

- **B.9 — lint-only rules (bucket L), seventh installment, closing the ktlint comment-position
  family plus five more remaining-L candidates — shipped 2026-07-22.** Twelve new ids:
  `kdoc-placement`, `type-argument-comment`, `type-parameter-comment`, `value-argument-comment`,
  `value-parameter-comment` (all five fused into one new `CommentPositionEngine`), `lambda-return`,
  `mixed-condition-operators`, `no-consecutive-comments`, `no-single-line-block-comment`,
  `redundant-to-string-in-template`, `kdoc-references-non-public-property`, and `debug-print`.
  Report, never fix — `canAutofix` is false everywhere in this batch. Every candidate was
  re-verified against the three ground-truth checkouts' own *current* source (not the B.8 prose
  alone) before porting; one genuine semantic gap was found and closed during this batch's own
  mandatory own-codebase measurement (see `lambda-return` below), not left for a future session.

  **Remaining-L inventory, continued:** B.8's own held list bundled two ktlint/diktat catalog rows
  each covering several distinct named concepts under one triage line (`type-argument-comment`/
  `type-parameter-comment`/`value-argument-comment`/`value-parameter-comment` as one row of four;
  `when-must-have-else`/`string-concatenation`/`boolean-expressions` as one row of three) — B.8's
  own headline "21 held" arithmetic only unbundles the first of those two rows, not the second.
  Read at the granularity of each row's own individually-named concepts (the reading this batch's
  own work list needs to track distinct candidates one-for-one), the prior held count was 22, not
  21. Twelve of those 22 ship this batch (enumerated above); **ten remain**, carried forward
  unchanged from B.8 (none re-verified this session, per the time this batch spent on the twelve
  it did ship): ktlint `function-expression-body` (judgment-flavored); detekt `no-name-shadowing`
  (still an open hard call — needs the "does kotlinc's own extra-checkers tier already cover this"
  verification `unused-parameter` (B.5) did for its own hard call); detekt
  `useless-postfix-expression` (held on complexity grounds, not a feasibility gap); diktat
  `collapse-if`, `extension-functions-same-name`, and `sync-in-async` (all three portable,
  simply not yet scoped); diktat `getter-setter-fields` (verification incomplete — the exact
  current-source row this catalog name refers to was still not re-derived this session either);
  and diktat `when-must-have-else`/`string-concatenation`/`boolean-expressions`, each still
  needing its own from-source re-derivation before a future batch scopes it.

  One item is explicitly retired from the inventory rather than carried forward or shipped:
  ktlint's own `string-template` rule bundles two independent concerns — the `.toString()`-
  redundancy slice this batch ships as `redundant-to-string-in-template`, and a second "redundant
  curly braces" slice (`"${x}"` → `"$x"`) that is a rewrite/style decision belonging to the
  opinionated printer (D16, Phase C), not a lint-only concern at all. It is recorded here once,
  explicitly, so it does not silently vanish from the record: it is neither shipped nor held, it
  is out of L-bucket scope entirely.

  **Triage table** (✓ = shipped this batch; every "skip" reason was checked against that rule's
  actual current source):

  | candidate | catalog | concern | syntax-only feasible? | outcome |
  |---|---|---|---|---|
  | `kdoc` | ktlint | where a KDoc may structurally sit (only at a documentable declaration's own first child; a dangling top-level KDoc, or one nested inside any other node kind, is disallowed) | yes — pure position/parent-type check (`ctx.ancestors.peekType()` + `ctx.childIndex`), no resolution | ✓ shipped as `kdoc-placement` |
  | `lambda-return` | ktlint | a labeled `return@label` carrying a value as a lambda's own last statement | yes — pure PSI, narrowed further than upstream (see below) | ✓ shipped |
  | `mixed-condition-operators` | ktlint | `&&`/`\|\|` mixed within one condition without disambiguating parens | yes — pure token/chain scan, same shape as shipped `unnecessary-part-of-binary-expression` | ✓ shipped |
  | `no-consecutive-comments` | ktlint | a comment immediately preceded by another, differently-classed comment | yes — pure leaf-adjacency check; simpler in this project's own model than upstream's own KDOC_START/KDOC_END split, since `KDOC` is already one leaf token here | ✓ shipped |
  | `no-single-line-block-comment` | ktlint | a single-line `/* ... */` with nothing but same-line whitespace after it | yes — pure leaf + forward text scan, no tree needed at all | ✓ shipped |
  | `string-template` (`.toString()` slice only) | ktlint | `"${x.toString()}"` redundancy inside a string template | yes — pure structural check on the template entry's own sole `DOT_QUALIFIED_EXPRESSION` child | ✓ shipped as `redundant-to-string-in-template`; the rule's own "redundant curly braces" slice is explicitly retired (see above), not ported |
  | `type-argument-comment` | ktlint | a non-KDoc comment discouraged inside a type argument list/projection | yes — pure parent-type + adjacency check | ✓ shipped (fused into `CommentPositionEngine`) |
  | `type-parameter-comment` | ktlint | same concern for a type parameter list/parameter | yes | ✓ shipped (fused) |
  | `value-argument-comment` | ktlint | any comment (including KDoc) whose immediate parent is a value argument | yes | ✓ shipped (fused) |
  | `value-parameter-comment` | ktlint | any comment whose immediate parent is a value parameter, except a KDoc that is that parameter's own first child | yes | ✓ shipped (fused) |
  | `kdoc-references-non-public-property` | detekt | a class KDoc linking one of its own non-public member properties | yes, narrowed — a class's own immediate `CLASS_BODY` members only, no nested-object qualified-name traversal (see below) | ✓ shipped |
  | `debug-print` | diktat | a bare `print()`/`println()` or `console.error`/`info`/`log`/`warn()` call | yes — pure structural/textual check, same resolution-free heuristic (argument-count cap) upstream itself relies on | ✓ shipped |
  | `function-expression-body` | ktlint | prefer expression-body over a single-`return` block body | judgment-flavored, not attempted this batch | held |
  | `no-name-shadowing` | detekt | a name reintroduced in a nested scope | unresolved hard call, not attempted this batch | held |
  | `useless-postfix-expression` | detekt | postfix `++`/`--` whose value is provably discarded | portable but meaningfully more machinery than this batch's other candidates, not attempted | held |
  | `collapse-if` | diktat | nested `if` with no `else` at either level, mergeable | portable, not attempted this batch | held |
  | `extension-functions-same-name` | diktat | two unrelated extension functions with an identical signature on related classes | portable, not attempted this batch | held |
  | `getter-setter-fields` | diktat | accessor recursing on itself instead of using `field` | verification incomplete, not attempted this batch | held |
  | `sync-in-async` | diktat | `runBlocking` reached from inside a coroutine | portable, not attempted this batch | held |
  | `when-must-have-else` | diktat | statement-`when` without `else` | verification incomplete, not attempted this batch | held |
  | `string-concatenation` | diktat | `+`-chained string building | verification incomplete, not attempted this batch | held |
  | `boolean-expressions` | diktat | boolean-algebra simplification | verification incomplete, not attempted this batch | held |

  Per-rule semantics, exemptions, and provenance:

  - **`kdoc-placement`** — a `KDOC` leaf whose immediate parent is one of eight documentable
    declaration kinds (`CLASS`, `ENUM_ENTRY`, `FUN`, `OBJECT_DECLARATION`, `PROPERTY`,
    `SECONDARY_CONSTRUCTOR`, `TYPEALIAS`, `VALUE_PARAMETER`) must be that parent's own first child
    (`ctx.childIndex == 0`) or is reported "allowed only at the start of"; a `KDOC` whose parent is
    `FILE` is a dangling top-level KDoc; any other parent reports "not allowed inside". Matches the
    upstream rule this derives from exactly — both rely on the same "leading KDoc is pulled in as
    the following declaration's own first child" kotlinc convention, confirmed against this
    project's own `KdocEngine` precedent, and, separately, against a fixture-harness failure this
    batch hit and fixed: a bare (non-KDoc) comment preceding a *sibling* declaration is **not**
    pulled into that declaration's own span the way a KDoc is (only KDoc gets that special
    treatment), which is exactly why `no-consecutive-comments`' own suppression fixtures had to
    annotate an *enclosing* class rather than the immediately-following declaration (see below).
  - **`type-argument-comment`/`type-parameter-comment`/`value-argument-comment`/
    `value-parameter-comment`** — fused with `kdoc-placement` into `CommentPositionEngine` since
    all five read nothing but a comment leaf's own `WNodeType`, `ctx.ancestors.peekType()`,
    `ctx.childIndex`, and `ctx.prevLeafType`/`prevLeafText` — facts `WContext` already carries for
    every leaf dispatch, so the whole engine is one `WLeafRule`, no buffering anywhere. A comment
    directly inside a `type_projection`/`type_parameter` is always disallowed; one that is a direct
    child of the enclosing `type_argument_list`/`type_parameter_list` itself is allowed only when
    preceded by a newline-containing whitespace (i.e. it sits alone on its own line). A comment
    (any kind) whose parent is `value_argument` is always disallowed. A comment whose parent is
    `value_parameter` is disallowed unless it is a KDoc that is that parameter's own first child —
    the same allowance `kdoc-placement` grants that exact shape, so the two ids never contradict
    each other on it (upstream ships them as independently overlapping ids too, so both firing on
    the same misplaced-KDoc-in-a-parameter case is expected, matching parity, not a bug).
  - **`lambda-return`** — a lambda's own `BLOCK` whose last non-whitespace statement is a labeled
    `return@label` carrying a value, **where `label` names that same immediately-enclosing lambda**
    (not some further-out scope), is reported at the `RETURN`'s own span. The label a lambda
    answers to is resolved from its own immediate ancestry: an explicit `label@ { }` wrapper, or,
    skipping through any `VALUE_ARGUMENT`/`VALUE_ARGUMENT_LIST` wrapping, the callee name of the
    call this lambda is passed to (trailing-lambda or plain-argument form) — the same
    callee-name-as-implicit-label idea `CustomLabelRule` already uses, narrowed to the single
    governing call/label rather than any enclosing one. **This label-matching check is a
    deliberate narrowing beyond the upstream rule this derives from**, whose own check fires on any
    labeled+valued return in tail position regardless of what the label actually names — this
    batch's own mandatory own-codebase measurement (below) caught the gap live: `by lazy { ...
    .let { return@lazy it } ... }` in `WrasseTestHarness.kt` has `return@lazy it` as the `let`
    lambda's own last statement, but `@lazy` is a genuine non-local exit from the *outer* `lazy`
    block, not a redundant label on the `let` lambda itself; removing it would silently change
    which scope the return exits. Ported and fixed before shipping, not left as a known gap — a
    dedicated `clean-outer-label.kt` fixture now guards this exact shape permanently.
  - **`mixed-condition-operators`** — a maximal chain of directly-nested `&&`/`\|\|`
    `BINARY_EXPRESSION`s using both operators somewhere in the chain is reported once, at the
    chain's own outermost span, via the same offset-keyed chain-merge shape
    `unnecessary-part-of-binary-expression` already established (B.8) — except every logical child
    is merged in regardless of whether its own operator matches (mixing *is* the different-operator
    case), where the sibling rule only merges same-operator children. A parenthesized
    sub-expression is never itself a recorded chain entry, so it is always an opaque, un-flattened
    operand — confirmed to be the same boundary the upstream rule's own recursive `.parent` walk
    respects (a `PARENTHESIZED` node's own `elementType` breaks that walk's `BINARY_EXPRESSION`
    chain identically). **Deliberately narrower than upstream**: the upstream implementation's own
    visitor fires independently from *every* mismatched nested node and can emit more than one
    finding at the same outer offset for one condition; this port decides and reports each chain
    exactly once, a documented, safer simplification.
  - **`no-consecutive-comments`** — a comment leaf (KDoc/block/EOL) whose nearest preceding
    non-whitespace leaf is also a comment is reported, tracked via a running
    last-significant-leaf-type `WStreamRule` (the same shape `NoSemicolonsRule` already
    establishes). Consecutive EOL comments are always allowed; a KDoc or a block comment preceding
    another comment is disallowed even across a blank line; any other mismatched pair is allowed
    only when separated by a blank line (more than one newline in the intervening whitespace, read
    directly off `ctx.prevLeafText` since the immediately-preceding leaf is always that whitespace
    run). This project's own single-token `KDOC` leaf makes the port simpler than upstream, which
    needs its own separate `KDOC_START`/`KDOC_END` tracking to get the same facts.
  - **`no-single-line-block-comment`** — a `BLOCK_COMMENT` leaf containing no `\n` with nothing but
    same-line spaces/tabs before either end-of-file or a newline is reported. Pure leaf-level text
    scan (`ctx.sourceText` forward from the comment's own `endOffset`), no tree access at all —
    the simplest rule in this batch.
  - **`redundant-to-string-in-template`** — a `${...}` string-template entry (`LONG_STRING_TEMPLATE_ENTRY`)
    whose sole content is one `DOT_QUALIFIED_EXPRESSION` of the exact literal shape
    `<receiver>.toString()` (a `CALL_EXPRESSION` selector whose own span-text equals `toString()`
    exactly — no arguments, no internal whitespace, matching the upstream rule this derives from's
    own equally literal text comparison) is reported at the whole expression's span; `super.toString()`
    is exempt (there is no bare `$super` shorthand). Only this slice of upstream's own bundled
    `string-template` rule is ported — see the retirement note above for the other slice.
  - **`kdoc-references-non-public-property`** — a class's own KDoc that links (`[name]`, never
    `[name][target]` — a real link with custom display text, not a same-name reference) one of
    that class's own direct `CLASS_BODY` member properties, when that member is `private` or
    `internal`, is reported at the property's own name span. **Narrowed to a class's own immediate
    `CLASS_BODY` members only**: a primary constructor's `val`/`var` parameters are naturally
    excluded (they are `PRIMARY_CONSTRUCTOR`'s own children, never `CLASS_BODY`'s — the same
    exclusion upstream's own logic applies, but reached here for free rather than by an explicit
    "is this a constructor parameter" check), and a nested class's/object's own properties reached
    through upstream's own qualified (`Outer.inner`) KDoc-link matching are never traversed at all
    — a documented, strictly narrower reading than upstream's own nested-`KtObjectDeclaration`
    qualified-name walk. Each `PROPERTY`'s own name/visibility facts, its own `CLASS_BODY`'s
    consumption of them, and that body's own consumption by its `CLASS`, are three levels of the
    same offset-correlation shape `KdocEngine` already establishes for parameter/constructor facts.
  - **`debug-print`** — a bare (unqualified — `someObj.print()` excluded by the call's own
    `childIndex` position inside its enclosing `DOT_QUALIFIED_EXPRESSION`, if any), zero/one-argument,
    non-trailing-lambda `print()`/`println()` call is reported at the whole call's own span, and so
    is a `console.error()`/`console.info()`/`console.log()`/`console.warn()` call on a bare
    `console` receiver (Kotlin/JS interop), matching the upstream rule this derives from's own two
    checks. The argument-count cap is upstream's own resolution-free proxy for "probably the real
    stdlib function, not a same-named user overload with more parameters", carried over unchanged;
    a `VALUE_ARGUMENT_LIST`'s own argument count is recorded and unconditionally consumed by its
    own enclosing `CALL_EXPRESSION` regardless of that call's own callee name, keeping the pending
    map bounded by call-nesting depth rather than the file's total call count.

  Fixture coverage: 72 fixtures across the 12 rule directories (error/clean/every-exemption,
  `@Suppress` happy/negative pairs per rule); `lambda-return` additionally covers a nested
  same-batch-of-labels case (proving the per-block pending-return sweep never cross-contaminates
  across nesting depth) and the `clean-outer-label.kt` regression fixture for the cross-scope-label
  exemption found during this batch's own measurement. Unit specs: one Decision spec per rule
  (`KdocPlacementDecisionSpec`, `TypeArgumentCommentDecisionSpec`, `TypeParameterCommentDecisionSpec`,
  `ValueArgumentCommentDecisionSpec`, `ValueParameterCommentDecisionSpec`, `LambdaReturnDecisionSpec`,
  `MixedConditionOperatorsDecisionSpec`, `NoConsecutiveCommentsDecisionSpec`,
  `NoSingleLineBlockCommentDecisionSpec`, `RedundantToStringInTemplateDecisionSpec`,
  `KdocReferencesNonPublicPropertyDecisionSpec`, `DebugPrintDecisionSpec`).

  **Own-codebase measurement** (isolated `rsync` copy, never this repo's own `wrasse.json`;
  `allWarningsAsErrors` disabled in the copy's convention plugin; `publishToMavenLocal` run in the
  copy before `compileKotlin compileTestKotlin -PwrasseCheck --continue`, so the mavenLocal plugin
  jar under test was freshly built from the code this batch actually ships): of the twelve new
  ids, ten fire **zero** times anywhere in this repo's own `main`+`test` sources —
  `kdoc-placement`, `type-argument-comment`, `type-parameter-comment`, `value-argument-comment`,
  `value-parameter-comment`, `mixed-condition-operators`, `no-consecutive-comments`,
  `no-single-line-block-comment`, `redundant-to-string-in-template`,
  `kdoc-references-non-public-property`. `lambda-return` fired once before this batch's own
  `lambda-return`-vs-`ownLambdaLabel` narrowing (the `WrasseTestHarness.kt` case described above)
  and fires zero times after it — confirmed by re-measuring after the fix, not merely asserted.
  `debug-print` fires twice, both in `libs/wrasse-lang/src/main/kotlin/com/varlanv/wrasse/lang/WPatchApplier.kt`
  (lines 109 and 112): `println("Fixed: ...")`/`println("Skipped: ...")` inside that file's own
  `fun main` — genuine, intentional CLI stdout for the `wrasseApply` command-line entry point, not
  leftover debug output, but a true positive by this rule's own literal, resolution-free
  definition (upstream's own rule has no `fun main`/CLI-entry-point exemption either, so this
  matches upstream fidelity, not a bug in the port). **Enablement recommendation** (not applied to
  this repo's own `wrasse.json`, per the task's own instruction): all eleven other ids are
  zero-risk to enable at `error` immediately. `debug-print` is correctly scoped but would need
  either a one-line `@Suppress("debug-print")` on `WPatchApplier.kt`'s own `main` or a per-rule
  `exclude` for that one file before enabling it repo-wide; enabling it everywhere else today is
  already zero-risk.

  **Updated remaining-portable-L count: 10** (see the remaining-L inventory note above for the
  full list and the recount that produced this number).

- **B.10 — lint-only rules (bucket L), eighth installment, closing B.9's held-10 list — shipped
  2026-07-22.** Nine new ids: `function-expression-body`, `useless-postfix-expression`,
  `collapse-if`, `extension-functions-same-name`, `getter-setter-fields`, `sync-in-async`,
  `when-must-have-else`, `string-concatenation`, `boolean-expressions`. Report, never fix —
  `canAutofix` is false everywhere in this batch. Every one of B.9's held-10 was re-read against
  the three ground-truth checkouts' own *current* source this session (not the B.9 prose): two
  renamed since the catalog rows were first written (diktat's `getter-setter-fields` NAME_ID is now
  `PropertyAccessorFields.NAME_ID`/`getter-setter-fields`, unchanged; its own class file is
  `PropertyAccessorFields.kt`, not `CustomGetterSetterRule.kt` — B.9's "verification incomplete"
  note is resolved, the two are genuinely distinct rules, confirmed by reading both current
  sources; diktat's `when-must-have-else` candidate is now shipped internally under
  `WhenMustHaveElseRule.kt`'s own `NAME_ID = "no-else-in-when"` — same concept, this project keeps
  its own tracking name for continuity across three batches' worth of held-list prose, not
  upstream's current literal id).

  **Triage table** (✓ = shipped this batch; every "skip"/"held" reason was checked against that
  rule's actual current source, not carried over from B.9's prose unread):

  | candidate | catalog | concern | syntax-only feasible? | outcome |
  |---|---|---|---|---|
  | `function-expression-body` | ktlint | prefer expression body over a block containing only one `return`/`throw` | yes — pure structural child-count check (`FunctionExpressionBodyRule`), the exact same shape this project's own `containingOnly`-style rules already use; B.9's "judgment-flavored, not attempted" framing was about time budget, not a feasibility gap — re-read this session, no analysis-API dependency anywhere in the upstream rule | ✓ shipped |
  | `no-name-shadowing` | detekt | a name reintroduced in a nested scope | **no** — `NoNameShadowing` implements `RequiresAnalysisApi`; its own explicit-name-shadowing checks (property/destructuring/parameter vs. an enclosing function/lambda/primary-constructor parameter) are genuinely syntax-only, but its most consequential case — a lambda's own implicit `it` shadowing an outer lambda's own implicit `it` — calls `hasImplicitParameter()`/`hasImplicitParameterReference()`, both of which `analyze(this) { functionLiteral.symbol.valueParameters... }`: real type inference is required to know whether a given lambda literal actually binds an implicit `it` at all (that depends on the target functional type's arity at the call site, not on anything in the lambda's own syntax) | **permanently skipped** (resolution-dependent) |
  | `useless-postfix-expression` | detekt | postfix `++`/`--` whose value is provably discarded | yes — `UselessPostfixExpression` has no `RequiresAnalysisApi`, no `analyze()` call anywhere; B.9's "held on complexity grounds" was accurate for the rule's own `return i++` detection path (gated by a local/class-property-name heuristic that needs whole-function property-name collection independent of source order — see below) but not for its assignment/comparison self-reference path, which is plain PSI-sibling text comparison | ✓ shipped, narrowed (see below) |
  | `collapse-if` | diktat | nested `if` with no `else` at either level, mergeable | yes — `CollapseIfStatementsRule` is pure structural AST matching (allowed-surrounding-node-types check, comment-tolerant), no resolution anywhere | ✓ shipped |
  | `extension-functions-same-name` | diktat | two unrelated extension functions with an identical signature on related classes | yes — `ExtensionFunctionsSameNameRule` matches by parameter *names* (never types) and by textual supertype-list membership within one file, explicitly resolution-free by its own design (its own code comment: "Fixme: should find all related classes in project, not only in file" — a scope limitation this project inherits for free, since it also sees one file at a time) | ✓ shipped |
  | `getter-setter-fields` | diktat | accessor recursing on its own property's name instead of `field` | yes — `PropertyAccessorFields` is pure PSI structural/text matching (first same-named reference, "am I inside a `DOT_QUALIFIED_EXPRESSION`" check, a local-shadow position check via `isGoingAfter`); the rule's own KDoc even flags its shadow-check as a `// fixme should use shadow-check when it will be done` — an *upstream-acknowledged* heuristic, not a resolution dependency | ✓ shipped, narrowed (see below) |
  | `sync-in-async` | diktat | `runBlocking` reached from inside `async`/`launch`/a `suspend` function | yes — `AsyncAndSyncRule` is a pure textual match on the callee name of the nearest ancestor call (`"async"`/`"launch"`) or a `suspend` modifier on the nearest ancestor function — no resolution of which coroutine builder those names actually refer to | ✓ shipped |
  | `when-must-have-else` | diktat | statement-`when` without `else`, unless the subject is enum/sealed-shaped | yes — `WhenMustHaveElseRule`'s (upstream `NAME_ID = "no-else-in-when"`) own "is this enum-only" check is itself a syntactic shape heuristic (dot-qualified/bare-reference conditions), never a real check of whether the subject's declared type is actually an enum or sealed class — genuinely resolution-free by upstream's own design, not merely under-verified | ✓ shipped, narrowed (see below) |
  | `string-concatenation` | diktat | `+`-chained string building starting from a literal | yes — `StringConcatenationRule`'s own detection is "is the chain's leftmost operand textually a string-template literal (or a `.toString()`-suffixed call)", a lexical fact needing no resolution: a quoted string literal's type is `String` by grammar alone | ✓ shipped |
  | `boolean-expressions` | diktat | boolean-algebra simplification | yes, but **out of proportion** — `BooleanExpressionsRule` embeds the third-party `jbool_expressions` library to run De Morgan's laws, the distributive law, and arbitrary-depth chain flattening; every one of its own atoms is compared by raw text (`textWithoutComments()`), so the *whole* rule is genuinely resolution-free, but re-implementing (or vendoring) a general propositional-logic simplifier is a different order of engineering than any of the ~140 rules this project has shipped to date, and two of its own component laws already ship independently under other ids (see the dedupe note below) | ✓ **shipped narrowed** — only the two laws not already covered by another id (literal absorption, direct complement) |

  **Dedupe/overlap map** (concepts `boolean-expressions` would otherwise re-detect under a second
  id, so deliberately excluded from its own narrowed scope):

  | law | already shipped as | provenance |
  |---|---|---|
  | idempotent duplicate operand (`a && a`) | `unnecessary-part-of-binary-expression` (B.8) | detekt `UnnecessaryPartOfBinaryExpression`, independent of diktat's `boolean-expressions` |
  | double negation (`!!a`) | `double-negative` (B.7) | detekt `DoubleNegativeExpression`, independent of diktat's `boolean-expressions` |

  Neither of the two ids above derives from diktat's `BooleanExpressionsRule` — both are
  independently-sourced detekt rules that happen to cover two of the same propositional laws jbool
  also implements — so this is a *conceptual* overlap, not a provenance one; recorded here so the
  two laws don't silently reappear as "gaps" in a future batch's own re-audit of diktat's rule.

  Per-rule semantics, narrowing, and provenance:

  - **`function-expression-body`** — a function's own `BLOCK` body (only ever inspected when its
    immediate parent is `FUN`) whose only content, ignoring `{`/`}`/whitespace, is a single
    `RETURN` or `THROW` is reported at the block's own span; a `RETURN` case is additionally
    dropped when the block contains more than one `return` keyword anywhere inside it (a nested
    `return` — e.g. inside an `if`/`else` value — would change meaning if hoisted to `=`), counted
    via `onChildLeaf` over the whole block regardless of nesting depth, matching upstream's own
    `leavesInClosedRange` scan. Any comment anywhere in the block disqualifies it (upstream's own
    `containingOnly` filter never drops comments either).
  - **`useless-postfix-expression`** — a `++`/`--` postfix expression that is either the direct
    right operand of *any* binary expression (not only `=` — matching upstream's own lack of an
    operator filter) whose left operand has the identical raw text, or a direct child of that right
    operand (`i = 1 + i++`), is reported at the postfix's own span. Every `BINARY_EXPRESSION`
    records its own direct postfix children at its own exit, keyed by offset, so an enclosing
    binary expression whose right operand is itself a binary expression can look up that operand's
    own direct children — one level below `expression.right`, never recursed deeper, matching
    upstream's own `getChildrenOfType` (direct children only). **Deliberately narrower than
    upstream**: only the assignment/comparison self-reference slice is ported; upstream's separate
    `return i++` detection is gated by `shouldBeReported()`, a heuristic that needs the *complete*
    set of local-variable names declared anywhere in the enclosing named function's own subtree,
    independent of whether that declaration comes before or after the `return` in source order —
    a genuine forward/backward reference a single SAX pass cannot resolve without deferring to
    end-of-function, and even then only for the specific enclosing-function scope upstream's own
    `getNonStrictParentOfType<KtNamedFunction>()` selects (a captured outer local var referenced
    from inside a *lambda* nested in that function would need bubbling the fact through multiple
    frames). Held out as a documented gap rather than an under-verified approximation of that one
    path — the covered slice (both real-world `i = i++`/`i = 1 + i++` shapes) ships in full.
  - **`collapse-if`** — an `if` whose own `then` branch's only content — braced or not, a leading
    or trailing comment tolerated either side — is another nested `if` is reported at the nested
    `if`'s own span, when neither the outer nor the inner carries an `else`. A stack of open `IF`
    frames, each with a "my own nested candidate" slot, is filled either directly (an unbraced
    nested `if` sets its own enclosing frame's slot at its own exit) or via a `BLOCK` whose
    immediate parent is `THEN` (which, at its own exit, confirms it holds nothing but one nested
    `if` plus whitespace/comments, then sets the *enclosing* frame's slot — its own frame having
    already been popped, since the nested `if` inside it exits first). Every `IF`'s own `else`
    presence is recorded at its own exit keyed by offset, for a `BLOCK`'s lookup of its nested
    `if`'s own fact; the enclosing `if`'s *own* `else` presence is read live off its still-open
    frame, never through that same map (its own `else`, if any, is only walked after its `then`
    branch closes, so the map would still read "no else" at the point a nested candidate is being
    recorded). A three-level chain (`if(a){if(b){if(c){}}}}`, none with `else`) reports at `b`'s and
    `c`'s own positions, never `a`'s — matching upstream's own chain-walk exactly, confirmed by a
    dedicated fixture proving the frame stack survives a rise-then-fall in nesting depth without
    cross-contamination.
  - **`extension-functions-same-name`** — collected once per file: every non-interface class's own
    directly-named supertypes (a class's own name captured from its first direct `IDENTIFIER`
    child; a supertype's own simple name read as the leading identifier characters of its
    `SUPER_TYPE_CALL_ENTRY`'s own span text — matching upstream's own "first identifier leaf,
    unqualified" extraction, including its quirk of taking a *qualified* supertype's own first
    segment rather than its simple name); every top-level extension function's own receiver class,
    name, parameter *names* (read from its `VALUE_PARAMETER_LIST`'s own `VALUE_PARAMETER` children,
    stashed by that list's own offset for the enclosing `FUN` to collect), and return-type text.
    Decided once in `afterFile`: two candidates sharing a signature (name + parameter names, never
    types, + return-type text) are both reported when their own receiver classes are found related.
    An interface's own supertype list is never a source of a related-class pair (matching upstream's
    own `filterNot { isInterface() }`, applied to the child, never the supertype).
  - **`getter-setter-fields`** — a `get()`/`set()` accessor body that references its own property's
    bare name anywhere inside it is reported at the accessor's own span, unless: that reference is
    itself a call's own callee (`name()`, a same-named function call, not a self-reference); a
    local variable of the same name was declared earlier in the accessor's own direct block (a
    genuine shadow); or the property is an extension property (which never has a backing `field` to
    redirect to in the first place — so the "use `field` instead" fix could never apply). The
    property's own name and whether it is an extension property are both read off its first direct
    `IDENTIFIER` child, using the same fragile "is the immediately-preceding leaf a bare `DOT`, no
    intervening whitespace" adjacency check upstream's own AST-sibling walk relies on (both fail to
    recognize `Foo. bar` — a spaced dot — as an extension property; a faithful reproduction of
    upstream's own quirk, not a narrowing). **Deliberately narrower than upstream**: only the
    *first* bare (non-dot-qualified) same-named reference is ever a candidate; upstream additionally
    allows a `this.name`-qualified reference to count as the same self-reference, which needs
    tracking a dot-qualified expression's own receiver shape one level below where the candidate
    identifier itself is found — dropped rather than approximated.
  - **`sync-in-async`** — a `runBlocking { }` trailing-lambda call (its own callee read directly off
    the `REFERENCE_EXPRESSION` at `CALL_EXPRESSION` child-index 0) is reported at the callee's own
    span when any open ancestor `CALL_EXPRESSION` frame's own callee is `async`/`launch`, or any
    open ancestor `FUN` frame carries a `suspend` modifier — a pure existence check across two
    independent frame stacks (order between them never matters, only "is any frame in either stack
    true"), so the two never need merging into one ordered structure.
  - **`when-must-have-else`** — a statement-position `when` missing `else`, whose own entries are
    not entirely enum-entry-shaped, is reported at its own span. "Statement position" excludes: a
    `when` with a `RETURN` ancestor anywhere above it; one whose immediate parent is `WHEN_ENTRY`
    (an unbraced branch of another `when`); one whose immediate parent is `PROPERTY`, `FUN`
    (an expression-body function), or *any* `BINARY_EXPRESSION` (broader than upstream, which only
    exempts a bare `=` sibling specifically — telling that one operator apart from any other would
    need the operand's own operator text, a safer direction to over-exempt on a shape rare enough
    not to matter in practice); and a `when` that is a lambda's own last statement (needs its
    enclosing `BLOCK`'s own last child, only known once that block closes — every candidate's
    final verdict is deferred to `afterFile` for this reason alone). "Enum-entry-shaped" checks
    each entry for an `is`-pattern condition (disqualifies unconditionally) and, for each plain
    expression condition, whether it is a bare or dot-qualified reference; a `when` nested inside a
    `WHEN_CONDITION_IN_RANGE` (`in RED..BLUE`) is deliberately never inspected for this heuristic —
    always treated as enum-like — a documented, safe-direction narrowing (never a new false
    positive, only a possible missed one on a non-enum `in` condition, a rare shape).
  - **`string-concatenation`** — a `+` binary-expression step whose own left operand is textually a
    string (a string-template literal, or a `.toString()`-suffixed call paired with a
    string-template right operand) is reported once, at the earliest such step found anywhere in a
    single-line, top-level `+` chain — matching upstream's own "first descendant found" selection
    for the common case (a straightforward left-associative literal-starting chain has only one
    genuine match; upstream's own check on each *outer* step's own left operand fails once that
    left operand is itself a compound expression rather than a bare literal). A parenthesized
    sub-expression is an opaque boundary — never itself flattened through — the same treatment this
    project's own `unnecessary-part-of-binary-expression`/`mixed-condition-operators` already give
    parens, so a `+`-chain finding buried inside an explicit paren is a documented, consistent miss.
  - **`boolean-expressions`** — narrowed to the two propositional laws not already covered by
    `unnecessary-part-of-binary-expression`/`double-negative` (see the dedupe map above): a bare
    `true`/`false` literal operand of a `&&`/`||` inside an `if`/`while`/`do-while`'s own `CONDITION`
    (matching upstream's own scope exactly), or a direct complement pair (`a && !a`, `a || !a`,
    comparing an operand's raw text against a sibling `PREFIX_EXPRESSION`'s own recorded `!`-negated
    base text). Only the condition's own two *direct* operands are ever checked — no chain
    flattening — so a complement or literal buried three or more operators deep in one larger
    chain (`a && b && !a`) is a documented miss, the same conservative simplification this batch's
    other binary-chain rules already accept.

  Fixture coverage: 79 fixtures across the 9 rule directories (error/clean/every-exemption,
  `@Suppress` happy/negative pairs per rule); `collapse-if` additionally covers a three-level
  nested chain (rise-then-fall proof for its own `IfFrame` stack); `when-must-have-else` covers
  every one of its five statement-position exemptions plus the lambda-last-statement deferred
  path independently. Unit specs: one Decision spec per rule (`FunctionExpressionBodyDecisionSpec`,
  `UselessPostfixExpressionDecisionSpec`, `CollapseIfDecisionSpec`,
  `ExtensionFunctionsSameNameDecisionSpec`, `GetterSetterFieldsDecisionSpec`,
  `SyncInAsyncDecisionSpec`, `WhenMustHaveElseDecisionSpec`, `StringConcatenationDecisionSpec`,
  `BooleanExpressionsDecisionSpec`).

  **Own-codebase measurement** (isolated `rsync` copy, never this repo's own `wrasse.json`;
  `allWarningsAsErrors` disabled in the copy's convention plugin; `publishToMavenLocal` run in the
  copy before `compileKotlin compileTestKotlin -PwrasseCheck --continue`, so the mavenLocal plugin
  jar under test was freshly built from the code this batch actually ships): of the nine new ids,
  six fire **zero** times anywhere in this repo's own `main`+`test` sources — `boolean-expressions`,
  `collapse-if`, `extension-functions-same-name`, `getter-setter-fields`, `sync-in-async`,
  `useless-postfix-expression`. `function-expression-body` fires twice (`WrassePlugin.kt`'s own
  `checkFile`-adjacent hook and `WrasseCompilerPluginRegistrar.kt`, both genuine single-`return`
  block bodies). `when-must-have-else` fires five times, every one a `when` over a sealed hierarchy
  (`WRule`, a config `Property`, `AnnotationScope`, `FileApplyResult`) using `is`-pattern branches
  with no `else` — genuine true positives by this rule's own literal, resolution-free definition
  (kotlinc may itself treat these as exhaustive via sealed-hierarchy coverage, but that is exactly
  the resolution fact this syntax-only port cannot and does not attempt to check, matching upstream
  fidelity). `string-concatenation` fires eight times, every one a literal-starting `+` chain
  (`"\n" + sorted.joinToString(...)`-shaped) — genuine true positives. **Enablement
  recommendation** (not applied to this repo's own `wrasse.json`, per the task's own instruction):
  all six zero-firing ids are zero-risk to enable at `error` immediately.
  `function-expression-body`/`string-concatenation` are correctly scoped and low-volume (2 and 8
  hits) — enabling either means either accepting those few sites as-is (both are genuine, harmless
  style nits) or converting them to expression bodies / templates first; either is a small, one-time
  cleanup, not a blocker. `when-must-have-else` is correctly scoped but its 5 hits are all
  sealed-`when` idioms this codebase uses deliberately and repeatedly (matching a sealed hierarchy
  exhaustively via `is`-branches, no `else`, by design) — enabling it repo-wide as configured today
  would mean either adding an `else -> error(...)` to five genuinely-exhaustive `when`s (a style
  regression, not a bug fix) or an `exclude`/`@Suppress` per site; recommend holding this one id at
  `off` (or `warn`) in this repo's own config specifically, not because the port is wrong, but
  because this codebase's own idiom is the documented, deliberate exception the rule's own enum/
  sealed exemption doesn't (and, being resolution-free, structurally cannot) recognize.

  **Definitive final accounting.** Every candidate carried forward from B.7 through B.9's own
  held-lists has now been re-verified against current upstream source at least once. Of B.9's
  held-10: nine ship this batch; one (`no-name-shadowing`) is **permanently skipped** — its own
  most consequential case is irreducibly resolution-dependent (`RequiresAnalysisApi`,
  `analyze() { functionLiteral.symbol.valueParameters }` to know whether a given lambda literal
  actually binds an implicit `it`), and its remaining syntax-only slice (explicit declaration
  shadowing a named parameter) was deliberately not carved out as a separate, narrower id this
  batch, since doing so would mean shipping a materially different rule than what "no-name-
  shadowing" means to a user reading either catalog — a scope decision left for a future session
  rather than decided unilaterally here. **Zero portable (syntax-only) L-bucket rules remain
  un-ported.** The complete permanently-skipped-for-resolution list, across every batch to date, is
  exactly one id: `no-name-shadowing` (detekt). This is the project's "all portable L rules ported"
  milestone.

**Exit:** a representative real project lints under wrasse with parity-equivalent findings to its
ktlint + detekt setup (minus parked outbound rules) at measurably lower wall-clock.

- **L-rule backfill wave 1 (naming + metrics) — shipped 2026-07-22.** Test-only pass: ported/adapted
  upstream ktlint/detekt/diktat test suites for the B.1 naming family and B.4 metrics family (plus
  the B.6/B.7 deferred-naming closures `function-parameter-naming`/`lambda-parameter-naming`/
  `variable-name-max-length`) into wrasse fixtures, deduping against the existing independently-
  derived fixture set. No production code touched. 46 new fixtures added across 21 rule
  directories; zero genuine bugs found (every newly-ported fixture that initially failed did so
  because of an arithmetic/column mistake in the fixture itself, corrected before landing — none
  needed quarantine under `fixtures-backfill-failing/`).

  Naming family (8 B.1 ids + 3 deferred ids), already-covered / newly-ported / out-of-scope /
  skipped-config counts per rule:

  | rule | already-covered | newly-ported | out-of-scope | skipped-config |
  |---|---|---|---|---|
  | `class-naming` | 4 | 2 | 1 | 1 |
  | `function-naming` | 7 | 4 | 1 | 1 |
  | `property-naming` | 9 | 6 | 1 | 0 |
  | `enum-entry-naming` | 4 | 3 | 0 | 1 |
  | `package-naming` | 4 | 3 | 0 | 0 |
  | `backing-property-naming` | 6 | 4 | 1 | 0 |
  | `filename` | 5 | 2 | 1 | 0 |
  | `constructor-parameter-naming` | 5 | 2 | 1 | 1 |
  | `function-parameter-naming` | 5 | 1 | 1 | 0 |
  | `lambda-parameter-naming` | 5 | 2 | 0 | 0 |
  | `variable-name-max-length` | 4 | 1 | 1 | 0 |
  | **naming total** | **58** | **30** | **8** | **4** |

  Metrics family (13 B.4 ids), same four buckets:

  | rule | already-covered | newly-ported | out-of-scope | skipped-config |
  |---|---|---|---|---|
  | `long-parameter-list` | 6 | 2 | 1 | 1 |
  | `long-method` | 4 | 1 | 0 | 1 |
  | `large-class` | 4 | 0 | 0 | 1 |
  | `too-many-functions` | 5 | 3 | 1 | 1 |
  | `nested-block-depth` | 5 | 1 | 0 | 1 |
  | `cyclomatic-complexity` | 4 | 3 | 3 | 1 |
  | `return-count` | 4 | 1 | 1 | 1 |
  | `throws-count` | 3 | 1 | 0 | 1 |
  | `destructuring-declaration-with-too-many-entries` | 2 | 2 | 0 | 1 |
  | `complex-condition` | 3 | 2 | 0 | 1 |
  | `function-name-max-length` | 4 | 0 | 1 | 1 |
  | `function-name-min-length` | 4 | 0 | 1 | 0 |
  | `file-size` | 3 | 0 | 0 | 1 |
  | **metrics total** | **51** | **16** | **8** | **12** |

  **Notable newly-locked shapes:** `class-naming`/`function-naming`/`enum-entry-naming`/
  `package-naming`/`backing-property-naming` diacritic-identifier acceptance (ktlint's own
  Unicode-identifier tests, previously asserted only in prose, never fixture-locked);
  `property-naming`'s local-variable enforcement, permanently landed as a real fixture pair
  (`local-variable-camel-clean`/`local-variable-pascal-error`) closing the gap B.1's own text
  admitted was only ever verified with a temporary, removed fixture; `backing-property-naming`'s
  companion-object-indirection narrowing (`companion-object-indirection-not-checked-clean`) and
  getter-with-parameter non-correlation, both previously documented in prose only;
  `destructuring-declaration-with-too-many-entries` firing on a lambda's own destructured parameter
  list (same `DESTRUCTURING_DECLARATION` node shape as a `val (a, b, c, d) = ...` statement — not
  previously fixture-tested for the lambda shape); `complex-condition`'s crude substring counting
  matching upstream's own known imprecision (a string-literal operand containing the literal text
  `&&` inflates the count, `string-literal-substring-crudely-counted-error`); `return-count`'s
  documented broadening over upstream's default (a labeled lambda return, `return@lit`, counts
  toward the enclosing function — `labeled-lambda-return-counts-toward-enclosing-error`); and
  `cyclomatic-complexity` counting every `if` in an unbraced `else if` chain unconditionally
  (`else-if-chain-counts-each-branch-error`/`else-if-chain-at-threshold-clean`).

  **Documentation inconsistency found (not a code bug, not quarantined):** B.4's own prose above
  states `cyclomatic-complexity` exempts "an unbraced `else if` continuation" from the `+1`-per-`if`
  count. Reading `FunctionMetricsEngine.kt`'s actual `WNodeType.IF` handling shows the `else`-parent
  check (`ctx.ancestors.peekType() != WNodeType.ELSE`) gates only the *nesting-construct* tracking
  shared with `nested-block-depth` (`enterNestingConstruct`/`exitNestingConstruct`) — the
  `addComplexity(1)` call for `cyclomatic-complexity` itself is unconditional on every `IF` node,
  matching detekt's own real `CyclomaticComplexity.visitIfExpression` (which also increments
  unconditionally, with no `else`-if exemption at all). The shipped code is therefore *more*
  faithful to upstream than B.4's own text claims; `else-if-chain-counts-each-branch-error`/
  `else-if-chain-at-threshold-clean` lock in the actual (unconditional) behavior. B.4's prose sentence
  should be corrected to scope that parenthetical to `nested-block-depth` only, not left as written.

  **Harness constraint found:** `filename`'s fixture harness always compiles every fixture under the
  fixed path `sample/test.kt` (`WrasseFixtureSpec.kt`), so upstream's own `.kts`-extension-ignored,
  `package.kt`-ignored, and diacritic-actual-filename shapes have no constructible fixture under the
  current harness — noted as a structural harness limitation, not a gap in rule behavior; the two
  new `filename` fixtures instead target the one facet the fixed path does allow varying: which
  top-level declaration shape (`object` vs. `typealias`) a mismatched `test.kt` gets checked against.

  Ladder: `build`, `test --rerun-tasks`, `testMinorHarness --rerun-tasks`, `testPatchHarness`, and
  `wrasseLint -Prepublish` all green after this wave. All three upstream checkouts (`ktlint`,
  `detekt`, `diktat`) verified byte-clean (`git status`) before and after — read-only throughout,
  no probe cases added this wave.

### Phase C — The printer

- `wrasse-format`: `Doc` + `DocBuilder` + `Layout` per §5.3; style parameters per D21.
- Whole-file record type in the patch format; hash-guarded apply (§5.4).
- Edit splicing (content → layout); check mode; residual `max-line-length` report.
- Harness: `format(format(x)) == format(x)`; formatted fixtures re-format to themselves.
- Resolve the 7 hard calls in autoformat-scope.md as they come up.

#### Phase C.1 — Printer foundation, proven on indentation alone — **done 2026-07-20**

`libs/wrasse-format` built for real: `Doc` (`Text`, `Break{HARD,SOFT}`, `Indent`, `Group`, `Concat`,
~90 lines), `Layout` (single recursive pass threading column/indent depth/flat-vs-broken mode,
~90 lines), `DocBuilder` (a `WStreamRule` registered via `WrassePlugin`'s existing `alwaysOn` seam
— no `StreamDispatch`/`LightTreeStreamAdapter` changes were needed at all). Style parameters live
as `WFormatConfig`/`FormatStyle` in `wrasse-model` (not `wrasse-format`, so `WConfig` can hold one
without an upward dependency) with the D21 defaults; only `indentWidth` is consumed. `format` is a
root `wrasse.json` key (own on/off + style block, not a `rules` entry), parsed in `WConfig` with
the same `extends`-overrides-whole-block semantics as `exclude`.

**DocBuilder's actual algorithm** (indentation only, everything else byte-identical): every leaf
becomes `Text` verbatim; a `WHITE_SPACE` leaf containing `\n` becomes a `HARD Break` whose literal
is the original text up to and including its final `\n` (blank lines and their own trailing
whitespace survive exactly) — `Layout` synthesizes the indent for the following line from the
ambient `Indent` depth instead of copying the original run of spaces/tabs, which is what
normalizes too-little/too-much/tabs/mixed indentation uniformly with no per-case logic. Comment
and string-literal leaves (`KDOC`, string-template entries) are never `WHITE_SPACE`, so their
entire text — embedded newlines included — rides one `Text` node and is never touched, by
construction, with no special-casing required. Children of a node are buffered until `exitNode`;
whether that node opens an indent scope is decided then, from the completed children list: a node
whose type is in `{BLOCK, CLASS_BODY, WHEN, FUNCTION_LITERAL}` **and** whose own last child is
literally `RBRACE` wraps its interior in `Indent` and dedents the line holding that `RBRACE`
(Wadler's standard closing-delimiter placement); a node that doesn't end in its own `RBRACE` is a
transparent pass-through.

**A design.md claim this falsified, found only by testing against reality (not assumed):** a
lambda body's `BLOCK` does **not** own its own `{`/whitespace/`}` — those belong to the enclosing
`FUNCTION_LITERAL`; the nested `BLOCK` is a bare statement-sequence with no delimiters of its own,
confirmed off a real LightTree dump (`WNodeTypeMappingCompletenessSpec`-style, no compiler
knowledge of this was assumed). A naive "every `BLOCK` opens an indent scope" rule double-indents a
multi-statement lambda body while a single-statement one looks accidentally correct, which would
have shipped silently wrong. This is exactly the risk §5.3 flags in the abstract ("the printer
design has never been tested against reality") — it reproduces concretely here on the very first
non-trivial construct, and is now the one required special case, gated structurally (own-`RBRACE`
check) rather than by a hardcoded parent-type exception.

**Patch record type — reused offset edits, no new format added.** A single `WEdit(0,
sourceText.length, renderedText)` inside the existing per-file `FileEdits` already **is** a
whole-file record: `WPatchWriter`/`WPatchReader`/`WPatchApplier`/`WPatchMerge` needed zero changes,
the hash guard and atomic-rename-on-apply already cover it, and it composes for free with the
suppression/`@Suppress("format")` and D22 merge-on-write machinery. §5.4's dedicated whole-file
record type is deferred, not built: nothing in this slice needed the efficiency a distinct record
type would buy (avoiding writing/escaping a full file as one line) — revisit only if that cost is
ever measured to matter. **Known gap this leaves, stated plainly:** the format edit is *not*
reconciled with other rules' `EditPlan` edits on the same file — it is a 0..length span, so it
trivially "overlaps" any other emitted edit and the existing disjointness check (correctly, by
design) fails loudly rather than corrupt output. Real coexistence needs the content→layout edit
splicing §5.3 describes; the foundation's fixtures avoid the conflict by construction (`format`
enabled with no other autofix-capable rule on) rather than solving it.

**Group/soft `Break` — built, not yet exercised by a real file.** `Layout` implements the
flat-vs-broken fit decision and nested-group composition per §5.3, proven by direct unit tests
against hand-built `Doc` trees (`LayoutSpec`). `DocBuilder` emits zero `Group`/`SOFT Break` nodes
in this slice — every real newline is a `HARD` break, so nothing here yet exercises the mechanism
against an actual Kotlin file. That wiring is genuinely open work for the F-bucket phase, not a
detail already covered by this foundation.

**Harness:** a `format-indentation` fixture dir (`testing/wrasse-test-harness/.../fixtures/`) with
its own `wrasse.json` (`{"format":{"enabled":true}}`) — no fixture-harness code changes were needed
for `format(format(x)) == format(x)`/"formatted fixtures re-format to themselves": the existing D19
idempotence cycle (apply → recompile → assert zero further edits) and `.fixed.kt` byte-exact
assertion already generalize to any rule id that reports through `WReporter`, `format` included.
Fixtures: too-little/too-much/tabs/mixed indentation (all normalize to the same canonical output,
each with a `.fixed.kt`), nested class→function→if→lambda (proves depth-derived indentation and is
what surfaced the `FUNCTION_LITERAL` finding above), an already-correctly-indented file
(`expect-clean`, byte-identical), and a KDoc + multiline-string-literal file proving their
interiors are preserved verbatim even while the surrounding structural indentation around them is
corrected (ties directly to autoformat-scope.md's own flagged "comment interiors" uncertainty —
this foundation's answer, an emergent consequence of the `WHITE_SPACE`-only `Break` rule rather
than a deliberated general policy, is "never touch them," matching ktfmt/prettier precedent).

**Ladder run for this slice:** `build`, `test --rerun-tasks`, `testMinorHarness --rerun-tasks`,
`testPatchHarness`, `wrasseLint -Prepublish` all green.

**Exit:** the formatter reformats a real module idempotently via `./gradlew wrasseFix`;
`wrasse.fix` expands a star import correctly on a real module; the compile-riding fix pass beats
a separate `ktlint -F` invocation on the same files.

#### Phase C.2 — Edit splicing: format and fixes coexist — **done 2026-07-20**

Closes Phase C.1's stated gap: `format` enabled alongside any autofix-capable rule now composes
into one whole-file edit instead of tripping `EditPlan`'s disjointness check. `Doc` (`libs/wrasse-
format/Doc.kt`) gained `start`/`end` on every node — `Text`/`Break` from the compiler leaf they were
built from, `Indent`/`Group` forwarding their one `body`'s span, `Concat` taking its span as an
explicit constructor argument (never inferred from `parts`, so an empty or degenerate first/last
part can't make inference lie) — satisfying §5.3's "doc leaves reference original source spans, so
they are addressable by offset" claim, which the C.1 foundation had not yet built.

**The splicer (`DocSplicer`, new file):** `splice(doc, edits): Doc?` applies each `EditPlan` edit
independently against `Doc.start`/`Doc.end` (never against rendered text or accumulated offsets) —
sound because `EditPlan`'s own disjointness invariant means application order never matters. Per
edit, a recursive descent classifies every node it touches into one of three shapes:

- **Whole-leaf/whole-subtree replacement** (no-semicolons' deletion, no-unused-imports' multi-leaf
  line removal, no-wildcard-imports' multi-line expansion, if-else-bracing's gap replacement): the
  first node the edit's span fully contains becomes one atomic `Text(replacement)`; every further
  node the same edit also fully contains is dropped, so a multi-leaf-spanning edit never duplicates
  its own replacement text. A "first-covered-wins" flag, scoped to one edit's splice call, is the
  only state threaded through the recursion.
- **Partial-leaf split**: a `Text` leaf is always cleanly splittable at any offset (its `value` is
  always the exact source substring of its span, by construction) into an unaffected prefix/suffix
  either side of the replacement. A `Break` leaf is splittable only up to where its `literal`'s
  represented length ends — the trailing indentation `Layout` regenerates instead of storing has no
  addressable position of its own.
- **Zero-width insertion** (if-else-bracing's brace-less-branch tail, trailing-newline's EOF
  insert): threaded to the exactly one leaf whose span starts at the insertion point, via an
  exclusive-end containment rule (`node.start <= X < node.end`) that resolves the leaf-boundary
  tie deterministically without an emitted-flag; true end-of-file insertion (`X >= doc.end`, no
  "next" leaf exists) is one explicit top-level case, appending after everything.

**Unmappable edits are refused, never guessed:** a cut landing strictly inside a `Break`'s elided
trailing-indent tail returns `null` from `splice`, propagated immediately through every enclosing
frame. `DocBuilder.finish` treats `null` as: no format report, no format edit, and the rule edits
that were pulled out of `EditPlan` to attempt the splice are handed back via a new `EditPlan.restore`
(paired with `EditPlan.takeAll`) so the declining rules' own fixes still reach the patch. No fixture
exercises this path (none of the shipped rule shapes produce it — see below); `DocSplicerSpec`
covers it directly against a hand-built `Doc`.

**A lifecycle bug found by construction, fixed before it could ship:** `DocBuilder`'s render used to
run from `afterFile`, a hook the walk calls for every `WRule` **before** the separate,
later-running `WFileRule` phase (`trailing-newline` is the only real one). Splicing needs to observe
`EditPlan` only after every rule's *final* contribution, so `afterFile` is no longer where the
printer finishes: `DocBuilder.finish(ctx, reporter)` is a new method the host (`WrassePlugin`) calls
explicitly, once, strictly after `LightTreeStreamAdapter.walk` returns in full (both its `afterFile`
loop and its `WFileRule` loop). This was invisible under C.1 (no fixture combined `format` with any
other autofix-capable rule) and is not one of this slice's five required combinations either
(`trailing-newline` isn't in that list) — found only because getting `finish`'s contract right
("observe *every* remaining edit") forced tracing exactly when the last edit could possibly land.

**A §5.3 claim this refined, found only by testing against a real combination:** the design's
"content → layout, no cycles" framing implicitly assumes a fix's own computed indentation and the
printer's structurally-derived indentation agree. They don't automatically: `if-else-bracing`
computes its inserted braces' indentation from the *original* physical column of the branch's line
(`BraceInsertion.physicalLineIndentColumn`), read directly off the pre-fix source text, while the
printer derives indentation for the *real* structural nodes around it (the enclosing function's
`BLOCK`) purely from tree depth × `indentWidth`. When the file's original physical indentation
already matches canonical depth-based indentation at that line, these coincide and the combination
is idempotent in one pass (proven by this slice's fixtures). When it doesn't — discovered by
deliberately building a grand-slam fixture with a 2-space-per-level `if`/`else` nested under
mis-indented siblings — the spliced brace text keeps its stale, physical-column-anchored indent
while the surrounding real tokens get reindented to the new canonical depth by `Layout`, producing a
file that is not stably formatted in one pass (a second `wrasseFix` cycle still emits edits,
tripping the `fix(fix(x)) == fix(x)` invariant). This was a genuine, unclosed gap at the time —
**closed in Phase C.3** (below): the fix is for `if-else-bracing`/`when-entry-bracing` to stop
computing indentation at all under `format` and let `DocSplicer` derive it structurally, rather than
for a T-bucket fix to somehow read the printer's canonical depth in advance. The shipped fixtures
below (this slice) sidestep the gap by construction (every if/else chain sits at a line whose
physical column already equals its canonical depth); the fixtures added in C.3 remove that
constraint and exercise the previously-unhandled mismatch directly.

**Fixtures:** `testing/wrasse-test-harness/.../fixtures/format-with-fixes/` (one `wrasse.json`,
`format` plus `no-semicolons`/`no-unused-imports`/`no-wildcard-imports`/`if-else-bracing` all
enabled) — `no-semicolons-and-reindent-error` (whole-leaf deletion + reindent), `no-unused-imports-
and-reindent-error` (multi-leaf whole-line deletion + reindent), `no-wildcard-imports-expansion-and-
reindent-error` (multi-line atomic-run insertion + reindent), `if-else-bracing-and-format-error`
(gap replacement plus a zero-width tail insertion, both indentation-matched per the caveat above),
and `grand-slam-error` (all four at once, plus an unrelated mis-indented sibling statement proving
`format` still does real reindentation work alongside the composed fixes). Each has a `.fixed.kt`
byte-exact companion; the existing D19 idempotence cycle (already generic over any reporting rule
id, per C.1) exercises `fix(fix(x)) == fix(x)` on all five with no harness changes needed.
`libs/wrasse-format/DocSplicerSpec` unit-tests every edit shape directly against hand-built `Doc`
trees, including the refusal path.

**Ladder run for this slice:** `build`, `test --rerun-tasks`, `testMinorHarness --rerun-tasks`,
`testPatchHarness`, `wrasseLint -Prepublish` all green (the last one exercises the rebuilt plugin
against this repo's own, `format`-disabled `wrasse.json` — enabling `format` for self-lint is future
work, not part of this slice).

#### Phase C.3 — Brace-insertion rules stop computing indentation under `format` — **done 2026-07-20**

Closes the C.2 gap above and lifts both brace rules' multiline-body bail while `format` is on,
per §5.3's "layout belongs to the printer, content to the rules."

**How the rules learn `format` is on:** `WrasseRuleConfig` gains `formatEnabled: Boolean = false`,
populated uniformly for *every* rule from `WConfig.buildConfig` — the exact D23 precedent
(`explicitApiActive`): a cross-cutting fact threaded onto every rule's config rather than gated
behind a rule's own `wrasse.json` key, with only the consuming rule(s) deciding what it means. Only
`if-else-bracing`/`when-entry-bracing` read it. The shared `BraceInsertion` helper gained a second
edit constructor, `wrapEditsMinimal` (alongside the original `wrapEdits`), so both rules branch on
`config.formatEnabled` through the *same* seam rather than duplicating the choice: `formatEnabled ==
false` keeps calling `physicalLineIndentColumn` + `wrapEdits` exactly as before (this is the
regression gate); `formatEnabled == true` skips the physical-column read entirely and calls
`wrapEditsMinimal`, which emits only `" {\n"` / `"\n}"` (or `"\n} "` before a following branch) with
no indentation characters at all, tagging the two edits `IndentScope.OPEN`/`CLOSE` (new
`WEdit`/`IndentScope`, `wrasse-lang`). `IfElseBracingDecision.decideBranch` and
`WhenEntryBracingDecision.decideEntry` both take the new `formatEnabled` flag directly: it gates only
the multiline-content bail (`hasEmbeddedNewline && !formatEnabled`) — the *other* bail
(`hasAdjacentComment`) and the unrelated chain/`when`-level scope gates are untouched in both modes.

**Existing brace fixtures needed no change — confirmed, not merely assumed.** Every fixture in
`if-else-bracing/`, `when-entry-bracing/`, `when-if-bracing-combined/`, and the five `format`+fixer
combinations in `format-with-fixes/` (including `if-else-bracing-and-format-error` and
`grand-slam-error`, which *do* run with `format` on) passed unmodified — `git status` on all four
directories is clean after the full ladder. The `format`-enabled pair pass unmodified because their
source was already built to sidestep the C.2 gap (physical column already equals canonical depth),
so the new mechanism's output is byte-identical to the old baked-indentation output at that specific
depth — not a coincidence the new code depends on, just the reason the old fixtures don't need new
`.fixed.kt` content to keep passing.

**`DocSplicer` gains the mechanism the C.2 gap was actually missing:** two additions, both scoped to
the format package only (no `wrasse-lang`/`wrasse-model` consumer outside it reads `IndentScope`
except to construct it).

1. `replacementDoc` (replacing the old inline `Doc.Text(edit.replacement, ...)` construction):
   whenever a spliced replacement contains an embedded `\n`, it is decomposed into alternating
   `Text`/`Break(HARD)` segments instead of one opaque `Text` blob, so `Layout` synthesizes each
   subsequent line's indentation from ambient depth exactly as it would for a break sourced from
   real source whitespace. Byte-identical to the old behavior whenever the replacement embeds no
   newline, and byte-identical even *with* an embedded newline at ambient depth 0 (verified: the
   existing `DocSplicerSpec` cases, including the one hand-built to simulate the old baked-indent
   `if-else-bracing` shape, pass unmodified) — the two rendering strategies coincide until something
   actually wraps the surrounding tree in a *new* `Indent`, which only C.3's own mechanism does.
2. `applyIndentScopes`, run once before the ordinary per-edit splice loop: stack-matches every
   `IndentScope.OPEN` edit against its next unmatched `CLOSE` (sound because emitted pairs nest
   exactly as their own source construct does) and wraps the span from the *open* edit's own start
   to the *close* edit's own start in one `Doc.Indent` — mirroring `DocBuilder.resolveFrame`'s own
   placement for a real `BLOCK`: the open edit's trailing break ends up inside the new indent (it
   decides the following line's column), the close edit's leading break stays outside it (it decides
   the closing line's column, which must stay at the outer depth).

**A §5.3 claim that failed on contact, found only by running a real fixture through the full
pipeline:** "leave the close edit outside the new `Indent` and let the ordinary per-edit splice loop
place it" is *not* sound when the close edit is a **zero-width** insertion (the last branch in its
chain/`when`, nothing textually to its right) — which is the common case, since most bare branches
have no following sibling. The generic per-edit loop threads a zero-width insertion to "the one leaf
whose span starts at the insertion point"; when the branch being braced is also the last statement
in its *enclosing* block, that leaf is the enclosing `BLOCK`'s own dedent whitespace — which
`DocBuilder.resolveFrame` deliberately places *outside* the `BLOCK`'s own `Indent`, one level
shallower. Threading the zero-width close edit into that leaf silently rendered the new closing
brace one level too shallow (reproduced directly: a debug harness dumping round-1/round-2 diagnostics
and patched content, not caught by unit tests against hand-built `Doc` trees, only by a real
`if`/`else` at the end of a function body). The fix: a zero-width `CLOSE` edit is never handed to the
ordinary per-edit loop at all — `applyIndentScopes` splices it directly as a new sibling appended
immediately after the wrapped `Indent`, at the exact same tree level, and returns it in a consumed
set the per-edit loop skips. A **non-zero-width** close edit (`THEN` immediately followed by `else`)
has no such ambiguity — it fully covers a real, pre-existing gap node, so the ordinary `fullyCovered`
replace handles it correctly with no special-casing, confirmed by the pre-existing
`if-else-bracing-and-format-error`/`grand-slam-error` fixtures continuing to pass unmodified.

**The multiline-under-format results:** with the bail lifted, a bare branch/entry whose own content
already spans multiple lines (a chained call split across lines) now gets braced instead of
report-only. Its interior lines — never touched by either brace edit, since they sit *inside* the
new `Indent` as unmodified original `Doc` nodes — get their indentation regenerated by `Layout` from
the new ambient depth like any other line, exactly like the printer already does for ordinary source
lines; Phase C.1's known limitation (no continuation-indent modeling — `DocBuilder` only tracks
depth via `{BLOCK, CLASS_BODY, WHEN, FUNCTION_LITERAL}` nodes ending in `RBRACE`) means a
user-written chained call's *own* extra continuation indent collapses to the same depth as the
braced body's first line rather than one level further, one line converging to a *different* column
than before formatting — a pre-existing printer limitation this slice surfaces for the first time,
not one it introduces.

**Fixtures:** two new directories, `bracing-format-on/` (`format` enabled, both bracing rules on) and
`bracing-format-off/` (same rules, no `format` key) — the existing `if-else-bracing/`,
`when-entry-bracing/`, `when-if-bracing-combined/`, and `format-with-fixes/` directories are
untouched, per above.
- `bracing-format-on/bad-indent-if-else-convergence-error` — the exact shape the C.2 gap's report
  described but did not build: an `if`/`else` at a 2-space physical indent, braced under `format`.
  One `wrasseFix` pass converges to canonical 4-space output; the D19 idempotence cycle (generic
  since C.1) confirms a second pass emits nothing.
- `bracing-format-on/multiline-if-body-braced-error` /
  `bracing-format-on/multiline-when-entry-braced-error` — a chained-call multiline body braced
  correctly under `format`, both the previously-bailed branch and its sibling.
- `bracing-format-off/multiline-if-body-bail-error` / `bracing-format-off/multiline-when-entry-bail-
  error` — the identical two sources, `format` off: the multiline branch still bails
  report-only (with the "no autofix for this shape" marker) exactly as before, while its non-
  multiline sibling still autofixes with the old baked-indentation edits — proving the bail and the
  pre-C.3 code path are both untouched when `format` is off.

**Ladder run for this slice:** `build`, `test --rerun-tasks`, `testMinorHarness --rerun-tasks`,
`testPatchHarness`, `wrasseLint -Prepublish` all green (391 fixture-spec cases per Kotlin minor, zero
failures; `wrasseLint` exercises the rebuilt plugin against this repo's own `format`-disabled
`wrasse.json`, unaffected by this slice).

#### Phase C.4 — The printer starts deciding line breaks — **done 2026-07-20**

Closes the two gaps C.1 stated plainly: `Layout`'s group-fit logic, unit-tested since C.1 but never
exercised by real output, and the total absence of continuation-indent modeling. `DocBuilder` now
emits `Group`/`SOFT` `Break` for three constructs — dot/safe-access chains, binary expressions, and
call argument lists — narrowly, per this slice's brief; every other construct is still reproduced
verbatim exactly as before.

**Ground truth used:** ktfmt is not checked out anywhere under `/var/home/vlad/dev/IdeaProjects`;
ktlint is (`ktlint-ruleset-standard`), so `ChainWrappingRule`/`ChainWrappingRuleTest` and
`BinaryExpressionWrappingRule`/`BinaryExpressionWrappingRuleTest` were read directly for break-point
and operator-position ground truth rather than inventing a house style: `.`/`?.` move to the *start*
of the continuation line (a line must not end with the operator); `?:` does the same (ktlint groups
elvis with the dot/safe-access set for exactly this reason — wrapping after `?:` would otherwise
violate `chain-wrapping` itself); every other binary operator (`+`, `-`, `*`, `/`, `%`, `&&`, `||`,
comparisons, `in`/`is`) stays at the *end* of the line it came from, with the operand moving down.

**Break points chosen** (one `Group`/`Indent` per construct, per §5.3):
- **Chain** (`WNodeType.DOT_QUALIFIED_EXPRESSION`/`SAFE_ACCESS_EXPRESSION`): a `SOFT` break before
  each `.`/`?.`, flat form empty (no space). A chain is built bottom-up in the LightTree (`a.b().c()`
  is a link whose *receiver* is itself the already-resolved `a.b()` link), so only the outermost
  link — the one whose parent frame is not itself a chain-link type — wraps the fully-flattened body
  in one `Group`/`Indent`; inner links just splice their own break into the flat body they hand
  upward. Without this flattening, an *n*-link chain would nest *n* independent `Indent`s and each
  further link would render one level deeper than the last — never what any real formatter does.
- **Binary expression** (`WNodeType.BINARY_EXPRESSION`): a `SOFT` break *after* the
  `OPERATION_REFERENCE`, flat form a single space — except when the operator's own text is `?:`,
  detected by walking the already-built operator `Doc` back to its literal text (no new kotlinc-facing
  type was needed), in which case the break goes *before* it instead, matching the chain case. The
  same bottom-up flattening as chains applies (`a + b + c` nests `BINARY_EXPRESSION` the same way),
  and for the same reason.
- **Argument list** (`WNodeType.VALUE_ARGUMENT_LIST`): its own independent `Group`/`Indent`, right
  after `(`, right after every comma with another argument following it, and right before `)`. A
  comma already followed by nothing but whitespace (a pre-existing trailing comma) gets no break of
  its own — the closing break already lands there, so doubling up would print a blank line before
  `)`. Trailing-comma *insertion* is out of scope for this slice (`FormatStyle.trailingCommas` is
  still unconsumed, per C.1); an argument list that already ends in one keeps it, byte-for-byte.

**Continuation indent, modeled as `Doc.Indent`, not a column:** exactly as §5.3's Doc IR was designed
to make possible. Each of the three constructs' outermost `Group` is wrapped in one `Doc.Indent`
before being spliced into its enclosing frame — `Layout` derives every continuation line's column
from ambient indent depth (depth × `indentWidth`) the same way it already does for a `BLOCK`'s
interior, so "one level deeper than the statement's own depth" falls out of the existing recursion
with no new machinery in `Layout` itself.

**Evidence the fits-check drives real output** (from `format-line-breaks/chain-fits-flat-error.kt`
and `format-line-breaks/chain-exceeds-max-line-length-error.kt`, `maxLineLength: 50`): the identical
construction —
```kotlin
val x = "hi"
    .uppercase()
    .reversed()
```
renders **flat**, joining a hard-broken source, because the whole chain's flat width plus the current
column fits in 50:
```kotlin
val x = "hi".uppercase().reversed()
```
— while the same shape with a longer receiver renders **broken**, one link per line, continuation
indented one level (`chain-exceeds-max-line-length-error.fixed.kt`):
```kotlin
val x = "quite a long string literal here"
    .uppercase()
    .reversed()
```
`format-line-breaks/nested-group-short-arglist-stays-flat-error` proves independent nested-group
composition directly: `"quite a long string literal here".padStart(10).reversed()` is too long
overall (broken, per above), yet `.padStart(10)`'s own argument-list `Group` — measured from its own,
post-break column — still renders flat (`(10)`, not `(\n    10\n)`), because `Layout` never forces a
nested `Group`'s mode from its enclosing one; each decides independently once its own starting column
is known (unit-tested since C.1, exercised by real `DocBuilder` output for the first time here).

**A §5.3-adjacent claim that failed on contact, found only by running real fixtures, not by
reasoning about the mechanism in isolation:** wrapping a chain's *entire* flattened body — including
a trailing call argument that can never itself render on one line (a multi-statement lambda, e.g.
`names.forEach { name -> println(...) }`, the exact shape in the pre-existing
`format-indentation/already-correct` and `format-indentation/nested-class-fun-if-lambda` fixtures) —
in one `Group`/`Indent` breaks that pairing two different ways at once. First, `Layout`'s
already-shipped, already-tested rule that *any* `HARD` break anywhere inside a `Group` forces it
broken (`LayoutSpec`, unchanged in this slice) means a lambda body's own interior line breaks would
force the *chain's* dot onto its own line too, splitting `names` from `.forEach {` even though that
pairing trivially fits — a regression against those two pre-existing, format-enabled fixtures,
caught immediately by the ladder rather than assumed away. Second, even if the fit-check problem were
solved, wrapping the lambda body inside the chain's own `Indent` would render its interior one level
deeper than before, for no structural reason — the lambda's own indent scope
(`resolveBraceFrame`/`FUNCTION_LITERAL`) already derives its depth correctly from *ambient* context
and does not need the chain's continuation indent layered on top. The fix, not a workaround: a
trailing part of a chain/binary expression's flattened body that can never render on one line by
itself (`containsForcedBreak`, the same "hard break or embedded-newline `Text`" predicate `Layout`'s
own `flatWidth` already uses, duplicated locally in `DocBuilder` rather than exposed from `Layout`,
which stays unchanged) is excluded from *both* the fit-checked `Group` and the continuation `Indent`
— it renders as a plain sibling, at its pre-existing ambient depth, exactly as `resolveBraceFrame`
already gives it. This is a real, general rule (a group's fit-check and its continuation indent
should only ever cover content that could actually render on one line), not a special case for
trailing lambdas specifically, even though that is the only shape that surfaces it today. Both
pre-existing fixtures needed no fixed-file changes once this was in place — collapsing
`names\n.forEach {` back to `names.forEach {` reproduces their original, already-`.fixed.kt`-matching
source verbatim.

**Two pre-existing C.3 fixtures *did* need their `.fixed.kt` updated, correctly, not as a
workaround:** `bracing-format-on/multiline-if-body-braced-error` and
`bracing-format-on/multiline-when-entry-braced-error` each contain a genuine (no trailing-lambda)
chain — `50.toString()`, `"two".plus("!")` — that C.3 explicitly flagged as surfacing "Phase C.1's
known limitation (no continuation-indent modeling)... not one it introduces." Both chains trivially
fit on one line; this slice's join logic now collapses them exactly as ktlint/any real formatter
would, so their expected output changed from a two-line, same-depth rendering (the C.3-era stand-in
for "no continuation-indent model yet") to one collapsed line. This is the fix the C.3 entry
predicted, not a new limitation.

**Fixtures:** `testing/wrasse-test-harness/.../fixtures/format-line-breaks/`, its own `wrasse.json`
(`{"format": {"enabled": true, "maxLineLength": 50}}` — the config plumbing for a per-fixture style
override already existed since C.1/`WConfigSpec`, so no harness changes were needed here): a chain
that fits (`chain-fits-flat-error`, collapses a hard-broken source), one that doesn't
(`chain-exceeds-max-line-length-error`, breaks at every `.`, continuation indented one level), the
nested-group proof above (`nested-group-short-arglist-stays-flat-error`), a short and a long binary
expression (`binary-expression-short-clean` — `expect-clean`, already canonical; `binary-expression-
long-error` — breaks after `+`), and a short and a long argument list (`argument-list-short-clean` —
`expect-clean`; `argument-list-long-error` — one argument per line, closing paren dedented). Each
`-error` fixture has a `.fixed.kt` byte-exact companion; the existing, already-generic D19 idempotence
cycle (`fix(fix(x)) == fix(x)`, no harness changes needed since C.1) exercises all of them. `libs/
wrasse-format/DocBuilderSpec` gained matching unit tests driven directly against hand-built SAX
events (no compiler) for all three constructs, both flat and broken, elvis's reversed break side, and
the pre-existing-trailing-comma non-doubling case — these are what actually caught two real bugs
before the fixture ladder ever ran: a break/whitespace-token index collision that silently ate the
space after a non-elvis binary operator (`spliceBreak`'s "skip-then-maybe-insert" ordering), and the
`ChildEntry.Ws`-only whitespace check that only recognizes a newline-carrying gap, missing the
far-more-common plain-space gap in already-flat source (fixed to check `WHITE_SPACE` type generally,
not the newline-carrying subclass specifically).

**Not attempted, per the brief:** statement/declaration/blank-line wrapping, trailing-comma
insertion, and any construct beyond these three (parameter lists, `when` conditions, `if`/`while`
conditions, etc.) — all still reproduced verbatim, hard breaks only, exactly as before this slice.

**Ladder run for this slice:** `build`, `test --rerun-tasks`, `testMinorHarness --rerun-tasks`,
`testPatchHarness`, `wrasseLint -Prepublish` all green (398 fixture-spec cases per Kotlin minor —
391 plus this slice's 7 — zero failures; `wrasseLint` exercises the rebuilt plugin against this
repo's own `format`-disabled `wrasse.json`, unaffected by this slice).

#### Phase C.5 — Horizontal spacing normalization — **done 2026-07-21**

Replaces `DocBuilder`'s remaining verbatim whitespace reproduction (everything but indentation and
C.4's three Group-wrapped constructs) with normalized single-space/no-space emission for the ~13
pure-spacing concerns ktlint covers, per this slice's brief. Line-break positions outside C.4's
constructs are untouched — this is horizontal-only.

**Ground truth used:** ktlint's own rule implementations *and* their tests (`ktlint-ruleset-
standard`), not textbook Kotlin-style assumptions — `SpacingAroundOperatorsRule`,
`SpacingAroundUnaryOperatorRule`, `SpacingAroundCommaRule`, `SpacingAroundColonRule`,
`SpacingAroundParensRule`, `SpacingAroundSquareBracketsRule`, `SpacingAroundAngleBracketsRule`,
`SpacingAroundCurlyRule`, `SpacingAroundDotRule`, `SpacingAroundDoubleColonRule`,
`SpacingAroundRangeOperatorRule`, `SpacingAroundKeywordRule`,
`SpacingBetweenFunctionNameAndOpeningParenthesisRule`, `NullableTypeSpacingRule` (plus
`TypeArgumentListSpacingRule`/`TypeParameterListSpacingRule` for the asymmetric gap this slice
deliberately does not close, below).

**Mechanism — one choke point plus three specializations:**

1. **`normalizeChildren`** (new): the generic pass every frame [resolveFrame] doesn't already give a
   dedicated `Group`/break treatment to (chains, binary expressions, argument lists keep their own
   C.4 machinery) now routes through, replacing `children.map { resolveEntry(it) }`. For every gap
   between two direct children — whether an actual single-line `WHITE_SPACE` child or no child at
   all (two tokens directly adjacent, the insertion case) — `spacingDecision(frameType, prevType,
   nextType)` returns the exact rendered text, or `null` to preserve verbatim. A gap is *never*
   double-decided: an explicit `WHITE_SPACE` child is handled in one branch, an absent gap in the
   other, mutually exclusive by construction.
2. **`resolveUnaryFrame`** (new `resolveFrame` branch, alongside chain/binary/arglist):
   `PREFIX_EXPRESSION`/`POSTFIX_EXPRESSION` collapse every internal single-line whitespace to
   nothing, unconditionally — the frame-type dispatch itself is the unary-vs-binary
   disambiguation (matching ktlint's own parent-node-type check), so no token-level guessing is
   needed the way `SpacingAroundOperatorsRule` needs `isUnaryOperator()`/`isSpreadOperator()`.
3. **`normalizeLambdaBraces`** (new, called from `resolveBraceFrame` only for `FUNCTION_LITERAL`):
   normalizes the gap right after `{` and right before `}` — the one curly-brace concern in scope
   (ordinary `BLOCK`/`CLASS_BODY`/`WHEN` braces are always followed by a real line break in
   practice). Collapses to `{}` when nothing real sits between; otherwise exactly one space each
   side.
4. **`spliceBreak`** (existing, extended): the gap on the side of a chain/binary anchor that is
   *not* the break candidate now also gets normalized to the same `flat` text, as a plain `Doc.Text`
   — previously left to `resolveEntry`'s verbatim default, an asymmetry C.4 didn't need to notice
   because it never normalized flat-form spacing on both sides at once.

**The normalization table** (`spacingDecision`, one `when`-cascade, `null` = preserve verbatim):
no space before a comma, one space after (none before a closing delimiter); colon spacing keyed on
the *enclosing declaration* — one space both sides for a class/object's supertype-list colon, a
secondary constructor's delegation colon, or a generic type parameter's bound colon
(`COLON_WANTS_SPACE_BOTH_SIDES`), the declaration-style default (no space before, one after)
otherwise, and no space at all for an annotation use-site-target colon (`@field:JvmField`); one
space after `if`/`when`/`for`/`while`/`catch` regardless of what follows; a spread operator's `*`
tight to its argument; no space just inside `(`/`)`/`[`/`]`; none between a name and its parameter
or argument list — declaration *and* call site, one rule (`nextType == VALUE_PARAMETER_LIST ||
VALUE_ARGUMENT_LIST`), matching ktlint's own division of labor, except a `FUNCTION_TYPE`'s own
parameter list (may legitimately carry a preceding annotation) and a `FUNCTION_LITERAL`'s own
parameter list (its gap from `{` is `normalizeLambdaHead`'s job, not this rule's — see the bug
below); no space just inside `<`/`>` when the enclosing frame is itself a
`TYPE_PARAMETER_LIST`/`TYPE_ARGUMENT_LIST` (the same structural signal that leaves a comparison
`<`/`>` — a `BINARY_EXPRESSION` frame — untouched, mirroring how ktlint's own angle-bracket rule
and op-spacing rule each key off structural parent type, never the bare token); `::` tight *after*
always; `..`/`..<` tight both sides; no space before `?`.

**Disambiguation mechanisms — all structural (parent/frame node type), never token-text guessing,**
matching ktlint's own approach exactly:
- **Colon kind** — the `COLON` leaf's own enclosing frame type (`spacingDecision`'s `frameType`
  parameter, which *is* the declaration/expression node the colon is a direct child of).
- **Angle brackets vs comparison** — `frameType == TYPE_PARAMETER_LIST/TYPE_ARGUMENT_LIST` (angle
  brackets) vs `frameType == BINARY_EXPRESSION` (comparison, handled entirely by C.4's existing
  `resolveBinaryFrame`, never reaching `normalizeChildren` at all).
- **Unary vs binary** — `resolveFrame`'s own `when (frame.type)` dispatch (`PREFIX_EXPRESSION`/
  `POSTFIX_EXPRESSION` vs `BINARY_EXPRESSION`) is the disambiguation; no operator token is ever
  inspected to decide this.
- **Star: spread vs multiply vs star-import** — `frameType == VALUE_ARGUMENT && prevType == MUL`
  (spread) vs `BINARY_EXPRESSION` (multiply, handled by C.4) vs star-import (owned by
  `no-wildcard-imports`/`ImportEngine`, never visible to this mechanism at all).

**Two real bugs found only by running real fixtures through the full pipeline, not by reasoning
about the mechanism in isolation:**
- **A lambda's own parameter list got tightened against `{`.** The declaration/call-site "no space
  before a nested parameter/argument list" rule (`nextType == VALUE_PARAMETER_LIST`) fired for
  `names.forEach { name -> ... }` too — a lambda parameter list is *also* `VALUE_PARAMETER_LIST` in
  Kotlin's own grammar (confirmed off a real LightTree dump, not assumed), so `{name ->` lost its
  space instead of gaining one. Found by the pre-existing `format-indentation/already-correct`
  fixture (previously `expect-clean`) failing for the first time this slice ran the full ladder —
  exactly the kind of regression the ladder exists to catch. Fixed by excluding `FUNCTION_LITERAL`
  from that rule, alongside the pre-existing `FUNCTION_TYPE` exception.
- **A lambda body's `BLOCK` child is never absent, even when empty** — `names.forEach {}` is
  `[LBRACE, BLOCK(empty), RBRACE]`, never bare `[LBRACE, RBRACE]` (confirmed off a real LightTree
  dump). Treating only a literal `RBRACE`/`LBRACE` neighbor as "nothing here" made the empty case
  get a space inserted on *both* sides of the empty `BLOCK` (`{  }`) instead of collapsing to `{}`.
  The first fix attempt (checking `Doc.start == Doc.end` for "no real content") was itself wrong —
  it happened to work against real compiler offsets but misfired against every hand-built
  `DocBuilderSpec` fixture, where offsets default to `0` and every leaf looks "zero-width" by that
  test, silently breaking three pre-existing unit tests. The real fix checks the resolved `Doc`'s
  actual rendered content (`isEmptyDoc`: an empty `Text`, or a `Concat` whose parts are all empty),
  never source span — offset-independent, and correct in both the real-compiler and hand-built-test
  environments by construction rather than by coincidence.

**A §5.3-adjacent claim C.4 made that failed on contact:** `resolveBinaryFrame`'s flat-form gap was
one space unconditionally for every operator but `?:` (only the break *side* differs for elvis).
The range operator (`..`) is a `BINARY_EXPRESSION` in Kotlin's own grammar too — no separate
construct — so this generic default would have silently *spaced* `1..5` to `1 .. 5`, directly
contradicting `SpacingAroundRangeOperatorRule`'s "tight, unconditionally" ground truth. Fixed by
keying the flat text (not just the break side, which stays after the operator) off the operator's
own literal text, the same way elvis-detection already worked.

**Preserved verbatim, deliberately, for context this walk cannot cheaply provide (listed, not
guessed at):**
- The gap *before* `::` when it introduces a bound reference with no receiver (`foo(bar, ::isOdd)`)
  — ktlint's own rule preserves (collapses to one space, never strips to zero) rather than
  normalizes to a fixed 0-or-1 answer here, because stripping it can fuse the reference onto a
  preceding token with zero separation; distinguishing "bound, no receiver" from "has a receiver"
  needs a PSI/parent-chain fact (`CALLABLE_REFERENCE_EXPRESSION`, not currently in `WNodeType`) this
  walk doesn't have. The *after*-`::` gap has no such exception and is always tightened.
- `else`/`do`/`try`/`finally` keyword-to-brace/newline spacing — ktlint's `SpacingAroundKeywordRule`
  covers these too, but also collapses a `}\nelse`-style newline back onto one line, which is a
  line-*join* behavior this horizontal-only slice must not do; porting the space-only half without
  the line-join half was judged not worth the inconsistency it would introduce and left out of
  scope, per the brief's explicit keyword list (`if`/`when`/`for`/`while`/`catch` only).
- A `TYPE_PARAMETER_LIST`'s *outer* spacing (`fun <T> foo` wants one space both before and after
  `<T>`; `class Foo<T>` wants zero before) — a real ktlint behavior
  (`TypeParameterListSpacingRule`) beyond this slice's "no space *inside* angle brackets" ask;
  adding it asymmetrically for `FUN` only, without the matching `CLASS` case, was judged more
  likely to surprise than help, so both directions are left verbatim.
- `in`/`is`/`as`/`as?` as infix keyword-operators, named-argument `=` spacing, annotation-entry
  interior spacing beyond its colon, and a `FUNCTION_TYPE`'s legitimately-preceded-by-annotation
  parameter list gap (`@Composable () -> Unit`) — none named in this slice's 13-concern brief.
- A trailing lambda's own gap from its call (`fun name {`, no explicit parens) — not one of the 13
  concerns; whatever whitespace (or its absence) was already there stays as-is.
- `where T : Any` generic constraints — `TYPE_CONSTRAINT` is not a `WNodeType` wrasse currently maps
  at all (only `TYPE_PARAMETER`'s own bound colon, `<T : Any>`, is represented), so this shape isn't
  reachable by this mechanism yet; not a deliberate scope cut, a genuine mapping gap.

**Comments, strings, KDoc:** untouched by construction, same as C.1 — a comment/string-template
leaf is never a `WHITE_SPACE` token, so it is never a candidate for `normalizeChildren`'s decision
at all; its entire text, oddly-spaced interior included, rides one `Text` node unchanged.

**Fixtures:** `testing/wrasse-test-harness/.../fixtures/format-spacing/` (own `wrasse.json`,
`{"format": {"enabled": true}}`), 16 top-level `.kt` fixtures: one per concern with a `.fixed.kt`
companion (`comma-spacing-error`, `colon-declaration-spacing-error`,
`colon-supertype-and-generic-bound-spacing-error`, `paren-and-name-spacing-error`,
`bracket-spacing-error`, `angle-bracket-spacing-error`, `curly-lambda-spacing-error`,
`double-colon-spacing-error`, `range-spacing-error`, `keyword-spacing-error`,
`nullable-type-spacing-error`, `unary-operator-spacing-error`, `spread-operator-spacing-error`), a
kitchen-sink combining many at once (`kitchen-sink-error`), an already-canonical file
(`already-clean`, `expect-clean`, byte-identical), and a preservation fixture
(`comment-and-string-interior-preserved-error`: KDoc/line-comment/string-literal interiors —
including oddly-spaced text that reads like Kotlin code inside a string value — stay byte-exact
while a real spacing error elsewhere in the same file still gets fixed). The existing, already-
generic D19 idempotence cycle (`fix(fix(x)) == fix(x)`, no harness changes needed since C.1)
exercises all 14 `-error` fixtures. `libs/wrasse-format/DocBuilderSpec` gained 16 unit tests driven
directly against hand-built SAX events, covering every disambiguation case called out above
(colon kinds ×3, angle brackets vs comparison, unary vs binary, the lambda-parameter-list
regression directly) plus one test per remaining concern not otherwise exercised structurally.

**Ladder run for this slice:** `build`, `test --rerun-tasks`, `testMinorHarness --rerun-tasks`,
`testPatchHarness`, `wrasseLint -Prepublish` all green (414 fixture-spec cases per Kotlin minor —
398 plus this slice's 16 — zero failures; `wrasseLint` exercises the rebuilt plugin against this
repo's own `format`-disabled `wrasse.json`, unaffected by this slice).

#### Phase C.6 — Blank-line (vertical whitespace) policies — **done 2026-07-21**

Replaces `DocBuilder`'s remaining verbatim reproduction of blank-line *count* (every `WHITE_SPACE`
leaf containing `\n` had, until now, its exact newline count copied into the `HARD` `Break`'s
`literal`, per C.1) with a policy decision. Line-break *positions* are untouched — this slice only
changes how many consecutive `\n`s a break may carry.

**Preliminary (per the brief, folded into the same commit-unit): `where`-clause mapping and
spacing.** `TYPE_CONSTRAINT_LIST`/`TYPE_CONSTRAINT` (`WNodeType`/`WNodeTypeMapping`) and the
`where` soft keyword itself (`KW_WHERE`, `KtTokens.WHERE_KEYWORD`) were entirely unmapped — C.5
had only flagged the constraint colon as unreachable, but the keyword wasn't even a `WNodeType`.
A real LightTree dump (`fun <T> describe(x: T) where T : Any, T : Comparable<T> = ...`, checked
directly rather than assumed) confirmed `KW_WHERE` and `TYPE_CONSTRAINT_LIST` are both **direct
children of the declaring `FUN`** (the parser calls `advance()` over `WHERE_KEYWORD` *before*
`mark()`ing `TYPE_CONSTRAINT_LIST`, so the keyword — and the whitespace after it — never nest
inside the constraint list), which is exactly why `spacingDecision`'s existing frame-type
dispatch reaches the keyword with no new plumbing: `KW_WHERE` joined
`KEYWORDS_WANTING_SPACE_AFTER` (space after, matching `if`/`when`/`for`/`while`/`catch`), a new
`nextType == KW_WHERE -> " "` case covers the space *before* it (a keyword none of the other five
need, since `where` is never brace-adjacent), and `TYPE_CONSTRAINT` joined
`COLON_WANTS_SPACE_BOTH_SIDES` (ground-truthed against `SpacingAroundColonRule`'s own
`TYPE_CONSTRAINT -> true` case, spaced like a supertype-list or secondary-constructor colon, never
like a type-annotation colon). The constraint list's own comma spacing needed no new code — it was
already frame-agnostic. Locked by `format-spacing/where-clause-spacing-error` (fixture) and a new
`DocBuilderSpec` test driven off the confirmed real tree shape.

**Ground truth used:** ktlint's own rule implementations *and* tests (`ktlint-ruleset-standard`):
`NoConsecutiveBlankLinesRule`, `NoBlankLineBeforeRbraceRule`, `NoEmptyFirstLineInMethodBlockRule`,
`NoEmptyFirstLineInClassBodyRule`, `NoBlankLinesInChainedMethodCallsRule`,
`BlankLineBetweenWhenConditions`, `PackageImportSpacingRule` — plus detekt's
`SpacingAfterPackageAndImports` (its test suite is the one place that spells out the
import-list→first-declaration half ktlint's own `PackageImportSpacingRule` doesn't cover).

**Mechanism — one choke point, symmetric with C.5's horizontal one:** every `HARD` `Break` built
from a real `ChildEntry.Ws` (C.1: any `WHITE_SPACE` leaf containing at least one `\n`, not only a
"true" blank line) now goes through `clampWs(entry, newlineCount)`, which renders exactly
`newlineCount` newlines — reusing the original literal verbatim (byte-identical, trailing
blank-line whitespace included) when the count is already correct, or synthesizing a fresh
`"\n".repeat(newlineCount)` otherwise (the only path that can *add* a newline, not just remove
one). `newlineCount` itself comes from one of two dispatchers:
- **`normalizeChildren`** (already C.5's horizontal choke point) gained a parallel `ChildEntry.Ws`
  branch calling `verticalGapNewlineCount(frameType, prevEntry, nextEntry, isFirstAfterLbrace,
  ancestorHasFun, actual)` — the position- and context-aware policy table (below). This is where
  every rule that needs to know *which* gap this is (first-after-`{`, package/import boundary,
  class/constructor) is decided.
- **`resolveEntry`**'s fallback (every `ChildEntry.Ws` reached *without* going through
  `normalizeChildren` — a chain/binary expression's non-anchor whitespace, a unary frame's
  interior, an argument list's no-parens fallback) now applies only the **context-free default**
  (cap to at most one blank line), matching `NoConsecutiveBlankLinesRule`'s own unconditional
  `node.isWhiteSpace` check — ktlint's rule really does apply everywhere, not just inside a
  `BLOCK`/`CLASS_BODY`.
- **`no-blank-line-before-rbrace` is not a `normalizeChildren` case at all** — every `RBRACE` in
  Kotlin's grammar is the last child of a `resolveBraceFrame`-handled `INDENTING_TYPES` node, and
  the one whitespace that could precede it is always exactly the pre-existing `hasDedent` entry
  `resolveBraceFrame` already special-cases (C.1). That call site now clamps it to `newlineCount =
  1` directly, unconditionally, for all four indenting types (`BLOCK`, `CLASS_BODY`, `WHEN`,
  `FUNCTION_LITERAL`) in one place — no `normalizeChildren` branch could ever see this gap, since
  the dedent-plus-`RBRACE` pair is deliberately excluded from the sublist `normalizeChildren`
  processes.
- **`ancestorHasFun(frameType)`** (`frameType == BLOCK && frames.any { it.type == WNodeType.FUN
  }`) — ktlint's `isPartOf(FUN)` is an *unbounded* ancestor walk, not "direct parent," so a nested
  `if`/`else` block several levels inside a function body is in scope too (locked by a fixture with
  nested `if`/`else if`/`else`, matching `NoEmptyFirstLineInMethodBlockRuleTest`'s own "if-statement
  in a function" case exactly). `frames` (the still-open ancestor stack) already holds this for
  free at the moment a child frame resolves, since the child's own frame was already popped.

**The policy table** (`verticalGapNewlineCount`, `null`-free — every path returns a concrete count,
since "preserve verbatim" here means "the general default happens to already match"):

| Context | Newline count forced | ktlint rule |
|---|---|---|
| Any gap, no more specific rule applies | `min(actual, 2)` (at most one blank line) | `no-consecutive-blank-lines` |
| Gap right before a `BLOCK`/`CLASS_BODY`/`WHEN`/`FUNCTION_LITERAL`'s own closing `}` | `1` (zero blank lines), always | `no-blank-line-before-rbrace` |
| First gap after a `CLASS_BODY`'s own `{` | `1`, unconditionally | `no-empty-first-line-in-class-body` |
| First gap after a `BLOCK`'s own `{`, when any ancestor is `FUN` | `1` | `no-empty-first-line-in-method-block` |
| Class-name identifier → explicit `PRIMARY_CONSTRUCTOR` gap | `1` (zero blank lines, not one) | `no-consecutive-blank-lines`'s own special case |
| `PACKAGE_DIRECTIVE` (non-empty) → non-empty `IMPORT_LIST` | `2` (exactly one blank line, can *add*) | `package-import-spacing` |
| Non-empty `IMPORT_LIST` → whatever follows it | `2` (exactly one blank line, can *add*) | `spacing-after-package-and-imports` (detekt) |
| Blank line inside a dot/safe-access chain gap | already `0` (structural, no new code) | `no-blank-lines-in-chained-method-calls` |

**`no-blank-lines-in-chained-method-calls` needed no new code — confirmed, not assumed.** C.4's
`spliceBreak`/`wsBreakAt` already consume the chain-operator's adjacent whitespace (`ChildEntry.Ws`
or plain) into a `SOFT` `Break` whose `literal` field is never read in broken mode (`Layout.
renderBreak`'s `BreakKind.SOFT` branch only ever emits one `'\n'`, regardless of how many the
source had) — so a blank line inside a chain link's own gap was *already* being discarded, a side
effect of C.4's mechanism no one had written a fixture for yet. `format-blank-lines/no-blank-
lines-in-chained-method-calls-error` locks this directly (a long-enough receiver name to force the
chain broken under this fixture's own `maxLineLength: 60`, so the fix is visible independent of the
chain also collapsing flat) rather than merely asserting it by reasoning.

**Deliberately not implemented, and why (preserved-not-guessed, same discipline as C.5):**
- **`blank-line-between-when-conditions`'s *add* direction** (insert a blank line between every
  `WHEN_ENTRY` when any one of them has a multiline condition) is not ported. Its own ktlint default
  gates this behind `ij_kotlin_line_break_after_multiline_when_entry`, an `.editorconfig` property
  wrasse has no equivalent surface for (D12). The *removal* half needs no special code: it
  falls out of the generic "at most one blank line" default already applied inside a `WHEN` frame
  (`format-blank-lines/when-entries-blank-lines-error` locks this — two blank lines between
  `WHEN_ENTRY`s collapse to one, the same as anywhere else, never to zero).
- **`blank-line-before-declaration`, `spacing-between-declarations-with-annotations`, and
  `spacing-between-declarations-with-comments`** (the ADD-a-blank-line-before-a-declaration family)
  — see Phase C.11 below, which implements this family and its own preserved-not-guessed list.
- **Blank lines at the true start of a file.** The brief's brief assumed `no-consecutive-blank-lines`
  also strips leading blank lines; reading the rule source shows the opposite: it explicitly skips
  any whitespace whose `prevSibling` is `null` (`node.isWhiteSpace && node.prevSibling != null`),
  i.e. ktlint itself never touches file-leading blank lines. Implementing a stricter policy than
  upstream is exactly what "never broader than upstream" forbids, so this was **not** added —
  a discovery, not an oversight.
- **Blank lines at true end-of-file.** ktlint's own rule *does* collapse them (its `eof` branch
  removes every trailing blank line down to a bare `\n`, stricter than the generic one-blank-line
  cap), but two things make porting it not worth doing in this slice: `TrailingNewlineRule` already
  owns end-of-file behavior for wrasse (adding a missing final `\n`, never removing excess ones —
  "file-end handled by trailing-newline, don't fight that rule," per the brief) and the two never
  actually collide (they fire on disjoint shapes: a missing trailing newline has no `ChildEntry.Ws`
  at EOF at all, so there is nothing to over-clamp). More decisively: `FixtureParser.parse` trims
  every trailing blank source line from *every* fixture before it ever reaches the compiler (`while
  (sourceLines.isNotEmpty() && sourceLines.last().isBlank()) sourceLines.removeLast()`), so this
  shape is **structurally unexercisable by the harness** regardless of what `DocBuilder` does with
  it — confirmed by reading `FixtureParser`, not assumed. Left as the generic one-blank-line-max
  default (untested, unreachable by construction) rather than special-cased for a scenario that
  cannot be locked by a fixture anyway.
- **Package/import gaps joined on one physical line** (`package foo;import bar` with zero
  whitespace at all between them, or with only single-line, no-newline whitespace) — detekt's own
  test suite exercises this shape (`"package test;import a.b;class A {}"`), but `verticalGapNewlineCount`
  only fires when the gap is already a real `ChildEntry.Ws`; turning a same-line, semicolon-joined
  triple into three separate lines would also require deleting the now-redundant statement-separator
  semicolons — exactly the cross-rule coupling §5.1 confines to a real fused engine, not something
  this slice's choke point can decide alone. Bails (preserves verbatim), not guessed.

**A pre-existing C.4 bug found by running a real fixture (fixed in C.7):** `wrapRoot`'s "exclude a
trailing forced-break part from the fit-check/indent" logic assumed the forced-break part is
*trailing*. A raw multi-line string literal as the chain's own receiver
(`"""...multiple lines...""".trimIndent()`) is the forced-break part at `splitIdx == 0` — the first
part, not a trailing one — so `wrapRoot` produced an empty head `Group` and rendered the entire
chain, dot included, as a bare, unwrapped sibling at ambient depth instead of one level deeper. First
surfaced by `format-blank-lines/comment-and-string-interior-preserved-error`'s original draft; the
fixture was rewritten at the time to sidestep the bug rather than fix it. See Phase C.7 below for the
fix.

**Fixtures:** `testing/wrasse-test-harness/.../fixtures/format-blank-lines/` (own `wrasse.json`,
`{"format": {"enabled": true, "maxLineLength": 60}}` — lowered from the 140 default only so the
chained-method-calls fixture can force a genuinely broken chain without inflating every other
fixture's identifiers; verified every other fixture's real content stays well under 60 chars, so
the lower width changes nothing else), 12 fixtures: `no-consecutive-blank-lines-error`,
`no-blank-line-before-rbrace-error`, `no-empty-first-line-in-method-block-error` (fun body plus a
nested `if`/`else if`/`else` chain, all three first-lines stripped), `lambda-first-line-preserved-
error` (the same construct's *lambda* counterpart, deliberately left alone — `FUNCTION_LITERAL` is
never in scope — alongside a real, unrelated excess blank line in the same file so the fixture still
exercises a genuine fix), `no-empty-first-line-in-class-body-error` (nested class body too),
`class-primary-constructor-blank-line-error`, `package-import-spacing-error` (both directions: a
missing blank line forced in, an excess one capped to exactly one), `no-blank-lines-in-chained-
method-calls-error`, `when-entries-blank-lines-error` (the generic cap, not the excluded add/remove-
all behavior), `kitchen-sink-error` (all of the above at once in one file), an already-canonical
`already-clean` (`expect-clean`, byte-identical), and `comment-and-string-interior-preserved-error`
(blank comment lines inside a KDoc block and inside a raw string literal both stay byte-exact while
a real excess blank line elsewhere in the same file still gets capped). Plus one new fixture in
`format-spacing/` for the preliminary `where`-clause work (`where-clause-spacing-error`). The
existing, already-generic D19 idempotence cycle (`fix(fix(x)) == fix(x)`, unchanged since C.1)
exercises all 12 `-error`/kitchen-sink fixtures. `libs/wrasse-format/DocBuilderSpec` gained 9 unit
tests: the `where`-clause spacing case (driven off the real, dumped tree shape); the generic
more-than-one-blank-line cap; the before-`}` strip; the method-block and class-body first-line
strips; the lambda-first-line preservation (built as a `BLOCK` inside a `FUN` *containing* a
`FUNCTION_LITERAL`, to prove the exclusion is about frame type, not "inside a function" broadly);
the class/primary-constructor case; and two package/import cases (forced insertion, and the
`entryHasContent` guard proving an empty import list forces nothing — this second test's tree shape
was corrected against a real compiler dump after an initial hand-built version assumed an
impossible split-whitespace shape around an empty `IMPORT_LIST`, caught before it shipped as a
wrong assertion rather than a real bug).

**No existing fixture's `.fixed.kt` needed updating.** Checked structurally (not assumed): every
`format-*`/`bracing-format-*` fixture already has exactly one blank line after its `package`
statement (with or without imports) and no blank-line-before-rbrace/empty-first-line/class-
constructor shapes anywhere — grepped across all six pre-existing format-enabled fixture
directories before writing a single new fixture, confirmed clean, and the full ladder re-confirms
it (`git status` on all of them stays clean after this slice).

**Ladder run for this slice:** `build`, `test --rerun-tasks`, `testMinorHarness --rerun-tasks`,
`testPatchHarness`, `wrasseLint -Prepublish` all green (427 fixture-spec cases per Kotlin minor —
414 plus this slice's 13 — zero failures; `wrasseLint` exercises the rebuilt plugin against this
repo's own `format`-disabled `wrasse.json`, unaffected by this slice). `ktlint`/`detekt` checkouts
used for ground truth confirmed byte-clean (`git status`) throughout — read-only.

#### Phase C.7 — Function-signature wrapping, plus the wrapRoot receiver bug fixed — **done 2026-07-21**

**Part 1 — the wrapRoot receiver bug, failing-first.** Reproduced the C.6 note directly: a
`DocBuilderSpec` case building `"""..."""`.trimIndent()` as a root chain (hand-built SAX events, no
compiler) rendered `.trimIndent()` flush at column 0 instead of one level deeper, confirmed failing
against the pre-fix code (`AssertionFailedError`, actual `"""\n    line one\n    line two\n"""\n.trimIndent()`
vs expected with 4-space indent). A matching integration fixture
(`format-line-breaks/chain-multiline-string-receiver-error`) reproduced the identical defect through
the real compiler and LightTree (`.trimIndent()` at 4 spaces instead of 8 inside a function body),
confirmed failing by temporarily reverting the fix and re-running the fixture spec (test count went
427 → 428, so the fixture was genuinely picked up, not vacuously passing).

**Root cause:** `wrapRoot`'s split-point predicate (`containsForcedBreak`, now `hasOwnIndentScope`)
treated a plain multi-line `Doc.Text` (a raw string/KDoc token) the same as a `Doc.Break(HARD)` —
both "forced the split." That equivalence is wrong: a `Doc.Break(HARD)` implies a nested `Doc.Indent`
scope of its own (a lambda body's block, which would double-indent if wrapped in another `Indent`),
while a plain multi-line `Text` carries no `Indent`/`Break` of its own — wrapping it in the shared
`Group`/`Indent` has no effect on its interior (`Layout` only re-indents at a `Break`, never inside a
`Text`'s own characters) and `Layout.flatWidth` (unchanged) still forces that `Group` broken on its
own whenever the embedded newline is present. The fix: `hasOwnIndentScope` returns `false` for
`Doc.Text` unconditionally, checking only for an actual `Doc.Break(HARD)` (nested through
`Indent`/`Group`/`Concat`). With this change, a receiver-only forced break no longer produces an
empty head group — `wrapRoot`'s `splitIdx < 0` branch wraps the whole chain in one `Group`/`Indent`
as usual, and `Layout.flatWidth`'s existing null-propagation still renders it broken, at the correct
one-level-deeper depth. Verified against both the one-link (`""".."""`.trimIndent()`) and two-link
(`""".."""`.trimIndent().length`) shapes by hand-tracing the `Doc` construction; the original C.4
trailing-lambda case is untouched (a lambda body's interior always contains a real `Doc.Break(HARD)`,
so `hasOwnIndentScope` still finds it).

**Before/after** (`format-line-breaks/chain-multiline-string-receiver-error`, inside a function body,
indent width 4):
```kotlin
// before (bug): flush, wrong depth
    val x = """
        line one
        line two
    """.trimIndent()
// after (fixed): one level deeper
    val x = """
        line one
        line two
    """
        .trimIndent()
```

**Part 2 — function-signature wrapping.** `DocBuilder` gains a `WNodeType.VALUE_PARAMETER_LIST`
branch in `resolveFrame`, scoped to a `WNodeType.FUN`'s own parameter list (`parentType == FUN`,
mirroring ktlint's `function-signature` rule's own `node.elementType == FUN` scope): a primary/
secondary constructor's, a `FUNCTION_TYPE`'s, or a lambda's parameter list (also
`VALUE_PARAMETER_LIST` in Kotlin's own grammar) is untouched.

**Mechanism (`resolveValueParameterListFrame`):** identical break points to
`resolveArgumentListFrame` — right after `(`, right after every comma with another parameter
following it, right before `)` — wrapped in one `Doc.Group`/`Doc.Indent`, reusing `Layout`'s existing
fit-check machinery (no parallel mechanism). The break `kind` is decided once per parameter list:
`HARD` (always split, one parameter per line) when the parameter count is at least
`FormatStyle.multilineSignatureThreshold`, or when any parameter's own text already spans multiple
lines (ktlint's `hasMinimumNumberOfParameters() || containsMultilineParameter()`, ground-truthed
against `FunctionSignatureRule`); `SOFT` (fit-dependent, inside the `Group`) otherwise — `Layout`'s
unchanged fits-check then joins an already-wrapped signature back onto one line whenever it fits, or
keeps a single-line signature wrapped whenever it doesn't, exactly the same join/break duality
`resolveArgumentListFrame` already exercises for call sites. A comment (`EOL_COMMENT`/`BLOCK_COMMENT`)
found directly inside the parameter list bails the whole decision to `passthroughParameterList`
(verbatim, spacing-only) — ktlint's own rule refuses the entire signature rewrite whenever any
comment appears anywhere in the signature; this narrower, structurally-reachable half (a comment
*inside* the parameter list only — a comment on the return type or modifier list is not visible from
this frame and is not detected) is preserved-not-guessed, not a claim of full parity.

**A pre-existing indentation gap found and fixed alongside this slice:** `passthroughParameterList`
(the fallback for every case above) previously never gave `VALUE_PARAMETER_LIST` an indent scope at
all — a side effect of C.1's foundation, which only ever indented `{`/`}`-delimited `INDENTING_TYPES`
nodes. An already-multi-line parameter list reaching this fallback (a constructor, a lambda, or a
`FUN` bailing on a comment) rendered every continuation line at the *ambient* depth instead of one
level deeper, discovered by the very first comment-bail fixture written for this slice
(`comment-in-parameter-list-bail-error`: `a: Int, // keep this comment` lost its 4-space indent
entirely). Fixed generally, not narrowly: `passthroughParameterList` now wraps its interior in one
`Doc.Indent` and dedents the line holding the closing `)`, gated on the same "own last child is the
closing delimiter, preceded by a real newline" structural check `resolveBraceFrame` already uses for
`}` — applying uniformly to every parameter list that reaches this fallback (constructors, lambdas,
`FUNCTION_TYPE`s, and a `FUN`'s own comment-bailed list alike), not special-cased to the one fixture
that surfaced it. No existing fixture exercised a multi-line `VALUE_PARAMETER_LIST` before this slice
(confirmed by grepping every fixture directory for an opening `(` immediately followed by a newline —
the two hits found were both call-site `VALUE_ARGUMENT_LIST`s), so this fix changes no existing
fixture's expected output.

**What `multilineSignatureThreshold: Int = 1` actually produces:** the schema's own description
("parameter count at or above which a signature is forced multiline. Default 1") means, read
literally, *any* function with one or more parameters is force-wrapped, unconditionally, regardless
of whether it fits — confirmed by running the full fixture ladder with the default value unchanged:
every pre-existing `format-*`/`bracing-format-on` fixture containing a `FUN` with one or more
parameters (`comma-spacing-error`'s `fun add(a: Int, b: Int, c: Int): Int`,
`nested-class-fun-if-lambda`, `multiline-if-body-braced-error`'s helper functions, and 20 more) failed
by rewriting a short, already-idiomatic one-line signature into one-parameter-per-line — the exact
"absurd, forced multiline repo-wide" outcome the task brief anticipated. This is not a mechanism bug:
`resolveValueParameterListFrame` correctly implements "paramCount >= threshold forces multiline" per
the value it's given; the *value* itself is almost certainly not what a user would want as a repo-wide
default (ktlint ships this feature *off* by default — `Int.MAX_VALUE`/`unset` — turning it on only
under its own `ktlint_official` code style, at `2`, not `1`). **Not changed here** (FormatStyle's
default is a product decision, not a mechanism one, and the task scope explicitly excludes changing
it): the 6 affected fixture directories instead got an explicit
`"multilineSignatureThreshold": 999999` override in their own `wrasse.json` (the same per-directory
style-override pattern already used for `maxLineLength`), isolating each directory's own concern from
this slice's absurd-by-default count trigger without touching the class default. **Flagged for owner
input:** whether `1` should become `2` (matching ktlint's own opinionated-style choice) or some other
value, or whether the "doesn't fit" half alone should be the real repo-wide default with the
count-based half opted into per-project.

**Preserved-not-guessed, listed:**
- A comment elsewhere in the signature (return type, modifier list) — not visible from
  `VALUE_PARAMETER_LIST`'s own frame, so not detected; only a comment directly inside the parameter
  list is.
- An annotated parameter forcing multiline under a `ktlint_official`-style code style — no equivalent
  axis exists in wrasse's single, fixed style (D21 erased the code-style meta-knob entirely), so this
  ktlint condition has no analog and is not ported.
- The `=`/expression-body boundary (ktlint's `fixFunctionBodyExpression`: whether the body's first
  line merges onto the same line as the signature, `functionBodyExpressionWrapping`'s three policies)
  is not implemented — out of this slice's scope (parameter-list wrapping only), not a silent gap: the
  `=`/body gap still gets only the pre-existing generic spacing/blank-line treatment, never rewrapped.
- Trailing-comma interaction: a wrapped parameter list whose source had no trailing comma stays
  without one (the closing break lands right after the last parameter, exactly as
  `resolveArgumentListFrame` already does for call sites); a source that already had a trailing comma
  keeps it, since a comma already followed by nothing but whitespace gets no break of its own
  (doubling-up avoided the same way). Trailing-comma *insertion* remains a later slice's job
  (`FormatStyle.trailingCommas` stays unconsumed). **Resolved in Phase C.8** — comma presence for
  this exact list now follows the group's own broken-vs-flat choice (`Doc.TrailingComma`), so a
  source's own choice no longer matters at all.

**Existing-fixture changes:** none required a `.fixed.kt` update. Six directories
(`bracing-format-on`, `format-blank-lines`, `format-indentation`, `format-line-breaks`,
`format-spacing`, and the new `format-signatures`' own default) needed a `multilineSignatureThreshold`
override in their `wrasse.json` for the reason above; no fixture source or `.fixed.kt` content
changed.

**Fixtures:** the Part 1 fixture lives in `format-line-breaks/` (a chain-wrapping concern, alongside
its C.4 siblings). A new `testing/wrasse-test-harness/.../fixtures/format-signatures/` directory (own
`wrasse.json`, `{"format": {"enabled": true, "maxLineLength": 40, "multilineSignatureThreshold": 3}}`
— a small `maxLineLength` so fit-based wrapping is exercisable without inflating fixture identifiers),
8 fixtures: `threshold-forces-multiline-error` (3 short parameters, forced by count alone),
`join-collapses-multiline-error` (an already-wrapped 2-parameter signature collapses once it fits
below the threshold), `fit-based-force-error` (2 parameters, below threshold, wrapped only because the
flat signature exceeds `maxLineLength`), `multiline-parameter-content-forces-error` (a single
lambda-default parameter whose own body spans multiple lines forces wrapping regardless of count or
fit), `primary-constructor-untouched-clean` (`expect-clean`: a 3-parameter primary constructor, which
would be forced if it were a `FUN`, stays untouched and single-line), `comment-in-parameter-list-bail-
error` (the comment bail, verbatim parameter list, alongside a real, unrelated excess-blank-line fix
in the same file proving the file is still processed), `kitchen-sink-error` (all of the above
combined in one file), and `already-clean` (`expect-clean`: a threshold-forced signature that already
has its trailing comma and correct indentation, a below-threshold single-line signature, an empty
parameter list, and an untouched constructor, all byte-identical). The existing, already-generic D19
idempotence cycle exercises all 6 `-error`/kitchen-sink fixtures. `libs/wrasse-format/DocBuilderSpec`
gained 8 unit tests: the Part 1 wrapRoot fix (hand-built one-link chain), and 7 for the Part 2 policy
(threshold-forced, fit-based-flat, fit-based-join, fit-based-break, non-`FUN` untouched, comment bail,
multiline-parameter-content force) — driven directly against hand-built SAX events, no compiler.

**Ladder run for this slice:** `build`, `test --rerun-tasks`, `testMinorHarness --rerun-tasks`,
`testPatchHarness`, `wrasseLint -Prepublish` all green (436 fixture-spec cases per Kotlin minor — 427
plus Part 1's 1 plus Part 2's 8 — zero failures; `wrasseLint` exercises the rebuilt plugin against
this repo's own `format`-disabled `wrasse.json`, unaffected by this slice). `ktlint` checkout used for
ground truth confirmed byte-clean (`git status`) throughout — read-only; `detekt` was not needed for
this slice.

#### Phase C.8 — Trailing-comma emission — **done 2026-07-21**

`FormatStyle.trailingCommas` (D21, previously unconsumed) becomes real behavior: the printer now
adds or removes a trailing comma on every multi-line list construct it recognizes, keyed off that
one flag rather than hardcoded on. Ground-truthed against the local `ktlint` checkout's
`trailing-comma-on-declaration-site`/`trailing-comma-on-call-site` rules (source + tests, read-only,
confirmed byte-clean via `git status` throughout, never the internet); the checkout's own construct
lists, per-construct multi-line definitions, and bail conditions are mirrored below, not ported
verbatim (a later slice backfills tests directly from that source).

**Mechanism — two mechanisms, not one, depending on whether the printer already reflows the list:**

1. **Comma-iff-broken (dynamic), one new `Doc` node.** For the two constructs C.4/C.7 already
   wrap in a `Doc.Group`/`Doc.Indent` and can join or break based on fit (`VALUE_ARGUMENT_LIST`,
   and a `FUN`'s own `VALUE_PARAMETER_LIST`), the trailing comma's presence must follow *that same
   group's* eventual flat-vs-broken choice, not a decision made when the `Doc` tree is built —
   `Layout` renders the Group before the answer exists. New leaf `Doc.TrailingComma(start, end)`
   (`Doc.kt`): `Layout.renderNode` appends `,` when the enclosing mode is `BROKEN`, nothing when
   `FLAT` — the same `mode`-threading `Doc.Break`'s `flat`/broken split already uses, so no new
   plumbing; `Layout.flatWidth` counts it as `0`, so it never affects a fits-check (a would-be comma
   never makes an otherwise-fitting line "not fit"). `resolveArgumentListFrame`/
   `resolveValueParameterListFrame`'s interior-building loop (`DocBuilder.kt`) now detects the
   existing trailing comma's index (`trailingCommaIndex` — a `COMMA` with nothing but whitespace
   before the closer), skips emitting it as literal text, and appends one `Doc.TrailingComma`
   (`addDynamicTrailingComma`) reusing that comma's own span if one existed, or a zero-width point
   right after the last element otherwise. One node, one mechanism, covers both the `HARD` (count-
   or content-forced, C.7) and `SOFT` (fit-dependent) break-kind cases uniformly — no special-casing
   needed for "statically-known-broken", since `Doc.TrailingComma`'s `BROKEN` rendering already
   covers a `HARD`-forced group (whose `flatWidth` is always `null`) for free.
2. **Static (build-time), for every list the printer renders verbatim.** `TYPE_PARAMETER_LIST`,
   `TYPE_ARGUMENT_LIST`, `DESTRUCTURING_DECLARATION`, `WHEN_ENTRY`'s condition list, and a non-`FUN`
   `VALUE_PARAMETER_LIST` (constructor, `FUNCTION_TYPE`, lambda) are never reflowed — `Layout` never
   decides a break for them, so their comma decision can be made once, at `DocBuilder` build time,
   from the construct's own already-fixed multi-line-ness. Shared helper `applyTrailingComma(children,
   fromIdx, closeIdx)`: multi-line iff any child in that range already carries a hard break or an
   embedded multi-line `Text` (`isMultilineEntry`, reusing the existing `spansMultipleLines`); if
   multi-line and no trailing comma exists, splice in a synthetic `ChildEntry.Resolved(COMMA, ...)`
   right after the last real element (`insertTrailingComma`); if single-line and one exists, drop
   that `ChildEntry` (`removeTrailingComma`) and let the pre-existing `normalizeChildren` spacing
   table (already flush against `RPAR`/`GT`) clean up the gap — no new spacing rule needed.
   `closeIdx` need not be a real delimiter's index: a lambda's own parameter list has no `RPAR` of
   its own (`{ a, b -> }`), so its call site passes `children.size`, reusing the identical function.

**Dispatch (`resolveFrame`):** `TYPE_PARAMETER_LIST`/`TYPE_ARGUMENT_LIST` → `resolveAngleListFrame`
(anchor: the list's own `GT`); `DESTRUCTURING_DECLARATION` → `resolveDestructuringFrame` (anchor:
its own `RPAR`); `WHEN_ENTRY` → `resolveWhenEntryFrame` (anchor: its own `ARROW`); all three
preprocess `frame.children` via `applyTrailingComma` then delegate to the pre-existing
`resolveBraceFrame` for everything else (spacing, verbatim line breaks — unchanged). A non-`FUN`
`VALUE_PARAMETER_LIST` gets the same treatment inside the pre-existing `passthroughParameterList`
fallback, so a comment-bailed `FUN` parameter list (C.7's own bail) *also* gets the static comma
decision on that same fallback path — ktlint's own rule doesn't bail on comments either, since
adding/removing one comma character is orthogonal to the wrapping decision the comment bail exists
to protect.

**Construct coverage:**

| Construct | Site | Mechanism | Anchor |
|---|---|---|---|
| `VALUE_ARGUMENT_LIST` | call | dynamic | own `RPAR` |
| `VALUE_PARAMETER_LIST` (parent `FUN`) | declaration | dynamic | own `RPAR` |
| `VALUE_PARAMETER_LIST` (parent constructor/`FUNCTION_TYPE`/lambda) | declaration | static | own `RPAR`, or list end for a lambda |
| `TYPE_PARAMETER_LIST` | declaration | static | own `GT` |
| `TYPE_ARGUMENT_LIST` | call | static | own `GT` |
| `DESTRUCTURING_DECLARATION` | declaration | static | own `RPAR` |
| `WHEN_ENTRY` condition list | declaration | static | own `ARROW`, bailed per below |

**Both directions, both mechanisms:** add-on-multiline and remove-on-single-line are the same
`if (multiline && no-comma) insert else if (!multiline && comma-exists) remove` shape for the
static path, and the same `Doc.TrailingComma` resolving to `,` or `""` for the dynamic path — there
is no separate "removal" code path to independently get wrong.

**The `FormatStyle.trailingCommas = false` case:** `addDynamicTrailingComma` no-ops entirely (no
node emitted, so an existing source comma is dropped since it's excluded from the interior loop
regardless) and `applyTrailingComma`'s `!style.trailingCommas` branch removes an existing comma
unconditionally, never inserting one — the mechanism reads the flag at every call site, it is never
assumed true. Covered by four `DocBuilderSpec` unit tests, not a fixture (the project's own default
is always `true`; a `trailingCommas: false` fixture would need its own directory-level override for
a case with no repo-wide use).

**Preserved-not-guessed, listed:**
- `COLLECTION_LITERAL_EXPRESSION` (an annotation's array-literal argument) and `INDICES` (`a[i, j]`)
  — both are call-site constructs in the checkout's rule, but neither has a `WNodeType` mapping in
  wrasse's adapter today (both resolve to `UNKNOWN`); adding the mapping is an adapter-layer change
  out of this slice's scope, not a trailing-comma decision.
- `CLASS` enum-entry bodies — excluded entirely, not partially. The checkout's own rule sometimes
  needs to *insert a semicolon* alongside the comma (when other class members follow the enum
  entries and none exists yet); the printer's own contract (§5.3) permits removing a *provably-
  redundant* separator semicolon, never inserting a new one. Splitting the enum case into "the
  simple sub-case that never needs a semicolon" and "the one that does" was considered and rejected
  as an inconsistent half-rule (whether a body's trailing comma gets fixed would depend on unrelated
  class members downstream of the entries); deferred whole, flagged for owner input alongside a
  semicolon-insertion policy decision.
- Lambda-parameter trailing comma is decided from the parameter list's own span alone, not the
  checkout's tighter `[paramList, arrow]` closed range: the two are one and the same `Doc` subtree
  in the checkout's own AST, but wrasse's single-pass `DocBuilder` resolves `VALUE_PARAMETER_LIST`
  into one opaque `Doc` at its own `exitNode`, before `FUNCTION_LITERAL` (the parent frame) ever
  sees the `ARROW` sibling — there is no cross-frame lookahead in a streaming builder. Consequence:
  a lambda whose parameters are single-line but separated from the arrow by a newline
  (`{ a, b\n    -> }`) is not detected as multi-line by wrasse, though the checkout's own rule would
  flag it. Judged an acceptable, narrow gap given the alternative (buffering `FUNCTION_LITERAL`'s
  children unresolved) is a materially bigger structural change for one rare source shape.
- A comment anywhere in a `TYPE_PARAMETER_LIST`/`TYPE_ARGUMENT_LIST`/`DESTRUCTURING_DECLARATION`/
  `WHEN_ENTRY`/non-`FUN` parameter list has no special bail at all (unlike C.7's own comment bail
  for a `FUN`'s reflowed signature): since none of these are reflowed, a comment inside one is
  already preserved verbatim by the pre-existing spacing-only path, and the trailing-comma decision
  composes with that unchanged.

**Existing-fixture changes**, each a direct, mechanical consequence of the policy above — a
previously-shipped multi-line, comma-less `FUN`/call-site list now gets one (all six are the
dynamic mechanism's `Doc.TrailingComma` resolving to `,` for the first time, not a new wrapping
decision):
- `format-signatures/threshold-forces-multiline-error.fixed.kt`,
  `format-signatures/kitchen-sink-error.fixed.kt` — `c: Int` → `c: Int,` (count-threshold-forced
  `FUN` signature).
- `format-signatures/fit-based-force-error.fixed.kt` — `ageParameter: Int` → `ageParameter: Int,`
  (fit-forced signature).
- `format-signatures/multiline-parameter-content-forces-error.fixed.kt` — the closing `}` of a
  multi-line lambda-default parameter gains a comma (`    }` → `    },`; content-forced signature).
- `format-signatures/comment-in-parameter-list-bail-error.fixed.kt` — `b: Int` → `b: Int,`: this one
  is the *static* mechanism (comment bail routes to `passthroughParameterList`), confirming the
  "comment bail only blocks rewrapping, not the comma decision" design point above with a real,
  previously-shipped fixture.
- `format-line-breaks/argument-list-long-error.fixed.kt` — `"second argument is long"` →
  `"second argument is long",` (fit-forced call-site argument list).

No other existing fixture changed: every other `format-*`/`bracing-format-on` directory was grepped
for a multi-line argument/parameter list, a multi-condition `when`-entry, a destructuring
declaration, or a multi-item generic list (angle-bracket or type-argument) and none exists beyond
the six above (`if-else-bracing`/`when-entry-bracing`'s own multi-line call-argument fixtures don't
enable `format` at all, so `DocBuilder` never runs there — untouched by construction, not by luck).

**Fixtures:** new `testing/wrasse-test-harness/.../fixtures/format-trailing-commas/` directory (own
`wrasse.json`, `maxLineLength: 40`/`multilineSignatureThreshold: 999999` — small enough to exercise
the dynamic mechanism's fit-driven path without the count-threshold interfering), 15 fixtures: a
fit-driven add and a join-that-removes pair for `VALUE_ARGUMENT_LIST`; an add and a remove pair each
for `TYPE_PARAMETER_LIST`, `DESTRUCTURING_DECLARATION`, and a `FUN`-external `VALUE_PARAMETER_LIST`
(constructor); one add fixture each for `TYPE_ARGUMENT_LIST`, `WHEN_ENTRY`, `FUNCTION_TYPE`'s own
parameter list, and a lambda's own parameter list (the last confirming empirically that
`FUNCTION_TYPE`'s unnamed, type-only parameters still register as content for `applyTrailingComma`,
without needing to assume a specific PSI shape for them); a `when-entry-no-subject-bail-clean`
`expect-clean` fixture isolating the subject-less bail (a single reference-expression condition
whose own line break sits between it and the `ARROW`, so nothing else in `DocBuilder` — no chain,
no binary expression, no argument list — could confound the result); a kitchen-sink combining four
constructs in one file; and an `already-clean` canonical file exercising all four already-correct
states (dynamic-broken-with-comma, dynamic-flat-without-comma, static-multiline-with-comma, static-
single-line-without-comma). All 15 fixtures' expected outputs were captured from the real compiler-
driven harness (not hand-derived) via a temporary, since-removed scratch spec, then re-verified by
running the committed fixture suite itself. `libs/wrasse-format/DocBuilderSpec` gained 11 unit
tests: 4 for the dynamic mechanism (join-drops-an-existing-comma, and `trailingCommas = false`
suppressing/stripping in both a fit-forced and a plain-broken list) and 6 for the static mechanism
across `TYPE_PARAMETER_LIST` (add/remove), `DESTRUCTURING_DECLARATION` (add), a constructor
`VALUE_PARAMETER_LIST` (add), and the `WHEN_ENTRY` subject-less bail, hand-built against `DocBuilder`
directly with no compiler.

**Ladder run for this slice:** `build`, `test --rerun-tasks`, `testMinorHarness --rerun-tasks`,
`testPatchHarness`, `wrasseLint -Prepublish` all green (451 fixture-spec cases per Kotlin minor —
436 plus this slice's 15 — zero failures). `ktlint` checkout confirmed byte-clean (`git status`)
throughout — read-only; `detekt` was not needed for this slice.

#### Phase C.9 — Class-signature wrapping and annotation placement — **done 2026-07-21**

**Part 1 — class-signature wrapping.** Two independent extensions, both reusing existing machinery
rather than adding a parallel one.

**1a. Primary-constructor parameter list joins `FUN`'s own mechanism.**
`resolveValueParameterListFrame`'s scoping guard (`parentType != FUN` routed everything else to
`passthroughParameterList`, per C.7) now also accepts `WNodeType.PRIMARY_CONSTRUCTOR`: the identical
threshold/multiline-parameter/comment-bail decision C.7 built for `FUN` applies verbatim to a
class's own parameter list, one shared code path, no branch duplication. A secondary constructor, a
`FUNCTION_TYPE`, and a lambda's own list are still routed to `passthroughParameterList` unchanged.
**Trailing-comma composition, proven, not assumed:** since `PRIMARY_CONSTRUCTOR` now reaches the same
`Doc.Group`/`Doc.TrailingComma` construction `FUN` already used (C.8's dynamic, comma-iff-broken
mechanism — §13 Phase C.8), the comma decision follows automatically, with zero new code;
`format-class-signatures/primary-constructor-trailing-comma-join-removes-error` proves the join
direction (a source with an existing trailing comma collapses to one line, dropping it, once it fits
below the threshold) alongside `threshold-forces-multiline-error`/`fit-based-force-error` (already
proving the add-on-break direction, mirroring C.8's own FUN-side fixtures).

**1b. Supertype-list wrapping (`resolveSuperTypeListFrame`, new `WNodeType.SUPER_TYPE_LIST` branch),
scoped to `parentType == CLASS`** — `WNodeType.OBJECT_DECLARATION`'s own supertype list (same node
type, different parent) falls through untouched to `resolveBraceFrame`, matching ktlint's own
`class-signature` rule scope (`CLASS` only). Bails the same way on a comment anywhere in the list.
One supertype: joins the primary constructor's own closing line unconditionally when that
constructor already spans multiple lines (`ctorWrapped`); otherwise wrapped in its own `Doc.Group` —
`HARD` when the supertype's own text already spans multiple lines, `SOFT` (fit-dependent,
Layout-decided) otherwise, the identical break-kind choice `resolveValueParameterListFrame` already
makes. Two or more supertypes: always broken, one per line, one `Doc.Indent` level deeper than the
class, comma-separated (reusing each source comma's own span) — never fit-dependent, mirroring
ktlint's own unconditional (not-fit-based) rule for the 2+ case. The first supertype joins the
constructor's closing line only when `ctorWrapped`; otherwise the colon ends that line and every
supertype, including the first, starts its own line.

**`ctorWrapped` mechanism — a same-frame peek, no new plumbing.** By the time `SUPER_TYPE_LIST`'s own
`exitNode` fires, the enclosing `CLASS` frame is still open on the walk's frame stack (its own
`exitNode` hasn't fired yet) and already holds `PRIMARY_CONSTRUCTOR`'s fully-resolved `Doc` as one of
its buffered children — `resolveSuperTypeListFrame` reads it via `frames.lastOrNull()`, the same
open-ancestor-peek idiom `ancestorHasFun` already established, then asks `spansMultipleLines` (an
existing helper, unchanged) whether that `Doc` contains a `HARD` break. This is exact for every
build-time-decidable case (count/multiline-parameter-forced, the comment-bail passthrough preserving
a real source newline, and the empty-parameter-list case) and a documented approximation for the one
case that genuinely cannot be decided before `Layout` runs: a `SOFT`-kind constructor group whose
fit is still undetermined reads as `ctorWrapped = false` here, since `spansMultipleLines` only
detects an already-committed `HARD` break, never a pending fit decision.

**The colon→supertype-list gap — a `Frame`-flag handshake, not a blanket rule.** The single space (or
break) between `CLASS`'s own `COLON` and its `SUPER_TYPE_LIST` child needs to move *inside*
`SUPER_TYPE_LIST`'s own `Doc` (so it can become a breakable `Doc.Break` rather than static text), but
`normalizeChildren`'s generic per-pair spacing table has no way to know, from the `CLASS` frame's own
children alone, whether the adjacent `SUPER_TYPE_LIST` actually supplies its own lead (the bailed
path never does). A new `Frame.ownsSuperTypeListLeadGap` boolean (default `false`), set by
`resolveSuperTypeListFrame` the moment it commits to *not* bailing (on the same still-open `CLASS`
frame the `ctorWrapped` peek already reads), is threaded into `normalizeChildren` as an explicit
`suppressSuperTypeListLeadGap` parameter from `resolveBraceFrame`'s two call sites; only when both
the flag is set *and* the adjacent pair is literally `(COLON, SUPER_TYPE_LIST)` does the gap — real
`Ws`, plain single-space, or the zero-width "no gap at all" case (`class Foo :Bar`, no space in
source) — collapse to nothing, in favor of `SUPER_TYPE_LIST`'s own leading `Doc.Text`/`Doc.Break`.
Found failing-first: an earlier, unconditional (type-adjacency-only) version of this rule
double-spaced a bailed supertype list's `class Foo(a: Int) : Sup1, /* keep */ Sup2` into
`... :Sup1 ...` — dropping the *only* space present, since the CLASS-level default colon rule and
the (never-taken, in the bail case) `SUPER_TYPE_LIST` lead had no way to coordinate without the flag;
caught by `format-class-signatures/supertype-comment-bail-error`'s own patched-content assertion,
fixed by gating on the flag instead of the raw type pair.

**Preserved-not-guessed, listed:**
- The `ctorWrapped` approximation above (a fit-undetermined `SOFT` constructor reads as not-wrapped).
- ktlint's own `ktlint_official`-only "annotated parameter forces multiline" condition — no analog,
  same reasoning as C.7 (D21 erased the code-style meta-knob entirely).
- A comment on the return type or modifier list, or anywhere outside the parameter list itself — not
  visible from this frame, same gap C.7 already documented for `FUN`.

**Part 2 — annotation placement (`resolveAnnotationContainerFrame`), scoped to
`WNodeType.MODIFIER_LIST` and `WNodeType.ANNOTATED_EXPRESSION`.** Bails to `resolveBraceFrame`
(verbatim, spacing-only) when: the owning declaration is a `WNodeType.VALUE_PARAMETER` or
`WNodeType.VALUE_ARGUMENT` (`parentType` — ktlint's own carve-out: an annotated value
parameter/argument never wraps, regardless of arguments); no `WNodeType.ANNOTATION_ENTRY` is present
at all; one is followed directly by a `WNodeType.LAMBDA_EXPRESSION` (an annotated trailing-lambda
value, e.g. `val f = @Suppress("x") { ... }`, never wraps — ktlint's own
`isAnnotatedExpressionBeforeLambdaExpression` exemption); a comment sits anywhere inside the
container; or an unrecognized child is present (the bare `@[Foo Bar]` array-annotation syntax has no
`WNodeType` mapping and resolves to `WNodeType.UNKNOWN` — detected and bailed on, not silently
mishandled). Otherwise: a single argument-less annotation is left exactly as the source had it
(ktlint permits both same-line and own-line placement for this case — a genuine "MAY", so wrasse
does not pick a canonical direction and reflow it, unlike the force cases below); an annotation with
arguments (`containsParen` — reliable since `(` cannot otherwise appear in an annotation entry's own
text, the same "introspect the already-resolved child `Doc`" idiom `spansMultipleLines` already
established) or two or more annotations always wrap, one per line, at the declaration's own ambient
depth (no `Doc.Indent` — annotations sit at the same depth as the declaration they precede).
**Canonical-form choice, not a ktlint port:** ktlint additionally permits consecutive argument-less
annotations to stay clustered on one shared line as long as the declaration itself moves to the next
line (`@Foo1 @Foo2\nfun foo() {}` is valid ktlint output); wrasse's printer needs exactly one
canonical rendering and picks the simpler, always-one-per-line form for 2+ annotations — a valid
*instance* of what ktlint accepts, never a broader one, just not the only one ktlint permits.

**Mechanism — `wrapAnnotationEntries` embeds its own trailing break; `adjustAnnotationTrailingGap`
(a `Frame`-flag-free version of the same handshake used for supertypes, keyed off doc *shape*
instead) drops the now-redundant source gap.** Each annotation entry after the first is preceded by
a `Doc.Break(HARD)` (reusing `wsBreakAt` to keep the original gap's own span where one exists); after
the last entry, one more `Doc.Break(HARD)` is appended as the container's own trailing element,
carrying the declaration onto its own line at the same depth. The owning declaration's frame (`FUN`,
`CLASS`, `PROPERTY`, or any other node reaching the generic `resolveBraceFrame` default — this
composes for every declaration kind for free, no per-declaration-type code) must not *also* render
its own real or synthesized gap after the annotation container, or the two would double into a
blank line: `adjustAnnotationTrailingGap` detects this via `endsWithHardBreak` (a new, purely
structural helper — is the container's own last rendered element literally a `HARD` break?) on the
already-resolved `MODIFIER_LIST`/`ANNOTATED_EXPRESSION` child, and — only when true — drops the
following gap entirely (real `Ws`, plain space, or none) before the rest of that frame's children
reach `normalizeChildren`. Unlike the supertype case, no `Frame` field is needed here: `endsWithHardBreak`
is precise on its own, since only `wrapAnnotationEntries`'s own construction ever leaves a bare
trailing `HARD` break as an annotation container's last element (confirmed against the real
compiler's own shape for both a single bare annotation — `[ANNOTATION_ENTRY]`, no trailing
whitespace child at all — and a bailed multi-keyword modifier list, neither of which end in one).

**Preserved-not-guessed, listed:**
- `WNodeType.FILE_ANNOTATION_LIST` (`@file:...` file-level annotations) — excluded entirely, not
  partially: ktlint's own rule additionally enforces a blank line between the file-annotation block
  and what follows it (`visitFileAnnotationList`), which — like the `CLASS`/colon gap above — is
  owned by the *enclosing* `FILE` frame, not `FILE_ANNOTATION_LIST`'s own children; composing that
  with C.6's existing blank-line policy was judged a separate, C.6-shaped concern out of this
  slice's scope, so file annotations are untouched by construction (the existing `else ->
  resolveBraceFrame` default), not a silent gap.
- The annotation use-site-target receiver exemption (`@receiver:Foo(args)` staying inline even with
  arguments) — ktlint's own rule carries this one narrow carve-out via `AnnotationUseSiteTarget`;
  detecting it here would need a textual sniff of the target keyword with no clean structural
  signal, judged not worth the fragility for a rare construct. An `@receiver:`-targeted argumented
  annotation is treated the same as any other argumented annotation (forced to its own line) —
  narrower behavior than ktlint's exemption, never broader.
- ktlint's own `ktlint_official`-only "annotation before a `constructor` keyword" carve-out — no
  analog, same reasoning as Part 1 and C.7.
- A comment anywhere inside a `MODIFIER_LIST`/`ANNOTATED_EXPRESSION` bails the whole container
  verbatim (no attempt to still move a comment-free trailing annotation) — the same discipline C.7's
  own parameter-list comment bail established.

**Existing-fixture changes, each a direct consequence of the mechanism, not a guess:**
- `format-signatures/primary-constructor-untouched-clean.kt` (`class Point(val x: Int, val y: Int,
  val z: Int)`, `expect-clean`) — its own premise (a primary constructor is never reflowed) is now
  false by design; the file no longer exercises anything distinct once `FUN`'s own already-covered
  threshold behavior applies identically. Replaced by
  `format-signatures/secondary-constructor-untouched-clean.kt`, preserving the directory's original
  point (a constructor list genuinely outside the wrap policy still exists — the secondary one) on a
  construct Part 1 does not touch.
- `format-signatures/already-clean.kt` and `kitchen-sink-error.fixed.kt` — both contained the same
  3-parameter `class Point(val x: Int, val y: Int, val z: Int)` (that directory's own
  `multilineSignatureThreshold` is `3`); now force-wrapped like any 3-parameter `FUN`. Updated to the
  wrapped, trailing-comma-bearing canonical form in both files.
- `format-trailing-commas/constructor-parameter-list-add-on-multiline-error.fixed.kt` — the whole
  point of this fixture (C.8) was the *static* mechanism's "already multi-line → insert comma,
  preserve the line breaks verbatim" behavior for a non-`FUN` parameter list, demonstrated on a
  primary constructor. That construct is no longer static (Part 1 routes it through the *dynamic*
  mechanism instead), and its 2-parameter flat text fits under the directory's `maxLineLength: 40`,
  so it now *joins* instead of preserving the multi-line source — collapsing to
  `class Point(val x: Int, val y: Int)`, dropping the comma the old expectation added. Both this
  fixture and its `remove-on-single-line-error` sibling were repointed from a primary constructor to
  a secondary one (`class Point { constructor(...) }`), preserving the directory's original intent
  (the static mechanism, demonstrated on a `FUN`-external parameter list) on a construct Part 1
  still does not touch, rather than asserting on behavior the mechanism no longer exhibits.
- No other existing fixture directory has a primary constructor with 2+ parameters that is either
  already multi-line in source or whose flat text exceeds that directory's own `maxLineLength`
  (audited across every `format`-enabled fixture directory) — the change is otherwise silent by
  construction.

**Fixtures:** two new directories. `format-class-signatures/` (own `wrasse.json`, `maxLineLength:
40`/`multilineSignatureThreshold: 3`, matching `format-signatures`' pattern), 14 fixtures: the four
parameter-list concerns mirrored from `format-signatures` on a primary constructor
(`threshold-forces-multiline-error`, `join-collapses-multiline-error`, `fit-based-force-error`,
`multiline-parameter-content-forces-error`, `comment-in-parameter-list-bail-error`), a dedicated
trailing-comma join-removes proof (`primary-constructor-trailing-comma-join-removes-error`), the five
supertype-wrapping shapes (`supertype-single-fit-error`, `supertype-single-join-with-wrapped-ctor-error`,
`supertype-multi-not-wrapped-error`, `supertype-multi-wrapped-ctor-error`,
`supertype-comment-bail-error`), the `OBJECT_DECLARATION` scope-boundary proof
(`object-declaration-supertype-untouched-clean`), a kitchen sink, and an already-clean canonical file.
`format-annotations/` (own `wrasse.json`, same style), 11 fixtures: both single-annotation
placements as independently clean (`single-annotation-inline-clean`,
`single-annotation-own-line-clean`), the force cases (`argumented-annotation-forces-own-line-error`,
`multiple-annotations-force-own-lines-error`, plus `multiple-annotations-already-own-lines-clean`
proving the canonical form is stable), the two carve-outs
(`value-parameter-annotation-untouched-clean`, `annotated-lambda-before-untouched-clean`), the
`@[...]` array-syntax bail (`annotation-array-syntax-untouched-clean`), a comment bail
(`comment-in-annotation-container-bail-error`), a kitchen sink, and an already-clean file. Both new
directories' `-error` fixtures exercise the existing, unchanged D19 idempotence cycle.
`libs/wrasse-format/DocBuilderSpec` gained 16 new unit tests (7 for supertype-list dispatch: flat,
fit-forced wrap, ctor-wrapped join, multi unconditional split, multi first-joins,
`OBJECT_DECLARATION` untouched, comment bail; 6 for annotation dispatch: single untouched, argumented
forces own line, two-annotations split, value-parameter exemption, annotated-expression-before-lambda
exemption, array-syntax bail; 3 for the `PRIMARY_CONSTRUCTOR`-now-dynamic parameter-list path: forced
by threshold, dynamic trailing-comma add, join dropping the comma) and repointed 2 existing tests
that had used `PRIMARY_CONSTRUCTOR` to demonstrate `passthroughParameterList`'s static behavior onto
`SECONDARY_CONSTRUCTOR` instead, for the same reason as the `format-trailing-commas` fixture change
above — all hand-built against `DocBuilder` directly, no compiler.

**Ladder run for this slice:** `build`, `test --rerun-tasks`, `testMinorHarness --rerun-tasks`,
`testPatchHarness`, `wrasseLint -Prepublish` all green (476 fixture-spec cases per Kotlin minor — 451
plus this slice's 14 (`format-class-signatures`) plus 11 (`format-annotations`) — zero failures).
`ktlint` checkout used for ground truth confirmed byte-clean (`git status`) throughout — read-only;
`detekt` was not needed for this slice.

#### `multilineSignatureThreshold` default: fit-only (owner decision, 2026-07-21)

C.7 shipped the threshold mechanism with the D21-era default of `1`, which forces every
parameterized function (and, after C.9, every parameterized class) one-parameter-per-line
regardless of fit — confirmed on real code to force-wrap idiomatic one-liners repo-wide. Owner
decided (2026-07-21, options presented: fit-only / ktlint-official's `2` / keep `1`): **fit-only**.
`FormatStyle.multilineSignatureThreshold` is now `Int?` defaulting to `null` (absent in
`wrasse.json` = no count-based forcing; signatures wrap only when exceeding `maxLineLength`, or
when a parameter's own text already spans lines). The `999999` workaround overrides C.7 planted in
seven fixture directories are removed — those directories now exercise the true default; the
`format-signatures` and `format-class-signatures` directories keep explicit `3` because their
fixtures test the threshold mechanism itself. Amends D21's locked value, not its locked surface
(the parameter stays; only the default and its optionality changed).

#### Phase C.10 — Statement/expression wrapping cluster — **done 2026-07-21**

Ground-truthed against the local `ktlint` checkout's `wrapping`, `statement-wrapping`,
`if-else-wrapping`, and `multiline-expression-wrapping` rules (source + tests, read-only, confirmed
byte-clean via `git status` throughout, never the internet). `condition-wrapping` does not exist as
a distinct rule in the checked-out version — see the covered-bucket entry below for what that name
was actually probing. `mixed-condition-operators` (autoformat-scope.md: `L`) is untouched, per the
brief.

**Three-bucket map** (this slice's most important artifact):

| Concern (upstream rule) | Bucket | Disposition |
|---|---|---|
| Chain/binary operator break position, argument-list wrapping (`wrapping`'s `LPAR`/`VALUE_ARGUMENT_LIST`/`VALUE_PARAMETER_LIST` paths) | (a) covered | Phase C.4/C.7/C.8's own `Doc.Group`/`SOFT`-break machinery, unchanged |
| `if`/`while` condition wrapping (`wrapping`'s bare-`LPAR` `rearrangeBlock`, the "IDEA quirk" `RPAR`/`{` stay attached) | (a) covered | Phase C.4's `resolveBinaryFrame` already produces exactly this shape (verified by a real fixture, `condition-multi-operator-already-covered-error`) — the reason `condition-wrapping` reads as ambiguous is that it names no real rule; this is what the brief's mention actually resolves to |
| A multi-line value argument starts on its own line (`multiline-expression-wrapping`'s `isValueArgument()`) | (a) covered | `resolveArgumentListFrame`'s existing `Doc.Group` is already forced broken by `Layout.flatWidth`'s null-propagation whenever the argument itself contains a `HARD` break, which already opens right after `(` — verified by `value-argument-multiline-already-covered-clean` |
| Class-signature/supertype-list/annotation wrapping | (a) covered | Phase C.9, unrelated to this cluster |
| `if`/`else` branch content starts on its own line, once braced (`if-else-wrapping`'s `THEN`/`ELSE`-interior newline, and its brace-adjacent `else`/`{` exemption) | (a) covered | composes for free from bracing (`if-else-bracing`, a prior rule) plus this slice's new block-own-line mechanism (below) — `THEN`/`ELSE` are plain wrappers around a `BLOCK`, which is exactly what that mechanism now owns |
| Block/class-body/`when` content shares its own `{`'s or `}`'s line (`wrapping`'s `BLOCK` newline-insertion, `statement-wrapping`'s `visitBlock`) | (b) implemented | `forceMultilineBraceGaps` (new) |
| Multiple statements on one physical line via a real (non-redundant) separator semicolon (`statement-wrapping`'s `visitSemiColon`) | (b) implemented | `convertStatementSeparatorSemicolons` (new) |
| A `PROPERTY`/reassignment/`when`-entry value that is itself an `if`/`when`/`try` block starts on its own line (`multiline-expression-wrapping`'s `isValueInAnAssignment()`/`isAfterArrow()`) | (b) implemented | `resolveAssignedValueFrame` (new) |
| Fit-driven forcing of a currently-single-line block/list into multi-line (`wrapping`'s `maxLineLength`-triggered `BLOCK`/type-argument-list paths) | (c) preserved | needs a `Doc.Group`-wrapped block body — a materially bigger mechanism (would also need per-statement break points inside a body that today only ever has hard-coded verbatim breaks); not attempted |
| A lambda's own first statement starts on its own line when multi-line (`multiline-expression-wrapping`'s `isLambdaExpression()`) | (c) preserved | the anchor lives in the *enclosing* `FUNCTION_LITERAL` frame's own head-gap decision (`normalizeLambdaHead`), not the nested `BLOCK`'s; composing this correctly without risking a regression to the existing lambda-brace-spacing mechanism was judged not worth attempting this slice |
| Type-argument/type-parameter list per-entry newline enforcement on an already-multi-line list (`wrapping`'s `rearrangeTypeArgumentList`) | (c) preserved | these lists are deliberately "never reflowed" as of Phase C.8 (their own comma decision is static, build-time); adding a reflow mechanism here would reopen that already-shipped, tested decision |
| A bare (non-list) parenthesized expression spanning multiple lines (`wrapping`'s bare-`LPAR`/`LBRACKET` `rearrangeBlock`, non-condition case) | (c) preserved | rare construct, no dedicated `WNodeType` handling today; verbatim |
| A raw multi-line string literal's closing `"""` gets its own line before `.trimIndent()` (`wrapping`'s `rearrangeClosingQuote`) | (c) preserved | narrow, rare construct; not attempted |
| Single-line if-statement "should be kept simple" judgment (`if-else-wrapping`'s `visitBranchSingleLineIf`) | (c) preserved | upstream itself reports this as non-autocorrectable (`emit(..., false)`); no printer-layout equivalent exists for a "don't write it this way" judgment, only for whitespace/line-break decisions |
| Forcing a fully single-line nested `else if` chain multi-line regardless of length (`if-else-wrapping`'s `nestedIf` override of `multilineIf`) | (c) preserved | `if-else-bracing`'s own chain-spans-multiple-lines gate is an already-recorded, deliberate, narrower-than-ktlint decision (matching detekt's default too); reopening it here would contradict that prior, owner-adjacent choice |

**Mechanism 1 — `convertStatementSeparatorSemicolons`.** Scoped to `WNodeType.BLOCK`/`WNodeType.WHEN`
only (`CLASS_BODY` is deliberately excluded: the only semicolon a class body's own children can
contain is the enum-entries-list terminator, already fully owned by `no-semicolons`'
`enumTailIsUnnecessary`, so scoping away from `CLASS_BODY` sidesteps that case by construction,
never by special-casing it). A `SEMICOLON` child not already followed by a real newline, with real
content (or the frame's own closing `}`) still to come before the next newline, is replaced —
reusing its own one-character span — by a synthetic `ChildEntry.Ws("\n", ...)`: the semicolon
character is dropped and a break takes its place in one step, satisfying the printer's own
born-clean discipline (a break inserted there without also dropping the semicolon would leave a
provably-redundant separator semicolon behind — exactly what a second `no-semicolons` pass would
then flag, breaking the idempotence invariant; the printer's own contract explicitly permits
removing that kind of semicolon, so this is not a new exception, just its first real trigger). Bails
(leaves the semicolon untouched) when a comment sits directly after it — the same "bail identically"
discipline as every other comment-adjacent decision in this file — or when nothing follows it at
all.

**Mechanism 2 — `forceMultilineBraceGaps`.** Scoped to `WNodeType.BLOCK`/`CLASS_BODY`/`WHEN`
(`FUNCTION_LITERAL` excluded — its own `{`/arrow head gap is `normalizeLambdaHead`'s concern, and
composing a second, independent break-insertion decision there risked fighting that existing
mechanism for a case this slice did not need to solve). Finds the frame's own `{` by index rather
than assuming it is the first child — `WHEN`'s own frame carries the `when (subject)` header before
its brace, unlike `BLOCK`/`CLASS_BODY` (found failing-first: an initial version assumed index `0`
and a `when (x) { ... }` construct with an already-embedded newline between entries rendered
completely untouched, caught by the fixture ladder, not reasoned out in advance). When the braced
body (from that `{` onward) already contains a forced multi-line child — post
`convertStatementSeparatorSemicolons`, so a fully single-line block with two semicolon-separated
statements also qualifies — a break is inserted right after `{` and right before `}` wherever one
is not already there, reusing an existing plain-space gap's span when present or a zero-width point
otherwise. No-ops for: a frame without its own `{`/`}` pair at all (a lambda's transparent `BLOCK`
has neither); an entirely single-line body (this mechanism never decides fit); an enum `CLASS_BODY`
that is, as a whole, still single-line (`enum class Foo { A, B }` stays exactly as written).

**Mechanism 3 — `resolveAssignedValueFrame`, shared by three call sites.** A new `WNodeType.PROPERTY`
dispatch branch (`resolvePropertyFrame`, anchored on `PROPERTY`'s own direct `EQ` child — a
declaration's own initializer never nests inside a `BINARY_EXPRESSION`, a different grammar
production entirely from reassignment); `resolveBinaryFrame`'s existing dispatch, extended to detect
an assignment-class operator (`=`, `+=`, `-=`, `*=`, `/=`, `%=` — plain assignment is a
`BINARY_EXPRESSION` too, never chained/nested, so it is always effectively root) and route around
the generic chain/binary `wrapRoot` machinery for that one case; `resolveWhenEntryFrame`, extended to
also inspect its own arrow's body independent of the (unrelated) trailing-comma bail. In all three,
when the value found right after the anchor is one of `MULTILINE_WRAPPABLE_VALUE_TYPES` (`IF`,
`WHEN`, `TRY` — deliberately an inclusion list, not an exclusion list; found failing-first against
`format-indentation/kdoc-and-multiline-string-preserved` and two sibling fixtures, whose raw
multi-line string/KDoc property initializers got wrongly relocated by an earlier, broader
`spansMultipleLines`-only version of this check) and is already forced multi-line, it moves onto its
own line, one `Doc.Indent` level deeper, reusing an existing gap's own break when the source already
had one. Declines (returns `null`, caller falls through to its prior, unchanged behavior) for: no
value; a comment; a value type outside the inclusion list (a raw multi-line string/KDoc token, a
`BLOCK` — owned by `forceMultilineBraceGaps` instead — a `LAMBDA_EXPRESSION`/`OBJECT_LITERAL`, whose
own `{` always stays attached to the anchor, exactly like ordinary Kotlin style, found failing-first
too: an initial version moved an entire lambda literal down onto its own line away from `=` merely
because its interior happened to already contain hard breaks — a dot-chain/call/binary value, since
`resolveChainFrame`/`resolveBinaryFrame`'s own `Doc.Group` already owns that placement, including
the deliberate "receiver stays, only `.method()` continues" choice from Phase C.4 that a broader type
match here would have fought); or a value that is not (yet) forced multi-line (a fit-pending
chain/binary expression, still `Doc.Group`-decided — excluded automatically, since `spansMultipleLines`
only detects an already-committed `HARD` break, never a pending fit decision, the same distinction
`resolveSuperTypeListFrame`'s own `ctorWrapped` peek already relies on).

**Idempotence, checked directly, not just asserted:** every new-mechanism fixture's already-formatted
output was fed back through the harness and confirmed to produce zero further diagnostics (a
temporary, since-removed scratch spec; the committed D19 idempotence cycle exercises the same
property for every fixture going forward).

**Fixtures:** new `testing/wrasse-test-harness/.../fixtures/format-wrapping/` directory (own
`wrasse.json`, `maxLineLength: 50`), 17 fixtures: `block-content-own-line-error`,
`class-body-content-own-line-error`, `when-content-own-line-error` (proves the `WHEN`-header index
fix), `enum-class-body-single-line-untouched-clean`, `semicolon-mid-block-error`,
`semicolon-before-rbrace-error`, `comment-adjacent-semicolon-bail-error`,
`property-declaration-multiline-value-error`, `reassignment-multiline-value-error`,
`compound-assignment-multiline-value-error`, `when-entry-arrow-multiline-value-error`,
`value-argument-multiline-already-covered-clean` and `condition-multi-operator-already-covered-error`
(both proving bucket (a) with a real, running fixture rather than an assertion), `fun-expression-body-
untouched-clean` and `lambda-literal-value-untouched-clean` (both proving the two Phase C.7/this-slice
exclusions), a kitchen sink, and an already-clean canonical file. All `-error` fixtures exercise the
existing, unchanged D19 idempotence cycle. `libs/wrasse-format/DocBuilderSpec` gained 14 unit tests,
hand-built against `DocBuilder` directly (no compiler): the semicolon conversion (mid-block, trailing-
before-`}`, comment bail), the brace-gap forcing (multi-line-no-semicolon case, single-line-untouched
case, enum-single-line exemption, non-enum class body, and the `WHEN`-header-skip case specifically),
and the assigned-value wrap (`PROPERTY`, single-line-untouched, the type-exclusion case using a raw
multi-line string, reassignment `BINARY_EXPRESSION`, and `WHEN_ENTRY`'s own arrow).

**Existing-fixture changes:** none. Every pre-existing `format-*`/`bracing-format-on` fixture was
re-verified by the full ladder; none needed a `.fixed.kt` update.

**Ladder run for this slice:** `build`, `test --rerun-tasks`, `testMinorHarness --rerun-tasks`,
`testPatchHarness`, `wrasseLint -Prepublish` all green. `ktlint` checkout used for ground truth
confirmed byte-clean (`git status`) throughout — read-only; `detekt` was not needed for this slice.

#### Phase C.11 — Blank-line (vertical whitespace) INSERTION around declarations, with comment attachment — **done 2026-07-21**

The ADD direction C.6 deliberately deferred: `blank-line-before-declaration`,
`spacing-between-declarations-with-annotations`, `spacing-between-declarations-with-comments`.
`blank-line-between-when-conditions`'s own add direction stays out of scope, per C.6's note above.

**Ground truth used:** the local `ktlint` checkout's own rule sources and test suites for all three
rules (`ktlint-ruleset-standard`), plus `KtTokenSets.DECLARATION_TYPES` from the local `kotlin`
compiler checkout (`compiler/psi/psi-api/.../KtTokenSets.java`) to resolve the wider type set the
two comment/annotation rules use versus the base rule's own narrower list. Both checkouts confirmed
byte-clean (`git status`) throughout — read-only.

**The attachment definition (the heart of this slice), confirmed by a real compiler dump, not
assumed:** a comment (EOL, block, or KDoc) or a KDoc/comment *run* directly preceding a declaration,
with no blank line breaking the run, is nested by kotlinc's own LightTree as the declaration's own
first child — not a preceding sibling of the enclosing container. This holds for every declaration
kind (`CLASS`, `FUN`, `PROPERTY`, `OBJECT_DECLARATION`, `CLASS_INITIALIZER`) in every container
(`FILE`, `CLASS_BODY`, `BLOCK`), including a *run* of several consecutive same-adjacency comment/KDoc
lines (all of them nest, not just the closest one) — with one confirmed exception: a `PROPERTY`
directly inside a `BLOCK` (a local variable) does not nest an immediately preceding comment as its
own child at all; the comment surfaces as `BLOCK`'s own sibling entry instead (see the
preserved-not-guessed entry below). A blank line anywhere inside a comment run breaks it: only the
contiguous group directly touching the declaration attaches; anything before that blank line remains
a genuine, untouched sibling of the container. Annotations attach the same way, via `MODIFIER_LIST`
nesting inside the declaration (never via `ANNOTATED_EXPRESSION`, which the annotations rule's own
`isAnnotated()` — `findChildByType(MODIFIER_LIST)` only — does not recognize either).

**Consequence for the mechanism: zero new comment-attachment code.** Because the comment/KDoc run is
already nested inside the following declaration's own resolved `Doc` by the time the *enclosing*
container processes the gap before it, inserting a blank line at that gap automatically lands before
the comment — the same gap decision that would apply to a bare, comment-free declaration. The two
per-declaration facts this slice's policy needs — "has a leading annotation," "has a leading
comment" — are computed once, structurally, and threaded onto the resolved `ChildEntry` the same way
C.9's `Frame.ownsSuperTypeListLeadGap` threads state across a parent/child boundary:

- **`Frame.hasLeadingAnnotation`** (new field) is set on the still-open *enclosing* frame from
  `resolveAnnotationContainerFrame`, the moment a `WNodeType.MODIFIER_LIST` is found to contain at
  least one `WNodeType.ANNOTATION_ENTRY` — regardless of that container's own bail/wrap decision,
  since "is annotated" and "how the annotation renders" are independent facts.
- **`hasLeadingComment`** needs no parent/child handshake at all: at `exitNode`, before a completed
  frame's `Doc` is packed into its own `ChildEntry.Resolved`, its *own* first buffered child is
  checked directly (`EOL_COMMENT`/`BLOCK_COMMENT`/`KDOC`) — the buffered-children context the SAX
  walk already provides for free at exit time.
- Both facts ride two new `ChildEntry.Resolved` fields (`hasLeadingAnnotation`, `hasLeadingComment`,
  both defaulting `false` so every other construction site is unaffected), read back only by the new
  policy function below.

**Mechanism — one more branch on C.6's existing choke point, no second policy path.**
`verticalGapNewlineCount` gained one more `if`, `forcesDeclarationBlankLine(frameType, prevEntry,
nextEntry, isFirstAfterLbrace)`, returning `2` (the ADD case, same as the package/import rule) when
it applies; C.6's existing checks (package/import spacing, first-after-`{`, class/primary-constructor)
run first, unaffected, so this never contests a gap they already own. Its own children-buffer
lookback is a single-parent comparison (`prevEntry`/`nextEntry`, already C.6's own parameters) since
the attachment nesting above means no wider scan is ever needed.

**The policy table:**

| Gap | Newline count forced | Rule |
|---|---|---|
| `CLASS`/`CLASS_INITIALIZER`/`FUN`/`OBJECT_DECLARATION`/`PROPERTY` preceded by another `CLASS`/`OBJECT_DECLARATION`/`FUN`/`PROPERTY`/`TYPEALIAS`/`SECONDARY_CONSTRUCTOR`/`CLASS_INITIALIZER`/`ENUM_ENTRY`, in `FILE`/`CLASS_BODY`/`BLOCK` | `2` (unless an exemption below applies) | `blank-line-before-declaration` |
| First member right after `CLASS_BODY`'s or any `BLOCK`'s own `{` | unchanged (no force) | `blank-line-before-declaration`'s own carve-out |
| A `PROPERTY` directly inside a `BLOCK` (a local variable) | unchanged (no force), regardless of what precedes | `blank-line-before-declaration`'s local-property carve-out |
| Two consecutive `PROPERTY`s, either container | unchanged (no force) | `blank-line-before-declaration`'s consecutive-property carve-out |
| A `PROPERTY_ACCESSOR`, always | unchanged (no force) — see preserved-not-guessed below | n/a |
| Any of `CLASS`/`OBJECT_DECLARATION`/`FUN`/`PROPERTY`/`TYPEALIAS`/`SECONDARY_CONSTRUCTOR`/`CLASS_INITIALIZER`/`ENUM_ENTRY` carrying its own leading annotation or leading comment, preceded by another member of that same wider set, in `FILE`/`CLASS_BODY`/`BLOCK` — **none of the carve-outs above apply** | `2` | `spacing-between-declarations-with-annotations` / `spacing-between-declarations-with-comments` |
| A `PROPERTY_ACCESSOR` carrying its own leading annotation, preceded by another `PROPERTY_ACCESSOR` (inside `PROPERTY`'s own frame) | `2` | `spacing-between-declarations-with-annotations` |

**Cap-vs-add precedence.** `forcesDeclarationBlankLine` returns exactly `2`, never more — the same
value the package/import ADD case already used — so an existing gap with three or more newlines
still collapses to one blank line, it never grows to two; C.6's caps and this slice's adds share one
result value, never compose into something larger. The class-body first-member carve-out is checked
*before* this branch is reached at all (via the existing `isFirstAfterLbrace` early return), so
insertion can never win against it.

**Preserved-not-guessed, listed:**
- **A comment/KDoc immediately preceding a local (`BLOCK`-scoped) `PROPERTY`** does not force a blank
  line, confirmed structurally rather than assumed: a compiler dump of `val a = 1 // comment before b
  \n val b = 2` inside a function body shows the comment as `BLOCK`'s own sibling entry, never nested
  inside `PROPERTY(b)`'s own frame — unlike every other declaration kind, and unlike a `CLASS_BODY`-
  or `FILE`-level `PROPERTY`, both of which do nest a directly-adjacent comment normally (confirmed by
  the same dump). Since ktlint's own `spacing-between-declarations-with-comments` keys off exactly the
  same structural nesting (`comment.parent.takeIf { it.isDeclaration }`), this shape is not merely
  unimplemented in wrasse — it is unreachable by construction for either tool, on the same parser.
- **`PROPERTY_ACCESSOR` is unconditionally exempt from `blank-line-before-declaration`.** ktlint's own
  rule folds accessor handling into its `isConsecutiveProperty()` check, whose `prevCodeSibling`
  fallback (`it.parent!!.propertyRelated()`) is true for *any* `PROPERTY_ACCESSOR`, since its
  `prevCodeSibling` always shares the same enclosing `PROPERTY` parent — so no accessor is ever forced
  by the base rule regardless of what precedes it. Only `spacing-between-declarations-with-annotations`
  still reaches a `PROPERTY_ACCESSOR` (via its own, wider `isDeclarationOrPropertyAccessor` check).
- **`DESTRUCTURING_DECLARATION`** is in ktlint's real `KtTokenSets.DECLARATION_TYPES` (so
  `spacing-between-declarations-with-annotations`/`-with-comments` do technically reach it), but an
  annotated or comment-preceded destructuring declaration is rare enough, and structurally distinct
  enough from every other member of that set, that it was left out of `DECLARATION_SPACING_TYPES` —
  narrower than upstream, never broader.
- The annotation use-site-target/`ktlint_official`-only carve-outs C.9 already declined for the same
  reasons apply identically here; not re-litigated.

**Existing-fixture audit and changes — the full blast radius, confirmed by running the ladder, not
estimated.** Every `format-*`/`bracing-format-on` fixture directory was grepped for adjacent
declaration-shaped lines before implementing, then the full harness run against the real mechanism
confirmed the exact same 8 files as the manual audit — no surprises in either direction:
- `format-wrapping/already-clean.kt` — its own `fun one() {}`/`fun accept(a: Any?) {}` preamble had no
  blank line between them; since this fixture's premise is "already canonical," the source itself
  gained the blank line (its own point — block-content wrapping — is unaffected).
- `format-wrapping/block-content-own-line-error.fixed.kt`,
  `comment-adjacent-semicolon-bail-error.fixed.kt`, `semicolon-mid-block-error.fixed.kt`,
  `kitchen-sink-error.fixed.kt` — all four share the same `fun one() {}`/`fun two() {}` preamble
  (unrelated to each fixture's own concern); only the `.fixed.kt` gained the blank line, since the
  `.kt` source's own missing blank line is now itself something this compile fixes, same as the
  concern each fixture already exercises.
- `format-with-fixes/grand-slam-error.fixed.kt`, `no-semicolons-and-reindent-error.fixed.kt` — a
  class's own `val x = 1` directly followed by `fun show()`, no blank line; `.fixed.kt` gained one
  (`PROPERTY` → `FUN`, not the consecutive-property or local-property carve-out).
  `no-unused-imports-and-reindent-error` and the two remaining `format-with-fixes` fixtures needed no
  change — audited, no adjacent-declaration shape present.
- `format-spacing/colon-supertype-and-generic-bound-spacing-error.fixed.kt` — `open class Animal`
  directly followed by `class Dog : Animal()`, no blank line; `.fixed.kt` gained one.
- No other `format-*`/`bracing-format-on` fixture directory (`format-annotations`, `format-blank-lines`,
  `format-class-signatures`, `format-indentation`, `format-signatures`, `format-line-breaks`,
  `format-trailing-commas`) needed any change — every apparent adjacency in those directories is
  either a class/function body's own first statement (exempt), two consecutive properties (exempt), a
  parameter-list line (not a declaration boundary at all), or already correctly blank-lined.

**Fixtures:** new `testing/wrasse-test-harness/.../fixtures/format-declaration-blank-lines/` directory
(own `wrasse.json`, default style), 14 fixtures: `top-level-classes-error`,
`top-level-functions-error` (the base rule at `FILE` scope), `class-body-members-error`
(`PROPERTY`→`FUN`→`FUN`→`CLASS_INITIALIZER`, all forced), `first-member-clean` (a class body's own
first member and a block's own first local declaration, both untouched), `consecutive-properties-clean`
(top-level, class-body, and local-after-statement, all untouched), `local-fun-after-local-declaration-
error` (the local-property-only-carve-out asymmetry), `eol-comment-run-attachment-error` (a two-line
comment run, blank line lands before both lines), `block-comment-attachment-error`,
`kdoc-attachment-error`, `comment-blank-line-already-present-clean` (no double insertion when a blank
line already precedes the attached comment), `annotated-consecutive-property-forces-blank-line-error`
and `comment-forces-consecutive-property-blank-line-error` (both defeating the base rule's own
consecutive-property carve-out), a kitchen sink, and an already-clean canonical file. `libs/wrasse-
format/DocBuilderSpec` gained 12 new unit tests: the base rule at `FILE` scope; the class-body- and
any-block's-own first-member carve-outs (the latter built on an `IF`'s own `BLOCK`, with no `FUN`
ancestor at all, to prove the exemption's unconditional scope); the consecutive-property and
local-property-after-a-statement carve-outs; the local-property/local-`FUN` asymmetry; the EOL-comment-
run and KDoc attachment cases; the annotation carve-out-defeat case; both `PROPERTY_ACCESSOR` cases
(annotated second accessor forces, annotated first accessor does not); and the cap-vs-add precedence
case (an existing four-newline gap before a forced declaration still collapses to exactly one blank
line).

**Ladder run for this slice:** `build`, `test --rerun-tasks`, `testMinorHarness --rerun-tasks`,
`testPatchHarness`, `wrasseLint -Prepublish` all green. `ktlint`/`kotlin` checkouts used for ground
truth confirmed byte-clean (`git status`) throughout — read-only.

#### Phase C.12 — Comment spacing and multiline raw-string indentation — **done 2026-07-21**

The final planned printer slice, resolving autoformat-scope.md's hard calls #2
(`comment-spacing`) and #3 (`multiline-raw-string-indentation`) — the only two of the seven listed
hard calls that belong to the printer/F bucket at all; the other five (brace insertion, the
Explicit-API/trivial-accessor T-bucket carve-outs, the two L-bucket rule-value questions) are
outside `wrasse-format`'s remit and untouched here.

**Part 1 — comment spacing.** Ground-truthed against the local `ktlint` checkout's
`CommentSpacingRule` (source + test, read-only, confirmed byte-clean via `git status` throughout,
never the internet): scoped to `WNodeType.EOL_COMMENT` only — block comments and KDoc are never
touched, matching the checkout's own `node.elementType == EOL_COMMENT` gate exactly. Two
independent mechanisms, both narrower-than-upstream where the checkout itself is narrow:

- **Space after `//`** (`normalizeEolCommentText`, called once in `visitLeaf` when a leaf's own
  text becomes its `Doc.Text`): a bare `//`, an already-`"// "`-prefixed comment, or one starting
  with `//noinspection`, `//region`, `//endregion`, or `//language=` is left byte-exact; every other
  `EOL_COMMENT` gets `"// "` substituted for its own leading `//`. This is the one place this slice
  touches a comment token's own first characters — the printer's `Doc.Text`/span-length invariant
  established by `clampWs`'s own existing "span may exceed rendered length" idiom absorbs the
  one-character insertion the same way an inserted space or trailing comma already does elsewhere in
  `DocBuilder`.
- **Space before a trailing `//` comment** (`normalizeChildren`'s existing insertion-only branch,
  one added clause): fires only when there is no whitespace at all between the preceding token and
  the comment — an *existing* gap of any size, one space or five, is left completely alone, matching
  the checkout's own `!prevLeaf.isWhiteSpace` check, which only ever detects total absence. This is
  deliberately not folded into the general `spacingDecision` table, since that table is shared by
  both the "gap already exists" and "gap absent" call sites and a table entry would have normalized
  an existing multi-space gap down to one — broader than upstream.

**Part 2 — multiline raw-string indentation, scoped to `trimIndent()` only.** Ground-truthed
against the local `ktlint` checkout's `StringTemplateIndentRule` (source + test) — the only local
checkout with any coverage of this concern at all; diktat's own semantics were deliberately not
consulted, per the task's own framing, so as not to port a different tool's algorithm under the
same rule name. The checkout's rule itself never touches `trimMargin()` either (`isFollowedByTrimIndent`
checks literally `it.text == "trimIndent()"`), so `trimMargin()` has no local precedent and is
preserved verbatim here too, not attempted — narrower than the hard-call note's own phrasing
(`trimIndent`/`trimMargin`), a deliberate scope cut: `trimMargin()`'s trim decision is keyed on a
per-line marker character rather than a common-whitespace computation, and confirming re-indentation
never disturbs a line lacking that marker would need argument-value resolution (the marker can be
overridden) this walk does not do.

**The value-preservation argument.** `String.trimIndent()` computes the minimum leading-whitespace
run shared by every non-blank line (its own opening and closing lines, when blank, are dropped
whole) and strips exactly that many characters from every line, verbatim beyond that point; blank
lines are wiped to `""` regardless of their own whitespace. `buildReindentedRawString` (new,
`DocBuilder.kt`) only ever rewrites the *shared* prefix — the same prefix length `trimIndent()`
itself would compute — replacing it with the printer's own ambient indent (via `Doc.Break`, so
`Layout` derives it the same way it derives every other line's indent) and leaving everything from
that offset onward, on every line, byte-identical. Since every content line ends up sharing the
identical new prefix (`Layout` renders one `Break` kind, pure spaces, deterministically, for every
break at one depth), `trimIndent()` re-run against the reformatted text recomputes the same minimum
(now equal to the new prefix's own length) and strips it back to the identical remainder — the
computed string value cannot change. A concrete instance, traced by hand: `"""\n    Roses are
red\n    Violets are blue\n    """.trimIndent()` evaluates to `"Roses are red\nViolets are blue"`
both before this slice's fixture reformats it (common prefix `4`, stripped) and after (common
prefix `8`, stripped) — `format-raw-strings/trimindent-reindent-error.fixed.kt`. A completely blank
non-mandatory interior line is left untouched on purpose (not merely accepted as safe): `trimIndent()`
discards such a line's content regardless of how much whitespace it carries, so rewriting it would
be a needless diff, not a needed one.

**Eligibility (`buildReindentedRawString`, `resolveStringTemplateFrame`, new): every one of these
must hold, or the receiver renders exactly as `resolveBraceFrame`'s pre-existing default already
would (byte-identical to pre-C.12 output — confirmed by a dedicated unit test with no chain at
all).**
- The `STRING_TEMPLATE`'s only children are `WNodeType.OPEN_QUOTE`/`WNodeType.CLOSING_QUOTE` and
  `WNodeType.LITERAL_STRING_TEMPLATE_ENTRY` — any `SHORT_STRING_TEMPLATE_ENTRY`/
  `LONG_STRING_TEMPLATE_ENTRY` (`$x`/`${...}` interpolation) bails the whole receiver, per the
  task's own explicit exemption list.
- The first line (right after the opening `"""`) is exactly `"\n"` — content already starts on its
  own line — and the last line (right before the closing `"""`) is either exactly `"\n"` or a
  non-empty run of nothing but whitespace immediately preceded by one — the closing quotes already
  sit on their own line. Either shape not holding bails: a raw string whose content touches the
  opening or closing quotes is left exactly as written, never rewritten to move it — a narrower
  scope than the checkout's own rule, which does perform that move (`checkAndFixNewLineAfterOpeningQuotes`/
  `checkAndFixNewLineBeforeClosingQuotes`); moving content onto a new line changes which lines
  `trimIndent()` itself considers when computing the shared minimum, which this single-pass walk
  cannot safely re-verify, so it is left alone rather than guessed at.
- At least one real (non-blank) content line exists, and the shared minimum indent among them is
  strictly greater than zero — a string whose common indent is already `0` is left untouched (no
  case in this repo's own fixtures needs it, and there is nothing to gain from adding indentation
  where none existed).
- The chain wrapping the receiver is exactly `<receiver>.trimIndent()` or `<receiver>?.trimIndent()`
  — no arguments, nothing else in the same `DOT_QUALIFIED_EXPRESSION`/`SAFE_ACCESS_EXPRESSION`
  (`substituteTrimIndentReceiver`, checked via the same `flatText`-comparison idiom C.4 already uses
  for the elvis operator, so a comment or unusual spacing inside `trimIndent(...)`'s own parens
  correctly fails the exact-text match and bails).

**Mechanism.** Every entry equal to `"\n"` becomes a `Doc.Break(HARD)` so `Layout` synthesizes its
own following line's indent from ambient depth, exactly like an ordinary source newline; a real
content line has its own leading run of `commonIndent` characters folded into the *preceding*
break's already-established elided tail (the same "trailing indent lives past the break's own
`literal`, never independently addressable" contract `clampWs`/`Doc.Break` already document) so its
own remaining `Doc.Text` keeps `Text.value.length == end - start` — no new invariant, the same one
every other `Doc.Text` in this codebase already satisfies. The mandatory closing blank line is
folded the same way, so the closing `"""` inherits the identical ambient depth with no separate
code path.

**Composing with C.7's chain-fix, not fighting it.** A reindented receiver's `Doc` now genuinely
contains a `Doc.Break(HARD)` — unlike the plain, frozen `Doc.Text` C.7's own fix was about — so
`wrapRoot`'s `hasOwnIndentScope` check (unchanged) finds it and would, unmodified, take the
`splitIdx >= 0` branch meant for a *trailing* forced-break part (a lambda argument), producing the
exact empty-head/unwrapped-tail defect C.7 fixed, just retriggered by a *leading* forced-break part
instead. Fixed generally, not narrowly: `wrapRoot`'s `splitIdx < 0` guard becomes `splitIdx <= 0` —
when the split-owning part is the chain's own first part there is no head content to protect from
the fit-check, so it folds into the same shared `Group`/`Indent` the no-split path already builds,
which is also exactly what lets the receiver's own break and the chain's continuation share one
depth. This is inert for every case that currently reaches `splitIdx == 0` (none — confirmed the
full ladder's existing chain fixtures are unaffected) and is what
`format-raw-strings/chain-composition-error` (`.trimIndent().uppercase()`) exercises directly, the
fixture the task asked for by name.

**Existing-fixture audit — the full blast radius, one file.** Every `format-*`/`bracing-format-*`
fixture directory was grepped for `//` (Part 1) and `"""` (Part 2) before writing a single new
fixture. Comment hits (`format-annotations`, `format-spacing`, `format-wrapping`,
`format-signatures`, `format-declaration-blank-lines`, `format-class-signatures`): every one already
has a real, existing gap before its trailing comment and already starts with `"// "` — confirmed by
inspection, then by the full ladder passing with zero diffs to any of them. Raw-string hits:
`format-blank-lines/comment-and-string-interior-preserved-error` and
`format-indentation/kdoc-and-multiline-string-preserved` — neither is followed by `trimIndent()`/
`trimMargin()` at all, so Part 2 bails on both by construction (verified, not assumed).
`format-line-breaks/chain-multiline-string-receiver-error` — this one *is* eligible (well-formed,
`trimIndent()`-only) and its `.fixed.kt` is updated: the closing `"""` now joins the content lines'
own depth (both move to the ambient depth one level past the statement) instead of staying frozen
at the depth the original fixture's source happened to use, which is exactly the gap Part 2 closes.

**Preserved verbatim, deliberately, listed:**
- `trimMargin()` (Part 2's whole scope cut above) — including a raw string with no trim call
  at all, which was never in scope for either upstream rule or this one.
- Any raw string containing `$`/`${...}` interpolation, even when followed by `trimIndent()`.
- A raw string whose content touches its opening or closing quotes (no blank first/last line).
- A raw string whose shared indent is already zero.
- A comment on the return type or modifier list of a `trimIndent()`-receiver's own statement — not
  visible from this mechanism, same class of gap C.7/C.9 already documented for signatures/
  annotations.
- The checkout's own mixed-tab/space diagnostic (`containsMixedIndentationCharacters`) has no
  analog here: it exists so the checkout's own line-by-line *rewrite* has an unambiguous character
  count to work from, but `buildReindentedRawString`'s transform only ever counts and moves a
  character run, never interprets tab width, so mixed indentation carries no extra value-preservation
  risk and needed no bail.

**Fixtures:** two new directories. `format-comment-spacing/` (own `wrasse.json`, `{"format":
{"enabled": true}}`), 6 fixtures: `space-after-slash-slash-error` (both the after-`//` and
before-comment insertions in one file), `space-before-trailing-comment-error`,
`exempt-prefixes-clean` (`//region`/`//endregion`/`//noinspection`/`//language=`, `expect-clean`),
`block-comment-and-kdoc-preserved-error` (both left byte-exact while an adjacent `EOL_COMMENT` in
the same file still gets its space), a kitchen sink, and an already-clean canonical file.
`format-raw-strings/` (same `wrasse.json`), 4 fixtures: `trimindent-reindent-error` (the traced
value-preservation instance above), `chain-composition-error` (the required composition-with-C.7
proof), `preserved-shapes-error` (interpolation/`trimMargin()`/no-trim-call all preserved verbatim
in one file, alongside a real, unrelated comment fix proving the file is still genuinely
processed), and an already-clean canonical file matching the reindent fixture's own expected
output (idempotence by construction). Both new directories' `-error`/kitchen-sink fixtures exercise
the existing, unchanged D19 idempotence cycle. `libs/wrasse-format/DocBuilderSpec` gained 15 new
unit tests: 7 for comment spacing (after-`//` insertion, already-spaced, bare `//`, all four exempt
prefixes in one test, block/KDoc untouched, before-comment insertion on a bare gap, and preserving
an existing multi-space gap) and 8 for raw-string reindentation (the happy path with an explicit
depth proof, the no-chain-at-all byte-identical-default proof, interpolation bail, `trimMargin()`
bail, both touching-quotes bails, the zero-common-indent bail, and the two-link chain composition
case) — all hand-built against `DocBuilder` directly, no compiler.

**Ladder run for this slice:** `build`, `test --rerun-tasks`, `testMinorHarness --rerun-tasks`,
`testPatchHarness`, `wrasseLint -Prepublish` all green (518 fixture-spec cases per Kotlin minor —
508 plus this slice's 6 (`format-comment-spacing`) plus 4 (`format-raw-strings`), plus the retained
`format-line-breaks` fixture whose `.fixed.kt` changed but whose case count is unchanged — zero
failures). `ktlint` checkout used for ground truth confirmed byte-clean (`git status`) throughout —
read-only; `diktat`/`detekt` were deliberately not consulted for this slice, per the task's own
scope.

**This completes the planned Phase C roadmap.** Every printer concern autoformat-scope.md's F
bucket lists is now either implemented (C.1–C.12) or explicitly recorded as preserved-verbatim with
its own reason, never silently dropped. The consolidated preserved-verbatim list across all of
Phase C, gathered here for the format-on-wrasse milestone that follows:

- **Style-axis features with no analog** (D21 erased the code-style meta-knob entirely): every
  `ktlint_official`-only condition named across C.7/C.9 (annotated-parameter-forces-multiline,
  annotation-before-`constructor`), and `blank-line-between-when-conditions`'s own
  `.editorconfig`-gated add direction (C.6).
- **Comment-adjacent bails, one per reflowed construct, never guessed past:** a comment inside a
  `FUN`'s parameter list (C.7), inside a class's supertype list or an annotation container (C.9),
  inside a `WHEN_ENTRY`'s condition list (C.6/C.7), or anywhere in a `TYPE_PARAMETER_LIST`/
  `TYPE_ARGUMENT_LIST`/`DESTRUCTURING_DECLARATION`/non-`FUN` parameter list (C.8, already
  preserved by never being reflowed at all) — plus a comment on a return type or modifier list,
  invisible from every one of these frames (C.7/C.9/C.12).
  A local (`BLOCK`-scoped) `PROPERTY`'s own immediately-preceding comment never forces a blank line
  either, since it does not nest as that declaration's own child at all (C.11).
- **Narrow structural gaps, not scope cuts:** `where`-constraint colons were a genuine unmapped
  gap closed in C.6; `COLLECTION_LITERAL_EXPRESSION`/`INDICES`/the bracket `@[A B]` annotation form
  resolve to `WNodeType.UNKNOWN` today and bail wherever they're checked for (C.8/C.9); a lambda
  parameter list separated from its own arrow by a newline is not detected as multi-line, a
  single-pass streaming-builder limitation, not a decision (C.8).
- **Deliberately narrower than a named upstream carve-out, never broader:** the `::`-bound-reference
  leading-gap exemption (C.5); the annotation use-site-target receiver exemption (C.9); a
  `DESTRUCTURING_DECLARATION`'s own blank-line-before-declaration eligibility (C.11); `trimMargin()`
  and the two raw-string quote-line moves (C.12).
- **Deferred whole, flagged for owner input:** enum `CLASS_BODY` trailing-comma insertion, since it
  sometimes also needs a semicolon the printer's own contract cannot insert (C.8).
- **No dedicated mechanism built (rare or materially bigger in scope):** a bare (non-list)
  parenthesized expression spanning multiple lines; fit-driven forcing of an already-single-line
  block/list into multi-line; a lambda's own first statement moving to its own line when the lambda
  body is multi-line; a fully single-line nested `else if` chain forced multi-line regardless of
  length (all C.10); a raw string's closing quotes moving onto their own line when they do not
  already sit there (C.10's own preserved entry, subsumed by C.12's own, narrower, value-safety
  reasoning for the identical construct).
- **Blank lines at the true start or end of a file** — the base blank-line rule's own upstream
  scope already excludes both; `TrailingNewlineRule` owns end-of-file behavior on wrasse's side, and
  the harness itself trims trailing blank source lines before any fixture ever compiles, making the
  end-of-file shape structurally unexercisable regardless (C.6).

#### C.12 hardening: whitespace-only interior lines make trimIndent reindentation ineligible (found by format-on-wrasse dogfood, 2026-07-21)

The first bug the format-on milestone caught, and it was a D19 violation. C.12's eligibility
reasoning claimed "`trimIndent()` discards a blank line's own content regardless of its
indentation" — false: kotlin's `trimIndent` keeps a whitespace-only line's residue beyond the
stripped prefix (and keeps the whole line verbatim when it doesn't start with the full prefix), so
that residue is significant content. `buildReindentedRawString` left such lines' text untouched
while still converting the preceding newline entry into a `HARD` break — and `Layout` synthesizes
ambient indent after every hard break, so each render PREPENDED one more ambient indent to the
line: non-idempotent, growing whitespace every fix round. Hit 11 real files during the wrasse
reformat (patch-format spec files whose raw strings deliberately carry whitespace-padded "blank"
lines, e.g. `WPatchWriterReaderSpec`), plateauing the convergence loop at 15 iterations; the
partial reformat was discarded (git stash) rather than committed. Fix (conservative, refusal not
handling): any interior whitespace-only line makes the whole string ineligible — preserved
verbatim. Failing-first per the dogfood hard rule:
`format-raw-strings/whitespace-only-line-bail-clean.kt` (expect-clean, failed before the fix,
passes after) plus a `DocBuilderSpec` untouched-rendering case.

#### C.12 hardening, part 2: a genuinely blank interior line was itself made non-idempotent by the first hardening's own eligibility check (found reproducing an owner-flagged printer bug, 2026-07-21)

The second D19 violation the format-on milestone's own mechanism has caught, surfaced by a `to`
infix expression whose right operand is a `trimIndent()`-chained multi-line raw string with a
genuinely blank interior line (`val x = "a" to` / `"""..."""` / `.trimIndent()`, the exact shape in
`InternalFailureIsolationSpec`'s `crashingSource` family): fixing such a file once left it flagged
"File is not wrasse-formatted" again on an immediate re-check, and a second fix pass converged
(third pass clean) to a *different* rendering than the first pass produced — `fix(fix(x)) !=
fix(x)`. Minimized and reproduced against the current tree with the actual harness (not by hand):
`format-raw-strings/infix-operand-blank-line-error.kt` failed at exactly this assertion
(`IdempotenceCycle`'s D2-must-equal-D1-minus-fixed check) before the fix below, on an otherwise
plain two-line-plus-one-blank-line raw string — no interpolation, no touching-quotes, no other
eligibility bail in play.

**Root cause.** `buildReindentedRawString`'s render loop (pre-fix) emitted one `Doc.Break(HARD,
literal = "\n")` *per source newline entry*, including a genuinely blank interior line's own
newline as its own separate break, immediately adjacent (in the `Doc.Concat`) to the break ending
the line before it. `Layout.renderBreak` appends `appendIndent` — real, visible space
characters — after *every* `HARD` break unconditionally, with no look-ahead for "another break
immediately follows." Two adjacent breaks therefore rendered as: newline, `indentDepth × 4` real
spaces (meant to lead the *next* line), then immediately another newline — i.e. the blank line
itself, not the line after it, inherited that indent run. `buildReindentedRawString` had thus
manufactured exactly the shape its own sibling eligibility rule (this entry's part 1, above) exists
to detect: an interior line that is not-quite-empty. Re-running the printer on that output (the
fixture's own round 2) found a whitespace-only interior line where round 1's input had a truly
blank one, correctly bailed per the existing rule, and left the string permanently
preserved-verbatim from that point on — a different, and now-frozen, rendering than round 1's own
reindented one. (This is a distinct mechanism from `resolveBinaryFrame`/`wrapRoot`'s own
`hasOwnIndentScope` — that function was already working as designed, treating a bailed string's
plain `Doc.Text` as carrying no indent scope of its own, per C.4/C.7's documented rationale; the
bug is upstream of it, in what `buildReindentedRawString` itself rendered.)

**Fix (`buildReindentedRawString`, `DocBuilder.kt`):** a maximal *run* of consecutive `"\n"`-only
entries — not one entry at a time — becomes a single `Doc.Break` whose own literal repeats that
many newlines, mirroring the run-newline-count pattern `clampWs` already uses elsewhere in this
same file for an ordinary multi-blank-line gap. `appendIndent` then fires exactly once per run,
right before whatever real content (or the closing quotes) follows it, never in the middle of a
blank line. This collapses the render loop's three-way `when` to two cases (the closing-tail case
and the real-content case); the interior-blank-to-interior-blank case that used to need its own
branch no longer exists, since the run-scan already consumes it.

**The invariant this generalizes to:** a printer mechanism that renders more than one forced break
in direct sequence, with no content between them, must merge them into one break carrying the
combined literal — never one break per source newline — because `Layout` synthesizes real,
visible indent after *every* `HARD` break unconditionally, and a lone break with nothing following
it inside the same run has no content to lead; it only manufactures whitespace on what must stay a
blank line. More generally still: any mechanism whose own eligibility check inspects previously
*rendered* text (here, "is this interior line whitespace-only") must never itself render text that
would fail that same check when fed back in — a transform must survive being re-run on its own
output.

**Per-file multi-pass is a D19 violation wherever observed, full stop** — this is the second
instance the format-on-wrasse milestone's own machinery has caught (the first is this entry's part
1, above), and both were real, silent violations that shipped before the machinery that caught
them existed. Neither is a special case; §5.1's fixed-point discipline and the D19 harness apply
uniformly to every printer mechanism, present and future — a construct that takes two fix rounds to
stabilize is a bug in that construct, not an acceptable cost of it.

**Fixtures:** `format-raw-strings/infix-operand-blank-line-error` (`.kt`/`.fixed.kt`), confirmed
failing-first against the pre-fix tree (fails inside `IdempotenceCycle.runIfFixEmitted`, not merely
at the `.fixed.kt` comparison) and green after. `DocBuilderSpec` gained a hand-built-SAX-events case
driving two consecutive genuinely-blank interior lines directly (a run of length two, not one)
through `buildReindentedRawString`, confirming no indentation is planted on either blank line and
also failing-first. Full sweep of every other `Doc.Break(HARD, ...)` call site in `DocBuilder.kt`
found none of the others assemble more than one break per gap already (`clampWs`, the
`forceMultilineBraceGaps`/`resolveAssignedValueFrame`/`resolveSuperTypeListFrame` single-insertion
call sites) — `buildReindentedRawString`'s own per-entry loop was the only site with this class of
defect.

**wrasseLint dogfood check:** with the fix applied, `InternalFailureIsolationSpec.kt` (the file that
surfaced this bug) required no further edit at all — its already-committed rendering (reached,
historically, only via more than one manual fix-and-reformat round before this bug was diagnosed)
is already a stable fixed point of the corrected printer: the blank interior lines in its own
`crashingSource`/`crashing`/`clean`/`source` raw strings are already padded from that prior history,
so they already bail the same way a freshly-manufactured-but-now-prevented pad would have, and the
file re-lints clean. The fix changes what a *fresh* fix round produces for a genesis-clean raw
string; it does not retroactively un-pad an already-scarred one, which stays exactly as
conservative and untouched as every other bailed shape in this entry.

#### Phase C test backfill — porting upstream ktlint/detekt rule-test suites onto the printer fixtures, 2026-07-21

A dedicated pass over the local `ktlint`/`detekt` checkouts' own rule test suites (read-only,
confirmed byte-clean throughout, never the internet) for every printer family C.5–C.12, enumerating
each upstream test case and classifying it: already covered by an existing fixture, worth porting as
a new one, out of scope per an already-documented preserved-verbatim/D21 decision, or upstream-
config-specific (a non-default `.editorconfig`/code-style axis with no wrasse analog). New fixtures
were added to the existing per-family directories only — no production code, no existing fixture, no
`wrasse.json`/`wrasse-schema.json` touched.

**Coverage table** (per family; counts are approximate tallies across each family's upstream rule
test files, not a strict per-`@Test` count):

| Family (dir) | (a) already-covered | (b) newly-ported | (c) out-of-scope (cited) | (d) config-skip |
|---|---|---|---|---|
| C.5 spacing (`format-spacing/`) | ~120 | 18 | ~85 (§13 C.5's own preserved-verbatim list; D21's erased code-style axis) | ~5 |
| C.6 blank-line caps (`format-blank-lines/`) | ~30 | 13 | ~13 (§13 C.6's "deliberately not implemented" list) | ~15 |
| C.7/C.9 signatures/annotations (`format-signatures/`, `format-class-signatures/`, `format-annotations/`) | ~120 | 34 | ~90 (§13 C.7/C.9's own preserved-not-guessed lists; the printer contract) | ~50 |
| C.8 trailing commas (`format-trailing-commas/`) | ~10 | 7 | ~20 (§13 C.8's own preserved-not-guessed list) | ~11 + detekt duplicates |
| C.10 wrapping cluster (`format-wrapping/`) | ~18 | 13 | ~26 (§13 C.10's own three-bucket map "(c) preserved" rows) | ~1 |
| C.11 declaration blank-line insertion (`format-declaration-blank-lines/`) | ~33 | 12 | ~9 (§13 C.11's own preserved-not-guessed list) | ~2 |
| C.12 comment-spacing/raw-string indent (`format-comment-spacing/`, `format-raw-strings/`) | ~20 | 10 | ~19 (§13 C.12's own preserved-verbatim/eligibility-bail list) | ~4 |

**Found bugs, three fixed in a follow-up pass, one resolved as an owner-triaged backend limitation.**
Four distinct real gaps, each originally demonstrated by one or more fixtures moved to
`testing/wrasse-test-harness/src/main/resources/fixtures-backfill-failing/<family>/` (a sibling of
`fixtures/`, not scanned by `wrasse.fixtures.dir` — excluded from the harness by construction, not by
any test-code change):

1. **Fixed.** A `PROPERTY_ACCESSOR` (`get()`/`set()`) was never given its own indent level relative
   to the property it belongs to — the printer rendered an accessor at the *same* depth as its
   owning `PROPERTY`, not one level deeper. `DocBuilder.resolvePropertyFrame` now checks for a
   `PROPERTY_ACCESSOR` child before its `EQ`-initializer check and, when one is found, delegates to
   `resolvePropertyAccessorsFrame`: the gap right before the first accessor, and everything from
   there through the frame's own end, move into one shared `Doc.Indent` — the same "gap plus tail
   share one indent scope" shape `resolveAssignedValueFrame` already used for a forced-multiline
   initializer value, just anchored at the first accessor instead of the value. Fixtures
   un-quarantined to `fixtures/format-declaration-blank-lines/{annotated-first-accessor-clean.kt,
   consecutive-properties-with-accessors-clean.kt, property-accessor-annotation-forces-error.{kt,
   fixed.kt}}`, `fixtures/format-spacing/property-accessor-keyword-paren-spacing-error.{kt,fixed.kt}}`
   — all pass. `property-accessor-annotation-forces-error.fixed.kt`'s own trailing byte was also
   corrected (it carried a stray final newline no sibling `.fixed.kt` in the family has, since this
   family never enables `trailing-newline`).
2. **Fixed.** A comment nested inside a property's own initializer expression (after `=`, before the
   value) lost its indentation, rendering flush left instead of one level deeper — not the
   blank-line-insertion misattribution originally hypothesized (`ChildEntry.Resolved.hasLeadingComment`
   was confirmed, by direct instrumentation of the real compile, to already be computed correctly:
   the comment is a direct `PROPERTY` sibling between `EQ` and the value, never the property's own
   first child, so `hasLeadingComment` was `false` all along and no blank-line-insertion branch ever
   misfired). The real defect: `resolveAssignedValueFrame` bailed outright whenever the token right
   after the anchor was a comment, so `resolvePropertyFrame` fell through to the non-indenting
   `resolveBraceFrame` default and the comment-plus-value tail rendered at the property's own ambient
   depth. Fixed by letting `resolveAssignedValueFrame` treat a leading comment as an unconditional
   trigger for its existing "move the tail into one shared `Doc.Indent`" handling (bypassing the
   `MULTILINE_WRAPPABLE_VALUE_TYPES`/`spansMultipleLines` gate, which still applies unchanged to every
   other value kind) — the same indent machinery, reused, not a new policy path. Fixture un-quarantined
   to `fixtures/format-declaration-blank-lines/comment-inside-initializer-not-leading-clean.kt` — passes.
3. **Fixed.** A trailing/attached comment defeated two of C.6's documented forced-newline special
   cases, both of which read only their gap's *immediate* neighbor entries: (a) the
   class-name-identifier→`PRIMARY_CONSTRUCTOR` zero-blank-line rule, when a trailing `// comment` sits
   on the class-name line before the blank line; (b) the `PACKAGE_DIRECTIVE`→`IMPORT_LIST`
   forced-blank-line insertion, when a comment sits directly between the package statement and the
   import. Ktlint's own equivalents (`NoConsecutiveBlankLinesRule`, `PackageImportSpacingRule`, local
   read-only checkout, confirmed byte-clean throughout) both skip over an intervening comment rather
   than bailing: the former via `prevCodeLeaf` (walks past non-code leaves to the real previous
   token), the latter via `siblings().takeWhile { it.elementType != IMPORT_LIST }` (comments are just
   more siblings in the scanned run). Mirrored the same skip-over shape: `verticalGapNewlineCount` now
   takes `children`/`index` instead of pre-extracted `prevEntry`/`nextEntry`, and two new helpers,
   `firstNonCommentEntry`/`lastNonCommentEntry`, walk past `WHITE_SPACE`/comment entries in the
   respective direction before the two special-case checks compare types — every other gap decision in
   the function is unaffected, since it still reads the unchanged immediate `prevEntry`/`nextEntry`.
   Fixtures un-quarantined to `fixtures/format-blank-lines/{class-primary-constructor-comment-before-
   blank-line-error.{kt,fixed.kt}, package-import-comment-attachment-error.{kt,fixed.kt}}` — both pass.
4. **Investigated, not fixed — resolved as a second Kotlin 2.1.x backend limitation, same shape as
   the existing `modifier-order`/`data`+`internal` entry below.** The printer's reformatted output for
   two unrelated constructs (a `data class` with a supertype forced multi-line by the parameter-count
   threshold; a sequence of adjacent top-level declarations gaining C.11-forced blank lines) crashes
   the Kotlin 2.1 compiler backend during IR lowering (`BackendException`, root cause
   `IllegalStateException` at `Fir2IrDeclarationStorage.findContainingIrClassSymbol` — the identical
   crash site as the existing entry). Confirmed independently: `testMinor` on
   `wrasse-kotlinc-plugin-tests-2-1-x` reproduces both crashes deterministically; the identical
   fixtures compile and idempotence-check cleanly on `wrasse-kotlinc-plugin-tests-2-{2,3,4}-x`'s own
   `testMinor`/`test`. Both underlying printer decisions are independently proven correct and
   2.1-compatible by other, already-shipped, non-crashing fixtures with nearly identical shapes minus
   the `data` keyword — `format-class-signatures/supertype-single-join-with-wrapped-ctor-error` (a
   plain `class` with the same wrapped-constructor-plus-supertype-join shape) and
   `format-declaration-blank-lines/top-level-classes-error` (the same adjacent-top-level-declaration
   blank-line insertion) — and round 1 (the original, unreformatted source, with the identical `data
   class` declarations) compiles cleanly in both fixtures; only round 2, the applied reformat, crashes,
   exactly mirroring the `data`+`internal` precedent's own round-1-clean/round-2-crash shape. This is
   evidence for a genuine Kotlin 2.1.x compiler defect, not a printer-side shape to avoid — searched
   the harness for a per-minor fixture-exclusion mechanism (there is none: a fixture set is replayed
   identically across every `wrasse-kotlinc-plugin-tests-<minor>-x` module, and the existing
   `data`+`internal` precedent resolved the same dilemma by removing its own crashing case from the
   end-to-end fixture set entirely rather than inventing one). No harness change made. The two
   fixtures remain exactly where they already were —
   `fixtures-backfill-failing/format-class-signatures/data-class-supertype-join-error.{kt,fixed.kt}`,
   `fixtures-backfill-failing/format-declaration-blank-lines/object-declaration-sequence-error.{kt,
   fixed.kt}` — permanently parked outside `wrasse.fixtures.dir`, not a leftover of this pass. See the
   new §14 entry below for the full evidence trail.

**Totals:** 173 new fixture files added across the ten family directories; 15 files (7 distinct
fixtures) un-quarantined once fixed (bugs 1–3 above); 4 files (2 distinct fixtures) remain
permanently parked under `fixtures-backfill-failing/` (bug 4). Full ladder
(`build`, `test --rerun-tasks`, `testMinorHarness --rerun-tasks`, `testPatchHarness`, `wrasseLint
-Prepublish`) green against the resulting fixture set. `ktlint`/`detekt` checkouts confirmed
byte-clean (`git status`) throughout — read-only.

**Follow-up fix pass (2026-07-21):** bugs 1–3 above fixed in `DocBuilder`
(`resolvePropertyFrame`/`resolvePropertyAccessorsFrame`/`resolveAssignedValueFrame`/
`verticalGapNewlineCount`), each with a new `DocBuilderSpec` unit test at its own decision point;
bug 4 investigated and resolved as documented above, no production code touched. The three live spots
this same accessor-indent gap had already put into the committed repo itself (`WContext.kt`,
`InternalFailureIsolationSpec.kt`, `WrasseTestHarness.kt` — flush-left `get()`/`set()` on multi-line
properties) are expected to report `format`/"File is not wrasse-formatted" on the next `wrasseLint`
until a `wrasseFix` re-convergence pass corrects them; that specific failure mode is this fix working
as intended, not a regression.

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

- **Report-only bails + `level: error` cannot converge.** A rule that reports but attaches no
  edit (every documented bail: comment adjacency, multiline bodies, ambiguity) makes `wrasseFix`
  unable to clear the finding, so an error-level build stays red with no automated remedy — the
  user must hand-edit or `@Suppress`. Found dogfooding `when-entry-bracing` on this repo, whose
  own `when`s hit the multiline-body bail repeatedly; its self-lint enablement is deferred for
  that reason (the rule itself is shipped and fixture-proven, and fixed 10 real findings here
  correctly before the bails blocked convergence). The build still cannot converge on its own —
  that reality is unchanged — but the silent-dead-end part is fixed: `WrassePlugin.checkFile`'s
  reporter now appends `" (no autofix for this shape)"` to a report's message whenever its rule is
  autofix-capable ([WUninitializedRule.canAutofix] / [WUninitializedRuleGroup.canAutofix]) and this
  particular occurrence attached no edit, so the user sees *why* `wrasseFix` didn't move it rather
  than just a build that stayed red. Report-only rules (naming/metrics/smells) never carry the
  marker — `canAutofix` defaults to false and is only set true on the shipped fixer rules/engine.
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
- **Blocker (wave-2 installment-7 backfill, 2026-07-20), fixed the same day by fusion into
  `ModifierEngine` — history kept below for the record: `redundant-visibility-modifier` +
  `modifier-order`, enabled together, crash the entire compile with `INTERNAL_ERROR`
  whenever a redundant `public` also participates in an out-of-order modifier list.** e.g.
  `suspend public fun bar() {}` inside a class, with both rules at `level: error`. Both rules
  independently compute an edit anchored at the same `public` keyword's own token span the moment
  it is *both* redundant *and* mis-ordered relative to a sibling modifier: `modifier-order`
  replaces that span's text in place (a same-span swap with the correctly-ordered keyword),
  `redundant-visibility-modifier` deletes that same span plus its trailing whitespace. Ground-
  truthed against a real compile (not inferred from reading the two rules): `EditPlan.finalEdits()`
  — the project's one existing cross-rule arbiter, whose own KDoc only ever documented the
  single-rule-composing-its-own-nested-edits case — throws `IllegalStateException` on the
  pairwise-disjointness `check(...)` (`"EditPlan: overlapping edits from rule 'modifier-order'
  (40..46 -> \"suspend\") and rule 'redundant-visibility-modifier' (40..47 -> \"\")"`), uncaught by
  `WrassePlugin.checkFile` (`ctx.editPlan.finalEdits()` is called unconditionally at line ~100,
  *before* the `fixOutputDir != null` gate — this fires during plain linting, `-Pwrasse.fix` is not
  required to trigger it). The exception propagates up through FIR's
  `DeclarationCheckersDiagnosticComponent` and is caught only by kotlinc's own top-level
  `FirCliExceptionHandler`, which turns it into an opaque `EXCEPTION`-severity diagnostic (a raw
  internal stack trace, not a wrasse-attributed message) and `ExitCode.INTERNAL_ERROR` — the whole
  file fails to compile with no clean wrasse diagnostic at all, worse UX than a normal `error`-level
  finding. Confirmed twice independently (this session's own reproduction via a throwaway,
  uncommitted probe spec against a real `K2JVMCompiler` invocation, discarded after confirmation —
  not left in the tree — plus a forked sub-agent's own separate reproduction, matching exactly) and
  by hand-tracing the pure decision objects (`ModifierOrderDecision.decide`/
  `RedundantVisibilityModifierDeletionSpan.compute`) against the same input, which independently
  predicted the exact overlapping `WEdit` ranges before either real-compile reproduction ran. A
  realistic trigger, not a contrived edge case: both rules are ordinary, independently-reasonable
  T-bucket style rules a real `wrasse.json` could enable together with no warning today. Not fixed
  in-task: neither rule's own decision logic is wrong in isolation (`modifier-order`'s reorder is
  correct; `redundant-visibility-modifier`'s deletion is correct), so there is no narrow, contained
  fix available in `ModifierOrderRule`/`ModifierOrderDecision` or `RedundantVisibilityModifierRule`/
  `RedundantVisibilityModifierDeletionSpan` — narrowing either rule's own deletion span cannot help,
  since the conflict is two *different* rules disagreeing about the same span's fate, not a
  self-inflicted span-computation bug in either one. A real fix means giving `EditPlan` an actual
  cross-rule conflict policy — e.g. demote every entry in a detected overlap cluster to report-only
  rather than throwing, mirroring the project's own established do-no-harm comment-bail precedent
  but generalized across rule boundaries for the first time — which is new, cross-cutting
  infrastructure with no precedent anywhere in the rule model, and a behavior change (from "crash"
  to "silently drop autofix but keep both diagnostics") an owner should sign off on, not an
  implementation detail: it is not obvious a silent downgrade is even the right choice over, say, a
  config-time validation that rejects enabling both rules, or a documented restriction. No fixture
  was added for this shape — a permanently red fixture is not how this project locks a known
  blocker (see the `modifier-order`/Kotlin-2.1-backend-crash blocker, above, which took the same
  approach). **Fixed 2026-07-20**, the same day this was found: the two rules are now
  `ModifierEngine`, one fused decision-maker behind both unchanged ids (§5.1's own "fighting rules
  get fused" mechanism, §13's "Phase B" now carries the as-built paragraph). The `EditPlan`-level
  conflict-policy question this entry raised — demote overlapping edits to report-only vs. reject
  the config vs. document the restriction — turned out not to need answering at all: fusing the two
  decision-makers means `modifier-order` never computes an edit for a keyword `redundant-visibility-
  modifier` is deleting in the first place, so `EditPlan` never sees an overlap to arbitrate.
  `EditPlan.finalEdits()`'s own pairwise-disjointness `check(...)` is unchanged and still load-
  bearing for genuine rule bugs; this entry's specific crash is now covered by a passing
  `modifier-order-visibility-combined/crash-repro-error` fixture (previously left un-fixture-tested
  on purpose, per this entry's own "no fixture for a permanently red case" reasoning — now that it
  passes, it is a real regression test instead). Superseded by D24 (§12, 2026-07-20): had this
  overlap still been reachable today, it would no longer take the whole compile down with it —
  `WrassePlugin.checkFile` now catches any `Throwable` from wrasse's own code per file, so the same
  `check(...)` failure this entry describes now surfaces as one `RuleLevel.WARN` diagnostic
  carrying this exact overlap message, with the file's own wrasse output skipped and the rest of
  the compile (including kotlinc's own diagnostics) unaffected.
- **Found while building D24's isolation tests (2026-07-20), not fixed, not wrasse's bug: a
  `warn`-level wrasse diagnostic is silently dropped by kotlinc whenever the same compile also
  carries any `error`-severity diagnostic — even on a different file.** Reproduced two ways: (1)
  directly against the production single-registrar harness with `no-semicolons` at `level: warn`
  on a file that also has a genuine type-mismatch error — the semicolon warning never reaches the
  `MessageCollector`, only the type-mismatch error does, though `WrassePlugin.checkFile` computes
  the violation correctly (confirmed by instrumenting `FirSyntacticChecker` directly); (2) the same
  shape across two files in one compile (one `error`, one `warn`, from two different rules) — same
  result. Flipping the same diagnostic to `error` severity makes it appear reliably (also
  confirmed), so this is specifically a severity-mixing interaction, not an offset, ordering, or
  message-content issue. Consequently, **D24's own internal-error warning is subject to this same
  constraint**: if the file that crashed a wrasse rule also has a genuine compiler error, the
  internal-error warning itself may not be visible to the user, though the compile still proceeds
  and reports that real error normally (`COMPILATION_ERROR`, never `INTERNAL_ERROR`) — the core
  safety property D24 exists for is unaffected, only the attribution's visibility in that specific
  combination. `InternalFailureIsolationSpec`'s first case works around this by running two
  compiles rather than asserting both diagnostics from one. Not investigated further: the
  mechanism lives inside kotlinc/FIR's own diagnostic-severity handling for plugin-contributed
  (`FirAdditionalCheckersExtension`) diagnostics, not in any wrasse-owned code path — fixing or
  even fully explaining it would mean instrumenting K2's checker/diagnostic-reporting internals, an
  investigation of its own rather than a contained change.
