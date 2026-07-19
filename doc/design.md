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
star covers. The `WStreamRule` shape here is interim: once the ImportEngine's buffered-node engine
exists, unused-import detection folds into it rather than staying a standalone stream rule.

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
each locked by a fixture in `no-wildcard-imports-expansion/`:
1. **Whole-file.** `ctx.resolvedUsage == null` or `hasResolutionErrors` — the rule never even
   calls the decision function, every star in the file reports with no edit.
2. **Class/object-star.** Any used callable whose `classFqName` equals `P` *exactly* means `P`
   itself names a class/object (a member-star import, e.g. `import p.SomeEnum.*` for its
   entries) rather than a package — package-stars only in this task, member-star expansion is
   deferred.
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
   visible names (alias if present, else simple name) or this star's own attributed simple names,
   else that star bails. Coverage deliberately excludes the file's own top-level declaration
   names — a second declaration-collecting pass is not "cheaply available" from this rule's
   leaf-stream assembly, so that source is skipped outright rather than approximated; skipping it
   only ever produces *more* bails, never a false "covered".

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

**Deferred to a future engine:** import re-sorting after expansion, member-star (class/object)
expansion, and fusing this with `no-unused-imports`/FQN-shortening into one `ImportEngine`
decision-maker remain future work per §6/§13 B.3 — this pass ships the expansion fix standalone,
bailing everywhere a real engine would eventually own the decision instead.

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
  via `./gradlew :testing:wrasse-benchmarks:jmh`; not part of `build`/`test`/`check`.

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
   `build`/`test`/`check`. Smoke run (2 warmup + 3 measurement iterations, 1 fork) over this repo's
   own concatenated `.kt` sources: zero-rules walk ~11.19ms/op after vs ~11.64ms/op before the
   punch list; three-shipped-rules walk ~11.56ms/op after vs ~12.17ms/op before (single-fork JMH
   noise is double-digit-percent at this iteration count — treat as directional, not precise).
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
- **B.2 — targeted fixes (~15, bucket T).** Braces family, `modifier-order`, redundant-syntax
  deletions. Each gated by the idempotence harness; born-clean discipline.
- **B.3 — ImportEngine (bucket S).** `SemanticWRule` + FIR resolution facade + LightTree↔FIR
  correlation adapter. One engine, several config keys. Bail on ambiguity. A mini-project.
  Resolution-facade spike done (`WResolvedUsage` on `WContext`, lazy/gated collection,
  `dumpResolvedUsage` debug option, §8) — de-risked the FIR surface across 2.1–2.4; the
  `SemanticWRule` unification and offset correlation remain. `no-unused-imports` shipped ahead
  of the engine (§8) — a first consumer of `requiresResolution`/`resolvedUsage`, not the engine
  itself. Removal autofix also shipped ahead of the engine: whole-line deletion when the
  directive is alone on its line(s); bails with no edit (report-only, D9) when it shares a line
  with a sibling import or trailing comment, to avoid a cross-rule idempotence break with
  `no-semicolons` (§8) — locked by `.fixed.kt` companion-file fixtures (§11) including a
  dedicated dual-rule fixture dir. `no-wildcard-imports` star expansion (package-stars only;
  member/class-star expansion, import re-sorting, and fusing with `no-unused-imports` into one
  decision-maker are still deferred to the eventual engine) shipped ahead of it too — §8 has the
  attribution rules and all seven bails; `imports-full/` locks that its edits compose correctly
  alongside `no-unused-imports` and `no-semicolons` without an engine.

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
