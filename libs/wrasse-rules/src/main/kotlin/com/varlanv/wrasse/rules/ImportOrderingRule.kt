package com.varlanv.wrasse.rules

import com.varlanv.wrasse.lang.WEdit
import com.varlanv.wrasse.model.WContext
import com.varlanv.wrasse.model.WNodeType
import com.varlanv.wrasse.model.WReporter
import com.varlanv.wrasse.model.WStreamRule
import com.varlanv.wrasse.model.WUninitializedRule
import com.varlanv.wrasse.model.WrasseRuleConfig

/**
 * Plain ASCII-alphabetical ordering of the file's import directives, no grouping, no config
 * knob. Purely syntactic — never sets `requiresResolution`.
 *
 * The other import rules ([NoUnusedImportsRule]'s removal, [NoWildcardImportsRule]'s expansion)
 * decide their edits in `afterFile`, after [WNodeType.IMPORT_LIST] has already exited the walk —
 * so this rule cannot compose at its own `exitNode` (design.md §5.2's post-order composition
 * assumes the inner edit already exists by the time the outer node exits, which does not hold
 * across rules that defer to `afterFile`). It must defer its own decision to `afterFile` too,
 * and — since `afterFile` runs in rule-registration order (`StreamDispatch.allRules`, itself
 * `WRuleSet`'s `activeRules` order, itself `wrasseMain`'s registration order; verified by reading
 * the chain, not assumed) — it must be registered *after* `no-unused-imports` and
 * `no-wildcard-imports` so their edits already sit in [WContext.editPlan] by the time this rule's
 * `afterFile` runs and calls [com.varlanv.wrasse.model.EditPlan.takeEditsIn].
 */
class ImportOrderingRule : WUninitializedRule {
    override val id: String = "import-ordering"

    override fun initRule(config: WrasseRuleConfig): WStreamRule {
        val ruleId = id
        return object : WStreamRule {
            override val id = ruleId
            override val config = config

            private var listStart = -1
            private var listEnd = -1
            private var directiveStart = -1
            private var hasCommentInList = false
            private val directiveSpans = mutableListOf<Pair<Int, Int>>()

            override fun enterNode(ctx: WContext) {
                when (ctx.type) {
                    WNodeType.IMPORT_LIST -> listStart = ctx.startOffset
                    WNodeType.IMPORT_DIRECTIVE -> directiveStart = ctx.startOffset
                    else -> {}
                }
            }

            override fun exitNode(ctx: WContext) {
                when (ctx.type) {
                    WNodeType.IMPORT_DIRECTIVE -> {
                        directiveSpans.add(directiveStart to ctx.endOffset)
                        directiveStart = -1
                    }

                    WNodeType.IMPORT_LIST -> listEnd = ctx.endOffset
                    else -> {}
                }
            }

            override fun visitLeaf(ctx: WContext, reporter: WReporter) {
                if (listStart < 0 || listEnd >= 0) return
                when (ctx.type) {
                    WNodeType.EOL_COMMENT, WNodeType.BLOCK_COMMENT, WNodeType.KDOC -> hasCommentInList = true
                    else -> {}
                }
            }

            override fun afterFile(ctx: WContext, reporter: WReporter) {
                if (listEnd < 0 || directiveSpans.size < 2) return
                val sourceText = ctx.sourceText
                val records = directiveSpans.map { (start, end) ->
                    ImportOrderingRecord(start, end, sourceText.substring(start, end))
                }

                if (!ImportOrderingDecision.isCleanList(sourceText, listStart, listEnd, directiveSpans, hasCommentInList)) {
                    reportIfOutOfOrder(records, reporter)
                    return
                }

                val probeEnd = probeEnd(sourceText, listEnd)
                val taken = ctx.editPlan.takeEditsIn(listStart, probeEnd)
                if (taken.isEmpty()) {
                    val firstBad = ImportOrderingDecision.firstOutOfOrder(records) ?: return
                    reporter.report(
                        ruleId, MESSAGE, firstBad.startOffset, firstBad.endOffset, this,
                        edits = listOf(WEdit(listStart, listEnd, ImportOrderingDecision.sortedReplacement(records))),
                    )
                    return
                }

                val composed = ImportOrderingDecision.composeRegion(sourceText, listStart, probeEnd, taken.map { it.edit })
                if (composed == null) {
                    for (entry in taken.asReversed()) {
                        ctx.editPlan.add(entry.ruleId, entry.edit)
                    }
                    reportIfOutOfOrder(records, reporter)
                    return
                }
                reporter.report(
                    ruleId, MESSAGE, listStart, probeEnd, this,
                    edits = listOf(WEdit(listStart, probeEnd, composed)),
                )
            }

            private fun reportIfOutOfOrder(records: List<ImportOrderingRecord>, reporter: WReporter) {
                val firstBad = ImportOrderingDecision.firstOutOfOrder(records) ?: return
                reporter.report(ruleId, MESSAGE, firstBad.startOffset, firstBad.endOffset, this)
            }

            private fun probeEnd(sourceText: CharSequence, listEnd: Int): Int {
                if (listEnd >= sourceText.length) return sourceText.length
                val newlineIndex = ImportLineSpan.indexOfNewlineFrom(sourceText, listEnd)
                return if (newlineIndex >= 0) newlineIndex + 1 else sourceText.length
            }
        }
    }

    private companion object {
        const val MESSAGE = "Imports are not sorted"
    }
}
