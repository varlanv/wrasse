package com.varlanv.wrasse.rules

/**
 * Verdict logic for a `val` property that could be declared `const val` instead, compiler-free so
 * it is unit-testable without a kotlinc dependency.
 *
 * Narrowed from the upstream rule this derives from: only a property whose own initializer is
 * directly a literal constant is considered — the upstream extension that also folds a binary
 * expression of two already-constant-foldable operands, including a bare reference to another
 * already-declared file-scope constant, needs either a whole-file forward-reference pre-pass or
 * unbounded recursive descent through nested binary/paren chains, neither of which this
 * single-pass, direct-initializer check attempts. Report-only: adding `const` changes the
 * property's own compiled representation (inlined at every call site), a decision this rule
 * leaves to the author.
 */
object MayBeConstantDecision {
    fun decide(
        eligibleScope: Boolean,
        isVar: Boolean,
        isAlreadyConst: Boolean,
        isActual: Boolean,
        isOverride: Boolean,
        hasGetter: Boolean,
        hasNonJvmFieldAnnotation: Boolean,
        initializerIsConstant: Boolean,
        propertyName: String,
    ): String? {
        if (!eligibleScope || isVar || isAlreadyConst || isActual || isOverride || hasGetter || hasNonJvmFieldAnnotation) return null
        if (!initializerIsConstant) return null
        return "Property '$propertyName' can be a 'const val'"
    }
}
