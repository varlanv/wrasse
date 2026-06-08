# Wrasse — Decision Log

Why each significant choice was made, and what was explicitly rejected. The point of this file is
to **stop re-litigating settled questions**. If you're about to reopen one of these, read the
rationale first; reopen only with new information.

Status legend: **Accepted** · **Rejected** · **Deferred** · **Superseded**.

---

## Architecture

### D1 — Compiler plugin, not a Gradle plugin or embedded compiler · Accepted
Ride kotlinc's FIR analysis and read its LightTree. Avoids the redundant full parse ktlint/detekt
pay, and avoids coupling to Gradle's churning API. Cost: lint only runs when a compile runs, and
config files aren't automatically tracked as build inputs (mitigated by an optional thin Gradle
plugin in Phase D).

### D2 — Own intermediate model (WNode) behind an adapter · Accepted
Rules depend only on `WNode`/`WFile`, never on kotlinc types. Isolates the unstable K2 surface to
the adapter and keeps rules testable without a compiler and portable to other hosts.

### D3 — Two-host model: read-only lint plugin + standalone read-write fixer/formatter · Accepted
A compiler plugin observing a live compile cannot rewrite the files being compiled. Linting stays a
read-only observer (Host A); formatting/fixing is a separate, explicitly-invoked tool (Host B) that
does its own parse. Same rule library behind both. This dissolves the "how do we write during
compile" problem.

### D15 — Inbound rules now; outbound deferred · Accepted / Deferred
Inbound checks (resolve what this file uses) are per-file and survive incremental compilation;
outbound checks (who uses this file) need the whole program and break under incremental. All
flagship work is inbound. Outbound is parked, and if ever built must be full-build-only — **not** a
persistent cross-ref index and **not** reading kotlinc's internal incremental caches.

---

## Config

### D4 — Effective config via `extends`; not mandatory-all-rules · Accepted (supersedes earlier)
Originally every rule had to be listed so users would notice new rules each release. That breaks
every config on every release. Replaced by `extends` inheritance; an absent rule is off/inherited.
Malformed config still fails fast. Discoverability moves to tooling (`--list-rules`) — see D6.

### D5 — `level: off | warn | error` per rule; not global-only severity · Accepted (reverses earlier)
Earlier decision was global severity with a `warnOnly` flag and "make separate tasks" as the
escape hatch — but separate tasks mean separate compiles, the exact double-work wrasse avoids.
Per-rule `level` is one tri-state axis (not separate enable + severity), composes cleanly with
`extends`, and the diagnostic mechanism already supports per-violation severity at no cost. Global
`warnOnly` survives as a blanket downgrade.

### D6 — No `recommended` preset; new rules default off · Accepted
Presets (biome/eslint-recommended) hide what's active and silently change CI results on upgrade.
Defaulting new rules off buys reproducibility-across-upgrades; discoverability is served by
`--list-rules` / an effective-config dump instead.

### D7 — No baseline file · Accepted
Adoption is handled by `level` and `exclude`, not a baseline. Avoids the violation-signature
machinery a baseline requires. (Revisit only if targeting drop-in adoption on huge legacy repos.)

### D8 — Suppression via `@Suppress("rule-id")` only; no comment directives · Accepted
No `wrasse:disable`-style comments scattered in source. Kotlin's `@Suppress` targets `EXPRESSION`,
so per-call suppression works without comments. Cost: suppression is annotation-granular (no
arbitrary line-level), which is an accepted trade.

### D11 — `formatting-` key convention + `formatting-opinionated` escape hatch · Accepted
One `rules` block, no lint/format split; the `formatting-` prefix only groups keys. The engine
derives "is a formatting rule" from the rule's type, not the string prefix. `formatting-opinionated`
is a whole-file formatter and errors if any other purely-formatting rule is enabled (you can't mix
a whole-file formatter with à-la-carte format rules coherently).

### D12 — No `.editorconfig` support · Accepted
Parsing editorconfig + ktlint's property semantics is a tar pit. Provide native rules and a
migration path instead.

---

## Fixing & formatting

### D9 — Autofix off by default, opt-in, behavior-preserving; no SAFE/SUGGESTED tiers · Accepted
A two-tier "safe vs suggested" fix taxonomy (eslint-style) is config bloat nobody drives by hand.
Instead: every autofix rule is off until opted in, the user owns the risk, and the single
implementation constraint is that a fixer must be behavior-preserving and **bail when uncertain**
(e.g. an if-split fixer bails on `else`; import expansion bails on ambiguous resolution).

### D10 — Semantic fixes as a per-module edit-list; exact offsets, hash-guarded · Accepted
Resolution exists only during the read-only compile, so resolution-needing fixes can't use the
cheap syntactic write path. They're emitted as exact offset edits `(file, start, end, replacement,
sourceHash)` — not fuzzy diff (git diff is an export-only view). One patch file per module (modules
compile in parallel processes; a global file would need flaky cross-process locking). Apply is
idempotent via the content hash (a stale patch is a no-op), atomic (temp + rename), non-overlapping
(descending offset order, fail loud), and explicit (`wrasseApply`, never auto-run). Syntactic fixes
bypass all this and write directly from Host B.

---

## Build & distribution

### D13 — `$schema` hosted on GitHub Pages · Accepted
Config JSON schema served statically for editor autocomplete.

### D14 — JVM 8 bytecode, single JAR across a Kotlin range · Accepted
The plugin loads into the kotlinc daemon classloader. Compile against latest supported Kotlin;
absorb version differences in the adapter + runtime-selected registrar shells (`k20`/`k22`).

---

## Rejected

### R1 — ktfmt byte-for-byte compatibility ("win Google/Meta off ktfmt") · Rejected
Drop-in compat requires 100% bug-for-bug fidelity against a moving target, forever, plus matching
ktfmt's IDE reformat-on-save workflow. Unwinnable treadmill. Formatting will be a stable "wrasse
style" with a one-time migration, differentiated by lint+format-in-one and import optimization.

### R2 — Nursery checked-exception rule (force-wrap known-throwing functions) · Rejected
Even the inbound-sound version (throwing-ness in the signature, DB as bootstrap) is a heuristic not
a guarantee, effectively rebuilds Java checked exceptions (viral function-coloring), and the throw
surface (operators/intrinsics, non-inline/suspend lambda escape, `runCatching` eating
`CancellationException`) makes it brittle and boilerplate-heavy. Against Kotlin's grain and off the
locked product focus. (The user's errors-as-values preference still informs wrasse's own code.)

### R4 — SAFE_FIX / SUGGESTED_FIX two-tier autofix taxonomy · Rejected
Superseded by D9 — the workflow is rare in practice and bloats config. Behavior-preservation is an
implementation constraint, not a config axis.

> A broader working principle behind several rejections: don't cargo-cult eslint/prettier/biome
> conventions. Prefer minimal, single-axis config; the user owns autofix risk.
