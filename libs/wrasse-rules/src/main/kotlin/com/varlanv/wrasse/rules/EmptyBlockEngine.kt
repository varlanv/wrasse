package com.varlanv.wrasse.rules

import com.varlanv.wrasse.model.ChildBuffer
import com.varlanv.wrasse.model.WBufferedNodeRule
import com.varlanv.wrasse.model.WContext
import com.varlanv.wrasse.model.WFileRule
import com.varlanv.wrasse.model.WNodeStack
import com.varlanv.wrasse.model.WNodeType
import com.varlanv.wrasse.model.WReporter
import com.varlanv.wrasse.model.WRule
import com.varlanv.wrasse.model.WUninitializedRuleGroup
import com.varlanv.wrasse.model.WrasseRuleConfig

/**
 * Fuses nine empty-block ids into one decision-maker: every one of them is "this `BLOCK`'s own
 * direct children are nothing but braces and whitespace" ([EmptyBlockCheck]), routed to the right
 * id purely by the block's immediate parent (an `if`'s `THEN`/`ELSE`, a loop's `BODY`, a `TRY`'s
 * own body, a `FINALLY`, a `CLASS_INITIALIZER`, a `SECONDARY_CONSTRUCTOR`) — one buffered walk per
 * block instead of nine independent rules each re-deriving the same emptiness check. A block whose
 * parent is `FUN` is deliberately never routed here — see [EmptyFunctionBlockRule], which needs
 * the function's own modifiers and interface-membership, unreachable from a `BLOCK`'s own
 * ancestors without extra text-slicing this engine avoids.
 */
class EmptyBlockEngine : WUninitializedRuleGroup {
    override val ids: Set<String> = setOf(
        IF_ID,
        ELSE_ID,
        FOR_ID,
        WHILE_ID,
        DO_WHILE_ID,
        FINALLY_ID,
        TRY_ID,
        INIT_ID,
        SECONDARY_CONSTRUCTOR_ID,
    )

    override fun initGroup(configs: Map<String, WrasseRuleConfig>): WRule {
        val rules = configs.mapValues { (id, config) -> ReportFacade(id, config) }

        return object : WBufferedNodeRule {
            override val id = ENGINE_ID
            override val config = configs.values.first()
            override val targetTypes = setOf(WNodeType.BLOCK)

            override fun exitNode(
                ctx: WContext,
                children: ChildBuffer,
                reporter: WReporter,
            ) {
                if (ctx.ancestors.isEmpty) return
                val (targetId, message) = routeFor(ctx) ?: return
                val rule = rules[targetId] ?: return
                if (!EmptyBlockCheck.isEmpty(children)) return
                reporter.report(targetId, message, ctx.startOffset, ctx.endOffset, rule)
            }

            private fun routeFor(ctx: WContext): Pair<String, String>? {
                val ancestors = ctx.ancestors
                return when (ancestors.peekType()) {
                    WNodeType.THEN -> IF_ID to IF_MESSAGE
                    WNodeType.ELSE -> ELSE_ID to ELSE_MESSAGE
                    WNodeType.FINALLY -> FINALLY_ID to FINALLY_MESSAGE
                    WNodeType.TRY -> TRY_ID to TRY_MESSAGE
                    WNodeType.CLASS_INITIALIZER -> INIT_ID to INIT_MESSAGE
                    WNodeType.SECONDARY_CONSTRUCTOR -> SECONDARY_CONSTRUCTOR_ID to SECONDARY_CONSTRUCTOR_MESSAGE
                    WNodeType.BODY -> routeLoopBody(ancestors)
                    else -> null
                }
            }

            private fun routeLoopBody(ancestors: WNodeStack): Pair<String, String>? {
                if (ancestors.size < 2) return null
                return when (ancestors.typeAt(ancestors.size - 2)) {
                    WNodeType.FOR -> FOR_ID to FOR_MESSAGE
                    WNodeType.WHILE -> WHILE_ID to WHILE_MESSAGE
                    WNodeType.DO_WHILE -> DO_WHILE_ID to DO_WHILE_MESSAGE
                    else -> null
                }
            }
        }
    }

    private class ReportFacade(override val id: String, override val config: WrasseRuleConfig) : WFileRule {
        override fun visit(ctx: WContext, reporter: WReporter) {}
    }

    private companion object {
        const val ENGINE_ID = "empty-block-engine"
        const val IF_ID = "empty-if-block"
        const val ELSE_ID = "empty-else-block"
        const val FOR_ID = "empty-for-block"
        const val WHILE_ID = "empty-while-block"
        const val DO_WHILE_ID = "empty-do-while-block"
        const val FINALLY_ID = "empty-finally-block"
        const val TRY_ID = "empty-try-block"
        const val INIT_ID = "empty-init-block"
        const val SECONDARY_CONSTRUCTOR_ID = "empty-secondary-constructor"

        const val IF_MESSAGE = "This if block is empty and can be removed"
        const val ELSE_MESSAGE = "This else block is empty and can be removed"
        const val FOR_MESSAGE = "Empty for block detected. Empty blocks of code serve no purpose and should be removed"
        const val WHILE_MESSAGE = "Empty while block detected. Empty blocks of code serve no purpose and should be removed"
        const val DO_WHILE_MESSAGE = "Empty do-while block detected. Empty blocks of code serve no purpose and should be removed"
        const val FINALLY_MESSAGE = "Empty finally block detected. Empty blocks of code serve no purpose and should be removed"
        const val TRY_MESSAGE = "Empty try block detected. Empty blocks of code serve no purpose and should be removed"
        const val INIT_MESSAGE = "Empty init block detected. Empty blocks of code serve no purpose and should be removed"
        const val SECONDARY_CONSTRUCTOR_MESSAGE =
            "Empty secondary constructor detected. Empty blocks of code serve no purpose and should be removed"
    }
}
