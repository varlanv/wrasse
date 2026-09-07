package com.varlanv.wrasse.rules

import com.varlanv.wrasse.model.ChildBuffer
import com.varlanv.wrasse.model.ChildLeafHandler
import com.varlanv.wrasse.model.WBufferedNodeRule
import com.varlanv.wrasse.model.WContext
import com.varlanv.wrasse.model.WNodeType
import com.varlanv.wrasse.model.WReporter
import com.varlanv.wrasse.model.WUninitializedRule
import com.varlanv.wrasse.model.WrasseRuleConfig

private val TARGET_TYPES = setOf(WNodeType.FUN, WNodeType.VALUE_PARAMETER)

/**
 * A top-level `fun main` containing a `throw` anywhere in its own subtree (nested local
 * functions/lambdas included, matching the upstream rule this derives from) is reported (see
 * [ThrowingExceptionInMainDecision]) at the function's own span.
 *
 * `FUN` frames stack (nested local functions get their own); a `KW_THROW` leaf marks every
 * currently open frame, so a throw nested inside a local function still surfaces at the
 * enclosing `main`'s own frame. `VALUE_PARAMETER` is targeted alongside `FUN` only to count each
 * frame's own direct parameter arity (0 or 1 is the only legal `main` shape).
 */
class ThrowingExceptionInMainRule : WUninitializedRule {
    override val id: String = "throwing-exception-in-main"

    override fun initRule(config: WrasseRuleConfig): WBufferedNodeRule {
        val ruleId = id
        return object : WBufferedNodeRule, ChildLeafHandler {
            override val id = ruleId
            override val config = config
            override val targetTypes = TARGET_TYPES

            private val frames = mutableListOf<MainFunctionFrame>()

            override fun enterNode(ctx: WContext, reporter: WReporter): Boolean {
                if (ctx.type == WNodeType.FUN) frames.add(MainFunctionFrame())
                return true
            }

            override fun onChildLeaf(ctx: WContext, reporter: WReporter) {
                if (ctx.type == WNodeType.KW_THROW) {
                    for (frame in frames) frame.hasThrow = true
                }
            }

            override fun exitNode(
                ctx: WContext,
                children: ChildBuffer,
                reporter: WReporter,
            ) {
                when (ctx.type) {
                    WNodeType.VALUE_PARAMETER -> countParam(ctx)
                    WNodeType.FUN -> finalizeFun(ctx, children, reporter)
                    else -> {}
                }
            }

            private fun countParam(ctx: WContext) {
                val ancestors = ctx.ancestors
                if (ancestors.peekType() != WNodeType.VALUE_PARAMETER_LIST || ancestors.size < 2) return
                if (ancestors.typeAt(ancestors.size - 2) != WNodeType.FUN) return
                frames.lastOrNull()?.let { it.paramCount++ }
            }

            private fun finalizeFun(
                ctx: WContext,
                children: ChildBuffer,
                reporter: WReporter,
            ) {
                val frame = frames.removeAt(frames.size - 1)
                val idIdx = children.firstChildOfType(WNodeType.IDENTIFIER)
                val name = if (idIdx < 0) "" else IdentifierCasing.unquote(children.textSpan(idIdx, ctx.sourceText))
                val isTopLevel = ctx.ancestors.peekType() == WNodeType.FILE
                val modifierIdx = children.firstChildOfType(WNodeType.MODIFIER_LIST)
                val modifierText = if (modifierIdx >= 0) children.textSpan(modifierIdx, ctx.sourceText) else ""
                val isOverride = WordBoundaryScan.containsWord(modifierText, "override")
                val hasNonPublicVisibility =
                    WordBoundaryScan.containsWord(modifierText, "private") ||
                        WordBoundaryScan.containsWord(modifierText, "protected") ||
                        WordBoundaryScan.containsWord(modifierText, "internal")

                val message = ThrowingExceptionInMainDecision.decide(
                    name = name,
                    isTopLevel = isTopLevel,
                    isOverride = isOverride,
                    hasNonPublicVisibility = hasNonPublicVisibility,
                    paramCount = frame.paramCount,
                    hasThrow = frame.hasThrow,
                ) ?: return
                reporter.report(ruleId, message, ctx.startOffset, ctx.endOffset, this)
            }
        }
    }

    private class MainFunctionFrame {
        var hasThrow = false
        var paramCount = 0
    }
}
