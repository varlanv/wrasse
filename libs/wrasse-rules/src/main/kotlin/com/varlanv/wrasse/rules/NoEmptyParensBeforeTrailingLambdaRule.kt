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
 * An empty `()` argument list immediately before a trailing lambda is redundant — `list.map() {
 * it }` and `list.map { it }` call the same overload — and, where deleting it stays compile-legal,
 * autofixed to remove it entirely.
 *
 * Matched purely syntactically on a `CALL_EXPRESSION`'s own direct children: the
 * `VALUE_ARGUMENT_LIST` child's own source span must be exactly `"()"` (nothing else, not even
 * whitespace or a comment), and the next significant sibling (skipping whitespace/comments) must
 * be a `LAMBDA_ARGUMENT`.
 *
 * Exempt entirely (not reported, not fixed) whenever the significant sibling immediately before
 * the argument list is itself a `CALL_EXPRESSION` — an invoke-operator chain (`foo()() { }`) or a
 * call already ending in its own trailing lambda (`fooBar { "Hello" }() { "world" }`) — since the
 * empty parentheses there are the only thing distinguishing "invoke the previous call's result"
 * from "call the previous callee directly with this trailing lambda", so they are load-bearing.
 *
 * Reported but never autofixed whenever any whitespace or comment token between the argument list
 * and the lambda contains a newline: deleting the parentheses there is not provably safe, since
 * losing the `()` call-syntax marker can turn what follows into a bare reference followed by a
 * separate lambda literal rather than a trailing-lambda call. A same-line block comment or KDoc in
 * that gap carries no such risk and is autofixed normally, preserving the comment.
 */
class NoEmptyParensBeforeTrailingLambdaRule : WUninitializedRule {
    override val id: String = "no-empty-parens-before-trailing-lambda"

    override fun initRule(config: WrasseRuleConfig): WBufferedNodeRule {
        val ruleId = id
        return object : WBufferedNodeRule {
            override val id = ruleId
            override val config = config
            override val targetTypes = setOf(WNodeType.CALL_EXPRESSION)

            override fun exitNode(ctx: WContext, children: ChildBuffer, reporter: WReporter) {
                val argsIndex = children.firstChildOfType(WNodeType.VALUE_ARGUMENT_LIST)
                if (argsIndex < 0 || !isEmptyParens(children, argsIndex, ctx.sourceText)) return

                val prevIndex = prevSignificant(children, argsIndex - 1)
                if (prevIndex >= 0 && children.type(prevIndex) == WNodeType.CALL_EXPRESSION) return

                val lambdaIndex = nextSignificant(children, argsIndex + 1)
                if (lambdaIndex < 0 || children.type(lambdaIndex) != WNodeType.LAMBDA_ARGUMENT) return

                val hasNewlineGap = hasNewlineBetween(children, argsIndex + 1, lambdaIndex, ctx.sourceText)

                val parensStart = children.startOffset(argsIndex)
                val parensEnd = children.endOffset(argsIndex)
                val edits =
                    if (hasNewlineGap) {
                        emptyList()
                    } else {
                        listOf(NoEmptyParensBeforeTrailingLambdaDeletionSpan.compute(parensStart, parensEnd))
                    }

                reporter.report(
                    ruleId, "Unnecessary empty parentheses before trailing lambda",
                    parensStart, parensEnd, this,
                    edits = edits
                )
            }

            private fun isEmptyParens(children: ChildBuffer, i: Int, sourceText: CharSequence): Boolean {
                val span = children.textSpan(i, sourceText)
                return span.length == 2 && span[0] == '(' && span[1] == ')'
            }

            private fun prevSignificant(children: ChildBuffer, from: Int): Int {
                var i = from
                while (i >= 0) {
                    if (!children.type(i).isWhitespaceOrComment) return i
                    i--
                }
                return -1
            }

            private fun nextSignificant(children: ChildBuffer, from: Int): Int {
                var i = from
                while (i < children.size) {
                    if (!children.type(i).isWhitespaceOrComment) return i
                    i++
                }
                return -1
            }

            private fun hasNewlineBetween(children: ChildBuffer, from: Int, until: Int, sourceText: CharSequence): Boolean {
                for (i in from until until) {
                    if (children.textSpan(i, sourceText).contains('\n')) return true
                }
                return false
            }
        }
    }
}
