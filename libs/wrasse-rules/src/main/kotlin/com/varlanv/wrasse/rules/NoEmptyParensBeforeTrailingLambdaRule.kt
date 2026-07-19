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
 * it }` and `list.map { it }` call the exact same overload — and, where deleting it stays
 * compile-legal, autofixed to remove it entirely.
 *
 * Matched purely syntactically on a `CALL_EXPRESSION`'s own direct children, exactly like
 * upstream ktlint's `unnecessary-parentheses-before-trailing-lambda` (empirically ground-truthed
 * against upstream's own real behavior via a probe harness built against the actual
 * ktlint-ruleset-standard/rule-engine jars, since upstream's own shipped test suite covers only
 * three shapes): the `VALUE_ARGUMENT_LIST` child's own source span must be the literal two
 * characters `"()"` — nothing else, not even whitespace or a comment, which is also why
 * `foo(   )` and `foo(/* x */)` are never candidates at all (empirically verified: upstream
 * itself does not flag either, matching a plain AST-children check with no special-casing) — and
 * the next significant sibling (skipping whitespace/comments) must be a `LAMBDA_ARGUMENT`.
 *
 * Two shapes are exempt entirely (not reported, not fixed), both empirically confirmed against
 * upstream's own real behavior and both because removing the parentheses would silently resolve
 * to a *different* call, not merely restyle this one: whenever the significant sibling
 * immediately before the argument list is itself a `CALL_EXPRESSION` — an invoke-operator chain
 * (`foo()() { }`, upstream issue #3016) or a call already ending in its own trailing lambda
 * (`fooBar { "Hello" }() { "world" }`, upstream issue #2884) — the empty parentheses are the only
 * thing distinguishing "invoke the previous call's result" from "call the previous callee
 * directly with this trailing lambda", so they are load-bearing, not redundant.
 *
 * Reported but never autofixed whenever any whitespace or comment token between the argument
 * list and the lambda contains a newline: deleting the parentheses in that shape is not provably
 * safe — probing a real compile of the fixed output surfaced a genuine, upstream-native
 * corruption bug (upstream applies the deletion anyway): losing the call syntax marker `()`
 * turns what follows into "bare reference, then a newline, then a lambda literal" instead of a
 * trailing-lambda call, which either fails to reparse at all (a property initializer at file
 * scope) or fails to recompile with a genuine compiler error ("Function invocation '<name>(...)'
 * expected", verified with a real `K2JVMCompiler` run) inside a function body. Since an
 * `EOL_COMMENT` between the parentheses and the lambda always forces a newline before the next
 * token, this single newline check also covers that shape without a separate comment-type
 * enumeration (unlike `no-unit-return`'s equivalent bail, which needed one). A same-line block
 * comment or KDoc between the parentheses and the lambda carries no such risk and is autofixed
 * normally, preserving the comment (empirically verified against upstream's own real formatted
 * output).
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
