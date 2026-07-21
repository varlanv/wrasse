package com.varlanv.wrasse.rules

import com.varlanv.wrasse.model.ChildBuffer
import com.varlanv.wrasse.model.WBufferedNodeRule
import com.varlanv.wrasse.model.WContext
import com.varlanv.wrasse.model.WNodeType
import com.varlanv.wrasse.model.WReporter
import com.varlanv.wrasse.model.WUninitializedRule
import com.varlanv.wrasse.model.WrasseRuleConfig

/**
 * Checks a leading-underscore property against its direct class-body siblings (see
 * [BackingPropertyNamingDecision]). Only member properties are targets — a top-level or local
 * underscore-prefixed identifier has no sibling-correlation concept to check at all, and reporting
 * one unconditionally (no correlated member ever exists outside a class body) would be a
 * false-positive shape this rule deliberately narrows away.
 */
class BackingPropertyNamingRule : WUninitializedRule {
    override val id: String = "backing-property-naming"

    override fun initRule(config: WrasseRuleConfig): WBufferedNodeRule {
        val ruleId = id
        return object : WBufferedNodeRule {
            override val id = ruleId
            override val config = config
            override val targetTypes = setOf(WNodeType.CLASS_BODY, WNodeType.PROPERTY, WNodeType.FUN)

            private val classBodyStack = mutableListOf<MutableList<MemberInfo>>()

            override fun enterNode(ctx: WContext, reporter: WReporter): Boolean {
                if (ctx.type == WNodeType.CLASS_BODY) classBodyStack.add(mutableListOf())
                return true
            }

            override fun exitNode(ctx: WContext, children: ChildBuffer, reporter: WReporter) {
                when (ctx.type) {
                    WNodeType.PROPERTY -> recordMember(ctx, children, isProperty = true)
                    WNodeType.FUN -> recordMember(ctx, children, isProperty = false)
                    WNodeType.CLASS_BODY -> finalizeClassBody(ctx, reporter)
                    else -> {}
                }
            }

            private fun recordMember(ctx: WContext, children: ChildBuffer, isProperty: Boolean) {
                if (classBodyStack.isEmpty() || ctx.ancestors.peekType() != WNodeType.CLASS_BODY) return
                val idIdx = children.firstChildOfType(WNodeType.IDENTIFIER)
                if (idIdx < 0) return
                val name = IdentifierCasing.unquote(children.textSpan(idIdx, ctx.sourceText))

                val modifierIdx = children.firstChildOfType(WNodeType.MODIFIER_LIST)
                val modifierText = if (modifierIdx >= 0) children.textSpan(modifierIdx, ctx.sourceText) else ""
                val hasOverride = Regex("\\boverride\\b").containsMatchIn(modifierText)
                val isPublic = !Regex("\\b(private|protected|internal)\\b").containsMatchIn(modifierText)

                val emptyParamList =
                    if (isProperty) {
                        false
                    } else {
                        val paramListIdx = children.firstChildOfType(WNodeType.VALUE_PARAMETER_LIST)
                        paramListIdx >= 0 && children.textSpan(paramListIdx, ctx.sourceText).toString().replace(Regex("\\s"), "") == "()"
                    }

                classBodyStack
                    .last()
                    .add(
                        MemberInfo(
                            isProperty = isProperty,
                            name = name,
                            isPublic = isPublic,
                            emptyParamList = emptyParamList,
                            hasOverride = hasOverride,
                            identifierStart = children.startOffset(idIdx),
                            identifierEnd = children.endOffset(idIdx),
                        ),
                    )
            }

            private fun finalizeClassBody(ctx: WContext, reporter: WReporter) {
                val members = classBodyStack.removeAt(classBodyStack.size - 1)
                for (member in members) {
                    if (!member.isProperty || !member.name.startsWith("_") || member.name == "_") continue
                    val strippedName = member.name.removePrefix("_")
                    val correlated = members.firstOrNull { candidate ->
                        candidate !==
                            member &&
                            ((candidate.isProperty && candidate.name == strippedName) ||
                                (!candidate.isProperty &&
                                    candidate.emptyParamList &&
                                    candidate.name ==
                                    "get" +
                                    strippedName.replaceFirstChar { it.uppercaseChar() }))
                    }
                    val message =
                    BackingPropertyNamingDecision.decide(member.name, member.hasOverride, correlated?.isPublic) ?: continue
                    reporter.report(ruleId, message, member.identifierStart, member.identifierEnd, this)
                }
            }
        }
    }

    private class MemberInfo(
        val isProperty: Boolean,
        val name: String,
        val isPublic: Boolean,
        val emptyParamList: Boolean,
        val hasOverride: Boolean,
        val identifierStart: Int,
        val identifierEnd: Int,
    )
}
