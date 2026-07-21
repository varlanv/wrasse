package com.varlanv.wrasse.rules

/**
 * Verdict logic for a function whose entire body is a single constant literal, compiler-free so
 * it is unit-testable without a kotlinc dependency. Exempt (hardcoded, matching the upstream
 * rule's own defaults — no config surface exists for these beyond `level`): an `override`d or
 * `open` function, a function declared directly inside an interface (all three: the function's
 * own name may not be a free choice), and an `actual` function (its `expect` counterpart is the
 * authoritative declaration). Report-only: turning the body into a `const val` reference is an
 * authored refactor this rule leaves to the user.
 */
object FunctionOnlyReturningConstantDecision {
    fun decide(
        isOverride: Boolean,
        isOpen: Boolean,
        isActual: Boolean,
        inInterface: Boolean,
        returnsConstant: Boolean,
        functionName: String,
    ): String? {
        if (isOverride || isOpen || inInterface || isActual) return null
        if (!returnsConstant) return null
        return "Function '$functionName' only returns a constant; consider declaring a constant instead"
    }
}
