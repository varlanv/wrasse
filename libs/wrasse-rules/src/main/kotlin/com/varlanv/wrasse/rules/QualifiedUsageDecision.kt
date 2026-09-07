package com.varlanv.wrasse.rules

import com.varlanv.wrasse.model.WCallableUsage
import com.varlanv.wrasse.model.WQualifiedUsage
import com.varlanv.wrasse.model.WQualifiedUsageKind

/**
 * One written `IDENTIFIER` leaf outside import directives and the package directive, position-aware
 * so a name's own trailing chain segment can be told apart from the same name written elsewhere.
 * Collected by [ImportEngine]'s walk only when `no-unnecessary-fqn` is enabled.
 */
class IdentifierOccurrence(val text: String, val startOffset: Int)

/**
 * A single, provably-rewritable `no-unnecessary-fqn` finding. [dropStart]/[dropEnd] span the
 * package-prefix to delete (the `a.b.` part of a written `a.b.C`), never the whole chain.
 * [newImportFqn] is non-null on exactly one usage per distinct target — the one with the smallest
 * [dropStart] — when that target needs a new `import` directive added; `null` otherwise.
 */
class UnnecessaryFqnReport(
    val dropStart: Int,
    val dropEnd: Int,
    val newImportFqn: String?,
)

/**
 * Pure decision logic for `no-unnecessary-fqn` (design.md §8.3), compiler-free and unit-testable
 * without kotlinc. Every ambiguity resolves to a silent skip (no report), never a guess.
 *
 * A [WQualifiedUsage] is provably rewritable only if the source text at its own span literally
 * spells [WQualifiedUsage.targetFqName]: a `QUALIFIER` span must equal it exactly; a `TYPE_REF`
 * span must start with it followed by end-of-span, `<`, or `?`. [WQualifiedUsage.packageFqName]
 * gives the real package/class split, so a nested chain (`a.b.C.Nested`) always proposes importing
 * the outermost class (`a.b.C`), never the nested one; the dropped span is always exactly
 * `packageFqName + "."`.
 *
 * Every usage sharing the same candidate import FQN is one target: the import-viability decision
 * below runs once per target, but every surviving usage still gets its own report at its own span.
 *
 * **Import viability, checked in order for every target (see design.md §8.3 for the full
 * collision-ordering rationale):**
 * 1. Already imported (a non-aliased explicit `import a.b.C` for the exact candidate) — report
 *    unconditionally, before either collision check below.
 * 2. Otherwise, skip if the candidate's simple name collides with another used FQN's own simple
 *    name ([SimpleNameCollisionIndex], shared with [WildcardExpansionDecision]'s bail 7), or with
 *    an explicit import's visible name (alias if present, else simple name) bound to a different
 *    FQN.
 * 3. No new import needed at all — same package, or a [DefaultImportPackages] target — report,
 *    skipping check 4.
 * 4. Otherwise (a new import is genuinely needed): skip if the candidate's simple name is a
 *    written `IDENTIFIER` occurrence anywhere in the file outside every usage span of this same
 *    target ([IdentifierOccurrence] carries offsets so the target's own spans can be excluded
 *    positionally). Otherwise, report.
 *
 * Once a target clears every check, which fix variant applies (design.md §8.3):
 * 1. Already imported (check 2) or same package (check 3) — body edit only, no import change.
 * 2. A [DefaultImportPackages] target — body edit only (a bare name already resolves via the
 *    compiler's own defaults).
 * 3. Otherwise — add `import <candidateImportFqn>`, attached to the earliest usage only, so
 *    [ImportEngine] emits the addition once per target.
 */
