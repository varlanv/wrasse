package com.varlanv.wrasse.rules

/**
 * Verdict logic for an `also { }` block whose every statement is itself `it`-qualified
 * (`it.foo()`, `it?.bar()`), compiler-free so it is unit-testable without a kotlinc dependency.
 * Report-only: rewriting to `apply { }` and dropping every `it` is a mechanical-looking change
 * this rule still leaves to the author, matching the upstream rule this derives from.
 */
object AlsoCouldBeApplyDecision {
    const val MESSAGE = "This 'also' block contains only 'it'-qualified statements; consider 'apply' instead"

    fun decide(
        calleeText: CharSequence,
        lambdaCount: Int,
        statementCount: Int,
        allItQualified: Boolean,
    ): String? {
        if (!calleeText.contentEquals("also")) return null
        if (lambdaCount != 1) return null
        if (statementCount == 0 || !allItQualified) return null
        return MESSAGE
    }
}
