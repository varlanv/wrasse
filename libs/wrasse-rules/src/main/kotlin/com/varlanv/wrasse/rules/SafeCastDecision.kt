package com.varlanv.wrasse.rules

/**
 * Verdict logic for an `if (x is T) x else null` / `if (x !is T) null else x` shape, compiler-free
 * so it is unit-testable without a kotlinc dependency. `thenText`/`elseText` are each branch's own
 * single-statement text with any wrapping `{ }` block already stripped by the caller (see
 * [SafeCastRule]); a branch containing anything other than exactly one statement never equals the
 * identifier or `null` after that stripping, so it naturally falls out of scope without a separate
 * "single statement" check. Report-only: rewriting to `as?` changes an `is`-check into a cast
 * expression, which this rule leaves to the author rather than attempting a substitution.
 */
object SafeCastDecision {
    const val MESSAGE = "This if/else can be replaced with a safe cast (as?)"

    fun decide(
        identifier: String,
        negated: Boolean,
        thenText: String,
        elseText: String,
    ): String? {
        val matches =
            if (negated) elseText == identifier && thenText == "null" else thenText == identifier && elseText == "null"
        return if (matches) MESSAGE else null
    }
}
