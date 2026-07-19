package com.varlanv.wrasse.rules

import com.varlanv.wrasse.lang.WEdit
import com.varlanv.wrasse.model.WContext
import com.varlanv.wrasse.model.WFileRule
import com.varlanv.wrasse.model.WNodeType
import com.varlanv.wrasse.model.WReporter
import com.varlanv.wrasse.model.WResolvedUsage
import com.varlanv.wrasse.model.WRule
import com.varlanv.wrasse.model.WStreamRule
import com.varlanv.wrasse.model.WUninitializedRuleGroup
import com.varlanv.wrasse.model.WrasseRuleConfig

/**
 * One fused decision-maker behind three user-facing rule ids — `no-unused-imports`,
 * `no-wildcard-imports`, `import-ordering` — replacing what were three independent
 * `WStreamRule`s that composed only via `afterFile` registration order plus
 * `EditPlan.takeEditsIn` self-consumption (design.md §5.1, §8, the interim mechanism now
 * retired for this family). Users still configure each id independently in `wrasse.json`;
 * [initGroup] receives exactly the enabled, non-excluded-for-this-file ids and their own
 * [WrasseRuleConfig] (D20 unchanged: fresh per file). An id absent from `configs` behaves as
 * if that rule does not exist for this file — every decision below is individually gated on
 * its own id being present.
 *
 * A single walk-side assembly (directives, star imports, comment/KDoc spans, written
 * identifiers, package FQN, and every import directive's own span for ordering) replaces the
 * three rules' near-duplicated bookkeeping. The pure decision objects
 * ([UnusedImportDecision], [UnusedStarDecision], [WildcardExpansionDecision], [StarAttribution],
 * [ImportOrderingDecision], [ImportRemovalSpan], [ImportLineSpan], [ImportDirectiveAssembler])
 * are unchanged — this engine only calls them, once each, in `afterFile`, then composes their
 * results itself instead of relying on the generic [com.varlanv.wrasse.model.EditPlan] as an
 * inter-rule bus.
 */
class ImportEngine : WUninitializedRuleGroup {

    override val ids: Set<String> = setOf(NO_UNUSED_IMPORTS_ID, NO_WILDCARD_IMPORTS_ID, IMPORT_ORDERING_ID)

    override fun requiresResolution(enabledIds: Set<String>): Boolean =
        NO_UNUSED_IMPORTS_ID in enabledIds || NO_WILDCARD_IMPORTS_ID in enabledIds

