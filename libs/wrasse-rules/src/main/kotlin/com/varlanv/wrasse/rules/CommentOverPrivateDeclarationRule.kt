package com.varlanv.wrasse.rules

import com.varlanv.wrasse.model.ChildBuffer
import com.varlanv.wrasse.model.WBufferedNodeRule
import com.varlanv.wrasse.model.WContext
import com.varlanv.wrasse.model.WNodeType
import com.varlanv.wrasse.model.WReporter
import com.varlanv.wrasse.model.WUninitializedRule
import com.varlanv.wrasse.model.WrasseRuleConfig

/**
 * A KDoc attached to an explicitly `private` function or property, at any nesting, is reported
 * (see [CommentOverPrivateDeclarationDecision]) at the declaration's own span.
 */
class CommentOverPrivateDeclarationRule : WUninitializedRule {
    override val id: String = "comment-over-private-declaration"

    override fun initRule(config: WrasseRuleConfig): WBufferedNodeRule {
        val ruleId = id
        return object : WBufferedNodeRule {
            override val id = ruleId
            override val config = config
            override val targetTypes = setOf(WNodeType.FUN, WNodeType.PROPERTY)

            override fun exitNode(
                ctx: WContext,
                children: ChildBuffer,
                reporter: WReporter,
            ) {
                val hasKdoc = children.hasChildOfType(WNodeType.KDOC)
                if (!hasKdoc) return
                val modifierIdx = children.firstChildOfType(WNodeType.MODIFIER_LIST)
                val modifierText = if (modifierIdx < 0) "" else children.textSpan(modifierIdx, ctx.sourceText)
                val isPrivate = WordBoundaryScan.containsWord(modifierText, "private")

                val message =
                    if (ctx.type == WNodeType.FUN) {
                        val nameIdx = children.firstChildOfType(WNodeType.IDENTIFIER)
                        val name =
                            if (nameIdx < 0) {
                                "<anonymous>"
                            } else {
                                IdentifierCasing.unquote(children.textSpan(nameIdx, ctx.sourceText))
                            }
                        CommentOverPrivateDeclarationDecision.decideFunction(hasKdoc, isPrivate, name)
                    } else {
                        CommentOverPrivateDeclarationDecision.decideProperty(hasKdoc, isPrivate)
                    } ?: return

                reporter.report(ruleId, message, ctx.startOffset, ctx.endOffset, this)
            }
        }
    }
}
