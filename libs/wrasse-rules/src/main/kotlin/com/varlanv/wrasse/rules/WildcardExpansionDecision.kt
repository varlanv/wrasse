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
 * Pure verdict logic for the `no-wildcard-imports` expansion fix, compiler-free and unit-testable
 * without kotlinc. The report always fires regardless of this decision (design.md §8) — a `null`
 * result means "report only, no edit", never "nothing wrong".
 *
 * A used symbol attributes to `import P.*` iff resolving it needs exactly one top-level name
 * under `P`: a classifier `P.X[.Y...]` attributes `P.X` (nested access is always written through
 * its top-level owner); a top-level callable with `packageFqName == P` attributes `P.name`; a
 * member callable with `classFqName == P.X[.Y...]` attributes `P.X`.
 *
 * Classifier and member-callable attribution additionally require the symbol's own simple name to
 * appear as a written `IDENTIFIER` somewhere in the file body (outside import/package directives);
 * top-level callable attribution is never gated this way, since operator/destructuring/`invoke`
 * conventions legitimately need an import whose name is never written as an identifier. A symbol
 * already covered by a non-aliased explicit import of the same FQN is excluded; an aliased import
 * (binds only the alias, never the plain name) does not exclude it.
 *
 * [StarAttribution.classify] additionally distinguishes a package-star from a member-star (`C` a
 * class/object/enum) via the file's [WResolvedImport]s. For a member-star,
 * [StarAttribution.attributedMembers] computes the attributed set — nested classifiers plus
 * callable members whose [WCallableUsage.isStatic] is true — under the same rules above.
 *
 * [decide] bails (returns `null`, no edit — every ambiguity resolves toward "don't touch it")
 * when:
 * 1. [StarAttribution.classify] returns [StarClassification.UNRESOLVED_OR_AMBIGUOUS] — no
 *    resolved-import counterpart for this star, or its usage-based cross-check disagrees.
 * 2. Attribution is empty once already-imported symbols are excluded.
 * 3. The star's directive shares its line with something else ([ImportLineSpan.isAloneOnLine]).
 * 4. `P == filePackageFqName`, or another identical `import P.*` directive exists in the file.
 * 5. An attributed symbol's simple name maps, across every classifier/callable used anywhere in
 *    the file, to any FQN other than itself ([SimpleNameCollisionIndex]) — `FirResolvedQualifier`
 *    cannot distinguish "resolved via this star" from "resolved via full qualification", so any
 *    ambiguity here (including cross-star collisions) bails rather than risk emitting a wrong or
 *    behavior-flipping import.
 * 6. A KDoc `[Name]`/`[qualified.Name]` reference's leading segment isn't covered by an explicit
 *    import's visible name or this star's own attributed names
 *    ([StarAttribution.kdocReferencesUncovered]) — such references resolve through imports
 *    invisibly to FIR, so an uncovered one can't be verified safe.
 *
 * Otherwise the star directive is replaced with one ASCII-sorted `import P.x` per attributed
 * symbol.
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
