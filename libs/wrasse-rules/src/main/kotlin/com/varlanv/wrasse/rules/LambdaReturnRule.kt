package com.varlanv.wrasse.rules

import com.varlanv.wrasse.model.ChildBuffer
import com.varlanv.wrasse.model.WBufferedNodeRule
import com.varlanv.wrasse.model.WContext
import com.varlanv.wrasse.model.WNodeType
import com.varlanv.wrasse.model.WReporter
import com.varlanv.wrasse.model.WUninitializedRule
import com.varlanv.wrasse.model.WrasseRuleConfig
import com.varlanv.wrasse.model.isWhitespaceOrComment

private val TARGET_TYPES = setOf(WNodeType.RETURN, WNodeType.BLOCK)

/**
 * A lambda's own `BLOCK` whose last non-whitespace statement is a labeled `return@label` carrying
 * a value, where `label` names that same immediately-enclosing lambda (not some further-out
 * scope), is reported (see [LambdaReturnDecision]) at the `RETURN`'s own span: the label is
 * redundant since the value is already this lambda's own last expression. A `return@label` naming
 * an outer lambda (a genuine non-local exit, e.g. `.let { return@lazy it }` inside `by lazy { }`)
 * is never a candidate — removing that label would change which scope the return actually exits.
 * The label a lambda answers to is resolved from its own immediate ancestry: an explicit
 * `label@ { }` wrapper ([WNodeType.LABELED_EXPRESSION]), or, once any [WNodeType.VALUE_ARGUMENT]/
 * [WNodeType.VALUE_ARGUMENT_LIST] wrapping is skipped through, the callee name of the
 * [WNodeType.CALL_EXPRESSION] this lambda is passed to (as a trailing [WNodeType.LAMBDA_ARGUMENT]
 * or a plain argument) — the same callee-name-as-implicit-label idea [CustomLabelRule] already
 * uses, narrowed here to the single governing call/label rather than any enclosing one. An
 * unlabeled `return` (a non-local return with no `LABEL_QUALIFIER` at all) is never a candidate.
 * Each `RETURN`'s own facts are recorded at its own exit, keyed by its own offsets, and consumed
 * by the enclosing `BLOCK`'s exit — whether or not that block turns out to be a lambda's own body
 * — the same offset-correlation shape [KdocEngine] already establishes for parameter/constructor
 * facts, with the unconditional per-block sweep keeping the pending list bounded by nesting depth
 * rather than the file's total return count.
 */
class LambdaReturnRule : WUninitializedRule {
    override val id: String = "lambda-return"

    override fun initRule(config: WrasseRuleConfig): WBufferedNodeRule {
        val ruleId = id
        return object : WBufferedNodeRule {
            override val id = ruleId
            override val config = config
            override val targetTypes = TARGET_TYPES

            private val completedReturns = mutableListOf<CompletedReturn>()

            override fun exitNode(
                ctx: WContext,
                children: ChildBuffer,
                reporter: WReporter,
            ) {
                when (ctx.type) {
                    WNodeType.RETURN -> recordReturn(ctx, children)
                    WNodeType.BLOCK -> checkBlock(ctx, children, reporter)
                    else -> {}
                }
            }

            private fun recordReturn(ctx: WContext, children: ChildBuffer) {
                val labelIdx = children.firstChildOfType(WNodeType.LABEL_QUALIFIER)
                val hasValue = labelIdx >= 0 &&
                    (labelIdx + 1 until children.size).any { !children.type(it).isWhitespaceOrComment }
                val labelName =
                    if (hasValue) children.textSpan(labelIdx, ctx.sourceText).toString().removePrefix("@") else null
                completedReturns.add(CompletedReturn(ctx.startOffset, ctx.endOffset, labelName))
            }

            private fun checkBlock(
                ctx: WContext,
                children: ChildBuffer,
                reporter: WReporter,
            ) {
                val lastIdx = (children.size - 1 downTo 0).firstOrNull { !children.type(it).isWhitespaceOrComment }
                if (lastIdx != null && children.type(lastIdx) == WNodeType.RETURN) {
                    val start = children.startOffset(lastIdx)
                    val end = children.endOffset(lastIdx)
                    val idx = completedReturns.indexOfFirst { it.start == start && it.end == end }
                    if (idx >= 0) {
                        val record = completedReturns.removeAt(idx)
                        if (ctx.ancestors.peekType() == WNodeType.FUNCTION_LITERAL && record.labelName != null) {
                            val matchesOwnLambda = record.labelName == ownLambdaLabel(ctx)
                            val message = LambdaReturnDecision.decide(matchesOwnLambda)
                            if (message != null) reporter.report(ruleId, message, start, end, this)
                        }
                    }
                }
                completedReturns.removeAll { it.start >= ctx.startOffset && it.end <= ctx.endOffset }
            }

            private fun ownLambdaLabel(ctx: WContext): String? {
                val ancestors = ctx.ancestors
                if (ancestors.size < 2 || ancestors.typeAt(ancestors.size - 2) != WNodeType.LAMBDA_EXPRESSION) {
                    return null
                }
                var i = ancestors.size - 3
                while (i >= 0 &&
                    (ancestors.typeAt(
                        i,
                    ) == WNodeType.VALUE_ARGUMENT || ancestors.typeAt(i) == WNodeType.VALUE_ARGUMENT_LIST)
                ) {
                    i--
                }
                if (i < 0) return null
                return when (ancestors.typeAt(i)) {
                    WNodeType.LABELED_EXPRESSION -> explicitLabel(
                        ctx.sourceText,
                        ancestors.startOffsetAt(i),
                        ancestors.endOffsetAt(i),
                    )
                    WNodeType.LAMBDA_ARGUMENT -> if (i - 1 < 0 ||
                        ancestors.typeAt(i - 1) != WNodeType.CALL_EXPRESSION) {
                        null
                    } else {
                        calleeName(ctx.sourceText, ancestors.startOffsetAt(i - 1))
                    }
                    WNodeType.CALL_EXPRESSION -> calleeName(ctx.sourceText, ancestors.startOffsetAt(i))
                    else -> null
                }
            }

            private fun explicitLabel(
                sourceText: CharSequence,
                start: Int,
                end: Int,
            ): String? {
                var i = start
                while (i < end && (sourceText[i].isLetterOrDigit() || sourceText[i] == '_')) i++
                if (i >= end || sourceText[i] != '@') return null
                return sourceText.subSequence(start, i).toString()
            }

            private fun calleeName(sourceText: CharSequence, start: Int): String {
                var i = start
                while (i < sourceText.length && (sourceText[i].isLetterOrDigit() || sourceText[i] == '_')) i++
                return sourceText.subSequence(start, i).toString()
            }
        }
    }

    private class CompletedReturn(
        val start: Int,
        val end: Int,
        val labelName: String?,
    )
}
