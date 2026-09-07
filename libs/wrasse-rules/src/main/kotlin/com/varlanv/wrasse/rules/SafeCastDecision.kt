package com.varlanv.wrasse.rules

import com.varlanv.wrasse.lang.WEdit

/** `null` means the `if` is out of this rule's scope entirely — no report at all. */
class SafeCastVerdict(val edits: List<WEdit>)

/**
 * Verdict logic for an `if (x is T) x else null` / `if (x !is T) null else x` shape, compiler-free
 * so it is unit-testable without a kotlinc dependency. `thenText`/`elseText` are each branch's own
 * single-statement text with any wrapping `{ }` block already stripped by the caller (see
 * [SafeCastRule]); a branch containing anything other than exactly one statement never equals the
 * identifier or `null` after that stripping, so it naturally falls out of scope without a separate
 * "single statement" check.
 *
 * [decide] replaces the whole `if` expression's span (braces included, when present) with
 * `<identifier> as? <typeText>` — always safe once the branches already match this exact shape,
 * since the subject is used verbatim on both sides of the check.
 */
object SafeCastDecision {
    const val MESSAGE = "This if/else can be replaced with a safe cast (as?)"

    fun decide(
        identifier: String,
        negated: Boolean,
        thenText: String,
        elseText: String,
        typeText: String,
        replaceStart: Int,
        replaceEnd: Int,
    ): SafeCastVerdict? {
        val matches =
            if (negated) elseText == identifier && thenText == "null" else thenText == identifier && elseText == "null"
        if (!matches) return null
        return SafeCastVerdict(listOf(WEdit(replaceStart, replaceEnd, "$identifier as? $typeText")))
    }
}
