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
 * A bare (unqualified — `x.print()` excluded), zero/one-argument, non-trailing-lambda `print()`/
 * `println()` call (see [DebugPrintDecision]) is reported at the whole call's own span, and so is
 * a `console.error()`/`console.info()`/`console.log()`/`console.warn()` call on a bare `console`
 * receiver (Kotlin/JS interop) — both purely textual/structural, no resolution. An unqualified
 * call is told apart from `someObj.print()` by its own position in the enclosing
 * `DOT_QUALIFIED_EXPRESSION` (index 0 is the receiver side, never the selector). The
 * argument-count cap is this batch's own resolution-free proxy for "probably the real stdlib
 * function, not a same-named user overload" — the same heuristic the upstream rule this derives
 * from already relies on. A `VALUE_ARGUMENT_LIST`'s own argument count is recorded, and
 * unconditionally consumed by its own enclosing `CALL_EXPRESSION` regardless of that call's own
 * callee name, so the pending map never grows beyond the file's current call-nesting depth.
 */
class DebugPrintRule : WUninitializedRule {
    override val id: String = "debug-print"

    override fun initRule(config: WrasseRuleConfig): WBufferedNodeRule {
        val ruleId = id
        return object : WBufferedNodeRule {
            override val id = ruleId
            override val config = config
            override val targetTypes = setOf(WNodeType.CALL_EXPRESSION, WNodeType.VALUE_ARGUMENT_LIST, WNodeType.DOT_QUALIFIED_EXPRESSION)

            private val argCounts = mutableMapOf<Long, Int>()

            override fun exitNode(ctx: WContext, children: ChildBuffer, reporter: WReporter) {
                when (ctx.type) {
                    WNodeType.VALUE_ARGUMENT_LIST -> recordArgCount(ctx, children)
                    WNodeType.CALL_EXPRESSION -> checkPrintCall(ctx, children, reporter)
                    WNodeType.DOT_QUALIFIED_EXPRESSION -> checkConsoleCall(ctx, children, reporter)
                    else -> {}
                }
            }

            private fun recordArgCount(ctx: WContext, children: ChildBuffer) {
                var count = 0
                for (i in 0 until children.size) if (children.type(i) == WNodeType.VALUE_ARGUMENT) count++
                argCounts[key(ctx.startOffset, ctx.endOffset)] = count
            }

            private fun checkPrintCall(ctx: WContext, children: ChildBuffer, reporter: WReporter) {
                val argListIdx = children.firstChildOfType(WNodeType.VALUE_ARGUMENT_LIST)
                val argCount =
                    if (argListIdx < 0) {
                        0
                    } else {
                        argCounts.remove(key(children.startOffset(argListIdx), children.endOffset(argListIdx))) ?: 0
                    }

                val nameIdx = children.firstChildOfType(WNodeType.REFERENCE_EXPRESSION)
                if (nameIdx < 0) return
                val name = children.textSpan(nameIdx, ctx.sourceText)
                if (!name.contentEquals("print") && !name.contentEquals("println")) return
                if (ctx.ancestors.peekType() == WNodeType.DOT_QUALIFIED_EXPRESSION && ctx.childIndex != 0) return
                if (children.hasChildOfType(WNodeType.LAMBDA_ARGUMENT)) return
                if (argCount > 1) return
                reporter.report(ruleId, DebugPrintDecision.message(name.toString()), ctx.startOffset, ctx.endOffset, this)
            }

            private fun checkConsoleCall(ctx: WContext, children: ChildBuffer, reporter: WReporter) {
                val significant = (0 until children.size).filter { !children.type(it).isWhitespaceOrComment }
                if (significant.size != 3) return
                val (receiverIdx, dotIdx, selectorIdx) = Triple(significant[0], significant[1], significant[2])
                if (children.type(dotIdx) != WNodeType.DOT) return
                if (!children.textSpan(receiverIdx, ctx.sourceText).contentEquals("console")) return
                val selectorText = children.textSpan(selectorIdx, ctx.sourceText)
                val callee =
                when {
                        WordBoundaryScan.startsWithWord(selectorText, "error") -> "error"
                        WordBoundaryScan.startsWithWord(selectorText, "info") -> "info"
                        WordBoundaryScan.startsWithWord(selectorText, "log") -> "log"
                        WordBoundaryScan.startsWithWord(selectorText, "warn") -> "warn"
                        else -> null
                    }
                    ?: return
                reporter.report(ruleId, DebugPrintDecision.message("console.$callee"), ctx.startOffset, ctx.endOffset, this)
            }

            private fun key(start: Int, end: Int): Long = (start.toLong() shl 32) or (end.toLong() and 0xFF_FFF_FFFL)
        }
    }
}
