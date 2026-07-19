package com.varlanv.wrasse.rules

import com.varlanv.wrasse.model.WCallableUsage
import com.varlanv.wrasse.model.WResolvedImport

/**
 * Which shape a `import P.*` star directive is, decided authoritatively from the file's own
 * [WResolvedImport]s (design.md §8) rather than inferred from usage. [UNRESOLVED_OR_AMBIGUOUS]
 * means "don't guess" — either this star has no resolved counterpart at all (an unresolved
 * import), or the authoritative answer and the cheap usage-based cross-check
 * ([StarAttribution.isMemberStar]) disagree; callers must bail entirely in that case.
 */
enum class StarClassification {
    PACKAGE,
    MEMBER,
    UNRESOLVED_OR_AMBIGUOUS,
}

/**
 * Pure attribution logic for a `import P.*` star directive, shared by [WildcardExpansionDecision]
 * (which star's attributed symbols to expand into) and [UnusedStarDecision] (whether a star
 * attributes nothing at all and is therefore unused). Compiler-free, unit-testable without
 * kotlinc.
 *
 * See [WildcardExpansionDecision]'s KDoc for the full attribution rationale (the written-identifier
 * gate, the ungated top-level-callable/operator-convention case, why default-imported packages
 * still attribute). This object only holds the computation itself; the two callers apply different
 * verdict logic on top of the same result.
 */
object StarAttribution {

    private val KDOC_REFERENCE_PATTERN = Regex("\\[([\\p{L}_][\\p{L}\\p{N}_.]*)]")

    fun isMemberStar(packageFqName: String, callables: Set<WCallableUsage>): Boolean =
        callables.any { it.classFqName == packageFqName }

    /**
     * The authoritative classification for a star whose own package/class FQN is
     * [starFqName], cross-checked against the cheap usage-based [isMemberStar] inference.
     * A star with zero, or inconsistent, matching [WResolvedImport]s (fqn + `isStarImport`) is
     * [StarClassification.UNRESOLVED_OR_AMBIGUOUS] — an unresolved import, per the compiler's own
     * `is FirResolvedImport` signal (design.md §8). Disagreement in the direction that matters
     * (authoritative says package, inference says member) is also
     * [StarClassification.UNRESOLVED_OR_AMBIGUOUS]; the reverse is not a contradiction — a
     * member-star with zero used members (only a nested classifier, or genuinely unused) is
     * still [StarClassification.MEMBER].
     */
    fun classify(
        starFqName: String,
        resolvedImports: List<WResolvedImport>,
        callables: Set<WCallableUsage>,
    ): StarClassification {
        val matches = resolvedImports.filter { it.isStarImport && it.fqn == starFqName }
        if (matches.isEmpty() || matches.any { !it.resolved }) return StarClassification.UNRESOLVED_OR_AMBIGUOUS
        val parents = matches.mapTo(mutableSetOf()) { it.resolvedParentClassFqName }
        if (parents.size > 1) return StarClassification.UNRESOLVED_OR_AMBIGUOUS
        val authoritativeIsMember = parents.single() != null
        val inferredIsMember = isMemberStar(starFqName, callables)
        return when {
            !authoritativeIsMember && inferredIsMember -> StarClassification.UNRESOLVED_OR_AMBIGUOUS
            authoritativeIsMember -> StarClassification.MEMBER
            else -> StarClassification.PACKAGE
        }
    }

    /**
     * Attribution for a member-star `import C.*`: a used nested classifier `C.X[.Y...]`
     * attributes `C.X` (same top-level-owner rule as a package-star's nested-class access); a
     * used callable member with `classFqName == C` exactly attributes `C.member` when
     * [WCallableUsage.isStatic] is true. Both are gated by the same written-identifier requirement
     * as [attributedSymbols] (a member's own simple name must appear as a written `IDENTIFIER`
     * somewhere in the file body) — unlike package-star attribution, member attribution here has
     * no ungated operator-convention exception, since a member-star can never legally expose an
     * operator/`componentN`/`invoke` convention member bare in the first place (those are always
     * instance members, never static or enum-entry shaped).
     *
     * A callable member with `classFqName == C` whose [WCallableUsage.isStatic] is false is
     * **skipped**, not treated as disqualifying: Kotlin's import-on-demand from a classifier
     * exposes only statics/enum entries/nested classifiers (empirically confirmed — a member-star
     * never legally brings a non-static member into scope, bare or otherwise, under any
     * circumstance), so any such usage recorded against `C` is, by construction, resolved through
     * something other than this star — a receiver (`x.instanceMember()`, which needs no import of
     * `C` at all) or, degenerately, a same-classFqName constructor call (`C()`, which needs `C`
     * itself in scope via some other mechanism, never this star, since the star imports `C`'s
     * members, not `C`). It is therefore simply not this star's business either way.
     */
    fun attributedMembers(
        classFqName: String,
        classifiers: Set<String>,
        callables: Set<WCallableUsage>,
        writtenIdentifiers: Set<String>,
    ): Set<String> {
        val prefix = "$classFqName."
        val legal = mutableSetOf<String>()
        for (classifier in classifiers) {
            if (classifier.startsWith(prefix)) {
                val symbol = topLevelSymbol(classFqName, prefix, classifier)
                if (isWritten(symbol, writtenIdentifiers)) {
                    legal.add(symbol)
                }
            }
        }
        for (callable in callables) {
            if (callable.classFqName == classFqName && callable.isStatic && callable.name in writtenIdentifiers) {
                legal.add("$classFqName.${callable.name}")
            }
        }
        return legal
    }

    fun attributedSymbols(
        packageFqName: String,
        classifiers: Set<String>,
        callables: Set<WCallableUsage>,
        writtenIdentifiers: Set<String>,
    ): Set<String> {
        val prefix = "$packageFqName."
        val result = mutableSetOf<String>()
        for (classifier in classifiers) {
            if (classifier.startsWith(prefix)) {
                val symbol = topLevelSymbol(packageFqName, prefix, classifier)
                if (isWritten(symbol, writtenIdentifiers)) {
                    result.add(symbol)
                }
            }
        }
        for (callable in callables) {
            val classFqName = callable.classFqName
            if (classFqName == null) {
                if (callable.packageFqName == packageFqName) {
                    result.add("$packageFqName.${callable.name}")
                }
            } else if (classFqName.startsWith(prefix)) {
                val symbol = topLevelSymbol(packageFqName, prefix, classFqName)
                if (isWritten(symbol, writtenIdentifiers)) {
                    result.add(symbol)
                }
            }
        }
        return result
    }

    fun kdocReferencesUncovered(
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

    private fun isWritten(symbol: String, writtenIdentifiers: Set<String>): Boolean =
        symbol.substringAfterLast('.') in writtenIdentifiers

    private fun topLevelSymbol(packageFqName: String, prefix: String, fqn: String): String =
        "$packageFqName.${fqn.removePrefix(prefix).substringBefore('.')}"
}
