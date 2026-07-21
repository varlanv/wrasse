package com.varlanv.wrasse.rules

import com.varlanv.wrasse.model.WContext
import com.varlanv.wrasse.model.WNodeStack
import com.varlanv.wrasse.model.WNodeType
import com.varlanv.wrasse.model.WReporter
import com.varlanv.wrasse.model.WStreamRule
import com.varlanv.wrasse.model.WUninitializedRule
import com.varlanv.wrasse.model.WrasseRuleConfig

/**
 * A function parameter never referenced by name anywhere in its own function's body is reported
 * (see [UnusedParameterDecision]) at the parameter's own name.
 *
 * Every open `FUN` frame's still-live parameter set is checked (not just the innermost) on every
 * plain name reference and every local property declaration sharing a name — mirroring the
 * upstream rule this derives from, which scans a function's *entire* subtree (nested local
 * functions included) by name alone, without lexical shadowing. This is a narrower, more
 * conservative match than true scoping would give (a nested function's own same-named parameter
 * usage can mark an unrelated outer parameter "used"), consistent with this batch's permissive
 * bias. Also narrowed: a named-argument label (`foo(paramName = value)`) is not distinguished
 * from a real reference and always counts as a use — the node model here has no distinct shape
 * for an argument's name position, and treating it as "used" only risks under- rather than
 * over-reporting.
 *
 * Exempt entirely (checked at each function's own name, hardcoded — the upstream rule's own
 * defaults, no wrasse config surface beyond `level`): `abstract`/`open`/`override`/`operator`/
 * `external`/`expect`/`actual`/`protected` functions, a function literally named `main`, and any
 * function declared inside an `expect`/`external` class or an interface. A parameter name matching
 * `ignored|expected` is ignored regardless.
 */
class UnusedParameterRule : WUninitializedRule {
    override val id: String = "unused-parameter"

