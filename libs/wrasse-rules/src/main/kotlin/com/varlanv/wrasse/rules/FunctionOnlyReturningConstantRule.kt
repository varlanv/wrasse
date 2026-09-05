package com.varlanv.wrasse.rules

import com.varlanv.wrasse.model.ChildBuffer
import com.varlanv.wrasse.model.WBufferedNodeRule
import com.varlanv.wrasse.model.WContext
import com.varlanv.wrasse.model.WNodeStack
import com.varlanv.wrasse.model.WNodeType
import com.varlanv.wrasse.model.WReporter
import com.varlanv.wrasse.model.WUninitializedRule
import com.varlanv.wrasse.model.WrasseRuleConfig
import com.varlanv.wrasse.model.isWhitespaceOrComment

/**
 * A function whose body is exactly `= <literal>` or `{ return <literal> }` — a bare numeric,
 * character, boolean, or non-interpolated string constant, nothing else — is reported (see
 * [FunctionOnlyReturningConstantDecision]) at its own name's span.
 *
 * `CLASS` frames track whether they are an interface (a leaf-level fact, always known before any
 * member is visited); `FUN` frames track their own `override`/`open`/`actual` modifiers the same
 * way. A function's block body is inspected via its `RETURN` child's own completed verdict,
 * looked up by exact offset against the block's single significant child — the same
 * completed-frame-by-offset idiom used elsewhere in this batch — so a nested/local function's own
 * unrelated `return`s never leak into an enclosing function's verdict.
 */
class FunctionOnlyReturningConstantRule : WUninitializedRule {
    override val id: String = "function-only-returning-constant"

