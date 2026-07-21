package com.varlanv.wrasse.rules

import com.varlanv.wrasse.model.WContext
import com.varlanv.wrasse.model.WNodeStack
import com.varlanv.wrasse.model.WNodeType
import com.varlanv.wrasse.model.WReporter
import com.varlanv.wrasse.model.WStreamRule
import com.varlanv.wrasse.model.WUninitializedRule
import com.varlanv.wrasse.model.WrasseRuleConfig

/**
 * A `private` class (top-level or nested) whose simple name never occurs anywhere else in the
 * file, either as a type reference (`USER_TYPE`, covering supertypes, parameter/return/property
 * types, `is`/`as` targets, generic arguments) or a plain name reference (`REFERENCE_EXPRESSION`,
 * covering constructor calls, qualifiers, and callable references), is reported (see
 * [UnusedPrivateClassDecision]) at its own span.
 *
 * Deliberately simpler and more permissive than the upstream rule this derives from, which tracks
 * a dozen distinct node shapes one by one (each ultimately funneling into a type reference or a
 * name reference in this model): the two broad node-type checks here are a superset of every one
 * of upstream's own tracked contexts, so this rule only ever reports a class upstream would also
 * flag, never one it wouldn't — permissive by construction, not merely by omission. Upstream's own
 * import-FQN correlation (checking a private class's simple name against imported paths) is
 * dropped outright: a private class cannot be imported from elsewhere by definition, so that check
 * has no bearing on a private declaration's own usage.
 */
class UnusedPrivateClassRule : WUninitializedRule {
    override val id: String = "unused-private-class"

    override fun initRule(config: WrasseRuleConfig): WStreamRule {
        val ruleId = id
        return object : WStreamRule {
            override val id = ruleId
            override val config = config

            private val pendingClasses = mutableListOf<PendingClass>()
            private val declaredClasses = mutableListOf<PendingClass>()
            private val usedNames = mutableSetOf<String>()

            override fun enterNode(ctx: WContext) {
                if (ctx.type == WNodeType.CLASS) pendingClasses.add(PendingClass())
            }

            override fun exitNode(ctx: WContext) {
                if (ctx.type == WNodeType.CLASS) {
                    val pending = pendingClasses.removeAt(pendingClasses.size - 1)
                    pending.startOffset = ctx.startOffset
                    pending.endOffset = ctx.endOffset
                    declaredClasses.add(pending)
                }
            }

            override fun visitLeaf(ctx: WContext, reporter: WReporter) {
                val ancestors = ctx.ancestors
                when (ctx.type) {
                    WNodeType.KW_PRIVATE ->
                        if (inOwnModifierList(ancestors, WNodeType.CLASS)) {
                            pendingClasses.lastOrNull()?.isPrivate = true
                        }

                    WNodeType.IDENTIFIER -> {
                        val text = IdentifierCasing.unquote(ctx.leafText ?: "")
                        val parent = ancestors.peekType()
                        if (parent == WNodeType.CLASS) {
                            val pending = pendingClasses.lastOrNull()
                            if (pending != null && pending.name == null) pending.name = text
                        } else if (parent == WNodeType.USER_TYPE || parent == WNodeType.REFERENCE_EXPRESSION) {
                            usedNames.add(text)
                        }
                    }

                    else -> {}
                }
            }

            private fun inOwnModifierList(ancestors: WNodeStack, ownerType: WNodeType): Boolean {
                if (ancestors.peekType() != WNodeType.MODIFIER_LIST || ancestors.size < 2) return false
                return ancestors.typeAt(ancestors.size - 2) == ownerType
            }

            override fun afterFile(ctx: WContext, reporter: WReporter) {
                for (decl in declaredClasses) {
                    val name = decl.name ?: continue
                    val message = UnusedPrivateClassDecision.decide(decl.isPrivate, name in usedNames, name) ?: continue
                    reporter.report(ruleId, message, decl.startOffset, decl.endOffset, this)
                }
            }
        }
    }

    private class PendingClass {
        var isPrivate = false
        var name: String? = null
        var startOffset = 0
        var endOffset = 0
    }
}
