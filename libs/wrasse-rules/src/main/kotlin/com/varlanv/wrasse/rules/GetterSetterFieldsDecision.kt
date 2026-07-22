package com.varlanv.wrasse.rules

/**
 * Verdict logic for a property accessor that references its own property's name instead of
 * `field`, compiler-free so it is unit-testable without a kotlinc dependency. Matches the upstream
 * rule this derives from exactly: reports only when a bare (non-dot-qualified) same-named
 * reference is found inside the accessor, that reference is not itself a call's callee (`name()`
 * calling a same-named function is not a self-reference), no same-named local variable was
 * declared earlier in the accessor's own block (a real local shadows the outer property), and the
 * property is not an extension property (an extension property never has a backing `field` to
 * redirect to in the first place).
 */
object GetterSetterFieldsDecision {
    const val MESSAGE = "Property accessor references its own property's name; use 'field' instead to avoid infinite recursion"

    fun decide(foundSelfReference: Boolean, isCallExpressionCallee: Boolean, shadowedByLocalVar: Boolean, isExtensionProperty: Boolean): String? =
    if (foundSelfReference && !isCallExpressionCallee && !shadowedByLocalVar && !isExtensionProperty) MESSAGE else null
}
