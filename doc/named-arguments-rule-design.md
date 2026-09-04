# Named Arguments Rule Design

## Rule: `named-arguments`

Wrap and add parameter names when any argument to a function/constructor call is itself a
constructor call or multi-argument function call.

### Exclusion

Never add parameter names to calls into `kotlin.*` or `java.*` packages — their parameter names
are often meaningless (`p0`, `element`), and `listOf(x, y)` / `mapOf(k to v)` are idiomatic
without names.

### Wrapping rule for constructors/functions

- ≥3 params → always wrap
- ≥2 params AND any param has a default value (`= ...`) → always wrap
- 1 param → wrap only on line length
- 2 params with no defaults → wrap only on line length

### Spec example

Input (flat, unreadable):

```kotlin
out.add(ParsedTags.AssetTag(t, FeedRelatedAssetTag.SpotDelisting(AssetDelistingTag(null, null))))
```

Output (wrapped and named):

```kotlin
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

- `out.add(...)` — `java.util` method, no name needed
- `ParsedTags.AssetTag(asset = t, tag = ...)` — domain code, named
- `FeedRelatedAssetTag.SpotDelisting(tag = ...)` — domain code, named
- `AssetDelistingTag(haltTradeTime = null, fullDelistTime = null)` — domain code, named

### Implementation approach

Needs FIR resolution — the rule must know the callee's parameter names and package.

1. **FIR checker:** New checker visits `FirFunctionCall`, records mapping:
   `callSiteOffset → List<paramName>` for positional args. Also records the callee's package FQN
   for the kotlin/java exclusion check.

2. **SAX-walk rule:** A `WNodeRule` targeting `VALUE_ARGUMENT_LIST` reads the FIR mapping. For
   each positional `VALUE_ARGUMENT` that doesn't already have a name, emits a `WEdit` inserting
   `paramName = ` before the argument expression.

3. **Trigger detection:** The rule fires when any argument in the call is itself a
   `CALL_EXPRESSION` (constructor or function call) with its own arguments. Simple value args
   (`x`, `null`, `42`) don't trigger wrapping on their own.

4. **Config:** Depends on per-rule options system (not yet built) for configuration like
   threshold tuning. Default behavior: trigger on nested calls as described above.

### Existing infrastructure

- `ResolvedUsageCollector` demonstrates the FIR visitor → SAX-walk data bridge pattern
- `WResolvedUsage` is the existing data structure for FIR-collected info
- The formatter's wrapping logic already handles arg-list wrapping once named args are inserted

### Prerequisites

- Per-rule options system in `WrasseRuleConfig` / `WConfig`
- The FIR checker needs to be registered alongside existing checkers in
  `WrasseCompilerPluginRegistrar` / `WrasseFirExtensionRegistrar`
