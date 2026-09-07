# Real-world OSS benchmark: wrasse vs the projects' own linters (2026-09-07)

Two open-source projects, each measured against the lint/format tool it already ships with, plus
wrasse wired in. Reproduce with the runner described at the bottom (`oss-trial/perf/run.sh`).

## Setup

- Machine: 32 cores, 93 GB RAM, Linux, Gradle daemon on Temurin 21.0.7 for every column; the
  Kotlin toolchain is whatever each project's build resolves from the same fixed JDK list
  (Temurin 21 for both). Two unrelated Gradle daemons (~9 GB RSS, other projects) were running on
  the machine throughout and were never sampled or stopped.
- wrasse `0.0.1-SNAPSHOT` from `~/.m2` (repo commit `d67876a`, jars published 09:01 the same day),
  applied through the Gradle plugin `com.varlanv.wrasse` (`wrasseLint` / `wrasseFormat`), with the
  same `wrasse.json` for both projects: `format.enabled=true` plus 16 rules
  (`no-semicolons`, `named-arguments`, `no-wildcard-imports`, `trailing-newline`,
  `no-empty-class-body`, `no-unit-return`, `unnecessary-backticks`,
  `redundant-constructor-keyword`, `no-empty-parens-before-trailing-lambda`, `modifier-order`,
  `if-else-bracing`, `import-ordering`, `no-unused-imports` at `error`; `empty-catch-block`,
  `empty-function-block`, `cyclomatic-complexity` at `warn`).
- **JetBrains/Exposed** at `4be9aee` — 139,147 lines in 802 `.kt` files, 24 Kotlin modules,
  Kotlin 2.3.20, Gradle 8.14.4, `org.gradle.caching=true`, `org.gradle.configuration.cache=true`,
  `org.gradle.parallel=false`, default daemon heap (`org.gradle.jvmargs` only sets the encoding).
  Its tool: **detekt 1.23.8** with the `detekt-formatting` ruleset, `detekt/detekt-config.yml`,
  `buildUponDefaultConfig = true`, `parallel = true`. The task the project wires into `check` is
  the plain **`detekt`** (no type resolution), one per module (25 tasks; verified with
  `./gradlew check --dry-run`). `detektMain` (type resolution) exists but is not part of `check`;
  it was measured once as an extra column. detekt has no format task here (`autoCorrect` is not
  enabled on the extension), so detekt has no format rows.
- **square/moshi** at `889013e` — 19,056 lines in 86 `.kt` files, 8 Kotlin modules,
  Kotlin 2.3.21, Gradle 9.5.1, `org.gradle.jvmargs=-Xmx2048m`, no build cache configured by the
  project. Its tool: **spotless 8.7.0 with ktfmt 0.63, `googleStyle()`**, applied in the root
  project over `**/*.kt`. Spotless also formats Java (google-java-format), `*.gradle.kts` (ktfmt)
  and misc files; the `spotless` column uses the Kotlin-only tasks **`spotlessKotlinCheck` /
  `spotlessKotlinApply`**, the extra `spotlessAll` column uses the aggregate `spotlessCheck` /
  `spotlessApply`.
- `compile` column = the project's own compile with no tool: `compileKotlin compileTestKotlin`
  across all modules (81 actionable tasks on Exposed, 30 on moshi).
- Configuration per column: the wrasse wiring (plugin applied to every Kotlin module +
  `mavenLocal()` repositories, saved as `oss-trial/logs/wiring/<project>.diff`) is present **only**
  for the `wrasse` column. The `compile`, `detekt`, `spotless` columns ran on the pristine build
  files, so they never pay the wrasse compiler plugin's cost. With the Gradle plugin applied every
  Kotlin compile carries the compiler plugin, so in the wrasse column "compile" means "compile
  with wrasse" (that is the configuration a wrasse user has), and `wrasseLint` = those compiles +
  a report replay per module.
- Every run passed `--build-cache` and an init script pointing the local build cache at
  `<project>/.build-cache` (deleted on every cold reset), so all three columns of a project see the
  same cache setup.

## Method