    override fun initGroup(configs: Map<String, WrasseRuleConfig>): WRule {
        val unusedRule = configs[NO_UNUSED_IMPORTS_ID]?.let { ReportFacade(NO_UNUSED_IMPORTS_ID, it) }
        val wildcardRule = configs[NO_WILDCARD_IMPORTS_ID]?.let { ReportFacade(NO_WILDCARD_IMPORTS_ID, it) }
        val orderingRule = configs[IMPORT_ORDERING_ID]?.let { ReportFacade(IMPORT_ORDERING_ID, it) }

        return object : WStreamRule {
            override val id = ENGINE_ID
            override val config = configs.values.first()

            private val assembler = ImportDirectiveAssembler()
            private val directives = mutableListOf<ImportRecord>()
            private val starImports = mutableListOf<StarImportRecord>()
            private val directiveSpans = mutableListOf<Pair<Int, Int>>()
            private val commentSpans = mutableListOf<IntRange>()
            private val kdocSpans = mutableListOf<IntRange>()
            private val writtenIdentifiers = mutableSetOf<String>()
            private var packagePathParts = mutableListOf<String>()
            private var filePackageFqName = ""
            private var listStart = -1
            private var listEnd = -1
            private var hasCommentInList = false

            override fun enterNode(ctx: WContext) {
                when (ctx.type) {
                    WNodeType.IMPORT_LIST -> listStart = ctx.startOffset
                    WNodeType.IMPORT_DIRECTIVE -> assembler.enterImportDirective(ctx.startOffset)
                    WNodeType.PACKAGE_DIRECTIVE -> packagePathParts = mutableListOf()
                    else -> {}
                }
            }

            override fun exitNode(ctx: WContext) {
                when (ctx.type) {
                    WNodeType.IMPORT_DIRECTIVE -> {
                        val raw = assembler.exitImportDirective(ctx.endOffset)
                        directiveSpans.add(raw.startOffset to raw.endOffset)
                        if (raw.pathParts.isNotEmpty()) {
                            val fqn = raw.pathParts.joinToString(".")
                            if (raw.isStar) {
                                starImports.add(StarImportRecord(fqn, raw.startOffset, raw.endOffset))
                            } else {
                                directives.add(
                                    ImportRecord(
                                        fqn = fqn,
                                        simpleName = raw.pathParts.last(),
                                        aliasName = raw.aliasName,
                                        startOffset = raw.startOffset,
                                        endOffset = raw.endOffset,
                                    )
                                )
                            }
                        }
                    }

                    WNodeType.IMPORT_LIST -> listEnd = ctx.endOffset
                    WNodeType.PACKAGE_DIRECTIVE -> filePackageFqName = packagePathParts.joinToString(".")
                    else -> {}
                }
            }

            override fun visitLeaf(ctx: WContext, reporter: WReporter) {
                when (ctx.type) {
                    WNodeType.EOL_COMMENT, WNodeType.BLOCK_COMMENT -> {
                        commentSpans.add(ctx.startOffset until ctx.endOffset)
                        if (listStart >= 0 && listEnd < 0) hasCommentInList = true
                        return
                    }

                    WNodeType.KDOC -> {
                        commentSpans.add(ctx.startOffset until ctx.endOffset)
                        kdocSpans.add(ctx.startOffset until ctx.endOffset)
                        if (listStart >= 0 && listEnd < 0) hasCommentInList = true
                        return
                    }

                    else -> {}
                }
                if (ctx.hasAncestor(WNodeType.IMPORT_DIRECTIVE)) {
                    assembler.visitLeaf(ctx)
                    return
                }
                if (ctx.type != WNodeType.IDENTIFIER) return
                val text = ctx.leafText?.toString()?.removeSurrounding("`") ?: return
                if (ctx.hasAncestor(WNodeType.PACKAGE_DIRECTIVE)) {
                    packagePathParts.add(text)
                } else {
                    writtenIdentifiers.add(text)
                }
            }

            override fun afterFile(ctx: WContext, reporter: WReporter) {
                val sourceText = ctx.sourceText
                val usage = ctx.resolvedUsage
                val usableUsage = if (usage != null && !usage.hasResolutionErrors) usage else null
                val pending = mutableListOf<PendingImportReport>()

                if (wildcardRule != null) {
                    collectWildcardExpansion(sourceText, usableUsage, pending)
                }
                if (unusedRule != null && usableUsage != null) {
                    collectUnusedImports(sourceText, usableUsage, pending)
                }
                if (orderingRule != null) {
                    decideOrdering(sourceText, pending, reporter)
                }

                for (p in pending) {
                    val rule = if (p.ruleId == NO_UNUSED_IMPORTS_ID) unusedRule else wildcardRule
                    if (rule == null) continue
                    reporter.report(
                        p.ruleId, p.message, p.reportStart, p.reportEnd, rule,
                        edits = p.edit?.let { listOf(it) } ?: emptyList(),
                    )
                }
            }

            private fun collectWildcardExpansion(
                sourceText: CharSequence,
                usableUsage: WResolvedUsage?,
                pending: MutableList<PendingImportReport>,
            ) {
                if (starImports.isEmpty()) return
                val duplicatePackages = starImports
                    .groupingBy { it.packageFqName }
                    .eachCount()
                    .filterValues { it > 1 }
                    .keys
                for (star in starImports) {
                    val edit = usableUsage?.let {
                        WildcardExpansionDecision.decide(
                            star = star,
                            duplicatePackages = duplicatePackages,
                            explicitImports = directives,
                            filePackageFqName = filePackageFqName,
                            classifiers = it.classifiers,
                            callables = it.callables,
                            writtenIdentifiers = writtenIdentifiers,
                            kdocSpans = kdocSpans,
                            sourceText = sourceText,
                        )
                    }
                    pending.add(PendingImportReport(NO_WILDCARD_IMPORTS_ID, WILDCARD_MESSAGE, star.startOffset, star.endOffset, edit))
                }
            }

            private fun collectUnusedImports(
                sourceText: CharSequence,
                usage: WResolvedUsage,
                pending: MutableList<PendingImportReport>,
            ) {
                for (import in directives) {
                    val unused = UnusedImportDecision.isUnused(
                        import = import,
                        classifiers = usage.classifiers,
                        callables = usage.callables,
                        sourceText = sourceText,
                        commentSpans = commentSpans,
                    )
                    if (unused) {
                        val edit = ImportRemovalSpan.compute(sourceText, import.startOffset, import.endOffset)
                        pending.add(PendingImportReport(NO_UNUSED_IMPORTS_ID, UNUSED_MESSAGE, import.startOffset, import.endOffset, edit))
                    }
                }
                for (star in starImports) {
                    val verdict = UnusedStarDecision.decide(
                        star = star,
                        allStars = starImports,
                        explicitImports = directives,
                        filePackageFqName = filePackageFqName,
                        classifiers = usage.classifiers,
                        callables = usage.callables,
                        writtenIdentifiers = writtenIdentifiers,
                        kdocSpans = kdocSpans,
                        sourceText = sourceText,
                    )
                    if (verdict is UnusedStarVerdict.Unused) {
                        pending.add(PendingImportReport(NO_UNUSED_IMPORTS_ID, UNUSED_MESSAGE, star.startOffset, star.endOffset, verdict.edit))
                    }
                }
            }

            private fun decideOrdering(
                sourceText: CharSequence,
                pending: MutableList<PendingImportReport>,
                reporter: WReporter,
            ) {
                if (listEnd < 0 || directiveSpans.size < 2) return
                val records = directiveSpans.map { (start, end) -> ImportOrderingRecord(start, end, sourceText.substring(start, end)) }

                if (!ImportOrderingDecision.isCleanList(sourceText, listStart, listEnd, directiveSpans, hasCommentInList)) {
                    reportOrderingIfOutOfOrder(records, reporter)
                    return
                }

                val probeEnd = probeEnd(sourceText, listEnd)
                val taken = pending.filter { p -> p.edit != null && p.edit!!.startOffset >= listStart && p.edit!!.endOffset <= probeEnd }
                if (taken.isEmpty()) {
                    val firstBad = ImportOrderingDecision.firstOutOfOrder(records) ?: return
                    reporter.report(
                        IMPORT_ORDERING_ID, ORDERING_MESSAGE, firstBad.startOffset, firstBad.endOffset, orderingRule!!,
                        edits = listOf(WEdit(listStart, listEnd, ImportOrderingDecision.sortedReplacement(records))),
                    )
                    return
                }

                val composed = ImportOrderingDecision.composeRegion(sourceText, listStart, probeEnd, taken.map { it.edit!! })
                if (composed == null) {
                    reportOrderingIfOutOfOrder(records, reporter)
                    return
                }
                for (p in taken) p.edit = null
                reporter.report(
                    IMPORT_ORDERING_ID, ORDERING_MESSAGE, listStart, probeEnd, orderingRule!!,
                    edits = listOf(WEdit(listStart, probeEnd, composed)),
                )
            }

            private fun reportOrderingIfOutOfOrder(records: List<ImportOrderingRecord>, reporter: WReporter) {
                val firstBad = ImportOrderingDecision.firstOutOfOrder(records) ?: return
                reporter.report(IMPORT_ORDERING_ID, ORDERING_MESSAGE, firstBad.startOffset, firstBad.endOffset, orderingRule!!)
            }

            private fun probeEnd(sourceText: CharSequence, listEnd: Int): Int {
                if (listEnd >= sourceText.length) return sourceText.length
                val newlineIndex = ImportLineSpan.indexOfNewlineFrom(sourceText, listEnd)
                return if (newlineIndex >= 0) newlineIndex + 1 else sourceText.length
            }
        }
    }

    private class PendingImportReport(
        val ruleId: String,
        val message: String,
        val reportStart: Int,
        val reportEnd: Int,
        var edit: WEdit?,
    )

    private class ReportFacade(
        override val id: String,
        override val config: WrasseRuleConfig,
    ) : WFileRule {
        override fun visit(ctx: WContext, reporter: WReporter) {}
    }

    companion object {
        const val NO_UNUSED_IMPORTS_ID = "no-unused-imports"
        const val NO_WILDCARD_IMPORTS_ID = "no-wildcard-imports"
        const val IMPORT_ORDERING_ID = "import-ordering"
        private const val ENGINE_ID = "import-engine"
        private const val WILDCARD_MESSAGE = "Replace wildcard import with explicit imports"
        private const val UNUSED_MESSAGE = "Unused import"
        private const val ORDERING_MESSAGE = "Imports are not sorted"
    }
}
