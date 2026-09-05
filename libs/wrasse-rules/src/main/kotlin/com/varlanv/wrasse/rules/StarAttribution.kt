package com.varlanv.wrasse.rules

import com.varlanv.wrasse.model.WCallableUsage
import com.varlanv.wrasse.model.WResolvedImport

/**
 * Which shape a `import P.*` star directive is, decided from the file's own [WResolvedImport]s
 * rather than inferred from usage. [UNRESOLVED_OR_AMBIGUOUS] means "don't guess" — either the
 * star has no resolved counterpart, or the resolved answer disagrees with the cheap usage-based
 * cross-check ([StarAttribution.isMemberStar]); callers must bail entirely in that case.
 */
enum class StarClassification {
    PACKAGE,
    MEMBER,
    UNRESOLVED_OR_AMBIGUOUS,
}

/**
 * Pure attribution logic for a `import P.*` star directive, shared by [WildcardExpansionDecision]
 * (which attributed symbols to expand into) and [UnusedStarDecision] (whether a star attributes
 * nothing and is therefore unused). Compiler-free, unit-testable without kotlinc; see
 * [WildcardExpansionDecision] for the full attribution rules this object implements.
 */
object StarAttribution {
    private val KDOC_REFERENCE_PATTERN = Regex("\\[([\\p{L}_][\\p{L}\\p{N}_.]*)]")

    fun isMemberStar(
        packageFqName: String,
        callables: Set<WCallableUsage>,
    ): Boolean = callables.any { it.classFqName == packageFqName }

    /**
     * Authoritative classification for a star whose own package/class FQN is [starFqName],
     * cross-checked against the cheap usage-based [isMemberStar] inference. A star with zero, or
     * inconsistent, matching [WResolvedImport]s (`fqn` + `isStarImport`, unresolved per
     * `FirResolvedImport`) is [StarClassification.UNRESOLVED_OR_AMBIGUOUS]. Disagreement in the
     * direction that matters (authoritative says package, inference says member) is also
     * [StarClassification.UNRESOLVED_OR_AMBIGUOUS]; the reverse is not a contradiction — a
     * member-star with zero used members is still [StarClassification.MEMBER].
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
     * Attribution for a member-star `import C.*`: a used nested classifier `C.X[.Y...]` attributes
     * `C.X`; a used callable member with `classFqName == C` attributes `C.member` when
     * [WCallableUsage.isStatic] is true. Both require the member's own simple name to appear as a
     * written `IDENTIFIER` somewhere in the file body, same as [attributedSymbols] — unlike
     * package-star attribution, there is no ungated operator-convention exception here, since a
     * member-star can never expose an operator/`componentN`/`invoke` convention member bare (those
     * are always instance members).
     *
     * A non-static callable member with `classFqName == C` is skipped, not disqualifying:
     * import-on-demand from a classifier only ever exposes statics/enum entries/nested classifiers,
     * so any such usage is resolved through something other than this star.
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
                if (symbol != classifier && !isTopLevelClassifier(symbol, prefix, classifiers)) continue
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
                if (symbol != classFqName && !isTopLevelClassifier(symbol, prefix, classifiers)) continue
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

    private fun isTopLevelClassifier(
        symbol: String,
        packagePrefix: String,
        classifiers: Set<String>,
    ): Boolean {
        if (symbol in classifiers) return true
        val simpleName = symbol.removePrefix(packagePrefix)
        if (simpleName.isEmpty()) return false
        return simpleName[0].isUpperCase()
    }

    private fun isWritten(symbol: String, writtenIdentifiers: Set<String>): Boolean =
        symbol.substringAfterLast('.') in writtenIdentifiers

    private fun topLevelSymbol(
        packageFqName: String,
        prefix: String,
        fqn: String,
    ): String =
        "$packageFqName.${fqn.removePrefix(prefix).substringBefore('.')}"
}
