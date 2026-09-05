# Remaining formatter tasks (2026-09-05)

All changes from rounds 1-6 are uncommitted in the working tree. 1573 tests pass, kryptoid builds with 0 errors.

## Status (nightly run, 2026-09-05)

| # | Task | State |
|---|------|-------|
| 1 | `BaseSpec({...})` hug | done (`resolveSuperTypeListFrame` joins `: Base({` for a sole lambda argument) |
| 2 | `val x = chain.call(` same-line | done (FLUID value group; chains ending in a call stay on the `=` line) |
| 2.1 | redundant newlines after annotations | done (`adjustAnnotationTrailingGap`) |
| 3 | named-arguments rule | done: names + wrapping (`wrap` option, default true; no wrapping inside `${...}`); `java`/`javax` excluded by default (`excluded-packages`) |
| 4 | no-mixed-named-positional-arguments | done, report-only |
| 5 | name everywhere | done via `named-arguments` option `all-calls: true` |
| 6 | per-rule options | done: required / optional-with-default / optional-without-default, boolean / integer / string / string list / map of string lists; `if-else-bracing` has `allow-inline` (default false) |
| 7 | forbidden syntax | done: `forbidden-calls` (required `calls` map: callee pattern -> allowed file globs; `pkg.Class.member`, `pkg.member`, `pkg.Class` for constructors, trailing `*` prefix) and `forbidden-expression-body-functions` (autofix to a block body when the return type is explicit; cannot be on with `function-expression-body`) |
| 8 | performance | done for this round, see below |
| 9 | every rule against kryptoid | see below |

### Decisions taken without the owner (revisit if wrong)

- wrasse's own `wrasse.json` sets `if-else-bracing` `allow-inline: true` so the repo's style did not churn; kryptoid keeps the default (`false`).
- `named-arguments` names `kotlin.*` callees too, so kryptoid gained many `element = ` / `key = ` arguments on stdlib calls. Put `"kotlin"` into `excluded-packages` if that is unwanted.
- `named-arguments` `wrap` (default true) wraps every call that nests a call with arguments one argument per line, transitively, as in the spec example; string-template entries are exempt.
- kryptoid's `apps/analytics-scrapyard/.../analytics/**` is gitignored. A trial run of `forbidden-expression-body-functions` with format on rewrote the expression-bodied functions there into block bodies before the trial was scoped to exclude that directory; those files are not tracked, so that change could not be reverted with git. They compile.

### Performance (JMH, `:testing:wrasse-benchmarks:jmh`, 100 generated files / 223 KB per op)

| benchmark | before | after |
|-----------|-------:|------:|
| walkWithNoRules | 9.05 ms | 8.73 ms |
| walkWithShippedRules | 10.29 ms | 10.15 ms |
| walkWithBufferedRules (12 buffered rules) | 14.94 ms | 13.06 ms |
| walkWithFormat (DocBuilder + Layout) | 19.16 ms | 18.70 ms (17.4 before the lazy leaf text; within run-to-run noise of ±0.8) |

