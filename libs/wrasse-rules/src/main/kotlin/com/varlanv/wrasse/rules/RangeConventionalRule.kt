package com.varlanv.wrasse.rules

import com.varlanv.wrasse.model.ChildBuffer
import com.varlanv.wrasse.model.WBufferedNodeRule
import com.varlanv.wrasse.model.WContext
import com.varlanv.wrasse.model.WNodeType
import com.varlanv.wrasse.model.WReporter
import com.varlanv.wrasse.model.WUninitializedRule
import com.varlanv.wrasse.model.WrasseRuleConfig
import com.varlanv.wrasse.model.isWhitespaceOrComment

private val TARGET_TYPES = setOf(
    WNodeType.DOT_QUALIFIED_EXPRESSION,
    WNodeType.CALL_EXPRESSION,
    WNodeType.VALUE_ARGUMENT_LIST,
    WNodeType.BINARY_EXPRESSION,
    WNodeType.PARENTHESIZED,
)

/**
 * Two conventional range rewrites, fused behind one id since both are the same "prefer the
 * operator form" preference: a qualified `rangeTo` call becomes `..` (see
 * [RangeConventionalDecision.decideRangeToCall]), and a `..` range whose upper bound is a `- 1`
 * subtraction becomes `until` (see [RangeConventionalDecision.decideUntil]).
 *
 * Both matches are purely syntactic — no resolution — so a user-defined `rangeTo` overload with a
 * single argument is rewritten the same as the standard library's; this rule only ever touches
 * calls actually spelled `rangeTo` with exactly one argument, and ranges whose upper bound is
 * exactly `<expr> - 1` (or `<expr> - 1` wrapped in one layer of parentheses).
 */
class RangeConventionalRule : WUninitializedRule {
    override val id: String = "range-conventional"
    override val canAutofix: Boolean = true