Same approach as `real-world-2026-09-05.md` (own bash runner, wall-clock around
`./gradlew <tasks> --console=plain --no-scan --continue`, so client and daemon startup and
configuration are included in cold rows and identical for every column; peak RSS = max over
250 ms samples of the summed RSS of the benchmark's own Gradle daemon and all its descendants,
which includes the Kotlin compile daemon and any worker JVMs).

Per tool and iteration:

- check session: `./gradlew --stop`, kill leftover daemons of the trial, delete every `build/`,
  `.gradle/`, `.kotlin/`, `.build-cache/`, `git checkout -- .`, re-apply the wiring for the
  wrasse column only; then **cold check**, **warm no-op check**, `rm -rf` of all build dirs +
  **cached check**. For tools without a format task the session continues with the one-file break
  and **check after that change**.
- format session (wrasse, spotless): same reset; **cold format**, **compile right after format**,
  **check right after format**, **second format pass** (does the tool change its own output?),
  break one file (append an unformatted private function with a semicolon and bad spacing to
  `exposed-core/.../core/Alias.kt` / `moshi/.../JsonReader.kt`), **format after one broken
  file**, **compile after that format**, **check after that change**.
- 3 iterations, medians; `detektMain` and `spotlessAll` 1 iteration.
- Findings are counted from each run's console output (wrasse `e:`/`w:` lines, detekt
  `file:line:col: message [Rule]` lines, spotless "files had format violations" lists). Files
  changed by a format pass = `git status` modified files minus the two wiring files, cross-checked
  against wrasse's `Fixed:` lines; the second-pass comparison is a `git write-tree` diff before and
  after (during the moshi run that diff still included the untracked `.build-cache/`, the
  recorded values were recomputed from the surviving tree objects excluding it: 0 in every case).

Both projects keep their own tool green in CI, so **the project's own tool reports 0 findings on
its own sources**; its real work shows in the cold-run time and in the broken-file rows.

## Exposed (139k LOC, 24 modules)

| scenario | compile | wrasse | detekt | detektMain (1 iter.) |
|---|---|---|---|---|
| cold check (fresh daemon, empty caches) | 40.7 s | 44.1 s | 23.2 s | 49.2 s |
| warm no-op check | 1.7 s | 2.4 s | 1.5 s | 12.0 s |
| check after `rm -rf` of all build dirs (build-cache hit) | 2.6 s | 3.3 s | 1.3 s | 11.4 s |
| cold format | - | 44.2 s | - | - |
| compile right after format | - | 18.4 s | - | - |
| check right after format | - | 1.9 s | - | - |
| second format pass (no source change) | - | 1.7 s | - | - |
| format after one broken file | - | 2.9 s | - | - |
| compile after that format | - | 4.6 s | - | - |
| check after that change | 2.7 s | 1.5 s | 2.4 s | 12.5 s |

Per-iteration cold checks: compile 51.0 / 40.7 / 40.6, wrasse 47.3 / 44.1 / 43.1,
detekt 33.2 / 22.4 / 23.2 (the first iteration of each column is the first build after a long
idle and is slower for every tool). "check after that change" is a real incremental compile for
`compile` (2.7 s, `:exposed-core:compileKotlin` only), a real detekt re-run of `exposed-core` for
`detekt`, and a no-op replay for wrasse because the preceding "compile after that format" step
already compiled the change.

| peak RSS | compile | wrasse | detekt | detektMain |
|---|---|---|---|---|
| check session | 9.8 GB | 10.1 GB | 7.4 GB | 10.7 GB |
| format session | - | 13.2 GB | - | - |

| findings | compile | wrasse | detekt | detektMain |
|---|---|---|---|---|
| cold check | 0 | 3972 (1933 `named-arguments`, 725 `if-else-bracing`, 584 `format`, 398 `import-ordering`, 307 `no-wildcard-imports`, 25 `cyclomatic-complexity` warnings) | 0 | 274 (100 `UnreachableCode`, 83 `UnsafeCallOnNullableType`, 29 `UnnecessaryAbstractClass`, ...) |
| after own format | - | 82: 57 `no-wildcard-imports` "no autofix for this shape" (errors, so `wrasseFormat`/`wrasseLint` exit 1) + 25 `cyclomatic-complexity` warnings | - | - |
| after the broken file, post-format | - | 82 (same set; the broken function was fixed) | 14 on `Alias.kt` (13 formatting rules + `UnusedPrivateMember`) | 288 |
| files changed by cold format | - | 584 | - | - |
| files changed by second format pass | - | 0 | - | - |
| internal errors | - | 0 | - | - |

