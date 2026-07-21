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
 * An `also { }` call whose lambda body statements are all qualified expressions on `it`
 * (`it.foo()`, `it?.bar()`) is reported (see [AlsoCouldBeApplyDecision]) at the `also` callee's
 * own span. A statement's own qualification is read directly off its raw source text (a leading
 * `it.`/`it?.` cannot be any other identifier, since Kotlin identifier characters would continue
 * past `it` otherwise) rather than inspecting its receiver node, so [BLOCK] never needs its own
 * children buffered beyond this rule's three target types.
 */
class AlsoCouldBeApplyRule : WUninitializedRule {
    override val id: String = "also-could-be-apply"

    override fun initRule(config: WrasseRuleConfig): WBufferedNodeRule {
        val ruleId = id
        return object : WBufferedNodeRule {
            override val id = ruleId
            override val config = config
            override val targetTypes = setOf(WNodeType.CALL_EXPRESSION, WNodeType.FUNCTION_LITERAL, WNodeType.BLOCK)

            private val pendingCalls = mutableListOf<PendingCall>()
            private val pendingLiterals = mutableListOf<LiteralVerdict>()

            override fun enterNode(ctx: WContext, reporter: WReporter): Boolean {
                when (ctx.type) {
                    WNodeType.CALL_EXPRESSION -> pendingCalls.add(PendingCall())
                    WNodeType.FUNCTION_LITERAL -> pendingLiterals.add(LiteralVerdict())
                    WNodeType.BLOCK -> if (ctx.ancestors.peekType() != WNodeType.FUNCTION_LITERAL) return false
                    else -> {}
                }
                return true
            }

            override fun exitNode(ctx: WContext, children: ChildBuffer, reporter: WReporter) {
                when (ctx.type) {
                    WNodeType.BLOCK -> recordBlock(ctx, children)
                    WNodeType.FUNCTION_LITERAL -> finalizeLiteral()
                    WNodeType.CALL_EXPRESSION -> finalizeCall(ctx, children, reporter)
                    else -> {}
                }
            }

            private fun recordBlock(ctx: WContext, children: ChildBuffer) {
                val verdict = pendingLiterals.lastOrNull() ?: return
                var statementCount = 0
                var allQualified = true
                for (i in 0 until children.size) {
                    val type = children.type(i)
                    if (type.isWhitespaceOrComment || type == WNodeType.LBRACE || type == WNodeType.RBRACE || type == WNodeType.SEMICOLON) {
                        continue
                    }
                    statementCount++
                    val text = children.textSpan(i, ctx.sourceText)
                    if (!startsWithItQualifier(text)) allQualified = false
                }
                verdict.statementCount = statementCount
                verdict.allItQualified = allQualified
            }

            private fun startsWithItQualifier(text: CharSequence): Boolean {
                val prefix = "it"
                if (text.length < prefix.length + 1 || !text.subSequence(0, prefix.length).contentEquals(prefix)) return false
                val next = text[prefix.length]
                return next == '.' || (next == '?' && text.length > prefix.length + 1 && text[prefix.length + 1] == '.')
            }

            private fun finalizeLiteral() {
                val verdict = pendingLiterals.removeAt(pendingLiterals.size - 1)
                val call = pendingCalls.lastOrNull() ?: return
                call.lambdaCount++
                call.lastVerdict = verdict
            }

            private fun finalizeCall(ctx: WContext, children: ChildBuffer, reporter: WReporter) {
                val call = pendingCalls.removeAt(pendingCalls.size - 1)
                val calleeIdx = children.firstChildOfType(WNodeType.REFERENCE_EXPRESSION)
                if (calleeIdx < 0) return
                val verdict = call.lastVerdict ?: return
                val calleeText = children.textSpan(calleeIdx, ctx.sourceText)
                val message = AlsoCouldBeApplyDecision.decide(calleeText, call.lambdaCount, verdict.statementCount, verdict.allItQualified)
                    ?: return
                reporter.report(ruleId, message, children.startOffset(calleeIdx), children.endOffset(calleeIdx), this)
            }
        }
    }

    private class PendingCall {
        var lambdaCount: Int = 0
        var lastVerdict: LiteralVerdict? = null
    }

    private class LiteralVerdict {
        var statementCount: Int = 0
        var allItQualified: Boolean = false
    }
}
