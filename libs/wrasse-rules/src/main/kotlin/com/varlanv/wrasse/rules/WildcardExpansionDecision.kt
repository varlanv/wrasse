package com.varlanv.wrasse.rules

import com.varlanv.wrasse.lang.WEdit
import com.varlanv.wrasse.model.WCallableUsage

/**
 * One `import P.*` directive assembled from the leaf stream: the star's package/class FQN `P`
 * and the directive's own span for reporting and for the replacement edit.
 */
class StarImportRecord(
    val packageFqName: String,
    val startOffset: Int,
    val endOffset: Int,
)

/**
 * Pure verdict logic for the `no-wildcard-imports` expansion fix, compiler-free so it is
 * unit-testable without a kotlinc dependency. The report always fires regardless of this
 * function's outcome (design.md §8) — a `null` result means "report only, no edit", never
 * "nothing wrong".
 *
 * **Attribution** — a used symbol attributes to `import P.*` iff resolving it needs exactly one
 * top-level name under `P`:
 * - a classifier `P.X[.Y...]` (nested-class access is always written qualified through its
 *   top-level owner, e.g. `Outer.Nested` — only `Outer` needs importing) attributes `P.X`;
 * - a top-level callable (`classFqName == null`) with `packageFqName == P` attributes `P.name`;
 * - a member callable with `classFqName == P.X[.Y...]` (covers constructors, whose
 *   `classFqName`/`name` are the class's own FQN/simple name; companion/enum members; Java
 *   statics) attributes `P.X`.
 *
 * A symbol already covered by an existing **non-aliased** explicit import of the same FQN is
 * excluded — that import already brings its plain simple name into scope. An *aliased* explicit
 * import (`import a.b.X as Y`) does **not** exclude `a.b.X` — it only binds the name `Y`, never
 * the plain `X`, so if the file also uses bare `X` (resolving today through the star), `X` still
 * needs its own explicit import; a plain `import a.b.X` and an aliased `import a.b.X as Y` of the
 * same target legally coexist (empirically confirmed: harness-driven real-compile probe, zero
 * diagnostics), so this is a normal expansion, never a bail. Symbols reachable only via Kotlin's
 * default imports still attribute if their resolved parent is `P`: expanding a redundant star of
 * a default-imported package (e.g. `import kotlin.collections.*`) into explicit imports is
 * compile-preserving and harmless, so no special-casing is done for default-import packages.
 *
 * **Bails** (report fires, no edit — every ambiguity resolves toward "don't touch it"):
 * 1. Whole-file bail on missing/errored resolution is the caller's job (no resolved usage
 *    facade passed in at all means "don't call [decide]").
 * 2. **Class/object-star.** Any used callable whose `classFqName` equals `P` *exactly* means `P`
 *    itself names a class/object (a member-star import, e.g. `import p.SomeEnum.*` for its
 *    entries) rather than a package — package-stars only in this task, so bail.
 * 3. **Zero attribution.** An unused star is `no-unused-imports`/engine territory, not expansion.
 * 4. **Shared line.** Same policy and rationale as [ImportRemovalSpan]'s bail: something else on
 *    the directive's line is a signal to leave the region alone.
 * 5. **Own package.** `P == filePackageFqName` is a degenerate star (everything in it already
 *    resolves without any import).
 * 6. **Duplicate stars.** Two identical `import P.*` directives in one file both bail — which one
 *    is "the" import of `P` is ambiguous.
 * 7. **Simple-name collision.** [WResolvedUsage][com.varlanv.wrasse.model.WResolvedUsage]'s
 *    classifier set cannot distinguish "resolved because this star brought the name into scope"
 *    from "resolved via full qualification, needing no import at all" — `FirResolvedQualifier`
 *    records a classifier for `P.X.member()` (fully qualified) exactly the same way it would for
 *    a bare `X` the star actually provides. Emitting `import P.X` for a qualified-only usage can
 *    (a) conflict with an existing explicit import of some other `Q.X` (a real, empirically
 *    confirmed compiler error: "Conflicting import: imported name 'X' is ambiguous", followed by
 *    "Unresolved reference" at every use), or (b) silently *flip* what bare `X` already resolves
 *    to — away from a default import or another package's same-named symbol — the worst outcome,
 *    a behavior change with no diagnostic at all pointing back at the fix (also empirically
 *    confirmed: a bare generic reference resolving to `kotlin.collections.List` before the fix
 *    resolves to a same-named non-generic class after, producing "No type arguments expected").
 *    The facade cannot tell these apart, so treat any ambiguity as disqualifying: build a
 *    simple-name → FQNs map from *every* used classifier (last segment) and callable (top-level
 *    callables by name; member callables by `classFqName`'s last segment) in the whole file, not
 *    just this star's own attribution. If an attributed symbol's simple name maps to any FQN
 *    other than itself in that map, bail the whole star. This also covers cross-star collisions
 *    (two stars attributing the same simple name from different packages) for free, since the
 *    map is built once over the whole file's usage, not per star. Over-bailing here is intended —
 *    a wrong expansion is the one outcome this rule may never produce.
 * 8. **KDoc bracket references.** KDoc `[Name]`/`[qualified.Name]` links resolve through imports
 *    invisibly to FIR (they never appear in [WCallableUsage]/classifier usage), so a reference
 *    that might have resolved only through this star cannot be verified safe. A reference's
 *    leading segment (the part before its first `.`, since that is the name that must be in
 *    scope) must be covered by explicit imports' visible names (alias if present, else simple
 *    name) or this star's own attributed simple names, else bail. Coverage does *not* include the
 *    file's own top-level declaration names — a second declaration-collecting pass over the file
 *    is not "cheaply available" from this rule's leaf-stream assembly, so that source is skipped
 *    entirely rather than approximated; the only effect of skipping it is more bails, never a
 *    false "covered".
 */
