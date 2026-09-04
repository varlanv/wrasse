# Remaining formatter tasks (2026-09-05)

All changes from rounds 1-6 are uncommitted in the working tree. 1573 tests pass, kryptoid builds with 0 errors.

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
