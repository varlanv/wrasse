package com.varlanv.wrasse.rules

import com.varlanv.wrasse.model.ChildBuffer
import com.varlanv.wrasse.model.WBufferedNodeRule
import com.varlanv.wrasse.model.WContext
import com.varlanv.wrasse.model.WNodeType
import com.varlanv.wrasse.model.WReporter
import com.varlanv.wrasse.model.WUninitializedRule
import com.varlanv.wrasse.model.WrasseRuleConfig
import com.varlanv.wrasse.model.isWhitespaceOrComment

/**
 * A primary constructor's own `constructor` keyword is removed when it carries no annotation and
 * no visibility modifier of its own — the keyword is then pure syntax noise, since Kotlin only
 * ever requires it to attach one of those two things to the constructor.
 *
 * Never reported at all (not merely left unfixed) when a comment sits anywhere in the class
 * header's own trivia between its name (or type parameter list) and the keyword: which side of
 * the keyword such a comment "belongs" to is a judgment call this rule declines to make.
 */
class RedundantConstructorKeywordRule : WUninitializedRule {
    override val id: String = "redundant-constructor-keyword"
    override val canAutofix: Boolean = true

    override fun initRule(config: WrasseRuleConfig): WBufferedNodeRule {
        val ruleId = id
        return object : WBufferedNodeRule {
            override val id = ruleId
            override val config = config
            override val targetTypes = setOf(WNodeType.CLASS, WNodeType.PRIMARY_CONSTRUCTOR)

            private val pendingKeywords = mutableListOf<PendingKeyword?>()

            override fun enterNode(ctx: WContext, reporter: WReporter): Boolean {
                if (ctx.type == WNodeType.CLASS) {
                    pendingKeywords.add(null)
                }
                return true
            }

            override fun exitNode(ctx: WContext, children: ChildBuffer, reporter: WReporter) {
                when (ctx.type) {
                    WNodeType.PRIMARY_CONSTRUCTOR -> recordConstructor(children)
                    WNodeType.CLASS -> finalizeClass(children, reporter)
                    else -> {}
                }
            }

            private fun recordConstructor(children: ChildBuffer) {
                if (pendingKeywords.isEmpty() || children.hasChildOfType(WNodeType.MODIFIER_LIST)) return
                val kwIdx = children.firstChildOfType(WNodeType.KW_CONSTRUCTOR)
                if (kwIdx < 0) return
                pendingKeywords[pendingKeywords.size - 1] = PendingKeyword(children.startOffset(kwIdx), children.endOffset(kwIdx))
            }

            private fun finalizeClass(children: ChildBuffer, reporter: WReporter) {
                val pending = pendingKeywords.removeAt(pendingKeywords.size - 1) ?: return
                val ctorIdx = children.firstChildOfType(WNodeType.PRIMARY_CONSTRUCTOR)
                if (ctorIdx < 0) return

                var i = ctorIdx - 1
                var hasComment = false
                while (i >= 0 && children.type(i).isWhitespaceOrComment) {
                    if (children.type(i) != WNodeType.WHITE_SPACE) hasComment = true
                    i--
                }
                if (hasComment) return

                val deletionStart = if (i >= 0) children.endOffset(i) else pending.keywordStart
                reporter
                    .report(
                        ruleId,
                        RedundantConstructorKeywordDecision.MESSAGE,
                        pending.keywordStart,
                        pending.keywordEnd,
                        this,
                        edits = RedundantConstructorKeywordDecision.decide(deletionStart, pending.keywordEnd),
                    )
            }
        }
    }

    private class PendingKeyword(val keywordStart: Int, val keywordEnd: Int)
}
