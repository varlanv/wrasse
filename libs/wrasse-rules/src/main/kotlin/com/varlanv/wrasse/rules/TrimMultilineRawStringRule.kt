package com.varlanv.wrasse.rules

import com.varlanv.wrasse.model.WContext
import com.varlanv.wrasse.model.WNodeRule
import com.varlanv.wrasse.model.WNodeType
import com.varlanv.wrasse.model.WReporter
import com.varlanv.wrasse.model.WUninitializedRule
import com.varlanv.wrasse.model.WrasseRuleConfig

/**
 * A raw string literal (`"""..."""`) whose own text spans more than one line, with no
 * `.trimIndent()`/`.trimMargin()` call chained onto it, is reported (see
 * [TrimMultilineRawStringDecision]) at its own span. The chained-call check peeks at the raw
 * source text immediately after the closing `"""` (skipping plain spaces/tabs only) rather than
 * inspecting a parent [WNodeType.DOT_QUALIFIED_EXPRESSION] node, so a comment or newline between
 * the string and its trim call is treated as "not trimmed" — narrower than the upstream rule this
 * derives from, strictly fewer reports.
 *
 * Exempt entirely (matching the upstream rule's own carve-outs): a `const val`'s own direct,
 * unwrapped initializer, and any string literal nested anywhere inside an
 * [WNodeType.ANNOTATION_ENTRY]'s arguments — both contexts require a compile-time constant, so a
 * trim call could never legally be attached there regardless.
 */
class TrimMultilineRawStringRule : WUninitializedRule {
    override val id: String = "trim-multiline-raw-string"

    override fun initRule(config: WrasseRuleConfig): WNodeRule {
        val ruleId = id
        return object : WNodeRule {
            override val id = ruleId
            override val config = config
            override val targetTypes = setOf(WNodeType.STRING_TEMPLATE)

            override fun enterNode(ctx: WContext, reporter: WReporter): Boolean {
                val text = ctx.sourceText.subSequence(ctx.startOffset, ctx.endOffset)
                val isRawWithLineBreak = text.startsWith("\"\"\"") && text.endsWith("\"\"\"") && text.contains('\n')
                val message =
                TrimMultilineRawStringDecision
                        .decide(
                            isRawWithLineBreak = isRawWithLineBreak,
                            isTrimmed = isRawWithLineBreak && isTrimmed(ctx),
                            isExpectedAsConstant = isRawWithLineBreak && isExpectedAsConstant(ctx),
                        )
                    ?: return false
                reporter.report(ruleId, message, ctx.startOffset, ctx.endOffset, this)
                return false
            }

            private fun isTrimmed(ctx: WContext): Boolean {
                val text = ctx.sourceText
                var i = ctx.endOffset
                while (i < text.length && (text[i] == ' ' || text[i] == '\t')) i++
                if (i >= text.length || text[i] != '.') return false
                val rest = text.subSequence(i, minOf(text.length, i + 12)).toString()
                return rest.startsWith(".trimIndent(") || rest.startsWith(".trimMargin(")
            }

            private fun isExpectedAsConstant(ctx: WContext): Boolean {
                if (ctx.hasAncestor(WNodeType.ANNOTATION_ENTRY)) return true
                val ancestors = ctx.ancestors
                if (ancestors.isEmpty || ancestors.peekType() != WNodeType.PROPERTY) return false
                val propertyStart = ancestors.peekStartOffset()
                return WordBoundaryScan.containsWord(ctx.sourceText.subSequence(propertyStart, ctx.startOffset), "const")
            }
        }
    }
}
