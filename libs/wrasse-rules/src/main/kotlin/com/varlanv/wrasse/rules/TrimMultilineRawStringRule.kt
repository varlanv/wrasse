package com.varlanv.wrasse.rules

import com.varlanv.wrasse.model.WContext
import com.varlanv.wrasse.model.WNodeRule
import com.varlanv.wrasse.model.WNodeType
import com.varlanv.wrasse.model.WReporter
import com.varlanv.wrasse.model.WUninitializedRule
import com.varlanv.wrasse.model.WrasseRuleConfig

private val TARGET_TYPES = setOf(WNodeType.STRING_TEMPLATE)

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
 * unwrapped initializer, any string literal nested anywhere inside an
 * [WNodeType.ANNOTATION_ENTRY]'s arguments, and an `annotation class`'s own primary-constructor
 * parameter default value — all three contexts require a compile-time constant, so a trim call
 * could never legally be attached there regardless.
 */
class TrimMultilineRawStringRule : WUninitializedRule {
    override val id: String = "trim-multiline-raw-string"

    override fun initRule(config: WrasseRuleConfig): WNodeRule {
        val ruleId = id
        return object : WNodeRule {
            override val id = ruleId
            override val config = config
            override val targetTypes = TARGET_TYPES

            override fun enterNode(ctx: WContext, reporter: WReporter): Boolean {
                val text = ctx.sourceText.subSequence(ctx.startOffset, ctx.endOffset)
                val isRawWithLineBreak = text.startsWith("\"\"\"") && text.endsWith("\"\"\"") && text.contains('\n')
                val message =
                    TrimMultilineRawStringDecision.decide(
                        isRawWithLineBreak = isRawWithLineBreak,
                        isTrimmed = isRawWithLineBreak && isTrimmed(ctx),
                        isExpectedAsConstant = isRawWithLineBreak && isExpectedAsConstant(ctx),
                    ) ?: return false
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
                if (isAnnotationClassConstructorParamDefault(ctx)) return true
                val ancestors = ctx.ancestors
                if (ancestors.isEmpty || ancestors.peekType() != WNodeType.PROPERTY) return false
                val propertyStart = ancestors.peekStartOffset()
                return WordBoundaryScan.containsWord(
                    ctx.sourceText.subSequence(propertyStart, ctx.startOffset),
                    "const",
                )
            }

            private fun isAnnotationClassConstructorParamDefault(ctx: WContext): Boolean {
                val ancestors = ctx.ancestors
                for (i in ancestors.size - 1 downTo 1) {
                    if (ancestors.typeAt(i) == WNodeType.PRIMARY_CONSTRUCTOR &&
                        ancestors.typeAt(i - 1) == WNodeType.CLASS) {
                        val classStart = ancestors.startOffsetAt(i - 1)
                        val constructorStart = ancestors.startOffsetAt(i)
                        return WordBoundaryScan.containsWord(
                            ctx.sourceText.subSequence(classStart, constructorStart),
                            "annotation",
                        )
                    }
                }
                return false
            }
        }
    }
}
