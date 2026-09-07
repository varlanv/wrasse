# AGENTS.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

Wrasse is a Kotlin compiler-plugin linter/formatter meant to replace ktlint + detekt: it rides
kotlinc's own FIR analysis and reads the LightTree the compiler already built, instead of each tool
re-parsing the world. **[doc/design.md](doc/design.md) is the single source of truth** — architecture,
the decision log D1–D21 (**read §12 before reopening any settled decision**), the roadmap, and known
issues, all in one document. Its §3 describes what is implemented today; §5 (fix/format pipeline with
the printer, EditPlan, idempotence invariant) is the decided target — **not implemented yet**, don't
treat it as current behavior. [doc/autoformat-scope.md](doc/autoformat-scope.md) classifies all 255
ktlint/detekt/diktat rules into formatter/fix/lint-only buckets — consult it before porting any rule.
The remaining doc/ files (ktlint/detekt/diktat catalogs) are reference data only.

The project is currently pre-Phase-B: 3 rules shipped (`no-semicolons`, `no-wildcard-imports`,
`trailing-newline`), MVP autofix (offset-patch edit-list) working end-to-end.

## Commands

Build/test via the Gradle wrapper only (`./gradlew`, never a bare `gradle`).

```
./gradlew build                     # compile everything
./gradlew test                      # unit tests in libs/* (kotest, JUnit platform)
./gradlew testMinorHarness           # fixture tests against all supported Kotlin minors (2.1–2.4)
./gradlew testPatchHarness           # fixture tests against all tracked Kotlin patch versions
./gradlew :testing:wrasse-kotlinc-plugin-tests-2-4-x:test   # fixture tests for one Kotlin minor
./gradlew wrasseLint                 # self-lint: republish plugin, then compile this repo with wrasse checks on
./gradlew wrasseFix                  # self-fix: the same check compile as wrasseLint (-PwrasseCheck, UP-TO-DATE
                                      # if check just ran), then apply the emitted patch (wrasseApply)
./gradlew :internal-convention-plugin:test   # build-logic tests (TestKit) — a separate included build,
                                              # NOT reached by the root `build`/`test` tasks above; run explicitly
./gradlew :app:wrasse-gradle-plugin:test     # functional (TestKit) suite of the published Gradle plugin; publishes
                                              # the compiler plugin to mavenLocal first, no unit tests by design
```

- `-Prepublish` on `wrasseLint`/`wrasseFix` forces `publishToMavenLocal` first — needed after changing
  rule/plugin code before wrasse can lint itself with the new build.
