package com.varlanv.wrasse.rules

import com.varlanv.wrasse.model.ChildLeafHandler
import com.varlanv.wrasse.model.WContext
import com.varlanv.wrasse.model.WNodeRule
import com.varlanv.wrasse.model.WNodeType
import com.varlanv.wrasse.model.WReporter
import com.varlanv.wrasse.model.WUninitializedRule
import com.varlanv.wrasse.model.WrasseRuleConfig
import com.varlanv.wrasse.model.isWhitespaceOrComment

private val TARGET_TYPES = setOf(WNodeType.CATCH, WNodeType.VALUE_PARAMETER_LIST)

/**
 * A caught exception never referenced anywhere in its own catch body is reported (see
 * [SwallowedExceptionDecision]) at the catch parameter's own name. A plain-name reference
 * anywhere in the catch's subtree — `ancestors.peekType() == REFERENCE_EXPRESSION` — counts as a
 * use regardless of depth, mirroring the unbounded subtree scan the upstream rule this derives
 * from performs; a nested catch clause's own references are attributed only to that nested
 * clause (the innermost currently open frame), never leaking up to an enclosing catch. A catch
 * body that is nothing but whitespace/comments beyond the parameter list is never reported — that
 * shape is `empty-catch-block`'s to claim — tracked by the first non-brace, non-whitespace-or-
 * comment leaf offset at or after the parameter list's own end.
 */
class SwallowedExceptionRule : WUninitializedRule {
    override val id: String = "swallowed-exception"

    override fun initRule(config: WrasseRuleConfig): WNodeRule {
        val ruleId = id
        return object : WNodeRule, ChildLeafHandler {
            override val id = ruleId
            override val config = config
            override val targetTypes = TARGET_TYPES

            private val pendingCatches = mutableListOf<PendingCatch>()

            override fun enterNode(ctx: WContext, reporter: WReporter): Boolean {
                when (ctx.type) {
                    WNodeType.CATCH -> {
                        pendingCatches.add(PendingCatch())
                        return true
                    }

                    WNodeType.VALUE_PARAMETER_LIST -> {
                        if (ctx.ancestors.peekType() == WNodeType.CATCH && pendingCatches.isNotEmpty()) {
                            val pending = pendingCatches.last()
                            pending.paramListEnd = ctx.endOffset
                            val facts = CatchParameterText.parse(
                                ctx.sourceText.subSequence(ctx.startOffset, ctx.endOffset),
                            )
                            if (facts != null) {
                                pending.name = facts.name
                                pending.typeText = facts.typeText
                                pending.nameStart = ctx.startOffset + facts.nameStart
                                pending.nameEnd = ctx.startOffset + facts.nameEnd
                            }
                        }
                        return false
                    }

                    else -> return false
                }
            }

            override fun onChildLeaf(ctx: WContext, reporter: WReporter) {
                val pending = pendingCatches.lastOrNull() ?: return
                if (!pending.hasBodyContent &&
                    pending.paramListEnd >= 0 &&
                    ctx.startOffset >= pending.paramListEnd &&
                    ctx.type != WNodeType.LBRACE &&
                    ctx.type != WNodeType.RBRACE &&
                    !ctx.type.isWhitespaceOrComment
                ) {
                    pending.hasBodyContent = true
                }
                if (ctx.type != WNodeType.IDENTIFIER || ctx.ancestors.peekType() != WNodeType.REFERENCE_EXPRESSION) {
                    return
                }
                val name = pending.name ?: return
                val text = IdentifierCasing.unquote(ctx.leafString() ?: "")
                if (SwallowedExceptionDecision.isUsageText(text, name)) pending.used = true
            }

            override fun exitNode(ctx: WContext, reporter: WReporter) {
                if (ctx.type != WNodeType.CATCH) return
                val pending = pendingCatches.removeAt(pendingCatches.size - 1)
                val name = pending.name ?: return
                val message = SwallowedExceptionDecision.decide(
                    pending.typeText ?: "",
                    name,
                    pending.used,
                    pending.hasBodyContent,
                ) ?: return
                reporter.report(ruleId, message, pending.nameStart, pending.nameEnd, this)
            }
        }
    }

    private class PendingCatch {
        var name: String? = null
        var typeText: String? = null
        var nameStart = -1
        var nameEnd = -1
        var used = false
        var paramListEnd = -1
        var hasBodyContent = false
    }
}