object QualifiedUsageDecision {
    fun decideAll(
        qualifiedUsages: List<WQualifiedUsage>,
        sourceText: CharSequence,
        filePackageFqName: String,
        classifiers: Set<String>,
        callables: Set<WCallableUsage>,
        explicitImports: List<ImportRecord>,
        identifierOccurrences: List<IdentifierOccurrence>,
        typeAliases: Map<String, String> = emptyMap(),
    ): List<UnnecessaryFqnReport> {
        val proven = qualifiedUsages.mapNotNull { provenUsage(it, sourceText) }
        if (proven.isEmpty()) return emptyList()

        val collisionIndex = SimpleNameCollisionIndex.build(classifiers, callables, typeAliases)
        val reports = mutableListOf<UnnecessaryFqnReport>()

        for ((candidateImportFqn, usages) in proven.groupBy { it.candidateImportFqn }) {
            val topLevelSimpleName = usages.first().topLevelSimpleName
            val packageFqName = usages.first().usage.packageFqName
            val isSamePackage = packageFqName == filePackageFqName
            val alreadyImported = explicitImports.any { it.aliasName == null && it.fqn == candidateImportFqn }
            val needsNoNewImport = isSamePackage || packageFqName in DefaultImportPackages.ALL
            val ownSpans = usages.map { it.usage.startOffset until it.usage.endOffset } +
                literalOccurrences(candidateImportFqn, sourceText)
            if (!isSafeToDrop(
                candidateImportFqn = candidateImportFqn,
                alreadyImported = alreadyImported,
                topLevelSimpleName = topLevelSimpleName,
                needsNoNewImport = needsNoNewImport,
                explicitImports = explicitImports,
                collisionIndex = collisionIndex,
                identifierOccurrences = identifierOccurrences,
                ownSpans = ownSpans,
                typeAliases = typeAliases,
            )
            ) {
                continue
            }

            val newImportFqn = if (alreadyImported || needsNoNewImport) null else candidateImportFqn
            val sortedUsages = usages.sortedBy { it.usage.startOffset }
            for ((index, p) in sortedUsages.withIndex()) {
                reports.add(
                    UnnecessaryFqnReport(
                        dropStart = p.usage.startOffset,
                        dropEnd = p.usage.startOffset + p.dropLength,
                        newImportFqn = if (index == 0) newImportFqn else null,
                    ),
                )
            }
        }
        return reports.sortedWith(compareBy({ it.dropStart }, { it.dropEnd }))
    }

    private class ProvenUsage(
        val usage: WQualifiedUsage,
        val candidateImportFqn: String,
        val topLevelSimpleName: String,
        val dropLength: Int,
    )

    private fun provenUsage(usage: WQualifiedUsage, sourceText: CharSequence): ProvenUsage? {
        val packageFqName = usage.packageFqName
        if (packageFqName.isEmpty()) return null
        val prefix = "$packageFqName."
        if (!usage.targetFqName.startsWith(prefix)) return null
        if (usage.startOffset < 0 || usage.endOffset > sourceText.length || usage.endOffset < usage.startOffset) {
            return null
        }

        val written = sourceText.subSequence(usage.startOffset, usage.endOffset).toString()
        val matches = when (usage.kind) {
            WQualifiedUsageKind.QUALIFIER -> written == usage.targetFqName
            WQualifiedUsageKind.TYPE_REF -> {
                written.startsWith(
                    usage.targetFqName,
                ) &&
                    (written.length == usage.targetFqName.length ||
                        written[usage.targetFqName.length].let { it == '<' || it == '?' })
            }
        }
        if (!matches) return null

        val relativeClassName = usage.targetFqName.removePrefix(prefix)
        val topLevelSimpleName = relativeClassName.substringBefore('.')
        return ProvenUsage(usage, "$packageFqName.$topLevelSimpleName", topLevelSimpleName, prefix.length)
    }

    /**
     * Every literal, word-bounded occurrence of [candidateImportFqn] in [sourceText] — not just the
     * [WQualifiedUsage] spans FIR gave us, since a fully-qualified constructor call of a plain
     * (non-object) class never gets its own [WQualifiedUsage] entry (see design.md §8.3).
     */
    private fun literalOccurrences(candidateImportFqn: String, sourceText: CharSequence): List<IntRange> {
        return WordScan.wordOccurrences(sourceText, candidateImportFqn)
    }

    private fun isSafeToDrop(
        candidateImportFqn: String,
        alreadyImported: Boolean,
        topLevelSimpleName: String,
        needsNoNewImport: Boolean,
        explicitImports: List<ImportRecord>,
        collisionIndex: Map<String, Set<String>>,
        identifierOccurrences: List<IdentifierOccurrence>,
        ownSpans: List<IntRange>,
        typeAliases: Map<String, String>,
    ): Boolean {
        if (alreadyImported) return true

        if (SimpleNameCollisionIndex.collidesWithOtherFqn(
            candidateImportFqn,
            topLevelSimpleName,
            collisionIndex,
            typeAliases,
        )
        ) {
            return false
        }

        val explicitImportCollision = explicitImports.any {
            (it.aliasName ?: it.simpleName) == topLevelSimpleName && it.fqn != candidateImportFqn
        }
        if (explicitImportCollision) return false

        if (needsNoNewImport) return true

        val writtenElsewhere = identifierOccurrences.any { occ ->
            occ.text == topLevelSimpleName && ownSpans.none { span -> occ.startOffset in span }
        }
        if (writtenElsewhere) return false

        return true
    }
}
