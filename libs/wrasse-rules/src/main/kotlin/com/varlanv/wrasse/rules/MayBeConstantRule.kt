package com.varlanv.wrasse.rules

import com.varlanv.wrasse.model.ChildBuffer
import com.varlanv.wrasse.model.WBufferedNodeRule
import com.varlanv.wrasse.model.WContext
import com.varlanv.wrasse.model.WNodeType
import com.varlanv.wrasse.model.WReporter
import com.varlanv.wrasse.model.WUninitializedRule
import com.varlanv.wrasse.model.WrasseRuleConfig
import com.varlanv.wrasse.model.isWhitespaceOrComment

private val TARGET_TYPES = setOf(WNodeType.PROPERTY)

/**
 * A top-level or direct object/companion-member `val` whose own initializer is directly a literal
 * constant is reported (see [MayBeConstantDecision]) at its own name's span. Entirely stateless:
 * every fact this rule needs — scope, `var`/`const` presence, a getter, non-`@JvmField`
 * annotations, the initializer's own shape — is read from the property's own [ChildBuffer] and
 * [WContext.ancestors] at its own exit, since a `PROPERTY` node can never itself be a direct child
 * of another `PROPERTY`. Autofixed by inserting `const ` right before the `val` keyword whenever
 * [MayBeConstantDecision.canAutofix] agrees, given whether an `@JvmField` annotation is present and
 * the property's own written type (`null` when the type is inferred).
 */
class MayBeConstantRule : WUninitializedRule {
    override val id: String = "may-be-constant"
    override val canAutofix: Boolean = true

    override fun initRule(config: WrasseRuleConfig): WBufferedNodeRule {
        val ruleId = id
        return object : WBufferedNodeRule {
            override val id = ruleId
            override val config = config
            override val targetTypes = TARGET_TYPES

            override fun exitNode(
                ctx: WContext,
                children: ChildBuffer,
                reporter: WReporter,
            ) {
                val ancestors = ctx.ancestors
                val isTopLevel = ancestors.peekType() == WNodeType.FILE
                val isObjectMember =
                    ancestors.peekType() ==
                        WNodeType.CLASS_BODY &&
                        ancestors.size >=
                        2 &&
                        ancestors.typeAt(ancestors.size - 2) ==
                        WNodeType.OBJECT_DECLARATION
                val eligibleScope = isTopLevel || isObjectMember

                val modifierListIdx = children.firstChildOfType(WNodeType.MODIFIER_LIST)
                val modifierText = if (modifierListIdx < 0) "" else children.textSpan(modifierListIdx, ctx.sourceText)
                val isAlreadyConst = WordBoundaryScan.containsWord(modifierText, "const")
                val isActual = WordBoundaryScan.containsWord(modifierText, "actual")
                val isOverride = WordBoundaryScan.containsWord(modifierText, "override")
                val annotations = scanAnnotations(modifierText)

                val nameIdx = children.firstChildOfType(WNodeType.IDENTIFIER)
                val name = if (nameIdx < 0) {
                    "<anonymous>"
                } else {
                    IdentifierCasing.unquote(children.textSpan(nameIdx, ctx.sourceText))
                }

                val message =
                    MayBeConstantDecision.decide(
                        eligibleScope = eligibleScope,
                        isVar = children.hasChildOfType(WNodeType.KW_VAR),
                        isAlreadyConst = isAlreadyConst,
                        isActual = isActual,
                        isOverride = isOverride,
                        hasGetter = children.hasChildOfType(WNodeType.PROPERTY_ACCESSOR),
                        hasNonJvmFieldAnnotation = annotations.hasOtherAnnotation,
                        initializerIsConstant = initializerIsConstant(ctx, children),
                        propertyName = name,
                    ) ?: return
                val reportStart = if (nameIdx < 0) ctx.startOffset else children.startOffset(nameIdx)
                val reportEnd = if (nameIdx < 0) ctx.endOffset else children.endOffset(nameIdx)

                val valIdx = children.firstChildOfType(WNodeType.KW_VAL)
                val typeRefIdx = children.firstChildOfType(WNodeType.TYPE_REFERENCE)
                val declaredType = if (typeRefIdx < 0) {
                    null
                } else {
                    children.textSpan(typeRefIdx, ctx.sourceText).toString()
                }
                val canAutofix = valIdx >= 0 &&
                    nameIdx >= 0 &&
                    MayBeConstantDecision.canAutofix(annotations.hasJvmField, declaredType)
                val edits = if (canAutofix) {
                    val valStart = children.startOffset(valIdx)
                    val nameStart = children.startOffset(nameIdx)
                    val gapText = ctx.sourceText.subSequence(valStart, nameStart)
                    listOf(MayBeConstantDecision.autofixEdit(valStart, nameStart, gapText))
                } else {
                    emptyList()
                }
                reporter.report(ruleId, message, reportStart, reportEnd, this, edits = edits)
            }

            private fun scanAnnotations(modifierText: CharSequence): AnnotationScan {
                val starts = ArrayList<Int>()
                var idx = WordBoundaryScan.indexOfChar(modifierText, '@')
                while (idx >= 0) {
                    starts.add(idx)
                    idx = WordBoundaryScan.indexOfChar(modifierText, '@', idx + 1)
                }
                if (starts.isEmpty()) return AnnotationScan(hasOtherAnnotation = false, hasJvmField = false)
                var hasJvmField = false
                var hasOtherAnnotation = false
                for (start in starts) {
                    val end = starts.firstOrNull { it > start } ?: modifierText.length
                    if (modifierText.subSequence(start, end).toString().trim() == "@JvmField") {
                        hasJvmField = true
                    } else {
                        hasOtherAnnotation = true
                    }
                }
                return AnnotationScan(hasOtherAnnotation, hasJvmField)
            }

            private fun initializerIsConstant(ctx: WContext, children: ChildBuffer): Boolean {
                val eqIdx = children.firstChildOfType(WNodeType.EQ)
                if (eqIdx < 0) return false
                var i = eqIdx + 1
                while (i < children.size && children.type(i).isWhitespaceOrComment) i++
                if (i >= children.size) return false
                return ConstantLiteralCheck.isConstant(children.type(i), children.textSpan(i, ctx.sourceText))
            }
        }
    }

    private class AnnotationScan(val hasOtherAnnotation: Boolean, val hasJvmField: Boolean)
}
