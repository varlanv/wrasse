package com.varlanv.wrasse.rules

import com.varlanv.wrasse.model.ChildBuffer
import com.varlanv.wrasse.model.WBufferedNodeRule
import com.varlanv.wrasse.model.WContext
import com.varlanv.wrasse.model.WNodeType
import com.varlanv.wrasse.model.WReporter
import com.varlanv.wrasse.model.WUninitializedRule
import com.varlanv.wrasse.model.WrasseRuleConfig
import com.varlanv.wrasse.model.isWhitespaceOrComment

private val TARGET_TYPES = setOf(WNodeType.BODY, WNodeType.BLOCK)

/**
 * Reports a loop (`for`/`while`/`do-while`) whose entire body is exactly one statement that is
 * itself an unconditional `break` or `return` ([UnconditionalJumpDecision]) — the loop can then
 * only ever run its first iteration. Narrowed from the upstream rule this id derives from: that
 * rule also flags a multi-statement body whenever *any* of its top-level statements is itself a
 * jump, backward-scanning earlier siblings for a "this is actually conditional" exemption — this
 * single-pass model only considers the single-statement case, a strict, provable subset (every
 * report here, upstream would also emit) that avoids re-deriving that backward-scan heuristic.
 */
class UnconditionalJumpStatementInLoopRule : WUninitializedRule {
    override val id: String = "unconditional-jump-statement-in-loop"

    override fun initRule(config: WrasseRuleConfig): WBufferedNodeRule {
        val ruleId = id
        return object : WBufferedNodeRule {
            override val id = ruleId
            override val config = config
            override val targetTypes = TARGET_TYPES

            override fun exitNode(
                ctx: WContext,
                children: ChildBuffer,
                reporter: WReporter,
            ) {
                when (ctx.type) {
                    WNodeType.BLOCK -> checkBlockBody(ctx, children, reporter)
                    WNodeType.BODY -> checkBareBody(ctx, children, reporter)
                    else -> {}
                }
            }

            private fun checkBlockBody(
                ctx: WContext,
                children: ChildBuffer,
                reporter: WReporter,
            ) {
                if (ctx.ancestors.peekType() != WNodeType.BODY || ctx.ancestors.size < 2) return
                val soleIdx = soleStatementIndex(children) ?: return
                report(
                    reporter,
                    children.type(soleIdx),
                    children.textSpan(soleIdx, ctx.sourceText),
                    ctx.ancestors.startOffsetAt(ctx.ancestors.size - 2),
                    ctx.ancestors.endOffsetAt(ctx.ancestors.size - 2),
                )
            }

            private fun checkBareBody(
                ctx: WContext,
                children: ChildBuffer,
                reporter: WReporter,
            ) {
                val loopType = ctx.ancestors.peekType()
                if (loopType != WNodeType.FOR && loopType != WNodeType.WHILE && loopType != WNodeType.DO_WHILE) return
                val soleIdx = soleStatementIndex(children) ?: return
                if (children.type(soleIdx) == WNodeType.BLOCK) return
                report(
                    reporter,
                    children.type(soleIdx),
                    children.textSpan(soleIdx, ctx.sourceText),
                    ctx.ancestors.peekStartOffset(),
                    ctx.ancestors.peekEndOffset(),
                )
            }

            private fun report(
                reporter: WReporter,
                soleType: WNodeType,
                soleText: CharSequence,
                loopStart: Int,
                loopEnd: Int,
            ) {
                val message =
                    when (soleType) {
                        WNodeType.BREAK -> UnconditionalJumpDecision.decideBreak()
                        WNodeType.RETURN -> UnconditionalJumpDecision.decideReturn(soleText)
                        else -> null
                    } ?: return
                reporter.report(ruleId, message, loopStart, loopEnd, this)
            }

            private fun soleStatementIndex(children: ChildBuffer): Int? {
                var found = -1
                for (i in 0 until children.size) {
                    val type = children.type(i)
                    if (type == WNodeType.LBRACE || type == WNodeType.RBRACE || type.isWhitespaceOrComment) continue
                    if (found >= 0) return null
                    found = i
                }
                return if (found >= 0) found else null
            }
        }
    }
}
