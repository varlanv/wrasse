package com.varlanv.wrasse.rules

import com.varlanv.wrasse.model.WCallableUsage

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
