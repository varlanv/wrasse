package com.varlanv.wrasse.rules

import com.varlanv.wrasse.model.ChildLeafHandler
import com.varlanv.wrasse.model.WContext
import com.varlanv.wrasse.model.WNodeRule
import com.varlanv.wrasse.model.WNodeType
import com.varlanv.wrasse.model.WReporter
import com.varlanv.wrasse.model.WUninitializedRule
import com.varlanv.wrasse.model.WrasseRuleConfig

private val TARGET_TYPES = setOf(WNodeType.FUN, WNodeType.THROW)

/**
 * A function named `equals`, `finalize`, `hashCode`, or `toString` whose own body contains a
 * `throw` anywhere — including inside a nested local function or lambda, mirroring the unbounded
 * subtree scan the upstream rule this derives from performs — is reported (see
 * [ExceptionRaisedInUnexpectedLocationDecision]) at the function's own name.
 *
 * Every currently open `FUN` frame is marked on any descendant `THROW`, the same
 * innermost-frame-agnostic idiom `unused-parameter` already established for a whole-subtree,
 * no-lexical-boundary scan.
 */
class ExceptionRaisedInUnexpectedLocationRule : WUninitializedRule {
    override val id: String = "exception-raised-in-unexpected-location"

    override fun initRule(config: WrasseRuleConfig): WNodeRule {
        val ruleId = id
        return object : WNodeRule, ChildLeafHandler {
            override val id = ruleId
            override val config = config
            override val targetTypes = TARGET_TYPES

            private val pendingFuns = mutableListOf<PendingFun>()

            override fun enterNode(ctx: WContext, reporter: WReporter): Boolean {
                when (ctx.type) {
                    WNodeType.FUN -> {
                        pendingFuns.add(PendingFun())
                        return true
                    }

                    WNodeType.THROW -> {
                        for (pending in pendingFuns) pending.hasThrow = true
                        return false
                    }

                    else -> return false
                }
            }

            override fun onChildLeaf(ctx: WContext, reporter: WReporter) {
                if (ctx.type != WNodeType.IDENTIFIER || ctx.ancestors.peekType() != WNodeType.FUN) return
                val pending = pendingFuns.lastOrNull() ?: return
                if (pending.nameStart < 0) {
                    pending.nameStart = ctx.startOffset
                    pending.nameEnd = ctx.endOffset
                    pending.name = IdentifierCasing.unquote(ctx.leafString() ?: "")
                }
            }

            override fun exitNode(ctx: WContext, reporter: WReporter) {
                if (ctx.type != WNodeType.FUN) return
                val pending = pendingFuns.removeAt(pendingFuns.size - 1)
                val message = ExceptionRaisedInUnexpectedLocationDecision.decide(pending.name ?: "", pending.hasThrow)
                    ?: return
                val start = if (pending.nameStart >= 0) pending.nameStart else ctx.startOffset
                val end = if (pending.nameStart >= 0) pending.nameEnd else ctx.endOffset
                reporter.report(ruleId, message, start, end, this)
            }
        }
    }

    private class PendingFun {
        var name: String? = null
        var nameStart = -1
        var nameEnd = -1
        var hasThrow = false
    }
}
