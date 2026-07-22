package com.varlanv.wrasse.rules

import com.varlanv.wrasse.model.WContext
import com.varlanv.wrasse.model.WNodeRule
import com.varlanv.wrasse.model.WNodeType
import com.varlanv.wrasse.model.WReporter
import com.varlanv.wrasse.model.WUninitializedRule
import com.varlanv.wrasse.model.WrasseRuleConfig

/** See [CustomLabelDecision]. Only a label reference on `return`/`break`/`continue` is a candidate; a loop's own label definition is not. */
class CustomLabelRule : WUninitializedRule {
    override val id: String = "custom-label"

    override fun initRule(config: WrasseRuleConfig): WNodeRule {
        val ruleId = id
        return object : WNodeRule {
            override val id = ruleId
            override val config = config
            override val targetTypes = setOf(WNodeType.LABEL_QUALIFIER)

            override fun enterNode(ctx: WContext, reporter: WReporter): Boolean {
                val parent = ctx.ancestors.peekType()
                if (parent != WNodeType.RETURN && parent != WNodeType.BREAK && parent != WNodeType.CONTINUE) return false

                val labelText = ctx.sourceText.subSequence(ctx.startOffset, ctx.endOffset).toString()
                val labelName = labelText.removePrefix("@")
                val matchesEnclosingCallName = matchesAnyEnclosingCallName(ctx, labelName)
                val count = countEnclosingLoopsOrForEach(ctx)
                val message = CustomLabelDecision.decide(labelText, matchesEnclosingCallName, count) ?: return false
                reporter.report(ruleId, message, ctx.startOffset, ctx.endOffset, this)
                return false
            }

            private fun countEnclosingLoopsOrForEach(ctx: WContext): Int {
                val ancestors = ctx.ancestors
                var count = 0
                for (i in 0 until ancestors.size) {
                    when (ancestors.typeAt(i)) {
                        WNodeType.FOR, WNodeType.WHILE, WNodeType.DO_WHILE -> count++
                        WNodeType.CALL_EXPRESSION ->
                        if (isForEachCallee(ctx.sourceText, ancestors.startOffsetAt(i), ancestors.endOffsetAt(i))) count++

                        else -> {}
                    }
                }
                return count
            }

            private fun matchesAnyEnclosingCallName(ctx: WContext, labelName: String): Boolean {
                val ancestors = ctx.ancestors
                for (i in 0 until ancestors.size) {
                    if (ancestors.typeAt(i) != WNodeType.CALL_EXPRESSION) continue
                    if (calleeName(ctx.sourceText, ancestors.startOffsetAt(i), ancestors.endOffsetAt(i)) == labelName) return true
                }
                return false
            }

            private fun calleeName(sourceText: CharSequence, start: Int, end: Int): String {
                var i = start
                while (i < end && (sourceText[i].isLetterOrDigit() || sourceText[i] == '_')) i++
                return sourceText.subSequence(start, i).toString()
            }

            private fun isForEachCallee(sourceText: CharSequence, start: Int, end: Int): Boolean {
                val limit = minOf(end, start + FOR_EACH_INDEXED.length)
                if (limit <= start) return false
                val text = sourceText.subSequence(start, limit)
                val matchesForEachIndexed = text.startsWith(FOR_EACH_INDEXED)
                val matchesForEach = text.startsWith(FOR_EACH) &&
                    (limit == start + FOR_EACH.length || !text[FOR_EACH.length].isLetterOrDigit())
                return matchesForEachIndexed || matchesForEach
            }
        }
    }

    private companion object {
        const val FOR_EACH = "forEach"
        const val FOR_EACH_INDEXED = "forEachIndexed"
    }
}
