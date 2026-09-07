package com.varlanv.wrasse.rules

import com.varlanv.wrasse.lang.WEdit

/**
 * Verdict logic for a `val` property that could be declared `const val` instead, compiler-free so
 * it is unit-testable without a kotlinc dependency.
 *
 * Narrowed from the upstream rule this derives from: only a property whose own initializer is
 * directly a literal constant is considered — the upstream extension that also folds a binary
 * expression of two already-constant-foldable operands, including a bare reference to another
 * already-declared file-scope constant, needs either a whole-file forward-reference pre-pass or
 * unbounded recursive descent through nested binary/paren chains, neither of which this
 * single-pass, direct-initializer check attempts.
 *
 * [decide] alone gates whether the property is reported at all; it does not itself check the
 * property's declared type, so a written type broader than "primitive or String" (e.g. `Any`) is
 * still reported here — [canAutofix] is the separate, narrower gate on whether inserting `const`
 * is offered as an edit for that same report, since `const` only compiles for `String` and the
 * eight Kotlin primitive types, and never together with `@JvmField` on the same property.
 *
 * [autofixEdit] replaces the gap between the `val` keyword and the property name with `const `
 * plus that same gap's own text, verbatim — a wider span than a bare insertion at the keyword
 * would need, but one that reaches the name the caller reports at, so the edit is recognized as
 * covering that report.
 */
object MayBeConstantDecision {
    private val PRIMITIVE_OR_STRING_TYPES = setOf(
        "Boolean",
        "Byte",
        "Short",
        "Int",
        "Long",
        "Float",
        "Double",
        "Char",
        "String",
    )

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
        if (!eligibleScope ||
            isVar ||
            isAlreadyConst ||
            isActual ||
            isOverride ||
            hasGetter ||
            hasNonJvmFieldAnnotation) {
            return null
        }
        if (!initializerIsConstant) return null
        return "Property '$propertyName' can be a 'const val'"
    }

    fun canAutofix(hasJvmFieldAnnotation: Boolean, declaredType: String?): Boolean =
        !hasJvmFieldAnnotation && (declaredType == null || declaredType in PRIMITIVE_OR_STRING_TYPES)

    fun autofixEdit(
        valKeywordStart: Int,
        nameStart: Int,
        textBetween: CharSequence,
    ): WEdit = WEdit(valKeywordStart, nameStart, "const $textBetween")
}
