package com.varlanv.wrasse.rules

import com.varlanv.wrasse.lang.WEdit

/** [edits] is empty for a report-only bail (a comment sits inside the matched span). */
class RangeConventionalVerdict(val reportStart: Int, val reportEnd: Int, val message: String, val edits: List<WEdit>)

/**
 * Verdict logic for wrasse's two conventional-range rewrites, compiler-free so it is
 * unit-testable without a kotlinc dependency.
 *
 * [decideRangeToCall]: a qualified call to the single-argument `rangeTo` function (`a.rangeTo(b)`)
 * is definitionally `a..b` — replaces the whole call expression's span with the receiver's own
 * text, `..`, and the single argument's own text, verbatim.
 *
 * [decideUntil]: a `..` range whose right operand is a `- 1` subtraction (`a..(b - 1)`, any single
 * layer of parentheses around the subtraction left untouched) replaces the `..` operator with
 * `until` — adding a single space on whichever side didn't already have one — and replaces the
 * `b - 1` subexpression with just its own left operand, leaving the surrounding parentheses, if
 * any, exactly where they were.
 *
 * Both bail to a report-only occurrence (empty [RangeConventionalVerdict.edits]) whenever a
 * comment sits anywhere inside the matched span: the replacement text is synthesized from
 * sub-expression spans alone, so a comment in between has nowhere to be preserved.
 */
object RangeConventionalDecision {
    const val RANGE_TO_MESSAGE = "Replace rangeTo call with the .. operator"
    const val UNTIL_MESSAGE = "Replace .. with until"

    fun decideRangeToCall(callStart: Int, callEnd: Int, receiverText: String, argumentText: String, hasComment: Boolean): RangeConventionalVerdict {
        val edits = if (hasComment) emptyList() else listOf(WEdit(callStart, callEnd, "$receiverText..$argumentText"))
        return RangeConventionalVerdict(callStart, callEnd, RANGE_TO_MESSAGE, edits)
    }

    fun decideUntil(
        rangeStart: Int,
        rangeEnd: Int,
        operatorStart: Int,
        operatorEnd: Int,
        hasLeadingSpace: Boolean,
        hasTrailingSpace: Boolean,
        minusOneStart: Int,
        minusOneEnd: Int,
        leftOperandText: String,
        hasComment: Boolean,
    ): RangeConventionalVerdict {
        val edits =
            if (hasComment) {
                emptyList()
            } else {
                val untilText = (if (hasLeadingSpace) "" else " ") + "until" + (if (hasTrailingSpace) "" else " ")
                listOf(WEdit(operatorStart, operatorEnd, untilText), WEdit(minusOneStart, minusOneEnd, leftOperandText))
            }
        return RangeConventionalVerdict(rangeStart, rangeEnd, UNTIL_MESSAGE, edits)
    }
}
