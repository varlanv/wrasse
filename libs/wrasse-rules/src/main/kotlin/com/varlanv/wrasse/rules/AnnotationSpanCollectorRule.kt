package com.varlanv.wrasse.rules

import com.varlanv.wrasse.model.RuleLevel
import com.varlanv.wrasse.model.WContext
import com.varlanv.wrasse.model.WNodeRule
import com.varlanv.wrasse.model.WNodeType
import com.varlanv.wrasse.model.WReporter
import com.varlanv.wrasse.model.WrasseRuleConfig

/**
 * Framework-owned rule that records the source span of every annotation entry into
 * [WContext.annotationSpans], so a layout directive can tell which lines hold annotations only.
 */
class AnnotationSpanCollectorRule : WNodeRule {
    override val id = "annotation-span-collector"
    override val config = WrasseRuleConfig(level = RuleLevel.OFF, exclude = emptyList(), effectiveLevel = RuleLevel.OFF)
    override val targetTypes = setOf(WNodeType.ANNOTATION_ENTRY)

    override fun enterNode(ctx: WContext, reporter: WReporter): Boolean {
        ctx.annotationSpans.add(ctx.startOffset, ctx.endOffset)
        return false
    }
}