    override fun initRule(config: WrasseRuleConfig): WStreamRule {
        val ruleId = id
        return object : WStreamRule {
            override val id = ruleId
            override val config = config

            private val pendingClasses = mutableListOf<PendingClass>()
            private val pendingFuns = mutableListOf<PendingFun>()
            private val completedFuns = mutableListOf<PendingFun>()

            override fun enterNode(ctx: WContext) {
                when (ctx.type) {
                    WNodeType.CLASS, WNodeType.OBJECT_DECLARATION -> pendingClasses.add(PendingClass())
                    WNodeType.FUN -> pendingFuns.add(PendingFun())
                    else -> {}
                }
            }

            override fun exitNode(ctx: WContext) {
                when (ctx.type) {
                    WNodeType.CLASS, WNodeType.OBJECT_DECLARATION -> pendingClasses.removeAt(pendingClasses.size - 1)
                    WNodeType.FUN -> {
                        val pending = pendingFuns.removeAt(pendingFuns.size - 1)
                        pending.containingClass = pendingClasses.lastOrNull()
                        completedFuns.add(pending)
                    }

                    else -> {}
                }
            }

            override fun visitLeaf(ctx: WContext, reporter: WReporter) {
                val ancestors = ctx.ancestors
                when (ctx.type) {
                    WNodeType.KW_ABSTRACT, WNodeType.KW_OPEN, WNodeType.KW_OVERRIDE, WNodeType.KW_OPERATOR,
                    WNodeType.KW_PROTECTED, WNodeType.KW_ACTUAL,
                    -> if (inOwnModifierList(ancestors, WNodeType.FUN)) markFunModifier(ctx.type)

                    WNodeType.KW_EXPECT, WNodeType.KW_EXTERNAL -> {
                        if (inOwnModifierList(ancestors, WNodeType.FUN)) markFunModifier(ctx.type)
                        if (inOwnModifierList(ancestors, WNodeType.CLASS) || inOwnModifierList(ancestors, WNodeType.OBJECT_DECLARATION)) {
                            markClassModifier(ctx.type)
                        }
                    }

                    WNodeType.KW_INTERFACE -> if (ancestors.peekType() == WNodeType.CLASS) pendingClasses.lastOrNull()?.isInterface = true

                    WNodeType.IDENTIFIER -> handleIdentifier(ctx)
                    else -> {}
                }
            }

            private fun handleIdentifier(ctx: WContext) {
                val ancestors = ctx.ancestors
                if (ancestors.peekType() == WNodeType.FUN && pendingFuns.isNotEmpty()) {
                    val pending = pendingFuns.last()
                    if (pending.functionName == null) pending.functionName = IdentifierCasing.unquote(ctx.leafText ?: "")
                    return
                }
                if (ancestors.peekType() ==
                    WNodeType.VALUE_PARAMETER &&
                    ancestors.size >=
                    3 &&
                    ancestors.typeAt(ancestors.size - 2) ==
                    WNodeType.VALUE_PARAMETER_LIST &&
                    ancestors.typeAt(ancestors.size - 3) ==
                    WNodeType.FUN
                ) {
                    val pending = pendingFuns.lastOrNull() ?: return
                    val name = IdentifierCasing.unquote(ctx.leafText ?: "")
                    if (name !in pending.params) pending.params[name] = intArrayOf(ctx.startOffset, ctx.endOffset)
                    return
                }
                val isUsage = ancestors.peekType() == WNodeType.REFERENCE_EXPRESSION
                val isLocalShadow = ancestors.peekType() ==
                    WNodeType.PROPERTY &&
                    ancestors.size >=
                    2 &&
                    ancestors.typeAt(ancestors.size - 2) ==
                    WNodeType.BLOCK
                if (isUsage || isLocalShadow) {
                    val name = IdentifierCasing.unquote(ctx.leafText ?: "")
                    for (frame in pendingFuns) frame.params.remove(name)
                }
            }

            private fun markFunModifier(type: WNodeType) {
                val pending = pendingFuns.lastOrNull() ?: return
                when (type) {
                    WNodeType.KW_ABSTRACT -> pending.hasAbstract = true
                    WNodeType.KW_OPEN -> pending.hasOpen = true
                    WNodeType.KW_OVERRIDE -> pending.hasOverride = true
                    WNodeType.KW_OPERATOR -> pending.hasOperator = true
                    WNodeType.KW_EXTERNAL -> pending.hasExternal = true
                    WNodeType.KW_EXPECT -> pending.hasExpect = true
                    WNodeType.KW_ACTUAL -> pending.hasActual = true
                    WNodeType.KW_PROTECTED -> pending.hasProtected = true
                    else -> {}
                }
            }

            private fun markClassModifier(type: WNodeType) {
                val pending = pendingClasses.lastOrNull() ?: return
                when (type) {
                    WNodeType.KW_EXPECT -> pending.isExpect = true
                    WNodeType.KW_EXTERNAL -> pending.isExternal = true
                    else -> {}
                }
            }

            private fun inOwnModifierList(ancestors: WNodeStack, ownerType: WNodeType): Boolean {
                if (ancestors.peekType() != WNodeType.MODIFIER_LIST || ancestors.size < 2) return false
                return ancestors.typeAt(ancestors.size - 2) == ownerType
            }

            override fun afterFile(ctx: WContext, reporter: WReporter) {
                for (pending in completedFuns) {
                    val containingClass = pending.containingClass
                    val functionExempt =
                    pending.hasAbstract ||
                        pending.hasOpen ||
                        pending.hasOverride ||
                        pending.hasOperator ||
                        pending.hasExternal ||
                        pending.hasExpect ||
                        pending.hasActual ||
                        pending.hasProtected ||
                        pending.functionName ==
                        "main" ||
                        containingClass?.isExpect ==
                        true ||
                        containingClass?.isExternal ==
                        true ||
                        containingClass?.isInterface ==
                        true
                    for ((name, span) in pending.params) {
                        val message = UnusedParameterDecision.decide(functionExempt, name, wasUsed = false) ?: continue
                        reporter.report(ruleId, message, span[0], span[1], this)
                    }
                }
            }
        }
    }

    private class PendingClass {
        var isExpect = false
        var isExternal = false
        var isInterface = false
    }

    private class PendingFun {
        var hasAbstract = false
        var hasOpen = false
        var hasOverride = false
        var hasOperator = false
        var hasExternal = false
        var hasExpect = false
        var hasActual = false
        var hasProtected = false
        var functionName: String? = null
        var containingClass: PendingClass? = null
        val params = LinkedHashMap<String, IntArray>()
    }
}
