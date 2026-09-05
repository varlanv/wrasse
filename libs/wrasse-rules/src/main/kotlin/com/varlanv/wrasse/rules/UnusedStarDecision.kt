package com.varlanv.wrasse.rules

import com.varlanv.wrasse.lang.WEdit
import com.varlanv.wrasse.model.WCallableUsage
import com.varlanv.wrasse.model.WResolvedImport

/**
 * Verdict for a zero-attribution `import P.*` star under `no-unused-imports`. [OutOfScope] means
 * no report. [Unused.edit] carries the whole-line removal when it can be emitted safely, `null`
 * when the directive shares its line with something else (report still fires either way).
 */
sealed interface UnusedStarVerdict {
    data object OutOfScope : UnusedStarVerdict

    data class Unused(val edit: WEdit?) : UnusedStarVerdict
}

/**
 * Decides whether a zero-attribution `import P.*` star is safely removable, compiler-free and
 * unit-testable without kotlinc.
 *
 * [decide] returns [UnusedStarVerdict.Unused] only when all hold for [star]:
 * 1. [StarAttribution.classify] is not [StarClassification.UNRESOLVED_OR_AMBIGUOUS].
 * 2. Its attributed set — [StarAttribution.attributedSymbols] for [StarClassification.PACKAGE],
 *    [StarAttribution.attributedMembers] for [StarClassification.MEMBER] — is empty after
 *    excluding symbols already covered by a non-aliased explicit import of the same FQN.
 * 3. [star]'s own package does not equal the file's own package.
 * 4. No KDoc bracket reference's leading segment is left uncovered by every *other* source (the
 *    file's explicit imports and every other star's own attribution) — this star's own
 *    attribution is empty, so a KDoc reference is otherwise-invisible usage FIR cannot see.
 *
 * Otherwise returns [UnusedStarVerdict.OutOfScope]. Duplicate identical zero-attribution stars are
 * decided independently, each producing its own disjoint whole-line edit via [ImportRemovalSpan].
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
        resolvedImports: List<WResolvedImport>,
    ): UnusedStarVerdict {
        if (star.packageFqName == filePackageFqName) return UnusedStarVerdict.OutOfScope

        val explicitFqns = explicitImports.filter { it.aliasName == null }.mapTo(mutableSetOf()) { it.fqn }
        val attributed = when (StarAttribution.classify(star.packageFqName, resolvedImports, callables)) {
            StarClassification.UNRESOLVED_OR_AMBIGUOUS -> return UnusedStarVerdict.OutOfScope
            StarClassification.MEMBER -> StarAttribution.attributedMembers(
                star.packageFqName,
                classifiers,
                callables,
                writtenIdentifiers,
            )
            StarClassification.PACKAGE -> StarAttribution.attributedSymbols(
                star.packageFqName,
                classifiers,
                callables,
                writtenIdentifiers,
            )
        }.filterNot { it in explicitFqns }
        if (attributed.isNotEmpty()) return UnusedStarVerdict.OutOfScope

        val coveredNames = explicitImports.mapTo(mutableSetOf()) { it.aliasName ?: it.simpleName }
        for (other in allStars) {
            if (other === star) continue
            when (StarAttribution.classify(other.packageFqName, resolvedImports, callables)) {
                StarClassification.MEMBER -> StarAttribution
                    .attributedMembers(other.packageFqName, classifiers, callables, writtenIdentifiers)
                    .mapTo(coveredNames) { it.substringAfterLast('.') }

                StarClassification.PACKAGE -> StarAttribution
                    .attributedSymbols(other.packageFqName, classifiers, callables, writtenIdentifiers)
                    .mapTo(coveredNames) { it.substringAfterLast('.') }

                StarClassification.UNRESOLVED_OR_AMBIGUOUS -> {}
            }
        }
        if (StarAttribution.kdocReferencesUncovered(kdocSpans, sourceText, coveredNames)) {
            return UnusedStarVerdict.OutOfScope
        }

        return UnusedStarVerdict.Unused(ImportRemovalSpan.compute(sourceText, star.startOffset, star.endOffset))
    }
}
