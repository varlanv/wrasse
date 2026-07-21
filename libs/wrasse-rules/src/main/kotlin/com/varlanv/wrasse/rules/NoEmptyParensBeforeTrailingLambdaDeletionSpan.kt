package com.varlanv.wrasse.rules

import com.varlanv.wrasse.lang.WEdit

/**
 * Deletion-span policy for a redundant empty argument list `()` immediately before a trailing
 * lambda, compiler-free so it is unit-testable without a kotlinc dependency.
 *
 * Only called for the shape [NoEmptyParensBeforeTrailingLambdaRule] has already proven safe: the
 * argument list's own source span is the literal two characters `()`, nothing else — no whitespace,
 * no comment. Deleting exactly that span (`parensStart` to `parensEnd`) collapses `foo() { it }` to
 * `foo { it }`; whatever sits on either side (the callee, the space or comment before the lambda)
 * is never touched, so `foo()  { it }` collapses to `foo  { it }` with the extra space preserved.
 */
object NoEmptyParensBeforeTrailingLambdaDeletionSpan {
    fun compute(parensStart: Int, parensEnd: Int): WEdit = WEdit(parensStart, parensEnd, "")
}
