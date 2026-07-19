package com.varlanv.wrasse.rules

import com.varlanv.wrasse.lang.WEdit
import com.varlanv.wrasse.model.WCallableUsage

/**
 * A `import P.*` star directive is either entirely out of `no-unused-imports`'s scope (no report
 * at all — it may still be used, or removing it might be unsafe for a reason this rule cannot
 * verify) or genuinely unused, in which case the report always fires and [edit] carries the
 * whole-line removal when one can be emitted safely, `null` otherwise (report-only, same D19
 * survivor policy as an unused explicit import sharing its line with something else).
 */
sealed interface UnusedStarVerdict {
    data object OutOfScope : UnusedStarVerdict
    data class Unused(val edit: WEdit?) : UnusedStarVerdict
}

/**
 * Pure verdict logic for a zero-attribution `import P.*` star under `no-unused-imports`,
 * compiler-free so it is unit-testable without a kotlinc dependency. Reuses
 * [StarAttribution.attributedSymbols] exactly as [WildcardExpansionDecision] does — same
 * written-identifier gating, same ungated top-level-callable/operator-convention rule — so a star
 * used only via an operator convention or destructuring is attributed and therefore never flagged
 * here.
 *
 * A star is removed iff **all** hold:
 * 1. Its attributed-symbol set, after excluding symbols already covered by a non-aliased explicit
 *    import of the same FQN (the identical filter [WildcardExpansionDecision.decide] applies before
 *    its own emptiness check), is empty.
 * 2. No used callable's `classFqName` equals [star]'s own package FQN exactly — that would mean
 *    the FQN names a class/object (a member-star), not a package; [StarAttribution.attributedSymbols]
 *    cannot see that usage at all (same reason [WildcardExpansionDecision] bails outright on it), so
 *    treating a member-star as zero-attribution without this check would misclassify a used one as
 *    unused. Member-star handling stays entirely out of scope here, same as expansion.
 * 3. [star]'s own package does not equal the file's own package — an own-package star is
 *    redundancy, not unusedness, deferred to a future engine (design.md).
 * 4. No KDoc bracket reference's leading segment is left uncovered by every *other* source (the
 *    file's explicit imports and every other star's own attribution) — since this star has zero
 *    attribution of its own, its only remaining source of coverage would be exactly a KDoc
 *    reference FIR cannot see; an uncovered one means removal might not be safe, so the star is
 *    left completely alone (no report), the same conservative direction as
 *    [WildcardExpansionDecision]'s own bail 8, inverted consequence (there: don't expand; here:
 *    don't even report).
 *
 * Duplicate identical zero-attribution stars are **not** specially bailed (unlike
 * [WildcardExpansionDecision]'s duplicate-package bail, which exists only to pick one expansion
 * target) — each is decided independently, so two duplicates both come back [UnusedStarVerdict.Unused]
 * with their own disjoint whole-line edits.
 *
 * Whether an edit is attached once a star clears 1-4 is [ImportRemovalSpan]'s existing alone-on-line
 * policy — same behavior as any other unused import: report always fires, edit is `null` when the
 * directive shares its line with something else.
 */
object UnusedStarDecision {

    fun decide(
        star: StarImportRecord,
        allStars: List<StarImportRecord>,
        explicitImports: List<ImportRecord>,
        filePackageFqName: String,
        classifiers: Set<String>,
        callables: Set<WCallableUsage>,
        writtenIdentifiers: Set<String>,
        kdocSpans: List<IntRange>,
        sourceText: CharSequence,
    ): UnusedStarVerdict {
        if (star.packageFqName == filePackageFqName) return UnusedStarVerdict.OutOfScope
        if (StarAttribution.isMemberStar(star.packageFqName, callables)) return UnusedStarVerdict.OutOfScope

        val explicitFqns = explicitImports.filter { it.aliasName == null }.mapTo(mutableSetOf()) { it.fqn }
        val attributed = StarAttribution.attributedSymbols(star.packageFqName, classifiers, callables, writtenIdentifiers)
            .filterNot { it in explicitFqns }
        if (attributed.isNotEmpty()) return UnusedStarVerdict.OutOfScope

        val coveredNames = explicitImports.mapTo(mutableSetOf()) { it.aliasName ?: it.simpleName }
        for (other in allStars) {
            if (other === star) continue
            StarAttribution.attributedSymbols(other.packageFqName, classifiers, callables, writtenIdentifiers)
                .mapTo(coveredNames) { it.substringAfterLast('.') }
        }
        if (StarAttribution.kdocReferencesUncovered(kdocSpans, sourceText, coveredNames)) return UnusedStarVerdict.OutOfScope

        return UnusedStarVerdict.Unused(ImportRemovalSpan.compute(sourceText, star.startOffset, star.endOffset))
    }
}
