package com.varlanv.wrasse.rules

import com.varlanv.wrasse.model.WContext
import com.varlanv.wrasse.model.WNodeType
import com.varlanv.wrasse.model.WReporter
import com.varlanv.wrasse.model.WStreamRule
import com.varlanv.wrasse.model.WUninitializedRule
import com.varlanv.wrasse.model.WrasseRuleConfig

/**
 * Reports a loop containing more than one `break`/`continue`, counted anywhere in its own body at
 * any nesting depth of `if`/`when`/blocks — but never descending into a nested loop's own body,
 * which gets its own independent [LoopJumpFrame] instead (the same never-merge-upward policy
 * [FunctionMetricsEngine] already established). `return` is not counted, matching the upstream
 * rule this id derives from.
 */
class LoopWithTooManyJumpStatementsRule : WUninitializedRule {
    override val id: String = "loop-with-too-many-jump-statements"

    override fun initRule(config: WrasseRuleConfig): WStreamRule {
        val ruleId = id
        return object : WStreamRule {
            override val id = ruleId
            override val config = config

            private val frames = mutableListOf<LoopJumpFrame>()
            private val completed = mutableListOf<LoopJumpFrame>()

            override fun enterNode(ctx: WContext) {
                when (ctx.type) {
                    WNodeType.FOR, WNodeType.WHILE, WNodeType.DO_WHILE -> frames.add(
                        LoopJumpFrame(ctx.startOffset, ctx.startOffset + keywordLength(ctx.type)),
                    )

                    WNodeType.BREAK, WNodeType.CONTINUE -> frames.lastOrNull()?.recordJump()
                    else -> {}
                }
            }

            override fun exitNode(ctx: WContext) {
                when (ctx.type) {
                    WNodeType.FOR, WNodeType.WHILE, WNodeType.DO_WHILE -> {
                        if (frames.isNotEmpty()) completed.add(frames.removeAt(frames.size - 1))
                    }

                    else -> {}
                }
            }

            override fun visitLeaf(ctx: WContext, reporter: WReporter) {}

            override fun afterFile(ctx: WContext, reporter: WReporter) {
                for (frame in completed) {
                    val message = LoopWithTooManyJumpStatementsDecision.decide(frame.jumpCount) ?: continue
                    reporter.report(ruleId, message, frame.loopStart, frame.loopEnd, this)
                }
            }

            private fun keywordLength(type: WNodeType): Int =
                when (type) {
                    WNodeType.FOR -> 3
                    WNodeType.WHILE -> 5
                    else -> 2
                }
        }
    }
}
