package com.varlanv.wrasse.rules

import com.varlanv.wrasse.lang.WEdit

/** [reportStart]/[reportEnd] locate the diagnostic; [edits] is empty for a report-only bail. */
class ExplicitItLambdaParameterVerdict(
    val reportStart: Int,
    val reportEnd: Int,
    val message: String,
    val edits: List<WEdit>,
)

/**
 * Verdict logic for a lambda's own single value parameter explicitly named `it`, compiler-free so
 * it is unit-testable without a kotlinc dependency.
 *
 * An untyped `it ->` is always redundant and safe to delete: whether the parameter is declared
 * explicitly or left implicit, it still names the exact same single slot, so deleting the
 * declaration changes neither what `it` refers to inside this lambda nor whether a nested lambda's
 * own implicit `it` shadows it (that shadowing depends only on the name `it` itself, which is
 * identical either way). A typed `it: Type ->` is never autofixed: whether that type annotation
 * can be dropped depends on whether the lambda's use site target-types it well enough for the
 * compiler to re-infer the same type from an implicit `it` alone — a call-site fact this
 * compiler-free check has no way to establish, so it always bails to a report.
 *
 * [decide] deletes from [lbraceEnd] (the position right after the lambda's own opening `{`, not
 * [vpListStart]) through [arrowEnd]: swallowing the whitespace between `{` and the parameter along
 * with the parameter and arrow themselves, while leaving every character from [arrowEnd] onward —
 * including the original whitespace or newline before the lambda body — completely untouched. That
 * asymmetry is what keeps the fix safe on a multiline lambda (`{ it ->\n    it.f()\n}`): the body's
 * own leading newline and indentation are never part of the deleted span, so they survive the fix
 * unchanged and the result never needs re-indenting.
 */
object ExplicitItLambdaParameterDecision {
    const val UNTYPED_MESSAGE = "Explicit 'it' lambda parameter is redundant"
    const val TYPED_MESSAGE = "Explicit 'it' lambda parameter with a declared type may not be safely inferred if removed"

    fun decide(
        vpListStart: Int,
        lbraceEnd: Int,
        arrowEnd: Int,
        hasType: Boolean,
        hasComment: Boolean,
    ): ExplicitItLambdaParameterVerdict {
        if (hasType) {
            return ExplicitItLambdaParameterVerdict(vpListStart, arrowEnd, TYPED_MESSAGE, emptyList())
        }
        val edits = if (hasComment) emptyList() else listOf(WEdit(lbraceEnd, arrowEnd, ""))
        return ExplicitItLambdaParameterVerdict(vpListStart, arrowEnd, UNTYPED_MESSAGE, edits)
    }
}
