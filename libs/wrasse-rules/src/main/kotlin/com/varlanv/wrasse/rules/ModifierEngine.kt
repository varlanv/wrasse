package com.varlanv.wrasse.rules

import com.varlanv.wrasse.lang.WEdit
import com.varlanv.wrasse.model.ChildBuffer
import com.varlanv.wrasse.model.WBufferedNodeRule
import com.varlanv.wrasse.model.WContext
import com.varlanv.wrasse.model.WFileRule
import com.varlanv.wrasse.model.WNodeType
import com.varlanv.wrasse.model.WReporter
import com.varlanv.wrasse.model.WRule
import com.varlanv.wrasse.model.WUninitializedRuleGroup
import com.varlanv.wrasse.model.WrasseRuleConfig
import com.varlanv.wrasse.model.isWhitespaceOrComment

/**
 * Fuses two user-facing rule ids into one decision-maker: `modifier-order` and
 * `redundant-visibility-modifier`. Each id is configured independently in `wrasse.json`;
 * [initGroup] receives only the enabled, non-excluded-for-this-file ids with their own
 * [WrasseRuleConfig]. An id absent from `configs` is inert for this file — every decision below
 * is individually gated on its own id being present, so a `MODIFIER_LIST` with only one id
 * enabled is decided exactly as that id's own standalone rule decided it before fusion.
 *
 * Both ids can independently compute an edit anchored at the same `public` keyword's own token
 * span whenever it is both redundant and mis-ordered relative to a sibling modifier — the crash
 * this engine exists to fix (design.md §14). The deletion wins: whenever
 * `redundant-visibility-modifier` fires on a `MODIFIER_LIST` (reports, regardless of whether
 * [RedundantVisibilityModifierDeletionSpan.compute] actually attaches an edit for this occurrence),
 * that `public` keyword is dropped from the set [ModifierOrderDecision.decide] ever sees for the
 * same list — its position becomes moot, since the other rule is deleting it outright. `modifier-
 * order` then reports and fixes only the keywords that remain, so the two ids never emit
 * overlapping [WEdit]s for the same list.
 */
class ModifierEngine : WUninitializedRuleGroup {

    override val ids: Set<String> = setOf(MODIFIER_ORDER_ID, REDUNDANT_VISIBILITY_MODIFIER_ID)

    override val canAutofix: Boolean = true

    override fun initGroup(configs: Map<String, WrasseRuleConfig>): WRule {
        val orderConfig = configs[MODIFIER_ORDER_ID]
        val visibilityConfig = configs[REDUNDANT_VISIBILITY_MODIFIER_ID]
        val orderRule = orderConfig?.let { ReportFacade(MODIFIER_ORDER_ID, it) }
        val visibilityRule =
            if (visibilityConfig != null && !visibilityConfig.explicitApiActive) {
                ReportFacade(REDUNDANT_VISIBILITY_MODIFIER_ID, visibilityConfig)
            } else {
                null
            }

        return object : WBufferedNodeRule {
            override val id = ENGINE_ID
            override val config = (orderConfig ?: visibilityConfig)!!
            override val targetTypes = setOf(WNodeType.MODIFIER_LIST)

            override fun exitNode(ctx: WContext, children: ChildBuffer, reporter: WReporter) {
                var hasComment = false
                var hasOverride = false
                var publicIndex = -1
                val keywords = ArrayList<ModifierKeywordOccurrence>(children.size)
                for (i in 0 until children.size) {
                    val type = children.type(i)
                    if (type == WNodeType.WHITE_SPACE) continue
                    if (type.isWhitespaceOrComment) {
                        hasComment = true
                        continue
                    }
                    if (type == WNodeType.KW_OVERRIDE) hasOverride = true
                    val canonicalIndex = ORDERED_MODIFIER_TYPES.indexOf(type)
                    if (canonicalIndex >= 0) {
                        if (type == WNodeType.KW_PUBLIC) publicIndex = keywords.size
                        keywords.add(ModifierKeywordOccurrence(canonicalIndex, children.startOffset(i), children.endOffset(i)))
                    }
                }

                val visibilityFired = reportRedundantVisibility(ctx, reporter, visibilityRule, publicIndex, hasOverride, hasComment, keywords)

                if (orderRule == null) return
                val orderKeywords = if (visibilityFired) keywords.filterIndexed { i, _ -> i != publicIndex } else keywords
                val verdict = ModifierOrderDecision.decide(orderKeywords, ctx.sourceText, hasComment) ?: return
                reporter.report(
                    MODIFIER_ORDER_ID,
                    "Modifiers out of order, expected: ${verdict.expectedOrder}",
                    verdict.reportStart, verdict.reportEnd, orderRule,
                    edits = verdict.edits,
                )
            }

            private fun reportRedundantVisibility(
                ctx: WContext,
                reporter: WReporter,
                visibilityRule: ReportFacade?,
                publicIndex: Int,
                hasOverride: Boolean,
                hasComment: Boolean,
                keywords: List<ModifierKeywordOccurrence>,
            ): Boolean {
                if (visibilityRule == null || publicIndex < 0 || hasOverride) return false
                val parentType = ctx.ancestors.peekType()
                if (parentType != WNodeType.CLASS && parentType != WNodeType.FUN && parentType != WNodeType.PROPERTY) {
                    return false
                }
                val publicOccurrence = keywords[publicIndex]
                val edit = RedundantVisibilityModifierDeletionSpan.compute(
                    ctx.sourceText, publicOccurrence.startOffset, publicOccurrence.endOffset, hasComment,
                )
                reporter.report(
                    REDUNDANT_VISIBILITY_MODIFIER_ID, "Redundant public visibility modifier",
                    publicOccurrence.startOffset, publicOccurrence.endOffset, visibilityRule,
                    edits = edit?.let { listOf(it) } ?: emptyList(),
                )
                return true
            }
        }
    }

    private class ReportFacade(
        override val id: String,
        override val config: WrasseRuleConfig,
    ) : WFileRule {
        override fun visit(ctx: WContext, reporter: WReporter) {}
    }

    companion object {
        const val MODIFIER_ORDER_ID = "modifier-order"
        const val REDUNDANT_VISIBILITY_MODIFIER_ID = "redundant-visibility-modifier"
        private const val ENGINE_ID = "modifier-engine"

        private val ORDERED_MODIFIER_TYPES =
            listOf(
                WNodeType.KW_PUBLIC, WNodeType.KW_PROTECTED, WNodeType.KW_PRIVATE, WNodeType.KW_INTERNAL,
                WNodeType.KW_EXPECT, WNodeType.KW_ACTUAL,
                WNodeType.KW_FINAL, WNodeType.KW_OPEN, WNodeType.KW_ABSTRACT, WNodeType.KW_SEALED,
                WNodeType.KW_CONST,
                WNodeType.KW_EXTERNAL,
                WNodeType.KW_OVERRIDE,
                WNodeType.KW_LATEINIT,
                WNodeType.KW_TAILREC,
                WNodeType.KW_VARARG,
                WNodeType.KW_SUSPEND,
                WNodeType.KW_INNER,
                WNodeType.KW_ENUM, WNodeType.KW_ANNOTATION,
                WNodeType.KW_COMPANION,
                WNodeType.KW_INLINE,
                WNodeType.KW_INFIX,
                WNodeType.KW_OPERATOR,
                WNodeType.KW_DATA,
            )
    }
}
