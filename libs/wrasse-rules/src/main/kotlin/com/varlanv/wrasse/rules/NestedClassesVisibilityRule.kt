package com.varlanv.wrasse.rules

import com.varlanv.wrasse.model.ChildBuffer
import com.varlanv.wrasse.model.WBufferedNodeRule
import com.varlanv.wrasse.model.WContext
import com.varlanv.wrasse.model.WNodeType
import com.varlanv.wrasse.model.WReporter
import com.varlanv.wrasse.model.WUninitializedRule
import com.varlanv.wrasse.model.WrasseRuleConfig

/**
 * A nested class or object carrying an explicit `public` modifier, declared directly inside a
 * top-level, non-interface, `internal`-modified class's own body, is reported (see
 * [NestedClassesVisibilityDecision]) at the nested declaration's own span. An enum entry, a class
 * itself carrying `enum`, and a companion object are never reported — none of those can be made
 * "more public" by the modifier the way an ordinary nested class/object can.
 *
 * [CLASS]/[WNodeType.OBJECT_DECLARATION] frames track their own modifier facts as they are
 * visited (leaf-level, attributed to the innermost currently-open frame via [ctx]'s own ancestor
 * chain — always correct for a fact that must appear, in source order, before any nested
 * declaration is even reached); [CompletedDecl]s are looked up by exact offset match against
 * [WNodeType.CLASS_BODY]'s own direct children, so only genuinely direct nested members are ever
 * considered, never a deeper-nested grandchild.
 */
class NestedClassesVisibilityRule : WUninitializedRule {
    override val id: String = "nested-classes-visibility"

    override fun initRule(config: WrasseRuleConfig): WBufferedNodeRule {
        val ruleId = id
        return object : WBufferedNodeRule {
            override val id = ruleId
            override val config = config
            override val targetTypes = setOf(WNodeType.CLASS, WNodeType.OBJECT_DECLARATION, WNodeType.CLASS_BODY)

            private val pendingClasses = mutableListOf<PendingClass>()
            private val completedDecls = mutableListOf<CompletedDecl>()

            override fun enterNode(ctx: WContext, reporter: WReporter): Boolean {
                if (ctx.type == WNodeType.CLASS || ctx.type == WNodeType.OBJECT_DECLARATION) {
                    pendingClasses.add(PendingClass(nodeType = ctx.type, isTopLevel = ctx.ancestors.peekType() == WNodeType.FILE))
                }
                return true
            }

            override fun onChildLeaf(ctx: WContext, reporter: WReporter) {
                val pending = pendingClasses.lastOrNull() ?: return
                when (ctx.type) {
                    WNodeType.KW_INTERFACE -> if (ctx.ancestors.peekType() == WNodeType.CLASS) pending.isInterface = true
                    WNodeType.KW_PUBLIC -> if (inOwnModifierList(ctx)) pending.hasPublic = true
                    WNodeType.KW_INTERNAL -> if (inOwnModifierList(ctx)) pending.hasInternal = true
                    WNodeType.KW_ENUM -> if (inOwnModifierList(ctx)) pending.hasEnum = true
                    WNodeType.KW_COMPANION -> if (inOwnModifierList(ctx)) pending.hasCompanion = true
                    else -> {}
                }
            }

            private fun inOwnModifierList(ctx: WContext): Boolean {
                val ancestors = ctx.ancestors
                if (ancestors.peekType() != WNodeType.MODIFIER_LIST || ancestors.size < 2) return false
                val ownerType = ancestors.typeAt(ancestors.size - 2)
                return ownerType == WNodeType.CLASS || ownerType == WNodeType.OBJECT_DECLARATION
            }

            override fun exitNode(ctx: WContext, children: ChildBuffer, reporter: WReporter) {
                when (ctx.type) {
                    WNodeType.CLASS, WNodeType.OBJECT_DECLARATION -> finalizeClass(ctx)
                    WNodeType.CLASS_BODY -> finalizeBody(children, reporter)
                    else -> {}
                }
            }

            private fun finalizeClass(ctx: WContext) {
                val pending = pendingClasses.removeAt(pendingClasses.size - 1)
                completedDecls.add(CompletedDecl(ctx.startOffset, ctx.endOffset, pending.hasPublic, pending.hasEnum, pending.hasCompanion))
            }

            private fun finalizeBody(children: ChildBuffer, reporter: WReporter) {
                val owner = pendingClasses.lastOrNull()
                val ownerQualifies =
                owner != null && owner.nodeType == WNodeType.CLASS && owner.isTopLevel && owner.hasInternal && !owner.isInterface
                for (i in 0 until children.size) {
                    val type = children.type(i)
                    if (type != WNodeType.CLASS && type != WNodeType.OBJECT_DECLARATION) continue
                    val decl = takeCompletedDecl(children.startOffset(i), children.endOffset(i)) ?: continue
                    val message = NestedClassesVisibilityDecision.decide(ownerQualifies, decl.hasPublic, decl.hasEnum, decl.hasCompanion)
                        ?: continue
                    reporter.report(ruleId, message, children.startOffset(i), children.endOffset(i), this)
                }
            }

            private fun takeCompletedDecl(start: Int, end: Int): CompletedDecl? {
                val idx = completedDecls.indexOfFirst { it.start == start && it.end == end }
                if (idx < 0) return null
                return completedDecls.removeAt(idx)
            }
        }
    }

    private class PendingClass(val nodeType: WNodeType, val isTopLevel: Boolean) {
        var hasPublic = false
        var hasInternal = false
        var hasEnum = false
        var hasCompanion = false
        var isInterface = false
    }

    private class CompletedDecl(val start: Int, val end: Int, val hasPublic: Boolean, val hasEnum: Boolean, val hasCompanion: Boolean)
}