- `wrasseFix` first runs `wrasseFormatRequest`, which drops a `format-request` file into every
  compilation's `build/wrasse/<compilation>/` (D25); the following check compile then keeps every
  autofixable diagnostic quiet and still emits the patch (`quiet=true` in that file keeps every
  diagnostic quiet — the Gradle plugin's lint and format runs use it and print from the report instead). The file is consumed by that compile,
  ignored after five minutes, and removed by `wrasseApply`. `./gradlew wrasseFix -PwrasseDebugPerformance`
  adds `debugPerformance=true` to it: the compile then records per-phase and per-rule timings into
  `build/wrasse/<compilation>/wrasse-perf.txt` and `wrasseApply` prints them together with its own.
- Patch emission rides check mode (D22): whenever `-PwrasseCheck` is set, every compile task emits its
  own patch under `build/wrasse/<compilation>/patch/wrasse-fixes.txt` (merge-on-write, self-cleaning),
  regardless of whether `wrasseFix` is the task being run — `wrasseFix` is just that same compile
  (identical args to `wrasseLint`, so Gradle sees it as UP-TO-DATE if check already ran) followed by
  `wrasseApply`. Editing `wrasse.json` does **not** invalidate the compile tasks (D1's cost), so after
  a config change run `wrasseLint`/`wrasseFix` with a change to a source file, or otherwise force
  recompilation, before trusting the patch.
- There is no per-test CLI filter for a single fixture (Kotest generates one dynamic test per fixture,
  named `"handle spec - ${ruleId} -> ${fixtureId}"`). To add a test, **add a fixture file** — see below.
  To scope a Gradle run to one Kotlin minor, target that submodule's `test`/`testMinor` task directly.
- Per-patch-version tasks are `testPatch_<version>` (e.g. `testPatch_2_4_0`) inside each
  `testing:wrasse-kotlinc-plugin-tests-<minor>-x` module.

### Adding a test = adding a fixture file

Fixture tests are auto-discovered `.kt` files under
`testing/wrasse-test-harness/src/main/resources/fixtures/<fixture-dir>/`, each paired with a
`wrasse.json` in that directory (or inherited via `"extends"`). Directives inside the `.kt` file drive
expectations (see `FixtureParser`):

- `// expect-error <line>:<col> <rule-id> "<message>"` / `// expect-warning ...`
- `// expect-clean` — file must produce zero diagnostics (mutually exclusive with expect-error/warning)
- `// fixture-option: trailing-newline` / `// fixture-option: warn-only` / `// fixture-option: multi-pass-fix` — harness options
  (`multi-pass-fix` lets a fixture whose fix needs a further apply round, because an overlapping edit was dropped,
  converge in up to five rounds; every other fixture must reach its fixed point in one)

The same fixture set runs against every supported Kotlin minor (2.1–2.4) via the per-minor test
modules — fixtures are Kotlin-version-agnostic by construction.

## Architecture

### Two-host model (the spine of the design)

Linting is read-only; fixing/formatting is read-write. A compiler plugin observing a live compile
can't rewrite the files being compiled, so there are two hosts sharing one rule library:

- **Host A — compiler plugin** (`app/wrasse-kotlinc-plugin`): runs during a real `kotlinc` compile,
  rides the LightTree kotlinc already parsed, reports `KtDiagnostic`s. Zero extra parse cost.
- **Host B — patch applier** (`wrasseApply`, e.g. via `./gradlew wrasseFix`): runs after that same
  compile and writes what Host A already decided. Pure byte-splicing against the patch file — no
  parse, no tree, at all. There is no standalone, build-independent entry point (no CLI tool, no IDE
  format-on-save, no pre-commit-without-Gradle) — every real usage is Gradle-mediated, so `libs/
  wrasse-format` (planned) never needs its own parser (see `doc/design.md` §12, D16).

### Module layout

```
libs/
  wrasse-model/            WNode/WContext, WNodeType, WRule hierarchy, StreamDispatch, WConfig — no kotlinc dep
  wrasse-rules/             rule implementations — depends only on wrasse-model (+ wrasse-lang for WEdit)
  wrasse-kotlinc-adapter/   LightTreeStreamAdapter, WNodeTypeMapping — the only place kotlinc LightTree
                            types are visible outside app/*/internal/
  wrasse-lang/              zero-dep utilities: JSONC config reader, FileWalkUp, WEdit/patch read-write-apply
  wrasse-format/            (Phase C) Doc IR + DocBuilder + Layout — the opinionated printer,
                            Gradle-mediated only — no standalone parser (see doc/design.md §12, D16)
app/
  wrasse-kotlinc-plugin/    WrassePlugin (dispatch entry point), wrasseMain(); internal/ = pure kotlinc
                            glue (FIR checkers, registrars, CommandLineProcessor) — zero wrasse rule logic
  wrasse-kotlinc-internal-k20 / -k22   version-specific FIR registrar shells, selected at runtime
  wrasse-gradle-plugin/     the Gradle plugin consumers apply (id `com.varlanv.wrasse`, artifact
                            `wrasse-gradle-plugin`): ONE Kotlin file, per-project only (parallel, configuration
                            cache and isolated projects safe), reaches KGP's compile tasks reflectively; adds
                            wrasseLint / wrasseFormat / wrasseApply and replays `patch/wrasse-report.txt`.
                            Its own wrasse `-P` args are added once the project is evaluated — immediately when
                            it already is, so a consumer that applies Kotlin or wrasse from its own
                            `afterEvaluate` is still wired — and not eagerly, so a consumer's later
                            `compilerOptions.freeCompilerArgs.set(...)` (common with `-Xcontext-parameters` etc.)
                            is registered first and wrasse's own append still wins at task realization instead of
                            being overwritten; the append keeps a value KGP only conventions onto the task.
                            Every `compile*Kotlin*` task owns a `build/wrasse/<compilation>` directory —
                            `main`/`test` for `compileKotlin`/`compileTestKotlin`, otherwise the task name minus
                            its `compile` prefix (`kotlinJvm`, `debugKotlinAndroid`) — so multiplatform and
                            Android compilations are wired like any other; a kapt stub task, which inherits the
                            real compile's arguments, is detected by class name and switched off with an
                            `enabled=false` of its own (the option is repeatable, last occurrence wins) so it
                            neither eats the compile's request nor writes into its declared output. It passes
                            the project's `layout.buildDirectory` as a compiler-plugin `excludedRoot` so
                            generated sources (KSP/kapt output under `build/`) are never linted or rewritten,
                            and its `projectDir` so the report resolves stored paths and `wrasseApply` can
                            replay what it could not fix. `wrasse.json` and every config it `extends` are
                            declared compile inputs; a missing config or a comma in the project or build path
                            fails at configuration time; `wrasseApply` depends on the compiles (a failed compile
                            never rewrites sources) and `wrasseFormat` on `wrasseApply`; a build service deletes
                            every request file when the build ends, whatever its outcome; and `wrasseLint`
                            replays only the entries of files its compile tasks still list as sources.
                            `excludedRoot` is a plain (never real-pathed) absolute path, matched against both
                            the plain and the real path of each file, so a project reached through a symlink
                            still excludes generated sources; `wrasseApply` (and so `wrasseFormat`) fails with
                            the same `wrasse found N error-level violation(s)` message as `wrasseLint` when a
                            replayed line is error-level, instead of a green build with unfixed errors left.
testing/
  common-test/                          BaseSpec (kotest ShouldSpec base), useTempDir
  wrasse-realworld-bench/               generator for synthetic 5k/50k/1M-LOC Gradle projects + bench.sh
                                        runner comparing wrasse with ktlint/ktfmt/detekt (doc/benchmarks/)
  wrasse-test-harness/                  FixtureLoader/Parser, WrasseTestHarness, and the fixtures/ resources
  wrasse-kotlinc-plugin-tests-base/     WrasseFixtureSpec — iterates all fixtures, one `should` per fixture
  wrasse-kotlinc-plugin-tests-2-{1,2,3,4}-x/   thin subclasses that run the base spec against each Kotlin minor
```

`wrasse-model` and `wrasse-rules` have **zero dependency on kotlinc** — rules are unit-testable
without a compiler and portable to other hosts (e.g. a hypothetical PSI adapter for an IDE plugin).
Rule code must never import kotlinc/LightTree types directly; those are confined to
`wrasse-kotlinc-adapter` and `app/*/internal/`.

### Rule model — SAX-style, single traversal

Rules are two-phase: a `WUninitializedRule` declares an `id` and produces a configured `WRule` via
`initRule(config)`. The sealed `WRule` hierarchy (`libs/wrasse-model/.../WRule.kt`) has four leaf
kinds, dispatched by `StreamDispatch` off one SAX-style walk (`LightTreeStreamAdapter.walk`) — not a
built tree of node objects:

- **`WLeafRule`** — fires on leaf tokens whose type is in `targetTypes`, ordinal-indexed array
  dispatch (O(1)). The common case (~most rules): comment-spacing, naming, nullable-type-spacing.
- **`WNodeRule`** — enter/exit on interior nodes by `targetTypes`; `enterNode` returning `true` opts
  into staying active until the matching `exitNode`; a node rule that also implements `ChildLeafHandler`
  receives every descendant leaf of an entered node, any other node rule is never called per leaf.
  `WBufferedNodeRule` extends it to auto-buffer direct children into a `ChildBuffer` for exit-time
  inspection (wrapping rules, argument lists).
- **`WStreamRule`** — receives every leaf event unfiltered, plus enter/exit node boundaries. Most
  expensive kind, kept to a small count; used for cross-cutting concerns (spacing, indentation,
  `no-semicolons`' deferred forward-lookup for the statement-separator case).
- **`WFileRule`** — called once after the walk with the final `WContext` (trailing-newline, max-line-
  length via offset tracking).

Rules report through `WReporter.report(ruleId, message, startOffset, endOffset, rule, edits)`; the
reporter reads `rule.config.effectiveLevel` to pick error vs. warning. Rules that can autofix attach
`WEdit(start, end, replacement)`s to the report.

### Fix pipeline (offset-patch, D22 merge-on-write) and diagnostics report

Next to the journal, every compile records each diagnostic it reported (configured level, line,
column, offset, whether it carried an autofix, message) in
`build/wrasse/<compilation>/patch/wrasse-report.txt` (`WReportStore`, same journal-and-compaction
scheme, hash-guarded per file, format `# wrasse-report v2`; an unrecognized or missing header reads
as an empty report). `replayReports` in `wrasse-lang` renders the still-current entries exactly as
kotlinc prints a diagnostic, which is how the Gradle plugin's `wrasseLint` shows the same findings
whether the compile ran, was UP-TO-DATE or came from the cache; when `wrasseApply` just rewrote a
file, `replayReports`/`WReportReplay.collect` remap that file's still-non-fixable entries onto the
new content (`WReportReplay.remap`: offset shift outside an edit's span, a line-by-line diff of that
edit's original and replacement text for any span longer than one line, and drop only for a
single-line span or a line genuinely gone with nothing new in its place) and persist
them back under the new hash instead of dropping them for looking stale, which is what makes a
file's non-fixable findings show up on the very same `wrasseFormat` run that fixed its other
findings, not only the next one. `isCurrent`/replay also fall back to an LF-normalized hash for a
CRLF checkout, resolve a stored path against the caller-supplied `projectDir` (compiler-plugin
option, mirroring `excludedRoot`) so a relocated build-cache hit still finds its files, and skip
(rather than fail) a single entry on an I/O error or invalid path.

`WrassePlugin.checkFile` collects `WEdit`s from the walk and, whenever `fixOutputDir` is set (i.e.
whenever the plugin is active under `-PwrasseCheck` — there is no separate fix flag; D22), merges
them into that compilation's own patch under `build/wrasse/<compilation>/patch/wrasse-fixes.txt`
(`FileEdits` = file + SHA-256 source hash + edits). On the first `checkFile` of a compilation the
existing patch is loaded into memory; each subsequent `checkFile` upserts or removes (on zero edits,
self-cleaning) that file's entry and atomically rewrites the whole patch from the in-memory map
(temp file + rename) — so files an incremental compile didn't touch keep their prior entry, and lint
and fix compiles have identical compiler args and never invalidate each other. Applying
(`wrasseApply` task → `WPatchApplierKt`) is a separate, explicit step that walks `build/wrasse/`
recursively for every `wrasse-fixes.txt`, hash-guarded (a stale patch whose recorded hash no longer
matches the file is a no-op) — never auto-run during a normal build. `./gradlew wrasseFix` wires
this together: the same check compile as `wrasseLint`, then `wrasseApply`.

### Config

`wrasse.json`/`wrasse.jsonc` is discovered by walking up from the source root, parsed by the
hand-rolled JSONC reader in `wrasse-lang`. Per-rule `"level": "off" | "warn" | "error"`; a config can
`"extends"` a base (child overrides base; a rule absent from the effective config is off, not an
error). `wrasse-schema.json` is the editor-autocomplete schema referenced by `$schema` in
`wrasse.json`. This repo lints itself (`wrasseLint`/`wrasseFix`) using its own `wrasse.json`.

### Kotlin version matrix

The plugin ships as a single JAR compiled against the latest supported Kotlin (2.4, see
`gradle/libs.versions.toml`), targeting JVM 8 bytecode so it loads into the kotlinc daemon
classloader like any bundled compiler plugin. Cross-version FIR API differences are absorbed by
runtime-selected registrar shells (`app/wrasse-kotlinc-internal-k20`, `-k22`) plus the adapter layer
— rule code and `wrasse-model` never see version-specific kotlinc APIs directly. The same fixture set
is replayed against each supported minor via the `testing/wrasse-kotlinc-plugin-tests-<minor>-x`
modules and their `testMinor`/`testPatch_*` tasks.

### Build conventions

All modules apply the local `internal-convention-plugin` (an included build, not published), which
centralizes: Kotlin/Java toolchain + target version wiring (from `gradle/libs.versions.toml`),
`allWarningsAsErrors`/`progressiveMode` on non-test source sets, JUnit Platform test execution, the
`wrasseApply` task registration, and wiring the `wrasseCheck` Gradle property into
`kotlinCompilerPluginClasspath` plus a distinct `fixOutputDir` compiler free-arg per compile task
(`build/wrasse/main` for `compileKotlin`, `build/wrasse/test` for `compileTestKotlin`, pattern-matched
off the `compile(.*)Kotlin` task name so future source sets get their own patch directory for free),
and declaring `<fixOutputDir>/patch` as an output of that compile task (`outputs.dir`, never
`outputs.file` — the Kotlin Gradle plugin recreates declared outputs as directories) so a build-cache
hit restores the journal too; the request and perf files stay in `fixOutputDir` itself, outside the
declared output.
Don't duplicate this logic in a module's own `build.gradle.kts` — extend the convention plugin
instead.