object WildcardExpansionDecision {

    private val KDOC_REFERENCE_PATTERN = Regex("\\[([\\p{L}_][\\p{L}\\p{N}_.]*)]")

    fun decide(
        star: StarImportRecord,
        duplicatePackages: Set<String>,
        explicitImports: List<ImportRecord>,
        filePackageFqName: String,
        classifiers: Set<String>,
        callables: Set<WCallableUsage>,
        kdocSpans: List<IntRange>,
        sourceText: CharSequence,
    ): WEdit? {
        if (star.packageFqName in duplicatePackages) return null
        if (star.packageFqName == filePackageFqName) return null
        if (!ImportLineSpan.isAloneOnLine(sourceText, star.startOffset, star.endOffset)) return null
        if (callables.any { it.classFqName == star.packageFqName }) return null

        val explicitFqns = explicitImports.filter { it.aliasName == null }.mapTo(mutableSetOf()) { it.fqn }
        val attributed = attributedSymbols(star.packageFqName, classifiers, callables)
            .filterNot { it in explicitFqns }
            .toSortedSet()
        if (attributed.isEmpty()) return null

        if (hasSimpleNameCollision(attributed, classifiers, callables)) return null

        val coveredNames = explicitImports.mapTo(mutableSetOf()) { it.aliasName ?: it.simpleName }
        attributed.mapTo(coveredNames) { it.substringAfterLast('.') }
        if (kdocReferencesUncovered(kdocSpans, sourceText, coveredNames)) return null

        val replacement = attributed.joinToString("\n") { "import $it" }
        return WEdit(star.startOffset, star.endOffset, replacement)
    }

    private fun attributedSymbols(
        packageFqName: String,
        classifiers: Set<String>,
        callables: Set<WCallableUsage>,
    ): Set<String> {
        val prefix = "$packageFqName."
        val result = mutableSetOf<String>()
        for (classifier in classifiers) {
            if (classifier.startsWith(prefix)) {
                result.add(topLevelSymbol(packageFqName, prefix, classifier))
            }
        }
        for (callable in callables) {
            val classFqName = callable.classFqName
            if (classFqName == null) {
                if (callable.packageFqName == packageFqName) {
                    result.add("$packageFqName.${callable.name}")
                }
            } else if (classFqName.startsWith(prefix)) {
                result.add(topLevelSymbol(packageFqName, prefix, classFqName))
            }
        }
        return result
    }

    private fun topLevelSymbol(packageFqName: String, prefix: String, fqn: String): String =
        "$packageFqName.${fqn.removePrefix(prefix).substringBefore('.')}"

    private fun hasSimpleNameCollision(
        attributed: Set<String>,
        classifiers: Set<String>,
        callables: Set<WCallableUsage>,
    ): Boolean {
        val fqnsBySimpleName = mutableMapOf<String, MutableSet<String>>()
        for (classifier in classifiers) {
            fqnsBySimpleName.getOrPut(classifier.substringAfterLast('.')) { mutableSetOf() }.add(classifier)
        }
        for (callable in callables) {
            val classFqName = callable.classFqName
            if (classFqName == null) {
                fqnsBySimpleName.getOrPut(callable.name) { mutableSetOf() }
                    .add("${callable.packageFqName}.${callable.name}")
            } else {
                fqnsBySimpleName.getOrPut(classFqName.substringAfterLast('.')) { mutableSetOf() }.add(classFqName)
            }
        }
        return attributed.any { symbol ->
            val fqns = fqnsBySimpleName[symbol.substringAfterLast('.')] ?: emptySet()
            fqns.any { it != symbol }
        }
    }

    private fun kdocReferencesUncovered(
        kdocSpans: List<IntRange>,
        sourceText: CharSequence,
        coveredSimpleNames: Set<String>,
    ): Boolean {
        for (span in kdocSpans) {
            val text = sourceText.subSequence(span.first, span.last + 1)
            for (match in KDOC_REFERENCE_PATTERN.findAll(text)) {
                val leadingSegment = match.groupValues[1].substringBefore('.')
                if (leadingSegment !in coveredSimpleNames) return true
            }
        }
        return false
    }
}
