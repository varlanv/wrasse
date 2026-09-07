package com.varlanv.wrasse.rules

import com.varlanv.wrasse.model.ChildBuffer
import com.varlanv.wrasse.model.ChildLeafHandler
import com.varlanv.wrasse.model.WBufferedNodeRule
import com.varlanv.wrasse.model.WContext
import com.varlanv.wrasse.model.WFileRule
import com.varlanv.wrasse.model.WNodeType
import com.varlanv.wrasse.model.WReporter
import com.varlanv.wrasse.model.WRule
import com.varlanv.wrasse.model.WUninitializedRuleGroup
import com.varlanv.wrasse.model.WrasseRuleConfig

private val TARGET_TYPES = setOf(
    WNodeType.CLASS,
    WNodeType.OBJECT_DECLARATION,
    WNodeType.FUN,
    WNodeType.PROPERTY,
    WNodeType.PRIMARY_CONSTRUCTOR,
    WNodeType.SECONDARY_CONSTRUCTOR,
    WNodeType.VALUE_PARAMETER_LIST,
    WNodeType.VALUE_PARAMETER,
)

/**
 * Fuses four ids sharing the same "read a declaration's own KDoc/visibility/parameters off its
 * own direct children" traversal into one decision-maker: `undocumented-public-class`,
 * `undocumented-public-function`, `undocumented-public-property` (all three top-level-only,
 * self-disabled entirely unless [WrasseRuleConfig.explicitApiActive] — see D23 — since requiring
 * KDoc on every public declaration would fight this project's own documented "KDoc is
 * contract-only, not mandatory" style outside of an explicit-API surface), and
 * `kdoc-tag-mismatch` (never gated on explicit-API mode — it only judges a KDoc that already
 * exists, at any nesting).
 *
 * A KDoc attaches as a direct child of the declaration it documents (confirmed against kotlinc's
 * own `findDocComment`, which walks `declaration.allChildren` — not a preceding sibling in the
 * enclosing `FILE`/`CLASS_BODY`), so every fact each id needs — `KDOC` presence, `MODIFIER_LIST`
 * text, the name, and (for `kdoc-tag-mismatch`) the primary/secondary constructor's or function's
 * own ordered parameter facts — is read directly off one `ChildBuffer` per declaration, no sibling
 * correlation needed anywhere. A secondary constructor's own KDoc is matched against its own
 * parameter list, reported against the enclosing class's name at the `constructor` keyword's span.
 */
class KdocEngine : WUninitializedRuleGroup {
    override val ids: Set<String> = setOf(
        UNDOCUMENTED_CLASS_ID,
        UNDOCUMENTED_FUNCTION_ID,
        UNDOCUMENTED_PROPERTY_ID,
        KDOC_TAG_MISMATCH_ID,
    )

