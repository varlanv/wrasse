package com.varlanv.wrasse.rules

import com.varlanv.wrasse.lang.WEdit
import com.varlanv.wrasse.model.WNodeType

/**
 * Verdict logic for a `val` property that could be declared `const val` instead, compiler-free so
 * it is unit-testable without a kotlinc dependency.
 *
 * [decide] reports a property in an eligible scope whose own initializer is directly a literal
 * constant; it does not itself check the property's declared type, so a written type broader than
 * "primitive or String" (e.g. `Any`) is still reported here — [canAutofix] is the separate,
 * narrower gate on whether inserting `const` is offered as an edit for that same report, since
 * `const` only compiles for `String` and the eight Kotlin primitive types, and never together with
 * `@JvmField` on the same property.
 *
 * [isEligibleScope] reports a property directly at file scope or a direct member of a named object
 * declaration's body — never an anonymous object expression's body, which cannot host a `const`.
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

    fun isEligibleScope(
        parentType: WNodeType,
        grandparentType: WNodeType?,
        greatGrandparentType: WNodeType?,
    ): Boolean {
        val isTopLevel = parentType == WNodeType.FILE
        val isNamedObjectMember = parentType == WNodeType.CLASS_BODY &&
            grandparentType == WNodeType.OBJECT_DECLARATION &&
            greatGrandparentType != WNodeType.OBJECT_LITERAL
        return isTopLevel || isNamedObjectMember
    }

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
