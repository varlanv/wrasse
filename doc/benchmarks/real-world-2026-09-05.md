# Real-world Gradle benchmark: wrasse vs ktlint, ktfmt, detekt (2026-09-05)

Reproduce with `testing/wrasse-realworld-bench` (generator, `bench.sh`, `report.py`, see the bottom).

## Setup

- Machine: 32 cores, 93 GB RAM, Linux, JDK 21 (Temurin) for the Gradle daemon and the Kotlin
  toolchain, Gradle 9.6.1 (the repo's wrapper), Kotlin 2.4.10.
- One single-module Gradle project per size, generated from a seed: application-shaped code
  (data/enum/sealed classes, services calling each other across packages, suspend functions,
  collection pipelines, string templates). 60% of files carry seeded violations: 2-space
  indentation, missing trailing commas, unused/wildcard/unsorted imports, semicolons, long lines,
  inline `if`, blank-line runs, missing final newline, unnecessary qualified names. 50k also has
  pathological files (a 15k-line object with 3000 functions, a 5000-statement function, 25-deep
  nesting, a 2000-char line, a 500-line raw string).
- Project setup is what a real project has: local build cache on, `kotlin.incremental=true`,
  in-process compiler, 8 GB daemon heap.
- Tools, latest at the time, all with their defaults:
  - wrasse `0.0.1-SNAPSHOT` (this repo at the report's commit), formatter on, every shipped rule on
    except `undocumented-public-*` and `forbidden-expression-body-functions`; that includes the
    FIR-backed rules (`no-unused-imports`, `no-unnecessary-fqn`, `named-arguments`,
    `forbidden-calls`), run as `compileKotlin -PwrasseCheck` (+ `wrasseApply` to format).
  - ktlint 1.8.0 via ktlint-gradle 14.2.0, `ktlint_official` style, main source set tasks only.
  - ktfmt 0.64 via ktfmt-gradle 0.27.0, `kotlinLangStyle()`, main source set tasks only.
  - detekt 1.23.8, default config, `detektMain` (type resolution), as agreed.
- `compile` column = plain `compileKotlin` with no tool, the baseline everything else adds to.

## Method

Own bash runner rather than gradle-profiler: the interesting scenarios are multi-step pipelines
(format, then compile; break one file, format, compile), each step timed separately, with the
daemon tree's RSS sampled throughout and tool findings counted from each run's output.
gradle-profiler times one invocation per scenario and would need one scenario file per step with
manual state handoff. Timing is wall-clock around `./gradlew <task> --console=plain`, so it
includes Gradle client startup and, for cold rows, daemon startup and configuration; that cost is
identical for every column.

Scenarios (per tool, per iteration):

- check session: stop daemon, delete `build/`, `.gradle/`, `.kotlin/`, `.build-cache/`, restore
  pristine sources; then **cold check**, **warm no-op check**, `rm -rf build` + **cached check**.
- format session: same reset; then **cold format**, **compile right after format**, **check right
  after format** (does the tool consider its own output clean?), then break one file (append an
  unformatted function to `File0.kt`), **incremental format**, **compile**, **check**.
- peak RSS = max over 250 ms samples of the summed RSS of the Gradle daemon and its descendants.

Findings counts are not comparable across tools (each counts what its rule set reports; ktfmt
counts files), they are there to show every tool had real work and to check convergence.

## 5k LOC (31 files, 3 iterations, medians)

| scenario | compile | wrasse | ktlint | ktfmt | detekt |
|---|---|---|---|---|---|
| cold check (fresh daemon, empty caches) | 10.8 s | 11.3 s | 5.1 s | 9.6 s | 15.8 s |
| warm no-op check | 0.6 s | 0.5 s | 0.5 s | 6.6 s | 0.6 s |
| check after `rm -rf build` (build-cache hit) | 0.5 s | 0.5 s | 0.5 s | 6.5 s | 0.5 s |
| cold format | - | 11.3 s + 0.6 s apply | 6.4 s | 9.4 s | - |
| compile right after format | - | 5.2 s | 7.9 s | 8.0 s | - |
| check right after format | - | 0.7 s | 2.3 s | 6.3 s | - |
| format after one broken file | - | 1.2 s + 0.5 s apply | 2.1 s | 6.1 s | - |
| compile after that format | - | 1.0 s | 1.2 s | 1.3 s | - |
| check after that change | 1.3 s | 0.4 s | 2.0 s | 6.0 s | 6.6 s |

| peak RSS | compile | wrasse | ktlint | ktfmt | detekt |
|---|---|---|---|---|---|
| check session | 1.2 GB | 1.2 GB | 0.9 GB | 5.4 GB | 1.3 GB |
| format session | 1.3 GB | 1.4 GB | 1.7 GB | 6.7 GB | 1.5 GB |

| correctness | wrasse | ktlint | ktfmt | detekt |
|---|---|---|---|---|
| findings on cold check | 487 | 2032 | 31 files | 138 |
| findings after own format | 33, all lint-only (`swallowed-exception`, 2 wildcard imports without autofix) | 8 | 0 | - |
| internal errors | 0 | - | - | - |

## 50k LOC (298 files incl. pathological ones, 1 iteration)

| scenario | compile | wrasse | ktlint | ktfmt | detekt |
|---|---|---|---|---|---|
| cold check (fresh daemon, empty caches) | 26.3 s | 26.0 s | 10.4 s | 14.3 s | 44.7 s |
| warm no-op check | 0.6 s | 0.6 s | 0.5 s | 11.2 s | 0.5 s |
| check after `rm -rf build` (build-cache hit) | 0.8 s | 0.8 s | 0.5 s | 11.3 s | 0.9 s |
| cold format | - | 25.5 s + 0.8 s apply | 21.2 s | 14.2 s | - |
| compile right after format | - | 17.3 s | 20.9 s | 21.1 s | - |
| check right after format | - | 0.6 s | 6.7 s | 10.6 s | - |
| format after one broken file | - | 1.5 s + 0.5 s apply | 6.2 s | 10.6 s | - |
| compile after that format | - | 1.3 s | 1.6 s | 1.6 s | - |
| check after that change | 1.4 s | 0.4 s | 6.3 s | 10.4 s | 23.3 s |

| peak RSS | compile | wrasse | ktlint | ktfmt | detekt |
|---|---|---|---|---|---|
| check session | 3.2 GB | 2.6 GB | 1.1 GB | 8.0 GB | 3.8 GB |
| format session | 2.5 GB | 4.2 GB | 3.2 GB | 10.3 GB | 3.9 GB |

| correctness | wrasse | ktlint | ktfmt | detekt |
|---|---|---|---|---|
| findings on cold check | 7110 | 18842 | 296 files | 3527 |
| findings after own format | 297, all lint-only (293 `swallowed-exception`, `file-size`, `large-class`, `too-many-functions`, `trim-multiline-raw-string`) | 36 | 0 | - |
| internal errors | 0 | - | - | - |
| patch journal after the format session | 6 KB (compacted, all tombstones) | - | - | - |

Pathological files: the 15k-line object, the 5000-statement function, the 25-deep nesting, the
2000-char line and the 500-line raw string were all formatted and re-checked clean; no file
was skipped and no internal error was reported.

## Reading the numbers

- **Check mode is free once you compile.** Cold, wrasse adds 0.5 s at 5k and nothing measurable
  at 50k on top of the compile it rides; warm and cache-hit checks are the compile's own
  up-to-date check. ktlint's standalone check is cheaper than a compile (5 s / 10 s) but a
  project compiles anyway, so the real comparison is compile + ktlint vs compile with wrasse.
- **Cold format is where wrasse pays.** A wrasse format is a full compile (with the plugin) plus a
  second compile of every rewritten file: 5k: 11.9 s + 5.2 s; 50k: 26.3 s + 17.3 s. ktfmt formats
  50k in 14 s without compiling, but then the first compile costs 21 s. Totals, format then
  compile: 5k wrasse 17 s / ktfmt 17 s / ktlint 14 s; 50k wrasse 44 s / ktfmt 35 s / ktlint 42 s.
- **Incremental is where wrasse wins**, and the gap grows with size. Break one file, format,
  compile: 5k wrasse 2.7 s / ktlint 3.3 s / ktfmt 7.4 s; 50k wrasse 3.3 s / ktlint 7.8 s /
  ktfmt 12.2 s. Wrasse only recompiles the changed file; ktlint and ktfmt re-run over the source
  set. The `wrasseApply` step is a flat 0.5 s (a JVM launch), and the compile after it is the
  ordinary incremental compile.
- **ktfmt-gradle's check task never goes up-to-date** (6.5 s / 11 s on a no-op) and its
  parallel workers peak at 5–10 GB RSS. That is the plugin's default behavior, not a
  configuration choice here.
- **detekt with type resolution** compiles first (its cold check is compile + 5 s at 5k,
  compile + 18 s at 50k) and re-runs fully on any change (6.6 s / 23 s).
- **Memory.** Wrasse's check session peaks at the compiler's own footprint. The format session
  peaks higher (4.2 GB vs 2.5 GB at 50k); that session is three compiles in one daemon plus the
  whole-file replacement edits held in memory, worth profiling before the 1M run.
- **Convergence.** Wrasse's formatter output was clean on the first re-check at both sizes; the
  remaining warnings are rules without an autofix. One exception seen at 5k: on the incremental
  step the broken file still reported `function-expression-body` after the fix pass, because that
  rule's edit overlapped the whole-file format edit and lost; a second `wrasseFix` clears it.
- **Build cache gotcha.** A `compileKotlin` restored from the build cache does not run the
  plugin, so `wrasseApply` after a cache hit has nothing to apply. Formatting a project that
  cache-hits needs a source change or `--rerun-tasks`. ktlint and ktfmt cache their own tasks the
  same way, but their failed checks are never cached.

## Limit test status

The 1M-LOC project generates in under two seconds (6110 files, 49 MB, with 3000-function /
5000-statement / 40-deep / 6000-char / 3000-line stress files) but was not run in this pass:
a single tool session on 1M is a multi-minute cold compile, and the full matrix was estimated at
over an hour. Run it with `./bench.sh 1m 1` (or `./bench.sh 1m 1 compile,wrasse` for the limit
test alone).

## Reproducing

```
./gradlew :testing:wrasse-realworld-bench:generateBenchProjects -PbenchSizes=5k,50k,1m
cd testing/wrasse-realworld-bench
./bench.sh 5k 3            # <size> [iterations] [tools-csv]
./bench.sh 50k 1
python3 report.py 5k 50k   # markdown tables from bench-projects/results/<size>.csv
```

Generated projects live in `testing/wrasse-realworld-bench/bench-projects/` (gitignored). The
wrasse plugin is taken from mavenLocal, so run `./gradlew publishToMavenLocal` after changing it.
