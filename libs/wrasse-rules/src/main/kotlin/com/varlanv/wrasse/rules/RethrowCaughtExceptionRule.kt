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
 * A catch clause whose body's first significant child (skipping leading whitespace, `LBRACE`/
 * `RBRACE`, and comments) is a bare `throw <own parameter>` is a rethrow candidate; only the
 * maximal trailing run of such candidates within one `try`, counted back from its last catch
 * clause, is actually reported (see [RethrowCaughtExceptionDecision]) at each reported throw's own
 * span — a non-rethrowing catch clause anywhere after a candidate suppresses every earlier
 * candidate's own report.
 *
 * "First significant child" skips `WHITE_SPACE`/`LBRACE`/`RBRACE` and comment nodes
 * (`EOL_COMMENT`/`BLOCK_COMMENT`/`KDOC`), so a leading comment before the rethrow does not defeat
 * detection.
 */
class RethrowCaughtExceptionRule : WUninitializedRule {
    override val id: String = "rethrow-caught-exception"

    override fun initRule(config: WrasseRuleConfig): WBufferedNodeRule {
        val ruleId = id
        return object : WBufferedNodeRule {
            override val id = ruleId
            override val config = config
            override val targetTypes = setOf(
                WNodeType.TRY,
                WNodeType.CATCH,
                WNodeType.VALUE_PARAMETER_LIST,
                WNodeType.BLOCK,
            )

            private val pendingCatchNames = mutableListOf<String?>()
            private val pendingTryOutcomes = mutableListOf<MutableList<CatchOutcome>>()

            override fun enterNode(ctx: WContext, reporter: WReporter): Boolean {
                when (ctx.type) {
                    WNodeType.TRY -> {
                        pendingTryOutcomes.add(mutableListOf())
                        return true
                    }

                    WNodeType.CATCH -> {
                        pendingCatchNames.add(null)
                        return true
                    }

                    WNodeType.VALUE_PARAMETER_LIST -> {
                        if (ctx.ancestors.peekType() == WNodeType.CATCH && pendingCatchNames.isNotEmpty()) {
                            val facts = CatchParameterText.parse(
                                ctx.sourceText.subSequence(ctx.startOffset, ctx.endOffset),
                            )
                            pendingCatchNames[pendingCatchNames.size - 1] = facts?.name
                        }
                        return false
                    }

                    WNodeType.BLOCK -> return ctx.ancestors.peekType() == WNodeType.CATCH
                    else -> return false
                }
            }

            override fun exitNode(
                ctx: WContext,
                children: ChildBuffer,
                reporter: WReporter,
            ) {
                when (ctx.type) {
                    WNodeType.CATCH -> pendingCatchNames.removeAt(pendingCatchNames.size - 1)
                    WNodeType.BLOCK -> recordOutcome(ctx, children)
                    WNodeType.TRY -> finalizeTry(reporter)
                    else -> {}
                }
            }

            private fun recordOutcome(ctx: WContext, children: ChildBuffer) {
                val outcomes = pendingTryOutcomes.lastOrNull() ?: return
                val name = pendingCatchNames.lastOrNull()
                var firstIdx = -1
                for (i in 0 until children.size) {
                    val type = children.type(i)
                    if (type == WNodeType.LBRACE || type == WNodeType.RBRACE || type.isWhitespaceOrComment) continue
                    firstIdx = i
                    break
                }
                val isTrivial =
                    firstIdx >=
                        0 &&
                        children.type(firstIdx) ==
                        WNodeType.THROW &&
                        name !=
                        null &&
                        isBareRethrow(children.textSpan(firstIdx, ctx.sourceText), name)
                outcomes.add(
                    CatchOutcome(
                        isTrivial,
                        if (firstIdx >= 0) children.startOffset(firstIdx) else -1,
                        if (firstIdx >= 0) children.endOffset(firstIdx) else -1,
                    ),
                )
            }

            private fun isBareRethrow(throwText: CharSequence, name: String): Boolean {
                val trimmed = throwText.toString().trim().removeSuffix(";").trim()
                return trimmed == "throw $name"
            }

            private fun finalizeTry(reporter: WReporter) {
                val outcomes = pendingTryOutcomes.removeAt(pendingTryOutcomes.size - 1)
                val indices = RethrowCaughtExceptionDecision.trailingViolationIndices(outcomes.map { it.isTrivial })
                for (i in indices) {
                    val outcome = outcomes[i]
                    if (outcome.start < 0) continue
                    reporter.report(ruleId, RethrowCaughtExceptionDecision.MESSAGE, outcome.start, outcome.end, this)
                }
            }
        }
    }

    private class CatchOutcome(
        val isTrivial: Boolean,
        val start: Int,
        val end: Int,
    )
}