Cacheability: `compileKotlin` tasks (both columns; 44 FROM-CACHE in the wrasse column too, and
`wrasseLint` replayed all 3972 findings from the restored `patch/wrasse-report.txt`), `detekt`
(26 of 29 tasks FROM-CACHE) are cacheable. `detektMain` fails in 14 modules and a failed detekt
task is never up-to-date or cached, so it reruns those modules every time (11–12 s). Build
exits: `detekt` and `wrasse` exit 1 whenever error-level findings remain, as they should.

## moshi (19k LOC, 8 modules)

| scenario | compile | wrasse | spotless (Kotlin only) | spotlessAll (1 iter.) |
|---|---|---|---|---|
| cold check (fresh daemon, empty caches) | 22.0 s | 22.8 s | 6.4 s | 7.6 s |
| warm no-op check | 1.0 s | 1.2 s | 0.8 s | 0.7 s |
| check after `rm -rf` of all build dirs (build-cache hit) | 1.0 s | 1.2 s | 0.7 s | 0.7 s |
| cold format | - | 23.6 s | 6.6 s | 7.1 s |
| compile right after format | - | 7.2 s | 17.9 s | 16.4 s |
| check right after format | - | 0.9 s | 0.8 s | 0.9 s |
| second format pass (no source change) | - | 0.9 s | 0.7 s | 0.8 s |
| format after one broken file | - | 5.1 s | 0.9 s | 1.0 s |
| compile after that format | - | 5.1 s | 5.2 s | 4.8 s |
| check after that change | 4.8 s | 0.8 s | 0.9 s | 0.8 s |

Per-iteration cold checks: compile 25.8 / 22.0 / 21.2, wrasse 35.2 / 22.8 / 21.6,
spotless 7.0 / 6.4 / 6.3. Adding one private function to `JsonReader.kt` makes moshi's build
recompile all 11 Kotlin compilations (same for every column, 4.8–5.2 s), which is why wrasse's
"format after one broken file" (a compile with the plugin) costs 5.1 s here versus 2.9 s on the
much larger Exposed.

| peak RSS | compile | wrasse | spotless | spotlessAll |
|---|---|---|---|---|
| check session | 3.3 GB | 2.9 GB | 0.7 GB | 0.7 GB |
| format session | - | 4.0 GB | 3.2 GB | 3.3 GB |

| findings | compile | wrasse | spotless | spotlessAll |
|---|---|---|---|---|
| cold check | 0 | 419 (181 `if-else-bracing`, 135 `named-arguments`, 84 `format`, 1 `no-unused-imports`, 18 `cyclomatic-complexity` warnings) | 0 | 0 |
| after own format | - | 18 (all `cyclomatic-complexity` warnings, lint-only; exit 0) | 0 | 0 |
| after the broken file, post-format | - | 18 | 0 | 0 |
| files changed by cold format | - | 84 | 0 | 0 |
| files changed by second format pass | - | 0 | 0 | 0 |
| internal errors | - | 0 | - | - |

Cacheability: the `spotlessKotlin` worker task is cacheable (FROM-CACHE on the cached row,
`spotlessKotlinCheck` UP-TO-DATE); the format-session peak RSS of the spotless column is the
Kotlin compile that follows, not spotless itself (0.7 GB alone).

## Reading the numbers

- **Check cost on top of the compile.** Exposed: wrasse's cold check is 44.1 s against a 40.7 s
  plain compile (+3.4 s, ~8%); moshi: 22.8 s vs 22.0 s (+0.8 s). Warm and cache-hit checks add
  0.2–0.7 s for the per-module `wrasseLintRequest`/`wrasseLint` tasks, which always execute to
  replay the report (Exposed: 46 such tasks; 3972 lines printed).
- **The project's own tool is cheaper than a compile on its own, but it does not replace one.**
  Plain `detekt` on Exposed is 23 s cold (no compile), spotless on moshi 6.4 s; the project
  compiles anyway, so the comparable totals for a fresh checkout are compile + tool: Exposed
  40.7 + 23.2 = 64 s vs 44.1 s with wrasse; moshi 22.0 + 6.4 = 28 s vs 22.8 s. Warm, both the
  tools and wrasse are within a second of a no-op build.
