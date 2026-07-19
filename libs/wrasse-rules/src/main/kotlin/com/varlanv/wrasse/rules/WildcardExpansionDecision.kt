package com.varlanv.wrasse.rules

import com.varlanv.wrasse.lang.WEdit
import com.varlanv.wrasse.model.WCallableUsage
import com.varlanv.wrasse.model.WResolvedImport

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
 * **Syntactic gate — a name must actually be written to be worth importing.** `WResolvedUsage`
 * records every type the compiler's inference touches, not just the ones the author typed: a
 * member chain (`x.y.z()`) resolves through every intermediate type's members without the
 * intermediate types themselves ever appearing as identifiers, and an implicit local/loop/lambda
 * parameter type is a real classifier reference with no token in the source at all. Importing a
 * class whose name is never written is over-expansion no IDE would produce, even though it is
 * harmless (compilable, idempotent) — so classifier attribution and member-callable attribution
 * additionally require `X` (the attributed symbol's own simple name) to appear as a written
 * `IDENTIFIER` somewhere in the file body (outside import directives and the package directive —
 * those can't self-justify an import). A constructor call needs no special case: writing
 * `Widget()` writes the identifier `Widget`, so the gate passes naturally for the common case.
 * Top-level callable attribution is **not** gated: operator conventions (`+` desugars to a member
 * named `plus`, destructuring to `componentN`, `()` call syntax to `invoke`) legitimately need an
 * import whose name never appears as a written identifier at all — gating those would silently
 * drop imports the file actually needs, trading over-expansion for a broken compile, a strictly
 * worse outcome.
 *
 * A symbol already covered by an existing **non-aliased** explicit import of the same FQN is
 * excluded — that import already brings its plain simple name into scope. An *aliased* explicit
 * import (`import a.b.X as Y`) does **not** exclude `a.b.X` — it only binds the name `Y`, never
 * the plain `X` — but if the file never writes plain `X` either (only ever `Y`), the syntactic
 * gate above already drops `X` from attribution on its own; the two mechanisms are independent
 * and either alone is sufficient once both exist. A plain `import a.b.X` and an aliased
 * `import a.b.X as Y` of the same target legally coexist (empirically confirmed: harness-driven
 * real-compile probe, zero diagnostics), so on the rare occasion both survive (alias present,
 * plain name also written via some other qualified reference) this is a normal expansion, never
 * a bail. Symbols reachable only via Kotlin's default imports still attribute if their resolved
 * parent is `P`: expanding a redundant star of a default-imported package (e.g.
 * `import kotlin.collections.*`) into explicit imports is compile-preserving and harmless, so no
 * special-casing is done for default-import packages.
 *
 * **Member-star (`import C.*`) expansion — the authoritative-resolved-import extension.**
 * [StarAttribution.classify] decides, from the file's own [WResolvedImport]s, whether a star is a
 * package-star (unchanged behavior above) or a member-star (`C` a class/object/enum) —
 * cross-checked against the cheap usage-based [StarAttribution.isMemberStar] inference, bailing
 * on any disagreement or on an unresolved star (never guessing, design.md §8). For a member-star,
 * [StarAttribution.attributedMembers] computes the legally explicit-importable attributed set —
 * nested classifiers and callable members whose [WCallableUsage.isStatic] is true (enum entries,
 * Java statics) — expanded exactly like a package-star's attributed set (same exclusion,
 * collision, and KDoc checks below, same ASCII-sorted `import C.member` replacement). A non-static
 * member sharing `classFqName == C` is silently skipped rather than disqualifying anything: a
 * member-star can never legally bring such a member into scope in the first place, so its
 * appearance in the file's usage always means it resolved some other way (a receiver, or a
 * same-named constructor call needing `C` itself in scope) and has nothing to do with this star.
 * A member-star's own package/class syntactically can never be an `object`/companion (that star
 * shape is a hard compiler error — `import Singleton.*` — confirmed empirically, so it can never
 * reach this decision in a file whose resolution didn't already error), so [WCallableUsage.isStatic]
 * alone is both necessary and sufficient here; no separate object/companion case exists to handle.
 *
 * **Bails** (report fires, no edit — every ambiguity resolves toward "don't touch it"):
 * 1. Whole-file bail on missing/errored resolution is the caller's job (no resolved usage
 *    facade passed in at all means "don't call [decide]").
 * 2. **Unresolved or ambiguous star classification.** [StarAttribution.classify] returning
 *    [StarClassification.UNRESOLVED_OR_AMBIGUOUS] — no resolved-import counterpart for this star,
 *    or the authoritative package/member answer disagrees with the usage-based cross-check.
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

    fun decide(
        star: StarImportRecord,
        duplicatePackages: Set<String>,
        explicitImports: List<ImportRecord>,
        filePackageFqName: String,
        classifiers: Set<String>,
        callables: Set<WCallableUsage>,
        writtenIdentifiers: Set<String>,
        kdocSpans: List<IntRange>,
        sourceText: CharSequence,
        resolvedImports: List<WResolvedImport>,
    ): WEdit? {
        if (star.packageFqName in duplicatePackages) return null
        if (star.packageFqName == filePackageFqName) return null
        if (!ImportLineSpan.isAloneOnLine(sourceText, star.startOffset, star.endOffset)) return null

        val attributed = when (StarAttribution.classify(star.packageFqName, resolvedImports, callables)) {
            StarClassification.UNRESOLVED_OR_AMBIGUOUS -> return null
            StarClassification.MEMBER ->
                StarAttribution.attributedMembers(star.packageFqName, classifiers, callables, writtenIdentifiers)

            StarClassification.PACKAGE ->
                StarAttribution.attributedSymbols(star.packageFqName, classifiers, callables, writtenIdentifiers)
        }

        val explicitFqns = explicitImports.filter { it.aliasName == null }.mapTo(mutableSetOf()) { it.fqn }
        val filtered = attributed.filterNot { it in explicitFqns }.toSortedSet()
        if (filtered.isEmpty()) return null

        if (hasSimpleNameCollision(filtered, classifiers, callables)) return null

        val coveredNames = explicitImports.mapTo(mutableSetOf()) { it.aliasName ?: it.simpleName }
        filtered.mapTo(coveredNames) { it.substringAfterLast('.') }
        if (StarAttribution.kdocReferencesUncovered(kdocSpans, sourceText, coveredNames)) return null

        val replacement = filtered.joinToString("\n") { "import $it" }
        return WEdit(star.startOffset, star.endOffset, replacement)
    }

    private fun hasSimpleNameCollision(
        attributed: Set<String>,
        classifiers: Set<String>,
        callables: Set<WCallableUsage>,
    ): Boolean {
        val index = SimpleNameCollisionIndex.build(classifiers, callables)
        return attributed.any { symbol ->
            SimpleNameCollisionIndex.collidesWithOtherFqn(symbol, symbol.substringAfterLast('.'), index)
        }
    }
}