    override fun initRule(config: WrasseRuleConfig): WBufferedNodeRule {
        val ruleId = id
        return object : WBufferedNodeRule {
            override val id = ruleId
            override val config = config
            override val targetTypes = setOf(WNodeType.CLASS, WNodeType.FUN, WNodeType.BLOCK, WNodeType.RETURN)

            private val pendingClasses = mutableListOf<PendingClass>()
            private val pendingFuns = mutableListOf<PendingFun>()
            private val completedReturns = mutableListOf<CompletedSpan>()
            private val completedBlocks = mutableListOf<CompletedSpan>()

            override fun enterNode(ctx: WContext, reporter: WReporter): Boolean {
                when (ctx.type) {
                    WNodeType.CLASS -> pendingClasses.add(PendingClass())
                    WNodeType.FUN -> pendingFuns.add(PendingFun())
                    WNodeType.BLOCK -> if (ctx.ancestors.peekType() != WNodeType.FUN) return false
                    else -> {}
                }
                return true
            }

            override fun onChildLeaf(ctx: WContext, reporter: WReporter) {
                val ancestors = ctx.ancestors
                when (ctx.type) {
                    WNodeType.KW_INTERFACE ->
                        if (ancestors.peekType() == WNodeType.CLASS) {
                            pendingClasses.lastOrNull()?.isInterface = true
                        }

                    WNodeType.KW_OVERRIDE, WNodeType.KW_OPEN, WNodeType.KW_ACTUAL ->
                        if (inOwnModifierList(ancestors, WNodeType.FUN)) {
                            val pending = pendingFuns.lastOrNull() ?: return
                            when (ctx.type) {
                                WNodeType.KW_OVERRIDE -> pending.hasOverride = true
                                WNodeType.KW_OPEN -> pending.hasOpen = true
                                WNodeType.KW_ACTUAL -> pending.hasActual = true
                                else -> {}
                            }
                        }

                    WNodeType.IDENTIFIER -> {
                        val pending = pendingFuns.lastOrNull()
                        if (pending != null && ancestors.peekType() == WNodeType.FUN && pending.nameStart < 0) {
                            pending.nameStart = ctx.startOffset
                            pending.nameEnd = ctx.endOffset
                            pending.functionName = IdentifierCasing.unquote(ctx.leafText ?: "")
                        }
                    }

                    else -> {}
                }
            }

            private fun inOwnModifierList(ancestors: WNodeStack, ownerType: WNodeType): Boolean {
                if (ancestors.peekType() != WNodeType.MODIFIER_LIST || ancestors.size < 2) return false
                return ancestors.typeAt(ancestors.size - 2) == ownerType
            }

            override fun exitNode(
                ctx: WContext,
                children: ChildBuffer,
                reporter: WReporter,
            ) {
                when (ctx.type) {
                    WNodeType.CLASS -> pendingClasses.removeAt(pendingClasses.size - 1)
                    WNodeType.RETURN -> finalizeReturn(ctx, children)
                    WNodeType.BLOCK -> finalizeBlock(ctx, children)
                    WNodeType.FUN -> finalizeFun(ctx, children, reporter)
                    else -> {}
                }
            }

            private fun finalizeReturn(ctx: WContext, children: ChildBuffer) {
                val significant = ArrayList<Int>(children.size)
                for (i in 0 until children.size) {
                    val type = children.type(i)
                    if (type.isWhitespaceOrComment || type == WNodeType.KW_RETURN) continue
                    significant.add(i)
                }
                val isConstant =
                    significant.size ==
                        1 &&
                        ConstantLiteralCheck.isConstant(
                            children.type(significant[0]),
                            children.textSpan(significant[0], ctx.sourceText),
                        )
                completedReturns.add(CompletedSpan(ctx.startOffset, ctx.endOffset, isConstant))
            }

            private fun finalizeBlock(ctx: WContext, children: ChildBuffer) {
                val significant = ArrayList<Int>(children.size)
                for (i in 0 until children.size) {
                    val type = children.type(i)
                    if (type.isWhitespaceOrComment || type == WNodeType.LBRACE || type == WNodeType.RBRACE) continue
                    significant.add(i)
                }
                var isConstant = false
                if (significant.size == 1 && children.type(significant[0]) == WNodeType.RETURN) {
                    val idx = significant[0]
                    isConstant =
                        takeCompleted(
                            completedReturns,
                            children.startOffset(idx),
                            children.endOffset(idx),
                        )?.isConstant == true
                }
                completedBlocks.add(CompletedSpan(ctx.startOffset, ctx.endOffset, isConstant))
            }

            private fun finalizeFun(
                ctx: WContext,
                children: ChildBuffer,
                reporter: WReporter,
            ) {
                val pending = pendingFuns.removeAt(pendingFuns.size - 1)
                val inInterface = pendingClasses.lastOrNull()?.isInterface == true

                val eqIdx = children.firstChildOfType(WNodeType.EQ)
                val returnsConstant =
                    if (eqIdx >= 0) {
                        exprBodyIsConstant(ctx, children, eqIdx)
                    } else {
                        val blockIdx = children.firstChildOfType(WNodeType.BLOCK)
                        blockIdx >=
                            0 &&
                            takeCompleted(
                                completedBlocks,
                                children.startOffset(blockIdx),
                                children.endOffset(blockIdx),
                            )?.isConstant ==
                            true
                    }

                val name = pending.functionName.ifEmpty { "<anonymous>" }
                val message =
                    FunctionOnlyReturningConstantDecision.decide(
                        pending.hasOverride,
                        pending.hasOpen,
                        pending.hasActual,
                        inInterface,
                        returnsConstant,
                        name,
                    ) ?: return
                val reportStart = if (pending.nameStart >= 0) pending.nameStart else ctx.startOffset
                val reportEnd = if (pending.nameStart >= 0) pending.nameEnd else ctx.endOffset
                reporter.report(ruleId, message, reportStart, reportEnd, this)
            }

            private fun exprBodyIsConstant(
                ctx: WContext,
                children: ChildBuffer,
                eqIdx: Int,
            ): Boolean {
                var i = eqIdx + 1
                while (i < children.size && children.type(i).isWhitespaceOrComment) i++
                if (i >= children.size) return false
                return ConstantLiteralCheck.isConstant(children.type(i), children.textSpan(i, ctx.sourceText))
            }

            private fun takeCompleted(
                list: MutableList<CompletedSpan>,
                start: Int,
                end: Int,
            ): CompletedSpan? {
                val idx = list.indexOfFirst { it.start == start && it.end == end }
                if (idx < 0) return null
                return list.removeAt(idx)
            }
        }
    }

    private class PendingClass {
        var isInterface = false
    }

    private class PendingFun {
        var hasOverride = false
        var hasOpen = false
        var hasActual = false
        var nameStart = -1
        var nameEnd = -1
        var functionName = ""
    }

    private class CompletedSpan(
        val start: Int,
        val end: Int,
        val isConstant: Boolean,
    )
}
