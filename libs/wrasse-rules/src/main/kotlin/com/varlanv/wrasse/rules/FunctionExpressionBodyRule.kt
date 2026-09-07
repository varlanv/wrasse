package com.varlanv.wrasse.rules

import com.varlanv.wrasse.lang.WEdit
import com.varlanv.wrasse.model.ChildBuffer
import com.varlanv.wrasse.model.ChildLeafHandler
import com.varlanv.wrasse.model.WBufferedNodeRule
import com.varlanv.wrasse.model.WContext
import com.varlanv.wrasse.model.WNodeType
import com.varlanv.wrasse.model.WReporter
import com.varlanv.wrasse.model.WUninitializedRule
import com.varlanv.wrasse.model.WrasseRuleConfig

private val TARGET_TYPES = setOf(WNodeType.BLOCK, WNodeType.FUN)

/**
 * A function's own `BLOCK` body whose only content, ignoring braces and whitespace, is a single
 * `return`/`throw` statement is reported (see [FunctionExpressionBodyDecision]) at the block's own
 * span. Only a `BLOCK` whose immediate parent is `FUN` is ever inspected (a lambda's or `when`
 * branch's own block is never a candidate); the report is issued at the owning `FUN`'s exit so the
 * fix can see the function's return type.
 *
 * Autofixed (see [FunctionExpressionBodyDecision.expressionText]) only when the function has an
 * explicit return type (a block body could otherwise be relying on an inferred `Unit`), the sole
 * statement is a `THROW` or a `RETURN` carrying a real, unlabeled expression, and the statement is
 * single-line or [WrasseRuleConfig.formatEnabled] is true — with format off, a multi-line
 * statement's continuation lines could not be re-indented safely.
 *
 * A candidate `BLOCK` (own parent `FUN`) pushes its own return-keyword counter on enter and pops
 * it on exit; a `return` keyword increments every counter currently on the stack, so a nested
 * named or anonymous function's own block counts only what lies inside its own span while a
 * return keyword still adds to every enclosing candidate block's count too.
 */
class FunctionExpressionBodyRule : WUninitializedRule {
    override val id: String = "function-expression-body"
    override val canAutofix: Boolean = true

    override fun initRule(config: WrasseRuleConfig): WBufferedNodeRule {
        val ruleId = id
        return object : WBufferedNodeRule, ChildLeafHandler {
            override val id = ruleId
            override val config = config
            override val targetTypes = TARGET_TYPES

            private var lastReturnKeywordStart = -1
            private val returnKeywordCounts = mutableListOf<Int>()
            private val pendingBlocks = mutableMapOf<Int, PendingBlock>()

            override fun enterNode(ctx: WContext, reporter: WReporter): Boolean {
                if (ctx.type != WNodeType.BLOCK) return true
                if (ctx.ancestors.peekType() != WNodeType.FUN) return false
                returnKeywordCounts.add(0)
                return true
            }

            override fun onChildLeaf(ctx: WContext, reporter: WReporter) {
                if (ctx.type == WNodeType.KW_RETURN && ctx.startOffset != lastReturnKeywordStart) {
                    lastReturnKeywordStart = ctx.startOffset
                    for (i in returnKeywordCounts.indices) returnKeywordCounts[i]++
                }
            }

            override fun exitNode(
                ctx: WContext,
                children: ChildBuffer,
                reporter: WReporter,
            ) {
                when (ctx.type) {
                    WNodeType.BLOCK -> finalizeBlock(ctx, children)
                    WNodeType.FUN -> finalizeFun(ctx, children, reporter)
                    else -> {}
                }
            }

            private fun finalizeBlock(ctx: WContext, children: ChildBuffer) {
                val returnKeywordCount = returnKeywordCounts.removeAt(returnKeywordCounts.size - 1)
                var soleIdx = -1
                var significantCount = 0
                for (i in 0 until children.size) {
                    val type = children.type(i)
                    if (type == WNodeType.LBRACE || type == WNodeType.RBRACE || type == WNodeType.WHITE_SPACE) continue
                    significantCount++
                    soleIdx = i
                }
                if (significantCount != 1) return

                val soleType = children.type(soleIdx)
                val message = FunctionExpressionBodyDecision.decide(soleType, returnKeywordCount) ?: return
                pendingBlocks[ctx.startOffset] =
                    PendingBlock(
                        message = message,
                        statementType = soleType,
                        statementStart = children.startOffset(soleIdx),
                        statementEnd = children.endOffset(soleIdx),
                    )
            }

            private fun finalizeFun(
                ctx: WContext,
                children: ChildBuffer,
                reporter: WReporter,
            ) {
                val blockIdx = children.firstChildOfType(WNodeType.BLOCK)
                if (blockIdx < 0) return
                val blockStart = children.startOffset(blockIdx)
                val pending = pendingBlocks.remove(blockStart) ?: return
                val blockEnd = children.endOffset(blockIdx)
                val edits = fixEdits(ctx, children, blockIdx, blockStart, blockEnd, pending)
                reporter.report(ruleId, pending.message, blockStart, blockEnd, this, edits = edits)
            }

            private fun fixEdits(
                ctx: WContext,
                children: ChildBuffer,
                blockIdx: Int,
                blockStart: Int,
                blockEnd: Int,
                pending: PendingBlock,
            ): List<WEdit> {
                if (returnTypeIndex(children, blockIdx) < 0) return emptyList()
                val source = ctx.sourceText
                val statementText = source.subSequence(pending.statementStart, pending.statementEnd)
                val expressionText = FunctionExpressionBodyDecision.expressionText(
                    pending.statementType,
                    statementText,
                ) ?: return emptyList()
                if (!config.formatEnabled && hasNewline(source, pending.statementStart, pending.statementEnd)) {
                    return emptyList()
                }
                val expressionStart = pending.statementStart + (statementText.length - expressionText.length)
                return listOf(WEdit(blockStart, expressionStart, "= "), WEdit(pending.statementEnd, blockEnd, ""))
            }

            private fun returnTypeIndex(children: ChildBuffer, blockIdx: Int): Int {
                val paramsIdx = children.firstChildOfType(WNodeType.VALUE_PARAMETER_LIST)
                if (paramsIdx < 0) return -1
                var i = paramsIdx + 1
                while (i < blockIdx && children.type(i) != WNodeType.COLON) i++
                while (i < blockIdx && children.type(i) != WNodeType.TYPE_REFERENCE) i++
                return if (i < blockIdx) i else -1
            }

            private fun hasNewline(
                source: CharSequence,
                start: Int,
                end: Int,
            ): Boolean {
                for (i in start until end) if (source[i] == '\n') return true
                return false
            }
        }
    }

    private class PendingBlock(
        val message: String,
        val statementType: WNodeType,
        val statementStart: Int,
        val statementEnd: Int,
    )
}
