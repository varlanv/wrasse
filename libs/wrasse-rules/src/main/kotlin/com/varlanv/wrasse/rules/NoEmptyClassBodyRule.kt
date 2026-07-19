package com.varlanv.wrasse.rules

import com.varlanv.wrasse.model.ChildBuffer
import com.varlanv.wrasse.model.WBufferedNodeRule
import com.varlanv.wrasse.model.WContext
import com.varlanv.wrasse.model.WNodeType
import com.varlanv.wrasse.model.WReporter
import com.varlanv.wrasse.model.WUninitializedRule
import com.varlanv.wrasse.model.WrasseRuleConfig

/**
 * A class/interface/object/enum/nested-class body containing nothing but whitespace is reported
 * and, where deleting it stays compile-legal, autofixed to remove it entirely.
 *
 * Companion object bodies are exempt entirely (not reported, not fixed) — matching upstream
 * ktlint's own exemption rather than going further just because deletion is provably safe there.
 *
 * Bails to report-only for an anonymous object expression's body (`object : Foo {}`, `object {}`)
 * — unlike every other empty-body shape, kotlinc's grammar requires that body syntactically; an
 * anonymous object with its braces removed is a syntax error, not merely restyled.
 */
class NoEmptyClassBodyRule : WUninitializedRule {
    override val id: String = "no-empty-class-body"

    override fun initRule(config: WrasseRuleConfig): WBufferedNodeRule {
        val ruleId = id
        return object : WBufferedNodeRule {
            override val id = ruleId
            override val config = config
            override val targetTypes = setOf(WNodeType.CLASS_BODY, WNodeType.OBJECT_DECLARATION)

            private val companionStack = mutableListOf<Boolean>()

            override fun enterNode(ctx: WContext, reporter: WReporter): Boolean {
                if (ctx.type == WNodeType.OBJECT_DECLARATION) {
                    companionStack.add(false)
                }
                return true
            }

            override fun onChildLeaf(ctx: WContext, reporter: WReporter) {
                if (ctx.type == WNodeType.KW_COMPANION && companionStack.isNotEmpty()) {
                    companionStack[companionStack.size - 1] = true
                }
            }

            override fun exitNode(ctx: WContext, children: ChildBuffer, reporter: WReporter) {
                if (ctx.type == WNodeType.OBJECT_DECLARATION) {
                    companionStack.removeAt(companionStack.size - 1)
                    return
                }

                if (isCompanionObjectBody(ctx)) return
                if (!isEmptyBody(children)) return

                val edits =
                    if (isAnonymousObjectBody(ctx)) {
                        emptyList()
                    } else {
                        listOf(EmptyClassBodyDeletionSpan.compute(ctx.sourceText, ctx.startOffset, ctx.endOffset))
                    }

                reporter.report(
                    ruleId, "Empty class body",
                    ctx.startOffset, ctx.endOffset, this,
                    edits = edits
                )
            }

            private fun isEmptyBody(children: ChildBuffer): Boolean {
                for (i in 0 until children.size) {
                    when (children.type(i)) {
                        WNodeType.LBRACE, WNodeType.RBRACE, WNodeType.WHITE_SPACE -> {}
                        else -> return false
                    }
                }
                return true
            }

            private fun isCompanionObjectBody(ctx: WContext): Boolean =
                ctx.ancestors.peekType() == WNodeType.OBJECT_DECLARATION &&
                    companionStack.isNotEmpty() && companionStack.last()

            private fun isAnonymousObjectBody(ctx: WContext): Boolean {
                val ancestors = ctx.ancestors
                if (ancestors.size < 2) return false
                return ancestors.peekType() == WNodeType.OBJECT_DECLARATION &&
                    ancestors.typeAt(ancestors.size - 2) == WNodeType.OBJECT_LITERAL
            }
        }
    }
}
