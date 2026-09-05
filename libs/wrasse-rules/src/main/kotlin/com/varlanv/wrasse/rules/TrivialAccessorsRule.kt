package com.varlanv.wrasse.rules

import com.varlanv.wrasse.model.ChildBuffer
import com.varlanv.wrasse.model.ChildLeafHandler
import com.varlanv.wrasse.model.WBufferedNodeRule
import com.varlanv.wrasse.model.WContext
import com.varlanv.wrasse.model.WNodeType
import com.varlanv.wrasse.model.WReporter
import com.varlanv.wrasse.model.WUninitializedRule
import com.varlanv.wrasse.model.WrasseRuleConfig
import com.varlanv.wrasse.model.isWhitespaceOrComment

private val TARGET_TYPES = setOf(
    WNodeType.PROPERTY_ACCESSOR,
    WNodeType.BLOCK,
    WNodeType.RETURN,
    WNodeType.BINARY_EXPRESSION,
)

/**
 * A property accessor whose whole body does nothing but read or write the backing field is
 * removed, since Kotlin generates that exact same accessor by default.
 *
 * A trivial getter is either bare (`get`, no parameter list and no body at all — already
 * identical to no accessor), `get() = field`, or `get() { return field }`. A trivial setter is
 * `set(value) { field = value }` — the block's own single statement must assign the parameter to
 * `field` verbatim; a compound-assignment operator (`+=` and similar) or any transformation of the
 * parameter is never trivial. Any nesting deeper than these exact shapes (an extra statement, a
 * wrapped call, a differently-named reference) is out of scope entirely — not reported.
 *
 * Never autofixed — reported only — whenever the accessor carries its own annotation or
 * visibility modifier: either may carry real behavior (JVM interop, restricted visibility) that a
 * plain deletion would silently drop. `@Suppress` this rule's id on the property, or on the
 * containing declaration, for the narrow case where that's actually intended.
 */
class TrivialAccessorsRule : WUninitializedRule {
    override val id: String = "trivial-accessors"
    override val canAutofix: Boolean = true

