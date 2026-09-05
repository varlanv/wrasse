package com.varlanv.wrasse.rules

import com.varlanv.wrasse.model.ChildBuffer
import com.varlanv.wrasse.model.WBufferedNodeRule
import com.varlanv.wrasse.model.WContext
import com.varlanv.wrasse.model.WNodeType
import com.varlanv.wrasse.model.WReporter
import com.varlanv.wrasse.model.WUninitializedRule
import com.varlanv.wrasse.model.WrasseRuleConfig
import com.varlanv.wrasse.model.isWhitespaceOrComment

private val TARGET_TYPES = setOf(WNodeType.BINARY_EXPRESSION)

/**
 * A single-line, top-level `+` binary-expression chain containing a `+` step whose own left
 * operand is textually a string (see [StringConcatenationDecision]) is reported once, at that
 * step's own span. Every `BINARY_EXPRESSION` records, at its own exit, the first such step found
 * anywhere in its own subtree — either itself, or inherited from a `BINARY_EXPRESSION` operand's
 * own already-recorded finding, preferring its own match over an inherited one, then the left
 * operand's finding over the right's — so a multi-step chain (`"a" + "b" + c`) reports only the
 * innermost genuine string-literal start, matching the upstream rule this derives from's own
 * "first descendant found" selection for the common case. Only a chain that, as physically
 * written, spans a single source line is a candidate, and only the chain's own true top-level node
 * (no `BINARY_EXPRESSION` ancestor) ever triggers the report — both matching the upstream rule's
 * own two gates exactly.
 */
class StringConcatenationRule : WUninitializedRule {
    override val id: String = "string-concatenation"

    override fun initRule(config: WrasseRuleConfig): WBufferedNodeRule {
        val ruleId = id
        return object : WBufferedNodeRule {
            override val id = ruleId
            override val config = config
            override val targetTypes = TARGET_TYPES

            private val findings = mutableMapOf<Long, Finding>()

            override fun exitNode(
                ctx: WContext,
                children: ChildBuffer,
                reporter: WReporter,
            ) {
                val significant = ArrayList<Int>(children.size)
                for (i in 0 until children.size) if (!children.type(i).isWhitespaceOrComment) significant.add(i)
                if (significant.size != 3) return
                val (leftIdx, opIdx, rightIdx) = Triple(significant[0], significant[1], significant[2])

                val isPlus = children.type(opIdx) ==
                    WNodeType.OPERATION_REFERENCE &&
                    children.textSpan(opIdx, ctx.sourceText).contentEquals("+")
                var finding: Finding? = null
                if (isPlus &&
                    StringConcatenationDecision.isStringConcatenationStart(
                        leftType = children.type(leftIdx),
                        leftText = children.textSpan(leftIdx, ctx.sourceText),
                        rightType = children.type(rightIdx),
                    )
                ) {
                    finding = Finding(ctx.startOffset, ctx.endOffset)
                }
                if (finding == null) finding = inherited(children, leftIdx) ?: inherited(children, rightIdx)
                if (finding != null) findings[key(ctx.startOffset, ctx.endOffset)] = finding

                if (ctx.hasAncestor(WNodeType.BINARY_EXPRESSION)) return
                findings.remove(key(ctx.startOffset, ctx.endOffset))?.let { top ->
                    if (spansSingleLine(ctx.sourceText, ctx.startOffset, ctx.endOffset)) {
                        reporter.report(ruleId, StringConcatenationDecision.MESSAGE, top.start, top.end, this)
                    }
                }
            }

            private fun inherited(children: ChildBuffer, idx: Int): Finding? {
                if (children.type(idx) != WNodeType.BINARY_EXPRESSION) return null
                return findings.remove(key(children.startOffset(idx), children.endOffset(idx)))
            }

            private fun spansSingleLine(
                sourceText: CharSequence,
                start: Int,
                end: Int,
            ): Boolean {
                for (i in start until end) if (sourceText[i] == '\n') return false
                return true
            }

            private fun key(start: Int, end: Int): Long = (start.toLong() shl 32) or (end.toLong() and 0xFF_FFF_FFFL)
        }
    }

    private class Finding(val start: Int, val end: Int)
}
