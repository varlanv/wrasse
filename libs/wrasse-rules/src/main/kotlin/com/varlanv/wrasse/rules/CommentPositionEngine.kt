package com.varlanv.wrasse.rules

import com.varlanv.wrasse.model.WContext
import com.varlanv.wrasse.model.WFileRule
import com.varlanv.wrasse.model.WLeafRule
import com.varlanv.wrasse.model.WNodeType
import com.varlanv.wrasse.model.WReporter
import com.varlanv.wrasse.model.WRule
import com.varlanv.wrasse.model.WUninitializedRuleGroup
import com.varlanv.wrasse.model.WrasseRuleConfig

private val TARGET_TYPES = setOf(WNodeType.KDOC, WNodeType.EOL_COMMENT, WNodeType.BLOCK_COMMENT)

/**
 * Fuses five ids sharing the same "a KDoc/EOL/block comment leaf's own immediate parent and
 * position among that parent's direct children" check into one leaf-level decision-maker:
 * `kdoc-placement` (where a KDoc may structurally sit at all), and `type-argument-comment`/
 * `type-parameter-comment`/`value-argument-comment`/`value-parameter-comment` (a comment
 * discouraged inside a type/value argument or parameter list). Every fact each id needs — the
 * comment's own [WNodeType], its immediate parent (`ctx.ancestors.peekType()`), its own position
 * among that parent's children (`ctx.childIndex`), and whether the immediately preceding leaf is
 * whitespace containing a newline (`ctx.prevLeafType`/`ctx.prevLeafText`) — is already carried by
 * [WContext] for every leaf dispatch, so no buffering is needed anywhere in this engine.
 */
class CommentPositionEngine : WUninitializedRuleGroup {
    override val ids: Set<String> = setOf(
        KDOC_PLACEMENT_ID,
        TYPE_ARGUMENT_COMMENT_ID,
        TYPE_PARAMETER_COMMENT_ID,
        VALUE_ARGUMENT_COMMENT_ID,
        VALUE_PARAMETER_COMMENT_ID,
    )

    override fun initGroup(configs: Map<String, WrasseRuleConfig>): WRule {
        val rules = configs.mapValues { (ruleId, ruleConfig) -> ReportFacade(ruleId, ruleConfig) }

        return object : WLeafRule {
            override val id = ENGINE_ID
            override val config = configs.values.first()
            override val targetTypes = TARGET_TYPES

            override fun visitLeaf(ctx: WContext, reporter: WReporter) {
                val parent = ctx.ancestors.peekType()
                checkKdocPlacement(ctx, parent, reporter)
                checkTypeArgumentComment(ctx, parent, reporter)
                checkTypeParameterComment(ctx, parent, reporter)
                checkValueArgumentComment(ctx, parent, reporter)
                checkValueParameterComment(ctx, parent, reporter)
            }

            private fun checkKdocPlacement(
                ctx: WContext,
                parent: WNodeType,
                reporter: WReporter,
            ) {
                if (ctx.type != WNodeType.KDOC) return
                val rule = rules[KDOC_PLACEMENT_ID] ?: return
                val message = KdocPlacementDecision.decide(parent, ctx.childIndex) ?: return
                reporter.report(KDOC_PLACEMENT_ID, message, ctx.startOffset, ctx.endOffset, rule)
            }

            private fun checkTypeArgumentComment(
                ctx: WContext,
                parent: WNodeType,
                reporter: WReporter,
            ) {
                if (ctx.type == WNodeType.KDOC) return
                val rule = rules[TYPE_ARGUMENT_COMMENT_ID] ?: return
                val message = TypeArgumentCommentDecision.decide(parent, precededByNewline(ctx)) ?: return
                reporter.report(TYPE_ARGUMENT_COMMENT_ID, message, ctx.startOffset, ctx.endOffset, rule)
            }

            private fun checkTypeParameterComment(
                ctx: WContext,
                parent: WNodeType,
                reporter: WReporter,
            ) {
                if (ctx.type == WNodeType.KDOC) return
                val rule = rules[TYPE_PARAMETER_COMMENT_ID] ?: return
                val message = TypeParameterCommentDecision.decide(parent, precededByNewline(ctx)) ?: return
                reporter.report(TYPE_PARAMETER_COMMENT_ID, message, ctx.startOffset, ctx.endOffset, rule)
            }

            private fun checkValueArgumentComment(
                ctx: WContext,
                parent: WNodeType,
                reporter: WReporter,
            ) {
                val rule = rules[VALUE_ARGUMENT_COMMENT_ID] ?: return
                val message = ValueArgumentCommentDecision.decide(parent) ?: return
                reporter.report(VALUE_ARGUMENT_COMMENT_ID, message, ctx.startOffset, ctx.endOffset, rule)
            }

            private fun checkValueParameterComment(
                ctx: WContext,
                parent: WNodeType,
                reporter: WReporter,
            ) {
                val rule = rules[VALUE_PARAMETER_COMMENT_ID] ?: return
                val isKdocFirstChild = ctx.type == WNodeType.KDOC && ctx.childIndex == 0
                val message = ValueParameterCommentDecision.decide(parent, isKdocFirstChild) ?: return
                reporter.report(VALUE_PARAMETER_COMMENT_ID, message, ctx.startOffset, ctx.endOffset, rule)
            }

            private fun precededByNewline(ctx: WContext): Boolean =
                ctx.prevLeafType == WNodeType.WHITE_SPACE && ctx.prevLeafText?.contains('\n') == true
        }
    }

    private class ReportFacade(override val id: String, override val config: WrasseRuleConfig) : WFileRule {
        override fun visit(ctx: WContext, reporter: WReporter) {}
    }

    companion object {
        const val KDOC_PLACEMENT_ID = "kdoc-placement"
        const val TYPE_ARGUMENT_COMMENT_ID = "type-argument-comment"
        const val TYPE_PARAMETER_COMMENT_ID = "type-parameter-comment"
        const val VALUE_ARGUMENT_COMMENT_ID = "value-argument-comment"
        const val VALUE_PARAMETER_COMMENT_ID = "value-parameter-comment"
        private const val ENGINE_ID = "comment-position-engine"
    }
}
