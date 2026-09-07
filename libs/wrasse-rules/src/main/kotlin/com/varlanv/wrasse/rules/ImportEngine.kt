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
 * Fuses four user-facing rule ids into one decision-maker: `no-unused-imports`,
 * `no-wildcard-imports`, `import-ordering`, `no-unnecessary-fqn`. Each id is configured
 * independently in `wrasse.json`; [initGroup] receives only the enabled, non-excluded-for-this-file
 * ids with their own [WrasseRuleConfig] (a fresh config per file). An id absent from `configs` is
 * inert for this file — every decision below is individually gated on its own id being present.
 *
 * `no-unnecessary-fqn` is the only id needing
 * [com.varlanv.wrasse.model.WResolvedUsage.qualifiedUsages] ([requiresQualifiedUsages]). Every one
 * of its reports carries a package-prefix-deletion [PendingImportReport.edit] at its usage site,
 * plus, for at most one usage per distinct import target, a new `import <fqn>` addition in that
 * report's [PendingImportReport.extraEdit] — composed into `import-ordering`'s whole-list rewrite
 * when available, placed standalone otherwise (see [resolveImportListChanges] and
 * [ImportInsertionDecision]).
 *
 * A single walk-side assembly collects directives, star imports, comment/KDoc spans, written
 * identifiers, the package FQN, and (only when `no-unnecessary-fqn` is enabled) every written
 * identifier's own offset. The pure decision objects ([UnusedImportDecision], [UnusedStarDecision],
 * [WildcardExpansionDecision], [StarAttribution], [ImportOrderingDecision], [ImportRemovalSpan],
 * [ImportLineSpan], [ImportDirectiveAssembler], [QualifiedUsageDecision]) are each called once in
 * `afterFile`; this engine composes their results itself rather than routing through
 * [com.varlanv.wrasse.model.EditPlan] as an inter-rule bus.
 */
class ImportEngine : WUninitializedRuleGroup {
    override val ids: Set<String> = setOf(
        NO_UNUSED_IMPORTS_ID,
        NO_WILDCARD_IMPORTS_ID,
        IMPORT_ORDERING_ID,
        NO_UNNECESSARY_FQN_ID,
    )

    override val canAutofix: Boolean = true

    override fun requiresResolution(enabledIds: Set<String>): Boolean =
        NO_UNUSED_IMPORTS_ID in enabledIds || NO_WILDCARD_IMPORTS_ID in enabledIds

    override fun requiresQualifiedUsages(enabledIds: Set<String>): Boolean =
        NO_UNNECESSARY_FQN_ID in enabledIds