    override fun initRule(config: WrasseRuleConfig): WBufferedNodeRule {
        val ruleId = id
        return object : WBufferedNodeRule {
            override val id = ruleId
            override val config = config
            override val targetTypes = TARGET_TYPES

            private val singleArgumentLists = mutableMapOf<Int, ArgSpan>()
            private val rangeToCalls = mutableMapOf<Int, ArgSpan>()
            private val minusOneExpressions = mutableMapOf<Int, MinusOneMatch>()
            private val parenUnwraps = mutableMapOf<Int, ParenUnwrap>()

            override fun exitNode(
                ctx: WContext,
                children: ChildBuffer,
                reporter: WReporter,
            ) {
                when (ctx.type) {
                    WNodeType.VALUE_ARGUMENT_LIST -> recordValueArgumentList(ctx, children)
                    WNodeType.CALL_EXPRESSION -> recordCallExpression(ctx, children)
                    WNodeType.DOT_QUALIFIED_EXPRESSION -> finalizeRangeToCall(ctx, children, reporter)
                    WNodeType.PARENTHESIZED -> recordParenthesized(ctx, children)
                    WNodeType.BINARY_EXPRESSION -> handleBinaryExpression(ctx, children, reporter)
                    else -> {}
                }
            }

            private fun recordValueArgumentList(ctx: WContext, children: ChildBuffer) {
                var argIdx = -1
                var argCount = 0
                var hasComment = false
                for (i in 0 until children.size) {
                    val type = children.type(i)
                    if (type == WNodeType.VALUE_ARGUMENT) {
                        argCount++
                        argIdx = i
                    } else if (type.isWhitespaceOrComment && type != WNodeType.WHITE_SPACE) {
                        hasComment = true
                    }
                }
                if (argCount != 1) return
                singleArgumentLists[ctx.startOffset] =
                    ArgSpan(children.startOffset(argIdx), children.endOffset(argIdx), hasComment)
            }

            private fun recordCallExpression(ctx: WContext, children: ChildBuffer) {
                if (ctx.ancestors.peekType() != WNodeType.DOT_QUALIFIED_EXPRESSION) return
                val calleeIdx = children.firstChildOfType(WNodeType.REFERENCE_EXPRESSION)
                if (calleeIdx < 0 || !children.textSpan(calleeIdx, ctx.sourceText).contentEquals("rangeTo")) return
                val argsIdx = children.firstChildOfType(WNodeType.VALUE_ARGUMENT_LIST)
                if (argsIdx < 0) return
                val match = singleArgumentLists[children.startOffset(argsIdx)] ?: return
                var hasComment = match.hasComment
                for (i in 0 until children.size) {
                    val type = children.type(i)
                    if (type.isWhitespaceOrComment && type != WNodeType.WHITE_SPACE) hasComment = true
                }
                rangeToCalls[ctx.startOffset] = ArgSpan(match.start, match.end, hasComment)
            }

            private fun finalizeRangeToCall(
                ctx: WContext,
                children: ChildBuffer,
                reporter: WReporter,
            ) {
                val selectorIdx = children.firstChildOfType(WNodeType.CALL_EXPRESSION)
                if (selectorIdx < 0) return
                val match = rangeToCalls[children.startOffset(selectorIdx)] ?: return
                var hasComment = match.hasComment
                for (i in 0 until children.size) {
                    val type = children.type(i)
                    if (type.isWhitespaceOrComment && type != WNodeType.WHITE_SPACE) hasComment = true
                }
                val receiverText = children.textSpan(0, ctx.sourceText).toString()
                val argumentText = ctx.sourceText.subSequence(match.start, match.end).toString()
                val verdict = RangeConventionalDecision.decideRangeToCall(
                    ctx.startOffset,
                    ctx.endOffset,
                    receiverText,
                    argumentText,
                    hasComment,
                )
                reporter.report(
                    ruleId,
                    verdict.message,
                    verdict.reportStart,
                    verdict.reportEnd,
                    this,
                    edits = verdict.edits,
                )
            }

            private fun recordParenthesized(ctx: WContext, children: ChildBuffer) {
                var innerIdx = -1
                var hasComment = false
                for (i in 0 until children.size) {
                    val type = children.type(i)
                    if (type == WNodeType.LPAR || type == WNodeType.RPAR) continue
                    if (type.isWhitespaceOrComment) {
                        if (type != WNodeType.WHITE_SPACE) hasComment = true
                        continue
                    }
                    if (innerIdx >= 0) return
                    innerIdx = i
                }
                if (innerIdx < 0) return
                parenUnwraps[ctx.startOffset] = ParenUnwrap(children.startOffset(innerIdx), hasComment)
            }

            private fun handleBinaryExpression(
                ctx: WContext,
                children: ChildBuffer,
                reporter: WReporter,
            ) {
                val sig = significantIndices(children)
                if (sig.size != 3) return
                val leftIdx = sig[0]
                val opIdx = sig[1]
                val rightIdx = sig[2]
                if (children.type(opIdx) != WNodeType.OPERATION_REFERENCE) return
                var hasComment = false
                for (i in 0 until children.size) {
                    val type = children.type(i)
                    if (type.isWhitespaceOrComment && type != WNodeType.WHITE_SPACE) hasComment = true
                }
                val opText = children.textSpan(opIdx, ctx.sourceText)
                if (opText.contentEquals("-")) {
                    recordMinusOne(ctx, children, leftIdx, rightIdx, hasComment)
                    return
                }
                if (opText.contentEquals("..")) {
                    finalizeUntil(ctx, children, opIdx, rightIdx, hasComment, reporter)
                }
            }

            private fun recordMinusOne(
                ctx: WContext,
                children: ChildBuffer,
                leftIdx: Int,
                rightIdx: Int,
                hasComment: Boolean,
            ) {
                if (children.type(
                    rightIdx,
                ) != WNodeType.INTEGER_CONSTANT || !children.textSpan(rightIdx, ctx.sourceText).contentEquals("1")) {
                    return
                }
                minusOneExpressions[ctx.startOffset] =
                    MinusOneMatch(ctx.endOffset, children.startOffset(leftIdx), children.endOffset(leftIdx), hasComment)
            }

            private fun finalizeUntil(
                ctx: WContext,
                children: ChildBuffer,
                opIdx: Int,
                rightIdx: Int,
                hasComment: Boolean,
                reporter: WReporter,
            ) {
                val rightStart = children.startOffset(rightIdx)
                val rightType = children.type(rightIdx)
                val parenUnwrap = if (rightType == WNodeType.PARENTHESIZED) parenUnwraps[rightStart] else null
                val candidateStart = when (rightType) {
                    WNodeType.BINARY_EXPRESSION -> rightStart
                    WNodeType.PARENTHESIZED -> parenUnwrap?.innerStart ?: return
                    else -> return
                }
                val minusOne = minusOneExpressions[candidateStart] ?: return

                val hasLeadingSpace = opIdx > 0 && children.type(opIdx - 1) == WNodeType.WHITE_SPACE
                val hasTrailingSpace = opIdx < children.size - 1 && children.type(opIdx + 1) == WNodeType.WHITE_SPACE
                val leftOperandText = ctx.sourceText.subSequence(minusOne.leftStart, minusOne.leftEnd).toString()

                val verdict = RangeConventionalDecision.decideUntil(
                    rangeStart = ctx.startOffset,
                    rangeEnd = ctx.endOffset,
                    operatorStart = children.startOffset(opIdx),
                    operatorEnd = children.endOffset(opIdx),
                    hasLeadingSpace = hasLeadingSpace,
                    hasTrailingSpace = hasTrailingSpace,
                    minusOneStart = candidateStart,
                    minusOneEnd = minusOne.wholeEnd,
                    leftOperandText = leftOperandText,
                    hasComment = hasComment || minusOne.hasComment || (parenUnwrap?.hasComment == true),
                )
                reporter.report(
                    ruleId,
                    verdict.message,
                    verdict.reportStart,
                    verdict.reportEnd,
                    this,
                    edits = verdict.edits,
                )
            }

            private fun significantIndices(children: ChildBuffer): List<Int> {
                val result = ArrayList<Int>(children.size)
                for (i in 0 until children.size) if (!children.type(i).isWhitespaceOrComment) result.add(i)
                return result
            }
        }
    }

    private class ArgSpan(
        val start: Int,
        val end: Int,
        val hasComment: Boolean,
    )

    private class MinusOneMatch(
        val wholeEnd: Int,
        val leftStart: Int,
        val leftEnd: Int,
        val hasComment: Boolean,
    )

    private class ParenUnwrap(val innerStart: Int, val hasComment: Boolean)
}
