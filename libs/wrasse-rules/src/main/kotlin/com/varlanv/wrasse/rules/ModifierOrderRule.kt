package com.varlanv.wrasse.rules

import com.varlanv.wrasse.model.ChildBuffer
import com.varlanv.wrasse.model.WBufferedNodeRule
import com.varlanv.wrasse.model.WContext
import com.varlanv.wrasse.model.WNodeType
import com.varlanv.wrasse.model.WReporter
import com.varlanv.wrasse.model.WUninitializedRule
import com.varlanv.wrasse.model.WrasseRuleConfig
import com.varlanv.wrasse.model.isWhitespaceOrComment

/**
 * Reorders a declaration's modifier keywords into the Kotlin coding-conventions canonical order
 * (kotlinc's own `KtTokens.MODIFIER_KEYWORDS_ARRAY`, the source both ktlint's `modifier-order` and
 * detekt's `ModifierOrder` derive their own comparison list from).
 *
 * Only [ORDERED_MODIFIER_TYPES] participate in the comparison and the fix: visibility, `expect`/
 * `actual`, modality, `const`/`external`/`override`/`lateinit`/`tailrec`/`vararg`/`suspend`/`inner`,
 * `enum`/`annotation`/`companion`/`inline`/`infix`/`operator`/`data`. Annotations, the `fun`/`value`
 * declaration keywords, and a context-parameter/receiver list are never compared or touched —
 * matching detekt's own narrower scope (which never even looks at them) over ktlint's broader one
 * (which repositions annotations and context lists too); see design.md §13 for the ground-truthed
 * disagreement between the two upstreams. Applies uniformly to every `MODIFIER_LIST` regardless of
 * its parent (a class/function/property, a property accessor, an enum entry, a value parameter),
 * since a list with fewer than two comparable keywords is always trivially ordered.
 *
 * See [ModifierOrderDecision] for the verdict/fix logic itself.
 */
class ModifierOrderRule : WUninitializedRule {
    override val id: String = "modifier-order"

    override fun initRule(config: WrasseRuleConfig): WBufferedNodeRule {
        val ruleId = id
        return object : WBufferedNodeRule {
            override val id = ruleId
            override val config = config
            override val targetTypes = setOf(WNodeType.MODIFIER_LIST)

            override fun exitNode(ctx: WContext, children: ChildBuffer, reporter: WReporter) {
                var hasComment = false
                val keywords = ArrayList<ModifierKeywordOccurrence>(children.size)
                for (i in 0 until children.size) {
                    val type = children.type(i)
                    if (type == WNodeType.WHITE_SPACE) continue
                    if (type.isWhitespaceOrComment) {
                        hasComment = true
                        continue
                    }
                    val canonicalIndex = ORDERED_MODIFIER_TYPES.indexOf(type)
                    if (canonicalIndex >= 0) {
                        keywords.add(ModifierKeywordOccurrence(canonicalIndex, children.startOffset(i), children.endOffset(i)))
                    }
                }

                val verdict = ModifierOrderDecision.decide(keywords, ctx.sourceText, hasComment) ?: return

                reporter.report(
                    ruleId,
                    "Modifiers out of order, expected: ${verdict.expectedOrder}",
                    verdict.reportStart, verdict.reportEnd, this,
                    edits = verdict.edits,
                )
            }
        }
    }

    private companion object {
        val ORDERED_MODIFIER_TYPES =
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
