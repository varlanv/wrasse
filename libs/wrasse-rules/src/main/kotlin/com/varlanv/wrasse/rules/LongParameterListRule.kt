package com.varlanv.wrasse.rules

import com.varlanv.wrasse.model.ChildBuffer
import com.varlanv.wrasse.model.WBufferedNodeRule
import com.varlanv.wrasse.model.WContext
import com.varlanv.wrasse.model.WNodeType
import com.varlanv.wrasse.model.WReporter
import com.varlanv.wrasse.model.WUninitializedRule
import com.varlanv.wrasse.model.WrasseRuleConfig

/**
 * Reports a function, primary constructor, or secondary constructor with too many parameters
 * (see [LongParameterListDecision]). `CLASS`/`MODIFIER_LIST` are tracked only to answer "is the
 * enclosing class a data class" and "does this function carry `override`" before the parameter
 * list itself is checked; `FUN`/`PRIMARY_CONSTRUCTOR`/`SECONDARY_CONSTRUCTOR` push a frame that
 * `VALUE_PARAMETER_LIST` (a later sibling, always exited after its own `MODIFIER_LIST`) consumes.
 */
class LongParameterListRule : WUninitializedRule {
    override val id: String = "long-parameter-list"

    override fun initRule(config: WrasseRuleConfig): WBufferedNodeRule {
        val ruleId = id
        return object : WBufferedNodeRule {
            override val id = ruleId
            override val config = config
            override val targetTypes = setOf(
                WNodeType.CLASS,
                WNodeType.MODIFIER_LIST,
                WNodeType.FUN,
                WNodeType.PRIMARY_CONSTRUCTOR,
                WNodeType.SECONDARY_CONSTRUCTOR,
                WNodeType.VALUE_PARAMETER_LIST,
            )

            private val classes = mutableListOf<PendingClass>()
            private val owners = mutableListOf<PendingOwner>()

            override fun enterNode(ctx: WContext, reporter: WReporter): Boolean {
                when (ctx.type) {
                    WNodeType.CLASS -> classes.add(PendingClass())
                    WNodeType.FUN -> owners.add(
                        PendingOwner(ParameterListOwner.FUNCTION, isDataClassConstructor = false),
                    )
                    WNodeType.PRIMARY_CONSTRUCTOR -> owners.add(
                        PendingOwner(
                            ParameterListOwner.PRIMARY_CONSTRUCTOR,
                            isDataClassConstructor = currentClassIsData(),
                        ),
                    )

                    WNodeType.SECONDARY_CONSTRUCTOR -> owners.add(
                        PendingOwner(
                            ParameterListOwner.SECONDARY_CONSTRUCTOR,
                            isDataClassConstructor = currentClassIsData(),
                        ),
                    )

                    else -> {}
                }
                return true
            }

            private fun currentClassIsData(): Boolean = classes.lastOrNull()?.isDataClass ?: false

            override fun exitNode(
                ctx: WContext,
                children: ChildBuffer,
                reporter: WReporter,
            ) {
                when (ctx.type) {
                    WNodeType.MODIFIER_LIST -> recordModifierList(ctx, children)
                    WNodeType.VALUE_PARAMETER_LIST -> recordValueParameterList(ctx, children, reporter)
                    WNodeType.FUN, WNodeType.PRIMARY_CONSTRUCTOR, WNodeType.SECONDARY_CONSTRUCTOR ->
                        if (owners.isNotEmpty()) owners.removeAt(owners.size - 1)

                    WNodeType.CLASS -> if (classes.isNotEmpty()) classes.removeAt(classes.size - 1)
                    else -> {}
                }
            }

            private fun recordModifierList(ctx: WContext, children: ChildBuffer) {
                when (ctx.ancestors.peekType()) {
                    WNodeType.CLASS -> classes
                        .lastOrNull()
                        ?.let { it.isDataClass = children.hasChildOfType(WNodeType.KW_DATA) }
                    WNodeType.FUN -> owners
                        .lastOrNull()
                        ?.let { it.isOverride = children.hasChildOfType(WNodeType.KW_OVERRIDE) }
                    else -> {}
                }
            }

            private fun recordValueParameterList(
                ctx: WContext,
                children: ChildBuffer,
                reporter: WReporter,
            ) {
                val parent = ctx.ancestors.peekType()
                val owner = owners.lastOrNull() ?: return
                val expectedType =
                    when (owner.kind) {
                        ParameterListOwner.FUNCTION -> WNodeType.FUN
                        ParameterListOwner.PRIMARY_CONSTRUCTOR -> WNodeType.PRIMARY_CONSTRUCTOR
                        ParameterListOwner.SECONDARY_CONSTRUCTOR -> WNodeType.SECONDARY_CONSTRUCTOR
                    }
                if (parent != expectedType) return
                var count = 0
                for (i in 0 until children.size) {
                    if (children.type(i) == WNodeType.VALUE_PARAMETER) count++
                }
                val message = LongParameterListDecision.decide(
                    owner.kind,
                    count,
                    owner.isOverride,
                    owner.isDataClassConstructor,
                ) ?: return
                reporter.report(ruleId, message, ctx.startOffset, ctx.endOffset, this)
            }
        }
    }

    private class PendingClass {
        var isDataClass = false
    }

    private class PendingOwner(val kind: ParameterListOwner, val isDataClassConstructor: Boolean) {
        var isOverride = false
    }
}
