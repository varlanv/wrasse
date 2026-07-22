package com.varlanv.wrasse.rules

import com.varlanv.wrasse.model.ChildBuffer
import com.varlanv.wrasse.model.WBufferedNodeRule
import com.varlanv.wrasse.model.WContext
import com.varlanv.wrasse.model.WNodeType
import com.varlanv.wrasse.model.WReporter
import com.varlanv.wrasse.model.WUninitializedRule
import com.varlanv.wrasse.model.WrasseRuleConfig

/**
 * An `if` whose own `then` branch's only content — braced or not, comments and whitespace aside —
 * is another nested `if` is reported (see [CollapseIfDecision]) at the nested `if`'s own span, when
 * neither carries an `else`. A stack of open `IF` frames (pushed on enter, popped on exit) each
 * carry a "my own nested candidate" slot: a `BLOCK` whose immediate parent is `THEN` sets its
 * enclosing frame's slot at its own exit once it confirms it holds nothing but one nested `if`
 * (matching the upstream rule this derives from's own allowed-surrounding-node-types check, which
 * — unlike this project's own [FunctionExpressionBodyRule] — tolerates a comment either side); an
 * unbraced nested `if` (`THEN`'s own direct child) sets the very same slot directly at its own
 * exit, since by then its own frame has already been read and popped. Every `IF`'s own `else`
 * presence is recorded at its own exit, keyed by offset, so a `BLOCK`'s lookup of its found nested
 * `if`'s own `else` fact is always already available (the nested `if` — a descendant — always
 * exits first). The enclosing `if`'s own `else` presence is read live off its still-open frame
 * (never through the map), since its own `else`, if any, is only walked *after* its `then` branch
 * closes — reading it through the same offset map at that point would see a stale "no else" value.
 */
class CollapseIfRule : WUninitializedRule {
    override val id: String = "collapse-if"

    override fun initRule(config: WrasseRuleConfig): WBufferedNodeRule {
        val ruleId = id
        return object : WBufferedNodeRule {
            override val id = ruleId
            override val config = config
            override val targetTypes = setOf(WNodeType.IF, WNodeType.BLOCK)

            private val ifFrames = mutableListOf<IfFrame>()
            private val hasElseByStart = mutableMapOf<Int, Boolean>()

            override fun enterNode(ctx: WContext, reporter: WReporter): Boolean {
                if (ctx.type == WNodeType.IF) {
                    ifFrames.add(IfFrame())
                    return true
                }
                return ctx.ancestors.peekType() == WNodeType.THEN
            }

            override fun exitNode(ctx: WContext, children: ChildBuffer, reporter: WReporter) {
                when (ctx.type) {
                    WNodeType.IF -> finalizeIf(ctx, children, reporter)
                    WNodeType.BLOCK -> finalizeBlock(ctx, children)
                    else -> {}
                }
            }

            private fun finalizeIf(ctx: WContext, children: ChildBuffer, reporter: WReporter) {
                val hasElse = children.hasChildOfType(WNodeType.KW_ELSE)
                hasElseByStart[ctx.startOffset] = hasElse
                val frame = if (ifFrames.isNotEmpty()) ifFrames.removeAt(ifFrames.size - 1) else IfFrame()

                if (frame.pendingNestedStart >= 0) {
                    val message = CollapseIfDecision.decide(hasElse, frame.pendingNestedHasElse)
                    if (message != null) {
                        reporter.report(ruleId, message, frame.pendingNestedStart, frame.pendingNestedEnd, this)
                    }
                }

                if (ctx.ancestors.peekType() == WNodeType.THEN) {
                    ifFrames.lastOrNull()?.let { outer ->
                        outer.pendingNestedStart = ctx.startOffset
                        outer.pendingNestedEnd = ctx.endOffset
                        outer.pendingNestedHasElse = hasElse
                    }
                }
            }

            private fun finalizeBlock(ctx: WContext, children: ChildBuffer) {
                if (ctx.ancestors.peekType() != WNodeType.THEN) return

                var nonTrivialCount = 0
                var nonTrivialIdx = -1
                for (i in 0 until children.size) {
                    if (children.type(i) in TRIVIAL_TYPES) continue
                    nonTrivialCount++
                    nonTrivialIdx = i
                }
                if (nonTrivialCount != 1 || children.type(nonTrivialIdx) != WNodeType.IF) return

                val nestedStart = children.startOffset(nonTrivialIdx)
                val nestedEnd = children.endOffset(nonTrivialIdx)
                val nestedHasElse = hasElseByStart[nestedStart] ?: return

                ifFrames.lastOrNull()?.let { outer ->
                    outer.pendingNestedStart = nestedStart
                    outer.pendingNestedEnd = nestedEnd
                    outer.pendingNestedHasElse = nestedHasElse
                }
            }
        }
    }

    private class IfFrame(var pendingNestedStart: Int = -1, var pendingNestedEnd: Int = -1, var pendingNestedHasElse: Boolean = false)

    private companion object {
        val TRIVIAL_TYPES =
        setOf(WNodeType.LBRACE, WNodeType.RBRACE, WNodeType.WHITE_SPACE, WNodeType.BLOCK_COMMENT, WNodeType.EOL_COMMENT)
    }
}
