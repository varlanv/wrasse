package com.varlanv.wrasse.rules

import com.varlanv.wrasse.model.ChildBuffer
import com.varlanv.wrasse.model.WBufferedNodeRule
import com.varlanv.wrasse.model.WContext
import com.varlanv.wrasse.model.WNodeType
import com.varlanv.wrasse.model.WReporter
import com.varlanv.wrasse.model.WUninitializedRule
import com.varlanv.wrasse.model.WrasseRuleConfig

private val TARGET_TYPES = setOf(WNodeType.FUN, WNodeType.VALUE_PARAMETER)

/**
 * See [FunctionParameterNamingDecision]. Only a plain function's own direct value parameters are
 * candidates — a primary/secondary constructor's own parameters are
 * [ConstructorParameterNamingRule]'s concern instead, never this one's.
 *
 * A function's own `override` fact is only known once its `MODIFIER_LIST` child has been visited,
 * so the report is deferred to the enclosing `FUN`'s own exit (a candidate parameter found while
 * that `FUN` frame is open is stashed, never decided immediately).
 */
class FunctionParameterNamingRule : WUninitializedRule {
    override val id: String = "function-parameter-naming"

    override fun initRule(config: WrasseRuleConfig): WBufferedNodeRule {
        val ruleId = id
        return object : WBufferedNodeRule {
            override val id = ruleId
            override val config = config
            override val targetTypes = TARGET_TYPES

            private val frames = mutableListOf<MutableList<Candidate>>()

            override fun enterNode(ctx: WContext, reporter: WReporter): Boolean {
                if (ctx.type == WNodeType.FUN) frames.add(mutableListOf())
                return true
            }

            override fun exitNode(
                ctx: WContext,
                children: ChildBuffer,
                reporter: WReporter,
            ) {
                when (ctx.type) {
                    WNodeType.VALUE_PARAMETER -> collectParam(ctx, children)
                    WNodeType.FUN -> finalizeFun(ctx, children, reporter)
                    else -> {}
                }
            }

            private fun collectParam(ctx: WContext, children: ChildBuffer) {
                val ancestors = ctx.ancestors
                if (ancestors.peekType() != WNodeType.VALUE_PARAMETER_LIST || ancestors.size < 2) return
                if (ancestors.typeAt(ancestors.size - 2) != WNodeType.FUN) return
                val frame = frames.lastOrNull() ?: return
                val idIdx = children.firstChildOfType(WNodeType.IDENTIFIER)
                if (idIdx < 0) return
                frame.add(
                    Candidate(
                        children.startOffset(idIdx),
                        children.endOffset(idIdx),
                        children.textSpan(idIdx, ctx.sourceText).toString(),
                    ),
                )
            }

            private fun finalizeFun(
                ctx: WContext,
                children: ChildBuffer,
                reporter: WReporter,
            ) {
                val candidates = frames.removeAt(frames.size - 1)
                if (candidates.isEmpty()) return
                val modifierIdx = children.firstChildOfType(WNodeType.MODIFIER_LIST)
                val modifierText = if (modifierIdx >= 0) children.textSpan(modifierIdx, ctx.sourceText) else ""
                val isOverride = WordBoundaryScan.containsWord(modifierText, "override")
                for (candidate in candidates) {
                    val message = FunctionParameterNamingDecision.decide(candidate.name, isOverride) ?: continue
                    reporter.report(ruleId, message, candidate.start, candidate.end, this)
                }
            }
        }
    }

    private class Candidate(
        val start: Int,
        val end: Int,
        val name: String,
    )
}