    override fun initRule(config: WrasseRuleConfig): WBufferedNodeRule {
        val ruleId = id
        return object : WBufferedNodeRule, ChildLeafHandler {
            override val id = ruleId
            override val config = config
            override val targetTypes = TARGET_TYPES

            private val pendingAccessors = mutableListOf<PendingAccessor>()

            override fun enterNode(ctx: WContext, reporter: WReporter): Boolean {
                when (ctx.type) {
                    WNodeType.PROPERTY_ACCESSOR -> pendingAccessors.add(PendingAccessor())
                    WNodeType.BLOCK -> if (ctx.ancestors.peekType() != WNodeType.PROPERTY_ACCESSOR) return false
                    WNodeType.RETURN, WNodeType.BINARY_EXPRESSION -> if (!isAccessorBlockStatement(ctx)) return false
                    else -> {}
                }
                return true
            }

            private fun isAccessorBlockStatement(ctx: WContext): Boolean {
                val ancestors = ctx.ancestors
                return ancestors.size >=
                    2 &&
                    ancestors.peekType() ==
                    WNodeType.BLOCK &&
                    ancestors.typeAt(ancestors.size - 2) ==
                    WNodeType.PROPERTY_ACCESSOR
            }

            override fun onChildLeaf(ctx: WContext, reporter: WReporter) {
                val pending = pendingAccessors.lastOrNull() ?: return
                when {
                    ctx.type == WNodeType.KW_GET && pending.isGetter == null -> pending.isGetter = true
                    ctx.type == WNodeType.KW_SET && pending.isGetter == null -> pending.isGetter = false
                    ctx.type == WNodeType.IDENTIFIER &&
                        pending.paramName == null &&
                        ctx.ancestors.peekType() == WNodeType.VALUE_PARAMETER ->
                        pending.paramName = ctx.leafString()
                    else -> {}
                }
            }

            override fun exitNode(
                ctx: WContext,
                children: ChildBuffer,
                reporter: WReporter,
            ) {
                when (ctx.type) {
                    WNodeType.RETURN -> recordReturn(ctx, children)
                    WNodeType.BINARY_EXPRESSION -> recordAssignment(ctx, children)
                    WNodeType.BLOCK -> recordBlock(children)
                    WNodeType.PROPERTY_ACCESSOR -> finalizeAccessor(ctx, children, reporter)
                    else -> {}
                }
            }

            private fun recordReturn(ctx: WContext, children: ChildBuffer) {
                val pending = pendingAccessors.lastOrNull() ?: return
                if (pending.isGetter != true) return
                val idx = singleSignificant(
                    children,
                ) { it.isWhitespaceOrComment || it == WNodeType.KW_RETURN } ?: return
                if (children.type(
                    idx,
                ) == WNodeType.REFERENCE_EXPRESSION && children.textSpan(idx, ctx.sourceText).contentEquals("field")) {
                    pending.blockMatched = true
                }
            }

            private fun recordAssignment(ctx: WContext, children: ChildBuffer) {
                val pending = pendingAccessors.lastOrNull() ?: return
                if (pending.isGetter != false) return
                val paramName = pending.paramName ?: return
                val sig = significantIndices(children)
                if (sig.size != 3) return
                val leftIdx = sig[0]
                val opIdx = sig[1]
                val rightIdx = sig[2]
                if (children.type(leftIdx) !=
                    WNodeType.REFERENCE_EXPRESSION ||
                    !children.textSpan(leftIdx, ctx.sourceText).contentEquals("field")) {
                    return
                }
                if (children.type(
                    opIdx,
                ) != WNodeType.OPERATION_REFERENCE || !children.textSpan(opIdx, ctx.sourceText).contentEquals("=")) {
                    return
                }
                if (children.type(rightIdx) !=
                    WNodeType.REFERENCE_EXPRESSION ||
                    !children.textSpan(rightIdx, ctx.sourceText).contentEquals(paramName)
                ) {
                    return
                }
                pending.blockMatched = true
            }

            private fun recordBlock(children: ChildBuffer) {
                val pending = pendingAccessors.lastOrNull() ?: return
                val idx = singleSignificant(children) {
                    it.isWhitespaceOrComment || it == WNodeType.LBRACE || it == WNodeType.RBRACE
                }
                if (idx == null) pending.blockMatched = false
            }

            private fun finalizeAccessor(
                ctx: WContext,
                children: ChildBuffer,
                reporter: WReporter,
            ) {
                val pending = pendingAccessors.removeAt(pendingAccessors.size - 1)
                val hasModifierList = children.hasChildOfType(WNodeType.MODIFIER_LIST)
                val hasParameterList =
                    children.hasChildOfType(WNodeType.LPAR) || children.hasChildOfType(WNodeType.VALUE_PARAMETER_LIST)
                val isGetter = pending.isGetter == true

                val isBareTrivial = isGetter && !hasParameterList && !hasModifierList
                val isExprFieldTrivial = isGetter && matchesExprField(children, ctx.sourceText)
                val isTrivialBody = isBareTrivial || isExprFieldTrivial || pending.blockMatched

                val verdict = TrivialAccessorsDecision.decide(
                    isTrivialBody = isTrivialBody,
                    hasModifierList = hasModifierList,
                ) ?: return
                val edits = if (verdict.fixable) {
                    listOf(TrivialAccessorsDeletionSpan.compute(ctx.sourceText, ctx.startOffset, ctx.endOffset))
                } else {
                    emptyList()
                }
                reporter.report(
                    ruleId,
                    TrivialAccessorsDecision.MESSAGE,
                    ctx.startOffset,
                    ctx.endOffset,
                    this,
                    edits = edits,
                )
            }

            private fun matchesExprField(children: ChildBuffer, sourceText: CharSequence): Boolean {
                val eqIdx = children.firstChildOfType(WNodeType.EQ)
                if (eqIdx < 0) return false
                var i = eqIdx + 1
                while (i < children.size && children.type(i).isWhitespaceOrComment) i++
                if (i >= children.size) return false
                return children.type(
                    i,
                ) == WNodeType.REFERENCE_EXPRESSION && children.textSpan(i, sourceText).contentEquals("field")
            }

            private fun singleSignificant(children: ChildBuffer, ignore: (WNodeType) -> Boolean): Int? {
                var found = -1
                for (i in 0 until children.size) {
                    if (ignore(children.type(i))) continue
                    if (found >= 0) return null
                    found = i
                }
                return if (found >= 0) found else null
            }

            private fun significantIndices(children: ChildBuffer): List<Int> {
                val result = ArrayList<Int>(children.size)
                for (i in 0 until children.size) if (!children.type(i).isWhitespaceOrComment) result.add(i)
                return result
            }
        }
    }

    private class PendingAccessor {
        var isGetter: Boolean? = null
        var paramName: String? = null
        var blockMatched = false
    }
}
