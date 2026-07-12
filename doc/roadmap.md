# Wrasse — Roadmap

**Product focus (locked):** be a drop-in replacement for **ktlint + detekt**, much faster, plus
resolution-powered **import optimization**. Do one thing very well. See
[decisions.md](decisions.md) for the choices behind this and [hld.md](hld.md) for the architecture.

Scope discipline is the point of this file. Anything not in a phase below is **parked** or
**dropped** — see the bottom — and should not creep into earlier work.

## Done — Foundation

- Compiler plugin loads; FIR checker fires; `KtDiagnostic` reported with file/line/column.
- `WNode` concrete CST + `LightTreeAdapter` (eager, stack-based) + `WNodeTypeMapping`.
- Hand-rolled zero-dep JSONC config reader; config loaded from source roots at registration.
- `WRule` sealed hierarchy (NodeVisitor + FileVisitor) + ordinal dispatch table.
- Runtime version-shell selection (`k20` / `k22`); single JAR.
- 3 rules: `no-semicolons`, `no-wildcard-imports`, `trailing-newline`.
- Fixture auto-discovery harness + Kotlin version matrix (2.1–2.4).

## Phase A — Config & severity model

**Goal:** a config model that scales to hundreds of rules without breaking on every release.

Done:
- ~~Replace per-rule `enabled: Boolean` with `level: off | warn | error`.~~ Rules carry their
  configured level; the reporter reads `rule.config.effectiveLevel` per violation and picks the
  diagnostic factory accordingly. Global `warnOnly` CLI flag downgrades error→warn.
- ~~Data-driven config parsing.~~ `WConfig.from()` takes the set of known rule IDs; adding a rule
  no longer requires editing the config parser.
- ~~Effective config with `extends`.~~ A config can extend a base via `"extends": "path"`;
  child values override base values. Absent rules default to off. Malformed config still
  fails fast; circular/deep extends chains are caught.

Remaining:
- Honor `@Suppress("rule-id")` at expression and declaration scope.
- `--list-rules` / effective-config dump + JSON schema for editor autocomplete (the
  discoverability story that replaces a `recommended` preset).
- Optional CLI override for config path (separate lint vs format configs).

**Exit:** a new rule can ship without editing any existing user config; a rule can be warn while
another is error in the same run; `@Suppress("id")` silences one rule.

## Phase B — Rule engine + parity port (the bulk)

**Goal:** cover what ktlint + detekt cover, on one parse.

- Solidify the syntactic rule API; fix the `no-semicolons` statement-separator false positive
  (with tests).
- Add the `SemanticWRule` family + FIR resolution facade + the LightTree↔FIR correlation adapter.
- Port rules in priority order using [rule-port-plan.md](rule-port-plan.md):
  1. Syntactic, non-autofix lint (smells/style) — fast wins, no new infra.
  2. Syntactic autofix-capable rules — feed Phase C.
  3. Inbound-resolution rules — once `SemanticWRule` lands.

**Exit:** a representative real project lints under wrasse with parity-equivalent findings to its
existing ktlint + detekt setup (minus deferred outbound rules), at measurably lower wall-clock.

## Phase C — Formatting & fix application

**Goal:** the write path, including the import-optimization moat.

- **First, the load-bearing spike:** can the LightTree parser run standalone without the heavy
  `KotlinCoreEnvironment` startup? Benchmark standalone parse vs ktlint/ktfmt. Everything else in
  this phase rests on the answer.
- Standalone read-write host (B): parse → `WNode` → transform → write (only-if-changed, atomic,
  `--check` mode exits nonzero).
- Whole-file single-pass formatter for `formatting-opinionated`; à-la-carte `formatting-` rules
  otherwise (with the mutual-exclusion guard).
- Semantic-fix edit-list pipeline: per-module patch files, exact offset edits, hash-guarded
  idempotent apply, `wrasse.fix` flag, explicit `wrasseApply`.
- **Flagship: import optimization** — star-import expansion + unused-import removal via resolution.
  Autofix off by default, opt-in, behavior-preserving (bail on ambiguity).

**Exit:** `formatting-opinionated` reformats a file idempotently; `wrasse.fix` expands a star
import correctly on a real module; format is faster than ktlint -F on the same files.

## Phase D — Hardening & release

**Goal:** trustworthy enough to publish.

- Extend the version matrix (per-patch coverage); fuzz on real-world Kotlin repos.
- Perf benchmarks vs ktlint + detekt (lint) and ktlint -F / ktfmt (format), published.
- SARIF / checkstyle reports from Host B for CI dashboards.
- Optional thin Gradle plugin: declare `wrasse.json` content as a compile input (so config edits
  re-trigger) and wire the format/apply tasks — while keeping the compiler plugin host-agnostic.
- Publish to Maven Central; `$schema` on GitHub Pages; write the README.

**Exit:** another team can adopt wrasse from published artifacts and docs without our help.

## Parked (deliberately deferred, not rejected)

- **Outbound / whole-program rules** — dead code (public/internal), architecture & layering,
  circular dependencies, unused/undeclared dependencies, convention-drift mining. Powerful but
  full-build-only and infra-heavy; revisit after the core ships. See
  [decisions.md](decisions.md) (D15).
- **Novel semantic rules** — no-recursion, split-compound-boolean/assertion, function visual line
  limit, explicit-library-defaults, restricted-API. Later differentiation, not parity.

## Dropped (do not revisit)

- **Nursery checked-exception rule** (force-wrap known-throwing functions) — too brittle, against
  Kotlin's grain. See [decisions.md](decisions.md) (R2).
- **ktfmt byte-compatibility** ("win Google/Meta off ktfmt") — unwinnable compat treadmill; was an
  adoption fantasy. See [decisions.md](decisions.md) (R1).
- **Baseline file** and **comment-based suppression** — see [decisions.md](decisions.md) (D7, D8).

## Load-bearing risks

1. **Standalone LightTree parse** without heavy environment startup (Phase C gate). If false, the
   format/fix perf story shrinks to per-file PSI-avoidance only.
2. **FIR API instability** across Kotlin versions — mitigated by the adapter isolation + version
   matrix; ongoing tax.
3. **Import-optimization correctness** — must bail on ambiguous/shadowed resolution rather than
   guess; a wrong autofix permanently burns trust.