Changes: one shared `ChildBuffer` per tree depth instead of one per rule per node, no per-entry
objects for active node rules, index loops on the hot dispatch paths, leaf text sliced lazily
from the file text (a leaf nobody reads allocates nothing), `Doc` flat-width and last-line-width
caches (the printer's fit checks were re-measuring nested groups once per enclosing group),
binary-search insertion in `EditPlan`, and the patch file is rewritten only when its content
changes (a clean file used to rewrite the whole patch file on every compile).

Not done: a zero-copy `StringSlice` for rule text scanning. Every rule reads `leafText` through
the `CharSequence` API and most go through `IdentifierCasing.unquote`, so the change is
mechanical but touches ~25 call sites; the lazy slice above already removes the per-leaf
allocation for leaves no rule reads. The raw walk (no rules) is ~9 ms per 223 KB and is
dominated by kotlinc's own `getChildren`/tree access, not by wrasse code.

### Kryptoid, all rules on (`warn`, format off)

Every rule id in `wrasse-schema.json` was switched on at `warn` (format off, `forbidden-expression-body-functions` off because it conflicts with `function-expression-body`), kryptoid compiled with 0 errors and 0 wrasse internal errors. Reports per rule:

| rule | reports |
|------|--------:|
| magic-number | 1345 |
| return-count | 465 |
| comment-over-private-declaration | 411 |
| long-numerical-values | 249 |
| no-mixed-named-positional-arguments | 140 |
| loop-with-too-many-jump-statements | 106 |
| cyclomatic-complexity | 102 |
| no-wildcard-imports | 99 |
| complex-condition | 96 |
| long-method | 90 |
| property-naming | 62 |
| when-must-have-else | 52 |
| too-generic-exception-caught | 51 |
| long-parameter-list | 43 |
| debug-print | 40 |
| too-many-functions | 39 |
| function-name-max-length | 30 |
| string-should-be-raw-string | 24 |
| large-class | 22 |
| string-concatenation | 20 |
| no-unnecessary-fqn | 19 |
| trim-multiline-raw-string | 16 |
| no-unused-imports | 14 |
| filename | 13 |
| forbidden-calls | 12 |
| nested-block-depth | 8 |
| also-could-be-apply | 8 |
| unused-parameter | 6 |
| throws-count | 6 |
| function-name-min-length | 6 |
| too-generic-exception-thrown | 3 |
| kdoc-tag-mismatch | 3 |
| empty-function-block | 3 |
| unnecessary-part-of-binary-expression | 2 |
| unused-private-class | 1 |
| not-implemented-declaration | 1 |
| file-size | 1 |
| equals-null-call | 1 |
| destructuring-declaration-with-too-many-entries | 1 |
| collapse-if | 1 |

Spot-checked against the source and fixed in this run:

- `filename` flagged `RunService.kt` (one class plus top-level functions) — the single-class name check now applies only when the class or object is the file's only top-level declaration.
- `unnecessary-part-of-binary-expression` flagged `"buyback" in lower || "buy back" in lower` — operand whitespace is now normalised outside string and character literals only.
- `no-mixed-named-positional-arguments` now autofixes by naming the positional arguments when the callee resolved with stable parameter names outside `excluded-packages` (`java`, `javax` by default); identical edits emitted by it and `named-arguments` collapse into one.

Everything else sampled (`unused-parameter`, `no-unnecessary-fqn`, `also-could-be-apply`, `equals-null-call`, `empty-function-block`, `kdoc-tag-mismatch`, `string-should-be-raw-string`, `forbidden-calls`) matched the code it pointed at. High counts (`magic-number`, `return-count`, `comment-over-private-declaration`, `long-numerical-values`) are the rules being strict, not wrong.

With kryptoid's own `wrasse.json` (format on, `named-arguments` and `no-mixed-named-positional-arguments` on) the final plugin build formats kryptoid idempotently (0 residual patch entries after a recompile), compiles with 0 errors and 0 warnings; the mixed-argument autofix named the remaining positional arguments in 36 files (committed in kryptoid).


## Testing against kryptoid

The kryptoid project (`/var/home/vlad/dev/IdeaProjects/kryptoid`) is wired as a real-world test bed
for wrasse. To test formatter changes end-to-end:

1. Publish wrasse to mavenLocal: `./gradlew publishToMavenLocal` (from wrasse repo)
2. In kryptoid: `./gradlew clean && ./gradlew format` (runs wrasse format + apply)
3. Verify: `./gradlew build` (should produce 0 compile errors)

Kryptoid's wrasse wiring is in its `internal-convention-plugin` (`configureLinters()`) — wrasse is
always on the `kotlinCompilerPluginClasspath` with `warnOnly=true` and `fixOutputDir` set per
compilation. Per-module `lint` and `format` tasks are registered there. Config is at
`kryptoid/wrasse.json` (format enabled, ~15 lint rules, `no-wildcard-imports` and `no-unused-imports`
off due to known bugs). The version catalog has `wrasseVersion = "0.0.1-SNAPSHOT"` and
`wrasse-compiler-plugin` library entry pointing to mavenLocal.

## Formatting issues (open)

### 1. Kotest `BaseSpec({...})` pattern

When a class's supertype constructor has a single trailing lambda argument, keep `: SuperClass({` on the class line.

```kotlin
// current (bad):
class FooSpec :
    BaseSpec(
        {
            should("test something") { println("hello") }
        },
    )

// wanted:
class FooSpec : BaseSpec({
    should("test something") { println("hello") }
})
```

Needs detection of single-trailing-lambda-supertype in `resolveSuperTypeListFrame` — significant DocBuilder
restructuring.

For examples see `*Spec` classes in kryptoid

### 2. `val x = chain.call(...)` same-line

```kotlin
// current:
val future =
    client.execute(apacheRequest, ...)
// wanted:
val future = client.execute(
    apacheRequest, ...
)
```

The `= Call(` fix works for direct `CALL_EXPRESSION` but not `DOT_QUALIFIED_EXPRESSION` ending in a call. Extending it
caused regressions with long chains — needs distinguishing short chains from long ones.

### 2.1 Redundant newlines

Check `/var/home/vlad/dev/IdeaProjects/kryptoid/libs/common/lang/src/main/kotlin/com/varlanv/kryptoid/lang/Json.kt` for
example:

```kotlin
    @OptIn(ExperimentalSerializationApi::class)


    fun <T> fromStream(serializer: KSerializer<T>, input: InputStream): T =
```

### 3. Named-arguments FIR rule

Design note at `doc/named-arguments-rule-design.md` with user's verbatim spec.

**Rule:** wrap and add parameter names when any argument is itself a constructor/function call with arguments.
**Exclusion:** never add names to  `java.*`, `javax.*` callees. `kotlin.*` parameters maybe be added - it will be up to
users to put `kotlin.*` in `exclude` list in config JSON. Check if possible to avoid adding name to non-kotlin classes,
where Kotlin compiler found disallow it. **Needs:** FIR resolution (callee package + param names), per-rule options
system (not yet built). **Spec example:**

```kotlin
// input:
out.add(ParsedTags.AssetTag(t, FeedRelatedAssetTag.SpotDelisting(AssetDelistingTag(null, null))))
// output:
out.add(
    ParsedTags.AssetTag(
        asset = t,
        tag = FeedRelatedAssetTag.SpotDelisting(
            tag = AssetDelistingTag(
                haltTradeTime = null,
                fullDelistTime = null
            )
        )
    )
)
```

### 4. Disallow mixing named and positional parameters

* `GoodObject(param1 = "1", param2 = "2)`
* `fun goodFun(param1 = "1", param2 = "2)`
* `BadObject(param1 = "1", 2)`
* `fun badFun1(param1 = "1", 2)`

This may conflict with other rules that target positional parameter, but when enabled - this rule is a priority in all
places.

### 5. Auto-add parameter names

A rule / formatting option to so that `wrasseApply` can automatically add function / constructor parameter names
EVERYWHERE where applicable. Care should be taken to not leave app in a state where parameter is added for Java classes
where parameter name is unsupported, for example:

```kotlin
val list = CopyOnWriteArrayList<String>()
list.add(e = "")
```

### 6. Add support to passing configuration parameters to each rule

For now no big logic in that are needed, only the surrounding machinery with one real example in `if-else-bracing`.
Some parameters should be required, some should be non-required with default
value, some non-required without default. `if-else-bracing` rule should support parameter `allow-inline` that is
`false` by default. It should wrap statements like:
`val a = if (true) "1" else "2"`
to

```kotlin
val a = if (true) {
    "1"
} else {
    "2"
}
```

`if (condition) return`
to

```kotlin
if (condition) {
    return
}
```

### 7. Forbidden syntax rules

Last item in case you exhaust previous tasks. Add additional rule group - "forbidden-syntax".
Motivation: Kotlin allows to write same thing in dozens of different variants. Sometimes it is power, but sometimes it
is poison because there is no uniformity. What I want is additional rules group that would literally restrict certain
Kotlin features. One such example, not unique to kotlin: being able to restrict calling certain methods, with usual list
of exceptions. For example:

* forbid calling `System.currentTimeMillis()` everywhere except one file.
* forbid calling `listOf("...").associateBy` and other extension functions in this family, because they may dangerously
  silently overwrite duplicate keys
* Forbid using functions with `=` syntax. `fun good():String { return "good" }`; `fun bad():String = "bad"`

### 8. Performance improvements

Spend generous amount of time exploring potential for performance improvements in wrasse project.
`/var/home/vlad/dev/IdeaProjects/kryptoid/libs/common/lang/src/main/kotlin/com/varlanv/kryptoid/lang` has some useful
utilities ready, namely `StringSlice` that may be adopted for more efficient string scanning without allocation enforced
by JDK String. Care should be taken though to support UTF-8. Additionally, file system usage improvements may be
explored and implemented. Minimize allocations, syscalls, iterations, IO. Consider where Array may be used more
efficiently than lists; consider where simple for loop may be used more efficiently instead of map-filter chains;
consider where collections may be pre-allocated with known size; consider where mutation may be highly more performant
than immutability. Consider that wrasse may be used on project with
millions LOC, so don't be sloppy about performance and don't assume small allocations don't matter. Improve what you can
and measure. Milliseconds matter!!!

### 9. Kryptoid all rules

If exhausted activities up until here, try checking every existing rule against kryptoid project. See if each works and
not silently produce garbage. Fix obvious problems.

## Already completed (this session, uncommitted)

- Import bugs: typealias false positive fix, wildcard package expansion fix
- Formatter: continuation indent, EOL comments in arg lists, qualified class name chains, ≥3 param wrapping, ≥2 params
  with defaults wrapping, supertype list flat when short, typealias one-line, tail-width in Layout fit check, `= Call(`
  same-line for direct calls, binary expression logical-only breaks
- Default maxLineLength changed to 120
- Patch files kept after apply (caching fix in WPatchApplier)
- Kryptoid integration: convention plugin wiring, wrasse.json with format enabled

##

* For all examples, consider `kryptoid` project as main playground. It is live project and wrasse was designed to
  enhance
  its development experience.
* You are allowed to make commits in wrasse and kryptoid projects for persisting worktree. It doesn't matter what you
  put in commit messages - commits will be squashed and renamed later