- **detekt with type resolution** (`detektMain`, not wired into Exposed's `check`) compiles
  first (49 s cold = compile + ~9 s), and because it fails in 14 modules it reruns them on every
  invocation (11–12 s warm, on a cache hit, and after a one-line change).
- **Cold format.** wrasse's `wrasseFormat` is the check compile plus the apply step in one
  invocation (Exposed 44 s, moshi 24 s), followed by a recompile of the rewritten files
  (Exposed 18 s for 584 files, moshi 7 s for 84). spotless formats moshi in 6.6 s but changes
  nothing (the sources are already ktfmt-formatted), and the first compile afterwards is a cold
  one (17.9 s). The format-then-compile totals on moshi: wrasse 31 s, spotless 25 s.
- **One broken file.** Exposed: wrasse formats it in 2.9 s and recompiles in 4.6 s (7.5 s total);
  detekt reports it in 2.4 s after a 2.7 s incremental compile (5.1 s, no fix). moshi: wrasse
  5.1 + 5.1 = 10.2 s; spotless 0.9 + 5.2 = 6.1 s. The difference is the compile the format
  rides: spotless rewrites one file in-process, wrasse needs the incremental compile (which on
  moshi recompiles every module) and then the ordinary compile of the rewritten file.
- **Convergence.** On both projects the second `wrasseFormat` pass changed 0 files and 0 files
  were fixed on the first re-check; all compiles of wrasse-formatted sources succeeded (Exposed
  `compileKotlin compileTestKotlin` BUILD SUCCESSFUL in 18 s, moshi in 7 s). What remains after
  the format is lint-only: 25 / 18 `cyclomatic-complexity` warnings and, on Exposed, 57 wildcard
  imports wrasse cannot expand ("no autofix for this shape"), which keep `wrasseFormat` and
  `wrasseLint` at exit 1 there. No internal errors in any run.
- **Memory.** Exposed's plain compile already peaks at 9.8 GB (Gradle daemon + Kotlin daemon,
  no heap limits set by the project); wrasse's check session is the same footprint (10.1 GB) and
  the format session, three compiles in one daemon, 13.2 GB. moshi: 3.3 GB compile, 2.9 GB wrasse
  check, 4.0 GB wrasse format. detekt alone peaks at 7.4 GB on Exposed (in-process, 25 modules,
  `parallel = true`); spotless alone at 0.7 GB on moshi.
- **No run was killed for memory**; no two Gradle builds ever ran concurrently.

## Not measured, and why

- A detekt format row: Exposed does not enable `autoCorrect`, so its detekt setup has no format
  step; adding one would not be "the tool the project uses".
- detekt findings on a green project: Exposed's `detekt` config is tuned to pass, so the findings
  count is 0 by construction; the 14 issues on the broken file show the run was real.
  Same for spotless on moshi (0 violations, 0 files changed).
- `detektMain` and `spotlessCheck` (all formats) were run once, not three times, to keep the
  total under budget (whole run: 10:29–10:52, 23 minutes).
- Compile times in the wrasse column are compiles with the plugin attached; there is no way to
  run a plugin-free compile in that column without changing the wiring, which is why the
  `compile` column exists.

## Reproducing

```
cd oss-trial/perf          # next to the two clones (Exposed/, moshi/) and logs/wiring/*.diff
./run.sh                   # moshi ×3, Exposed ×3, Exposed detektMain ×1, moshi spotlessAll ×1
./bench.sh Exposed 3 compile,wrasse,detekt     # one project / tool subset
python3 report.py moshi Exposed                # markdown tables from results/<project>.csv
```

`env.sh` fixes `JAVA_HOME` (Temurin 21) and the toolchain search path; `init.gradle.kts` points
the local build cache at `<project>/.build-cache`. Per-step Gradle logs are in
`perf/logs/<project>/<tool>-<scenario>-<iter>.log`. The wrasse plugin is taken from mavenLocal;
republish with `./gradlew publishToMavenLocal` in the wrasse repo after changing it.
