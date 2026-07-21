package com.varlanv.wrasse.rules

import com.varlanv.wrasse.model.WContext
import com.varlanv.wrasse.model.WNodeRule
import com.varlanv.wrasse.model.WNodeType
import com.varlanv.wrasse.model.WReporter
import com.varlanv.wrasse.model.WUninitializedRule
import com.varlanv.wrasse.model.WrasseRuleConfig

private val ESCAPE_SEQUENCES = charArrayOf('t', '"', '\\', 'n')

/**
 * A non-raw string literal carrying more than two `\t`/`\"`/`\\`/`\n` escape sequences is
 * reported (see [StringShouldBeRawStringDecision]) at its own span, scanning its full raw text
 * (quotes and any interpolation markup included) exactly like the upstream rule this derives
 * from.
 *
 * Exempt: an already-raw (`"""`) string, an empty `""` literal, a string that is one operand of a
 * `+` concatenation (the upstream rule's own multi-piece "pivot element" analysis — where several
 * adjacent concatenated pieces are jointly counted — is dropped entirely; only a syntactically
 * standalone string is evaluated on its own escape count, narrower and strictly fewer reports),
 * and a string passed as an argument to `replaceIndent(...)`/`prependIndent(...)` (checked via the
 * nearest enclosing [WNodeType.CALL_EXPRESSION] ancestor's own callee name).
 */
class StringShouldBeRawStringRule : WUninitializedRule {
    override val id: String = "string-should-be-raw-string"

    override fun initRule(config: WrasseRuleConfig): WNodeRule {
        val ruleId = id
        return object : WNodeRule {
            override val id = ruleId
            override val config = config
            override val targetTypes = setOf(WNodeType.STRING_TEMPLATE)

            override fun enterNode(ctx: WContext, reporter: WReporter): Boolean {
                val text = ctx.sourceText.subSequence(ctx.startOffset, ctx.endOffset)
                if (text.startsWith("\"\"\"")) return false
                if (text.length <= 2) return false
                if (ctx.ancestors.peekType() == WNodeType.BINARY_EXPRESSION) return false
                if (isArgumentOfAllowedMethod(ctx)) return false

                val message = StringShouldBeRawStringDecision.decide(countEscapes(text)) ?: return false
                reporter.report(ruleId, message, ctx.startOffset, ctx.endOffset, this)
                return false
            }

            private fun countEscapes(text: CharSequence): Int {
                var count = 0
                var i = 0
                while (i < text.length - 1) {
                    if (text[i] == '\\' && text[i + 1] in ESCAPE_SEQUENCES) {
                        count++
                        i += 2
                    } else {
                        i++
                    }
                }
                return count
            }

            private fun isArgumentOfAllowedMethod(ctx: WContext): Boolean {
                val ancestors = ctx.ancestors
                for (i in ancestors.size - 1 downTo 0) {
                    if (ancestors.typeAt(i) != WNodeType.CALL_EXPRESSION) continue
                    val span = ctx.sourceText.subSequence(ancestors.startOffsetAt(i), ancestors.endOffsetAt(i))
                    val parenIdx = WordBoundaryScan.indexOfChar(span, '(')
                    if (parenIdx < 0) return false
                    val callee = span.subSequence(0, parenIdx).toString().trim()
                    return callee == "replaceIndent" || callee == "prependIndent"
                }
                return false
            }
        }
    }
}
