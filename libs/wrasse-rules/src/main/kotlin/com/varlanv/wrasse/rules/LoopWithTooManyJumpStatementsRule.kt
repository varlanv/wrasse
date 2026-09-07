package com.varlanv.wrasse.rules

import com.varlanv.wrasse.model.WContext
import com.varlanv.wrasse.model.WNodeRule
import com.varlanv.wrasse.model.WNodeType
import com.varlanv.wrasse.model.WReporter
import com.varlanv.wrasse.model.WRuleOptionSpec
import com.varlanv.wrasse.model.WRuleOptionType
import com.varlanv.wrasse.model.WRuleOptionValue
import com.varlanv.wrasse.model.WUninitializedRule
import com.varlanv.wrasse.model.WrasseRuleConfig

private val TARGET_TYPES = setOf(
    WNodeType.FOR,
    WNodeType.WHILE,
    WNodeType.DO_WHILE,
    WNodeType.BREAK,
    WNodeType.CONTINUE,
)

/**
 * Reports a loop containing more than one `break`/`continue`, counted anywhere in its own body at
 * any nesting depth of `if`/`when`/blocks — but never descending into a nested loop's own body,
 * which gets its own independent [LoopJumpFrame] instead (the same never-merge-upward policy
 * [FunctionMetricsEngine] already established). `return` is not counted, matching the upstream
 * rule this id derives from. A node rule over the three loop types plus `BREAK`/`CONTINUE`: a
 * jump attributes itself to the innermost open loop frame on enter and needs no exit.
 */
class LoopWithTooManyJumpStatementsRule : WUninitializedRule {
    override val id: String = "loop-with-too-many-jump-statements"
    override val options: List<WRuleOptionSpec> = listOf(
        WRuleOptionSpec.Optional(
            name = THRESHOLD,
            type = WRuleOptionType.INTEGER,
            description = "Highest number of break or continue statements a single loop may contain",
            default = WRuleOptionValue.Num(LoopWithTooManyJumpStatementsDecision.DEFAULT_THRESHOLD.toLong()),
            minimum = 0,
            maximum = Int.MAX_VALUE.toLong(),
        ),
    )

    override fun initRule(config: WrasseRuleConfig): WNodeRule {
        val ruleId = id
        val threshold = config.options.integer(THRESHOLD).toInt()
        return object : WNodeRule {
            override val id = ruleId
            override val config = config
            override val targetTypes = TARGET_TYPES

            private val frames = mutableListOf<LoopJumpFrame>()
            private val completed = mutableListOf<LoopJumpFrame>()

            override fun enterNode(ctx: WContext, reporter: WReporter): Boolean {
                when (ctx.type) {
                    WNodeType.FOR, WNodeType.WHILE, WNodeType.DO_WHILE -> {
                        frames.add(LoopJumpFrame(ctx.startOffset, ctx.startOffset + keywordLength(ctx.type)))
                        return true
                    }

                    else -> {
                        frames.lastOrNull()?.recordJump()
                        return false
                    }
                }
            }

            override fun exitNode(ctx: WContext, reporter: WReporter) {
                if (frames.isNotEmpty()) completed.add(frames.removeAt(frames.size - 1))
            }

            override fun afterFile(ctx: WContext, reporter: WReporter) {
                for (frame in completed) {
                    val message = LoopWithTooManyJumpStatementsDecision.decide(frame.jumpCount, threshold) ?: continue
                    reporter.report(ruleId, message, frame.loopStart, frame.loopEnd, this)
                }
            }

            private fun keywordLength(type: WNodeType): Int = when (type) {
                WNodeType.FOR -> 3
                WNodeType.WHILE -> 5
                else -> 2
            }
        }
    }

    private companion object {
        const val THRESHOLD = "threshold"
    }
}
