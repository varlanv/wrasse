package com.varlanv.wrasse.rules

import com.varlanv.wrasse.model.ChildLeafHandler
import com.varlanv.wrasse.model.WContext
import com.varlanv.wrasse.model.WNodeRule
import com.varlanv.wrasse.model.WNodeType
import com.varlanv.wrasse.model.WReporter
import com.varlanv.wrasse.model.WUninitializedRule
import com.varlanv.wrasse.model.WrasseRuleConfig

private val TARGET_TYPES = setOf(WNodeType.PROPERTY, WNodeType.PROPERTY_ACCESSOR)

/**
 * A `get()`/`set()` accessor body that references its own property's bare name — rather than
 * `field` — anywhere inside it is reported (see [GetterSetterFieldsDecision]) at the accessor's
 * own span. The property's own name and whether it is an extension property are captured from its
 * first direct-child `IDENTIFIER` (the name immediately follows a `DOT` with no intervening
 * whitespace only when the property is an extension — the same fragile, whitespace-sensitive
 * adjacency check the upstream rule this derives from itself relies on, faithfully reproduced, not
 * a narrowing). Only the *first* bare same-named reference found inside the accessor is ever
 * consulted (matching upstream's own single `firstOrNull`); a reference nested inside any
 * dot-qualified expression, in either receiver or selector position, is never a candidate — a
 * deliberate narrowing relative to upstream's own additional `this.name` allowance, which needs
 * tracking a dot-qualified expression's own receiver shape one level below where the candidate
 * identifier itself is found.
 */
class GetterSetterFieldsRule : WUninitializedRule {
    override val id: String = "getter-setter-fields"

    override fun initRule(config: WrasseRuleConfig): WNodeRule {
        val ruleId = id
        return object : WNodeRule, ChildLeafHandler {
            override val id = ruleId
            override val config = config
            override val targetTypes = TARGET_TYPES

            private val propertyFrames = mutableListOf<PropertyFrame>()
            private val accessorFrames = mutableListOf<AccessorFrame>()

            override fun enterNode(ctx: WContext, reporter: WReporter): Boolean {
                when (ctx.type) {
                    WNodeType.PROPERTY -> propertyFrames.add(PropertyFrame())
                    WNodeType.PROPERTY_ACCESSOR -> accessorFrames.add(
                        AccessorFrame(propertyFrames.lastOrNull()?.name ?: ""),
                    )

                    else -> {}
                }
                return true
            }

            override fun onChildLeaf(ctx: WContext, reporter: WReporter) {
                if (ctx.type != WNodeType.IDENTIFIER) return
                val ancestors = ctx.ancestors
                val parentType = ancestors.peekType()
                val text = IdentifierCasing.unquote(ctx.leafString() ?: "")

                if (parentType == WNodeType.PROPERTY) {
                    val property = propertyFrames.lastOrNull()
                    if (property != null && !property.nameCaptured) {
                        property.nameCaptured = true
                        property.name = text
                        property.isExtension = ctx.prevLeafType == WNodeType.DOT
                    }

                    val accessor = accessorFrames.lastOrNull()
                    if (accessor != null &&
                        !accessor.foundChosen &&
                        ancestors.size >= 3 &&
                        ancestors.typeAt(ancestors.size - 2) == WNodeType.BLOCK &&
                        ancestors.typeAt(ancestors.size - 3) == WNodeType.PROPERTY_ACCESSOR &&
                        text == accessor.propertyName
                    ) {
                        accessor.sawLocalVarBefore = true
                    }
                    return
                }

                val accessor = accessorFrames.lastOrNull() ?: return
                if (accessor.foundChosen) return
                if (parentType != WNodeType.REFERENCE_EXPRESSION || text != accessor.propertyName) return
                if (ancestors.size >= 2 && ancestors.typeAt(ancestors.size - 2) == WNodeType.DOT_QUALIFIED_EXPRESSION) {
                    return
                }

                accessor.foundChosen = true
                accessor.rejectedAsCall =
                    ancestors.size >= 2 &&
                    ancestors.typeAt(ancestors.size - 2) == WNodeType.CALL_EXPRESSION
            }

            override fun exitNode(ctx: WContext, reporter: WReporter) {
                when (ctx.type) {
                    WNodeType.PROPERTY ->
                        if (propertyFrames.isNotEmpty()) propertyFrames.removeAt(propertyFrames.size - 1)
                    WNodeType.PROPERTY_ACCESSOR -> finalizeAccessor(ctx, reporter)
                    else -> {}
                }
            }

            private fun finalizeAccessor(ctx: WContext, reporter: WReporter) {
                val accessor =
                    if (accessorFrames.isNotEmpty()) accessorFrames.removeAt(accessorFrames.size - 1) else return
                val isExtension = propertyFrames.lastOrNull()?.isExtension == true
                val message =
                    GetterSetterFieldsDecision.decide(
                        foundSelfReference = accessor.foundChosen,
                        isCallExpressionCallee = accessor.rejectedAsCall,
                        shadowedByLocalVar = accessor.sawLocalVarBefore,
                        isExtensionProperty = isExtension,
                    ) ?: return
                reporter.report(ruleId, message, ctx.startOffset, ctx.endOffset, this)
            }
        }
    }

    private class PropertyFrame(
        var nameCaptured: Boolean = false,
        var name: String = "",
        var isExtension: Boolean = false,
    )

    private class AccessorFrame(
        val propertyName: String,
        var foundChosen: Boolean = false,
        var rejectedAsCall: Boolean = false,
        var sawLocalVarBefore: Boolean = false,
    )
}