    override fun initGroup(configs: Map<String, WrasseRuleConfig>): WRule {
        val classConfig = configs[UNDOCUMENTED_CLASS_ID]
        val functionConfig = configs[UNDOCUMENTED_FUNCTION_ID]
        val propertyConfig = configs[UNDOCUMENTED_PROPERTY_ID]
        val mismatchConfig = configs[KDOC_TAG_MISMATCH_ID]

        val classRule = if (classConfig != null && classConfig.explicitApiActive) {
            ReportFacade(UNDOCUMENTED_CLASS_ID, classConfig)
        } else {
            null
        }
        val functionRule = if (functionConfig != null && functionConfig.explicitApiActive) {
            ReportFacade(UNDOCUMENTED_FUNCTION_ID, functionConfig)
        } else {
            null
        }
        val propertyRule = if (propertyConfig != null && propertyConfig.explicitApiActive) {
            ReportFacade(UNDOCUMENTED_PROPERTY_ID, propertyConfig)
        } else {
            null
        }
        val mismatchRule = mismatchConfig?.let { ReportFacade(KDOC_TAG_MISMATCH_ID, it) }

        return object : WBufferedNodeRule, ChildLeafHandler {
            override val id = ENGINE_ID
            override val config = (classConfig ?: functionConfig ?: propertyConfig ?: mismatchConfig)!!
            override val targetTypes = TARGET_TYPES

            private val pendingParamLists = mutableListOf<MutableList<KdocDeclaration>>()
            private val completedParamLists = mutableListOf<CompletedParams>()
            private val completedConstructors = mutableListOf<CompletedParams>()
            private val pendingClassNames = mutableListOf<String?>()

            override fun enterNode(ctx: WContext, reporter: WReporter): Boolean {
                if (ctx.type == WNodeType.VALUE_PARAMETER_LIST) pendingParamLists.add(mutableListOf())
                if (ctx.type == WNodeType.CLASS) pendingClassNames.add(null)
                return true
            }

            override fun onChildLeaf(ctx: WContext, reporter: WReporter) {
                if (ctx.type ==
                    WNodeType.IDENTIFIER &&
                    pendingClassNames.isNotEmpty() &&
                    pendingClassNames[pendingClassNames.size - 1] ==
                    null &&
                    ctx.ancestors.peekType() ==
                    WNodeType.CLASS
                ) {
                    pendingClassNames[pendingClassNames.size - 1] = IdentifierCasing.unquote(ctx.leafString()!!)
                }
            }

            override fun exitNode(
                ctx: WContext,
                children: ChildBuffer,
                reporter: WReporter,
            ) {
                when (ctx.type) {
                    WNodeType.VALUE_PARAMETER -> recordParameter(ctx, children)
                    WNodeType.VALUE_PARAMETER_LIST -> completedParamLists.add(
                        CompletedParams(
                            ctx.startOffset,
                            ctx.endOffset,
                            pendingParamLists.removeAt(pendingParamLists.size - 1),
                        ),
                    )
                    WNodeType.PRIMARY_CONSTRUCTOR -> recordConstructor(ctx, children)
                    WNodeType.SECONDARY_CONSTRUCTOR -> handleSecondaryConstructor(ctx, children, reporter)
                    WNodeType.CLASS -> {
                        handleClass(ctx, children, reporter)
                        pendingClassNames.removeAt(pendingClassNames.size - 1)
                    }
                    WNodeType.OBJECT_DECLARATION -> handleObject(ctx, children, reporter)
                    WNodeType.FUN -> handleFun(ctx, children, reporter)
                    WNodeType.PROPERTY -> handleProperty(ctx, children, reporter)
                    else -> {}
                }
            }

            private fun recordParameter(ctx: WContext, children: ChildBuffer) {
                val nameIdx = children.firstChildOfType(WNodeType.IDENTIFIER)
                if (nameIdx < 0) return
                val name = IdentifierCasing.unquote(children.textSpan(nameIdx, ctx.sourceText))
                val isValOrVar = children.hasChildOfType(WNodeType.KW_VAL) || children.hasChildOfType(WNodeType.KW_VAR)
                val modifierIdx = children.firstChildOfType(WNodeType.MODIFIER_LIST)
                val isPrivate = modifierIdx >= 0 &&
                    WordBoundaryScan.containsWord(children.textSpan(modifierIdx, ctx.sourceText), "private")
                val isProperty = isValOrVar && !isPrivate
                pendingParamLists
                    .lastOrNull()
                    ?.add(
                        KdocDeclaration(
                            name,
                            if (isProperty) KdocDeclarationKind.PROPERTY else KdocDeclarationKind.PARAM,
                        ),
                    )
            }

            private fun handleSecondaryConstructor(
                ctx: WContext,
                children: ChildBuffer,
                reporter: WReporter,
            ) {
                if (mismatchRule == null) return
                val keywordIdx = children.firstChildOfType(WNodeType.KW_CONSTRUCTOR)
                if (keywordIdx < 0) return
                val className = pendingClassNames.lastOrNull() ?: return
                val facts = DeclarationFacts(
                    name = className,
                    nameStart = children.startOffset(keywordIdx),
                    nameEnd = children.endOffset(keywordIdx),
                    hasKdoc = children.hasChildOfType(WNodeType.KDOC),
                    isPublic = false,
                    isOverride = false,
                )
                if (!facts.hasKdoc) return
                val elementParams = takeCompletedForChild(children, WNodeType.VALUE_PARAMETER_LIST, completedParamLists)
                reportMismatch(ctx, children, facts, elementParams, reporter)
            }

            private fun recordConstructor(ctx: WContext, children: ChildBuffer) {
                val params = takeCompletedForChild(children, WNodeType.VALUE_PARAMETER_LIST, completedParamLists)
                completedConstructors.add(CompletedParams(ctx.startOffset, ctx.endOffset, params))
            }

            private fun takeCompletedForChild(
                children: ChildBuffer,
                childType: WNodeType,
                list: MutableList<CompletedParams>,
            ): List<KdocDeclaration> {
                val idx = children.firstChildOfType(childType)
                if (idx < 0) return emptyList()
                return takeCompleted(list, children.startOffset(idx), children.endOffset(idx))
            }

            private fun takeCompleted(
                list: MutableList<CompletedParams>,
                start: Int,
                end: Int,
            ): List<KdocDeclaration> {
                val idx = list.indexOfFirst { it.start == start && it.end == end }
                if (idx < 0) return emptyList()
                return list.removeAt(idx).params
            }

            private fun handleClass(
                ctx: WContext,
                children: ChildBuffer,
                reporter: WReporter,
            ) {
                val facts = declarationFacts(ctx, children) ?: return
                if (classRule != null && ctx.ancestors.peekType() == WNodeType.FILE) {
                    val message = UndocumentedPublicApiDecision.decideClass(facts.name, facts.hasKdoc, facts.isPublic)
                    if (message != null) {
                        reporter.report(UNDOCUMENTED_CLASS_ID, message, facts.nameStart, facts.nameEnd, classRule)
                    }
                }
                if (mismatchRule != null && facts.hasKdoc) {
                    val elementParams = takeCompletedForChild(
                        children,
                        WNodeType.PRIMARY_CONSTRUCTOR,
                        completedConstructors,
                    )
                    reportMismatch(ctx, children, facts, elementParams, reporter)
                }
            }

            private fun handleObject(
                ctx: WContext,
                children: ChildBuffer,
                reporter: WReporter,
            ) {
                if (classRule == null || ctx.ancestors.peekType() != WNodeType.FILE) return
                val facts = declarationFacts(ctx, children) ?: return
                val message = UndocumentedPublicApiDecision.decideClass(facts.name, facts.hasKdoc, facts.isPublic)
                if (message != null) {
                    reporter.report(UNDOCUMENTED_CLASS_ID, message, facts.nameStart, facts.nameEnd, classRule)
                }
            }

            private fun handleFun(
                ctx: WContext,
                children: ChildBuffer,
                reporter: WReporter,
            ) {
                val facts = declarationFacts(ctx, children) ?: return
                if (functionRule != null && ctx.ancestors.peekType() == WNodeType.FILE) {
                    val message = UndocumentedPublicApiDecision.decideFunction(
                        facts.name,
                        facts.hasKdoc,
                        facts.isPublic,
                        facts.isOverride,
                    )
                    if (message != null) {
                        reporter.report(UNDOCUMENTED_FUNCTION_ID, message, facts.nameStart, facts.nameEnd, functionRule)
                    }
                }
                if (mismatchRule != null && facts.hasKdoc) {
                    val elementParams = takeCompletedForChild(
                        children,
                        WNodeType.VALUE_PARAMETER_LIST,
                        completedParamLists,
                    )
                    reportMismatch(ctx, children, facts, elementParams, reporter)
                }
            }

            private fun handleProperty(
                ctx: WContext,
                children: ChildBuffer,
                reporter: WReporter,
            ) {
                if (propertyRule == null || ctx.ancestors.peekType() != WNodeType.FILE) return
                val facts = declarationFacts(ctx, children) ?: return
                val message = UndocumentedPublicApiDecision.decideProperty(
                    facts.name,
                    facts.hasKdoc,
                    facts.isPublic,
                    facts.isOverride,
                )
                if (message != null) {
                    reporter.report(UNDOCUMENTED_PROPERTY_ID, message, facts.nameStart, facts.nameEnd, propertyRule)
                }
            }

            private fun reportMismatch(
                ctx: WContext,
                children: ChildBuffer,
                facts: DeclarationFacts,
                elementParams: List<KdocDeclaration>,
                reporter: WReporter,
            ) {
                val kdocIdx = children.firstChildOfType(WNodeType.KDOC)
                if (kdocIdx < 0) return
                val docTags = KdocTagParser.parseTags(children.textSpan(kdocIdx, ctx.sourceText))
                val message = KdocTagMismatchDecision.decide(docTags, elementParams) ?: return
                reporter.report(
                    KDOC_TAG_MISMATCH_ID,
                    "Documentation of ${facts.name} is outdated: $message",
                    facts.nameStart,
                    facts.nameEnd,
                    mismatchRule!!,
                )
            }

            private fun declarationFacts(ctx: WContext, children: ChildBuffer): DeclarationFacts? {
                val nameIdx = children.firstChildOfType(WNodeType.IDENTIFIER)
                if (nameIdx < 0) return null
                val name = IdentifierCasing.unquote(children.textSpan(nameIdx, ctx.sourceText))
                val modifierIdx = children.firstChildOfType(WNodeType.MODIFIER_LIST)
                val modifierText = if (modifierIdx < 0) "" else children.textSpan(modifierIdx, ctx.sourceText)
                val isPublic = !WordBoundaryScan.containsWord(
                    modifierText,
                    "private",
                ) && !WordBoundaryScan.containsWord(modifierText, "internal")
                val isOverride = WordBoundaryScan.containsWord(modifierText, "override")
                return DeclarationFacts(
                    name = name,
                    nameStart = children.startOffset(nameIdx),
                    nameEnd = children.endOffset(nameIdx),
                    hasKdoc = children.hasChildOfType(WNodeType.KDOC),
                    isPublic = isPublic,
                    isOverride = isOverride,
                )
            }
        }
    }

    private class DeclarationFacts(
        val name: String,
        val nameStart: Int,
        val nameEnd: Int,
        val hasKdoc: Boolean,
        val isPublic: Boolean,
        val isOverride: Boolean,
    )

    private class CompletedParams(
        val start: Int,
        val end: Int,
        val params: List<KdocDeclaration>,
    )

    private class ReportFacade(override val id: String, override val config: WrasseRuleConfig) : WFileRule {
        override fun visit(ctx: WContext, reporter: WReporter) {}
    }

    companion object {
        const val UNDOCUMENTED_CLASS_ID = "undocumented-public-class"
        const val UNDOCUMENTED_FUNCTION_ID = "undocumented-public-function"
        const val UNDOCUMENTED_PROPERTY_ID = "undocumented-public-property"
        const val KDOC_TAG_MISMATCH_ID = "kdoc-tag-mismatch"
        private const val ENGINE_ID = "kdoc-engine"
    }
}