    override fun initGroup(configs: Map<String, WrasseRuleConfig>): WRule {
        val unusedRule = configs[NO_UNUSED_IMPORTS_ID]?.let { ReportFacade(NO_UNUSED_IMPORTS_ID, it) }
        val wildcardRule = configs[NO_WILDCARD_IMPORTS_ID]?.let { ReportFacade(NO_WILDCARD_IMPORTS_ID, it) }
        val orderingRule = configs[IMPORT_ORDERING_ID]?.let { ReportFacade(IMPORT_ORDERING_ID, it) }
        val unnecessaryFqnRule = configs[NO_UNNECESSARY_FQN_ID]?.let { ReportFacade(NO_UNNECESSARY_FQN_ID, it) }
        val collectIdentifierPositions = unnecessaryFqnRule != null

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
            private val identifierOccurrences = mutableListOf<IdentifierOccurrence>()
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
                                    ),
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
                val text = ctx.leafString()?.removeSurrounding("`") ?: return
                if (ctx.hasAncestor(WNodeType.PACKAGE_DIRECTIVE)) {
                    packagePathParts.add(text)
                } else {
                    writtenIdentifiers.add(text)
                    if (collectIdentifierPositions) {
                        identifierOccurrences.add(IdentifierOccurrence(text, ctx.startOffset))
                    }
                }
            }

            override fun afterFile(ctx: WContext, reporter: WReporter) {
                val sourceText = ctx.sourceText
                val usage = ctx.resolvedUsage
                val usableUsage = if (usage != null && !usage.hasResolutionErrors) usage else null
                val pending = mutableListOf<PendingImportReport>()
                val importInsertions = mutableListOf<Pair<String, PendingImportReport>>()

                if (wildcardRule != null) {
                    collectWildcardExpansion(sourceText, usableUsage, pending)
                }
                if (unusedRule != null && usableUsage != null) {
                    collectUnusedImports(sourceText, usableUsage, pending)
                }
                if (unnecessaryFqnRule != null && usableUsage != null) {
                    collectUnnecessaryFqn(sourceText, usableUsage, pending, importInsertions)
                }
                if (orderingRule != null || importInsertions.isNotEmpty()) {
                    resolveImportListChanges(sourceText, pending, reporter, importInsertions)
                }

                for (p in pending) {
                    val rule = when (p.ruleId) {
                        NO_UNUSED_IMPORTS_ID -> unusedRule
                        NO_WILDCARD_IMPORTS_ID -> wildcardRule
                        else -> unnecessaryFqnRule
                    }
                    if (rule == null) continue
                    reporter.report(
                        p.ruleId,
                        p.message,
                        p.reportStart,
                        p.reportEnd,
                        rule,
                        edits = listOfNotNull(p.edit, p.extraEdit),
                    )
                }
            }

            private fun collectUnnecessaryFqn(
                sourceText: CharSequence,
                usage: WResolvedUsage,
                pending: MutableList<PendingImportReport>,
                importInsertions: MutableList<Pair<String, PendingImportReport>>,
            ) {
                val reports = QualifiedUsageDecision.decideAll(
                    qualifiedUsages = usage.qualifiedUsages,
                    sourceText = sourceText,
                    filePackageFqName = filePackageFqName,
                    classifiers = usage.classifiers,
                    callables = usage.callables,
                    explicitImports = directives,
                    identifierOccurrences = identifierOccurrences,
                    typeAliases = usage.typeAliases,
                )
                for (r in reports) {
                    val report = PendingImportReport(
                        NO_UNNECESSARY_FQN_ID,
                        UNNECESSARY_FQN_MESSAGE,
                        r.dropStart,
                        r.dropEnd,
                        edit = WEdit(r.dropStart, r.dropEnd, ""),
                    )
                    pending.add(report)
                    val newImportFqn = r.newImportFqn
                    if (newImportFqn != null) {
                        importInsertions.add(newImportFqn to report)
                    }
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
                            allStars = starImports,
                            duplicatePackages = duplicatePackages,
                            explicitImports = directives,
                            filePackageFqName = filePackageFqName,
                            classifiers = it.classifiers,
                            callables = it.callables,
                            writtenIdentifiers = writtenIdentifiers,
                            kdocSpans = kdocSpans,
                            sourceText = sourceText,
                            resolvedImports = it.resolvedImports,
                            typeAliases = it.typeAliases,
                        )
                    }
                    pending.add(
                        PendingImportReport(
                            NO_WILDCARD_IMPORTS_ID,
                            WILDCARD_MESSAGE,
                            star.startOffset,
                            star.endOffset,
                            edit,
                        ),
                    )
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
                        pending.add(
                            PendingImportReport(
                                NO_UNUSED_IMPORTS_ID,
                                UNUSED_MESSAGE,
                                import.startOffset,
                                import.endOffset,
                                edit,
                            ),
                        )
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
                        resolvedImports = usage.resolvedImports,
                    )
                    if (verdict is UnusedStarVerdict.Unused) {
                        pending.add(
                            PendingImportReport(
                                NO_UNUSED_IMPORTS_ID,
                                UNUSED_MESSAGE,
                                star.startOffset,
                                star.endOffset,
                                verdict.edit,
                            ),
                        )
                    }
                }
            }

            /**
             * Decides `import-ordering`'s composed rewrite and, when there are new-import
             * insertions to place, where they land — fused because an insertion's sorted position
             * falls out of the same composed rewrite. Runs even with `import-ordering` disabled,
             * solely to place insertions; no sort-order report is ever emitted in that case.
             *
             * Every report whose own edit was folded into the composed rewrite carries that same
             * rewrite (the plan keeps one copy of an identical edit), so each still shows as fixable.
             *
             * **Truthfulness invariant:** the `import-ordering` report fires if and only if
             * [ImportOrderingDecision.firstOutOfOrder] finds a genuine violation in the file's own,
             * pre-edit directive order — never merely because a composed rewrite ran. An
             * already-sorted list plus a pure insertion, or plus a pure removal/expansion, still
             * composes one edit, but that edit rides an existing, genuinely-true report
             * ([carrierFor]) instead of a fabricated one.
             */
            private fun resolveImportListChanges(
                sourceText: CharSequence,
                pending: MutableList<PendingImportReport>,
                reporter: WReporter,
                importInsertions: List<Pair<String, PendingImportReport>>,
            ) {
                if (listStart < 0) return
                val newFqns = importInsertions.map { it.first }.distinct().sorted()
                val anchorsByFqn = importInsertions.toMap()
                val records = directiveSpans.map { (start, end) ->
                    ImportOrderingRecord(start, end, sourceText.substring(start, end))
                }

                if (directiveSpans.isEmpty()) {
                    if (newFqns.isNotEmpty()) {
                        anchorsByFqn.getValue(newFqns.first()).extraEdit =
                            ImportInsertionDecision.emptyListInsertion(sourceText, listStart, newFqns)
                    }
                    return
                }

                if (orderingRule == null) {
                    attachStandaloneInsertions(sourceText, pending, records, newFqns, anchorsByFqn)
                    return
                }

                if (!ImportOrderingDecision.isCleanList(
                    sourceText,
                    listStart,
                    listEnd,
                    directiveSpans,
                    hasCommentInList,
                )) {
                    attachStandaloneInsertions(sourceText, pending, records, newFqns, anchorsByFqn)
                    reportOrderingIfOutOfOrder(records, reporter)
                    return
                }

                val probeEnd = probeEnd(sourceText, listEnd)
                val taken = pending.filter { p ->
                    p.edit != null && p.edit!!.startOffset >= listStart && p.edit!!.endOffset <= probeEnd
                }
                if (taken.isEmpty() && newFqns.isEmpty()) {
                    val firstBad = ImportOrderingDecision.firstOutOfOrder(records) ?: return
                    reporter.report(
                        IMPORT_ORDERING_ID,
                        ORDERING_MESSAGE,
                        firstBad.startOffset,
                        firstBad.endOffset,
                        orderingRule,
                        edits = listOf(WEdit(listStart, listEnd, ImportOrderingDecision.sortedReplacement(records))),
                    )
                    return
                }

                val composed = ImportOrderingDecision.composeRegion(
                    sourceText,
                    listStart,
                    probeEnd,
                    taken.map { it.edit!! },
                    newFqns.map { "import $it" },
                )
                if (composed == null) {
                    attachStandaloneInsertions(sourceText, pending, records, newFqns, anchorsByFqn)
                    reportOrderingIfOutOfOrder(records, reporter)
                    return
                }
                val composedEdit = WEdit(listStart, probeEnd, composed)
                for (p in taken) p.edit = composedEdit
                if (ImportOrderingDecision.firstOutOfOrder(records) != null) {
                    reporter.report(
                        IMPORT_ORDERING_ID,
                        ORDERING_MESSAGE,
                        listStart,
                        probeEnd,
                        orderingRule,
                        edits = listOf(composedEdit),
                    )
                } else {
                    val carrier = carrierFor(taken, newFqns, anchorsByFqn)
                    if (carrier.edit !== composedEdit) carrier.extraEdit = composedEdit
                }
            }

            /**
             * Picks which existing report the composed edit attaches to, so the report's own
             * message stays true regardless of which edit rides it. When [newFqns] is non-empty,
             * the earliest-sorting target's `no-unnecessary-fqn` anchor report carries it;
             * otherwise any report in [taken] already has a true message of its own
             * (`no-unused-imports`/`no-wildcard-imports`), so the choice among them is arbitrary.
             */
            private fun carrierFor(
                taken: List<PendingImportReport>,
                newFqns: List<String>,
                anchorsByFqn: Map<String, PendingImportReport>,
            ): PendingImportReport = if (newFqns.isNotEmpty()) {
                anchorsByFqn.getValue(newFqns.first())
            } else {
                taken.minWith(compareBy({ it.reportStart }, { it.reportEnd }))
            }

            /**
             * Used whenever a new import can't ride `import-ordering`'s composed rewrite (ordering
             * disabled, the list isn't clean, or composition bailed): each insertion group from
             * [ImportInsertionDecision.standaloneEdits] becomes the [PendingImportReport.extraEdit]
             * of one of its own target's anchor reports.
             *
             * [listEnd] equals the last directive's own `endOffset`; if that directive is also
             * being removed as unused, its deletion span extends past [listEnd] into the trailing
             * newline, so a zero-width insert exactly at [listEnd] would land inside that deletion
             * — an overlap. [adjustForSwallowingEdit] pushes such an insertion past whatever
             * pending edit would otherwise swallow it.
             */
            private fun attachStandaloneInsertions(
                sourceText: CharSequence,
                pending: List<PendingImportReport>,
                records: List<ImportOrderingRecord>,
                newFqns: List<String>,
                anchorsByFqn: Map<String, PendingImportReport>,
            ) {
                if (newFqns.isEmpty()) return
                for (group in ImportInsertionDecision.standaloneEdits(
                    sourceText,
                    listStart,
                    listEnd,
                    records,
                    newFqns,
                )) {
                    anchorsByFqn.getValue(group.fqns.first()).extraEdit = adjustForSwallowingEdit(group.edit, pending)
                }
            }

            private fun adjustForSwallowingEdit(edit: WEdit, pending: List<PendingImportReport>): WEdit {
                if (edit.startOffset != listEnd) return edit
                val swallowingEnd = pending
                    .mapNotNull { it.edit }
                    .filter { it.startOffset <= listEnd && it.endOffset > listEnd }
                    .maxOfOrNull { it.endOffset } ?: return edit
                return WEdit(swallowingEnd, swallowingEnd, edit.replacement.removePrefix("\n") + "\n")
            }

            private fun reportOrderingIfOutOfOrder(records: List<ImportOrderingRecord>, reporter: WReporter) {
                if (orderingRule == null) return
                val firstBad = ImportOrderingDecision.firstOutOfOrder(records) ?: return
                reporter.report(
                    IMPORT_ORDERING_ID,
                    ORDERING_MESSAGE,
                    firstBad.startOffset,
                    firstBad.endOffset,
                    orderingRule,
                )
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
        var extraEdit: WEdit? = null,
    )

    private class ReportFacade(override val id: String, override val config: WrasseRuleConfig) : WFileRule {
        override fun visit(ctx: WContext, reporter: WReporter) {}
    }

    companion object {
        const val NO_UNUSED_IMPORTS_ID = "no-unused-imports"
        const val NO_WILDCARD_IMPORTS_ID = "no-wildcard-imports"
        const val IMPORT_ORDERING_ID = "import-ordering"
        const val NO_UNNECESSARY_FQN_ID = "no-unnecessary-fqn"
        private const val ENGINE_ID = "import-engine"
        private const val WILDCARD_MESSAGE = "Replace wildcard import with explicit imports"
        private const val UNUSED_MESSAGE = "Unused import"
        private const val ORDERING_MESSAGE = "Imports are not sorted"
        private const val UNNECESSARY_FQN_MESSAGE = "Unnecessary fully qualified name"
    }
}
