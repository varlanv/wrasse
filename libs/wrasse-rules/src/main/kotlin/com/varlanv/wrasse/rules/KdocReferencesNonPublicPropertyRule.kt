package com.varlanv.wrasse.rules

import com.varlanv.wrasse.model.ChildBuffer
import com.varlanv.wrasse.model.WBufferedNodeRule
import com.varlanv.wrasse.model.WContext
import com.varlanv.wrasse.model.WNodeType
import com.varlanv.wrasse.model.WReporter
import com.varlanv.wrasse.model.WUninitializedRule
import com.varlanv.wrasse.model.WrasseRuleConfig

/**
 * A class's own KDoc that links (see [KdocReferencesNonPublicPropertyDecision]) one of that
 * class's own direct member properties, when that member is `private` or `internal`, is reported
 * at the property's own name span. Narrowed to a class's own immediate `CLASS_BODY` member
 * properties only — never a primary constructor's `val`/`var` parameters (naturally excluded:
 * those are `PRIMARY_CONSTRUCTOR`'s own children, never `CLASS_BODY`'s), and never a nested
 * class's/object's own properties reached through a qualified KDoc link — a documented narrowing
 * of the upstream rule this derives from, which also matches such qualified links through nested
 * objects. Each `PROPERTY`'s own name/visibility facts are recorded at its own exit, consumed by
 * its own `CLASS_BODY`'s exit (keyed by offset), and that body's own property list is in turn
 * consumed by its own `CLASS`'s exit — the same offset-correlation shape [KdocEngine] already
 * establishes for parameter/constructor facts. A flagged property is nonetheless suppressed when
 * the same class body also declares a non-private member (function or property) of the same name
 * — the link resolves to that member instead, never the private one.
 */
class KdocReferencesNonPublicPropertyRule : WUninitializedRule {
    override val id: String = "kdoc-references-non-public-property"

    override fun initRule(config: WrasseRuleConfig): WBufferedNodeRule {
        val ruleId = id
        return object : WBufferedNodeRule {
            override val id = ruleId
            override val config = config
            override val targetTypes = setOf(WNodeType.CLASS, WNodeType.CLASS_BODY, WNodeType.PROPERTY, WNodeType.FUN)

            private val completedProperties = mutableListOf<CompletedProperty>()
            private val completedFuns = mutableListOf<CompletedFun>()
            private val completedBodies = mutableListOf<CompletedBody>()

            override fun exitNode(ctx: WContext, children: ChildBuffer, reporter: WReporter) {
                when (ctx.type) {
                    WNodeType.PROPERTY -> recordProperty(ctx, children)
                    WNodeType.FUN -> recordFun(ctx, children)
                    WNodeType.CLASS_BODY -> recordBody(ctx, children)
                    WNodeType.CLASS -> handleClass(ctx, children, reporter)
                    else -> {}
                }
            }

            private fun recordProperty(ctx: WContext, children: ChildBuffer) {
                val nameIdx = children.firstChildOfType(WNodeType.IDENTIFIER)
                if (nameIdx < 0) return
                val name = IdentifierCasing.unquote(children.textSpan(nameIdx, ctx.sourceText))
                val modifierText = modifierText(children, ctx)
                val isPrivate = WordBoundaryScan.containsWord(modifierText, "private")
                val isNonPublic = isPrivate || WordBoundaryScan.containsWord(modifierText, "internal")
                completedProperties
                    .add(
                        CompletedProperty(
                            start = ctx.startOffset,
                            end = ctx.endOffset,
                            name = name,
                            nameStart = children.startOffset(nameIdx),
                            nameEnd = children.endOffset(nameIdx),
                            isNonPublic = isNonPublic,
                            isPrivate = isPrivate,
                        ),
                    )
            }

            private fun recordFun(ctx: WContext, children: ChildBuffer) {
                val nameIdx = children.firstChildOfType(WNodeType.IDENTIFIER)
                if (nameIdx < 0) return
                val name = IdentifierCasing.unquote(children.textSpan(nameIdx, ctx.sourceText))
                val isPrivate = WordBoundaryScan.containsWord(modifierText(children, ctx), "private")
                completedFuns.add(CompletedFun(start = ctx.startOffset, end = ctx.endOffset, name = name, isPrivate = isPrivate))
            }

            private fun modifierText(children: ChildBuffer, ctx: WContext): CharSequence {
                val modifierIdx = children.firstChildOfType(WNodeType.MODIFIER_LIST)
                return if (modifierIdx < 0) "" else children.textSpan(modifierIdx, ctx.sourceText)
            }

            private fun recordBody(ctx: WContext, children: ChildBuffer) {
                val properties = mutableListOf<CompletedProperty>()
                val funs = mutableListOf<CompletedFun>()
                for (i in 0 until children.size) {
                    when (children.type(i)) {
                        WNodeType.PROPERTY -> {
                            val idx =
                            completedProperties.indexOfFirst { it.start == children.startOffset(i) && it.end == children.endOffset(i) }
                            if (idx >= 0) properties.add(completedProperties.removeAt(idx))
                        }

                        WNodeType.FUN -> {
                            val idx = completedFuns.indexOfFirst { it.start == children.startOffset(i) && it.end == children.endOffset(i) }
                            if (idx >= 0) funs.add(completedFuns.removeAt(idx))
                        }

                        else -> {}
                    }
                }
                completedBodies.add(CompletedBody(ctx.startOffset, ctx.endOffset, properties, funs))
            }

            private fun handleClass(ctx: WContext, children: ChildBuffer, reporter: WReporter) {
                val kdocIdx = children.firstChildOfType(WNodeType.KDOC)
                val bodyIdx = children.firstChildOfType(WNodeType.CLASS_BODY)
                val bodyRecord =
                    if (bodyIdx < 0) {
                        null
                    } else {
                        val idx =
                        completedBodies.indexOfFirst { it.start == children.startOffset(bodyIdx) && it.end == children.endOffset(bodyIdx) }
                        if (idx < 0) null else completedBodies.removeAt(idx)
                    }
                if (kdocIdx < 0 || bodyRecord == null) return
                val kdocText = children.textSpan(kdocIdx, ctx.sourceText)
                for (property in bodyRecord.properties) {
                    if (!property.isNonPublic) continue
                    if (!KdocReferencesNonPublicPropertyDecision.isReferenced(kdocText, property.name)) continue
                    val otherMembers = otherMemberNames(bodyRecord, property)
                    if (KdocReferencesNonPublicPropertyDecision.hasNonPrivateSameNameMember(property.name, otherMembers)) continue
                    reporter
                        .report(
                            ruleId,
                            KdocReferencesNonPublicPropertyDecision.message(property.name),
                            property.nameStart,
                            property.nameEnd,
                            this,
                        )
                }
            }

            private fun otherMemberNames(body: CompletedBody, property: CompletedProperty): List<Pair<String, Boolean>> {
                val result = mutableListOf<Pair<String, Boolean>>()
                for (fn in body.funs) result.add(fn.name to fn.isPrivate)
                for (other in body.properties) if (other !== property) result.add(other.name to other.isPrivate)
                return result
            }
        }
    }

    private class CompletedProperty(
        val start: Int,
        val end: Int,
        val name: String,
        val nameStart: Int,
        val nameEnd: Int,
        val isNonPublic: Boolean,
        val isPrivate: Boolean,
    )

    private class CompletedFun(val start: Int, val end: Int, val name: String, val isPrivate: Boolean)

    private class CompletedBody(val start: Int, val end: Int, val properties: List<CompletedProperty>, val funs: List<CompletedFun>)
}
