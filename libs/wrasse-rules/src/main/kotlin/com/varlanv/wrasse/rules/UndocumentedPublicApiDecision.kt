package com.varlanv.wrasse.rules

/**
 * Verdict logic for a missing-KDoc report on a top-level public class/function/property,
 * compiler-free so it is unit-testable without a kotlinc dependency. Every check here is scoped
 * to top-level declarations only (never nested/member) — a deliberate narrowing dropping the
 * upstream rules' own container-visibility-inheritance chain entirely: a top-level declaration's
 * effective visibility depends only on its own modifier, nothing else.
 */
object UndocumentedPublicApiDecision {
    fun decideClass(name: String, hasKdoc: Boolean, isPublic: Boolean): String? =
    if (!hasKdoc && isPublic) "$name is missing required documentation." else null

    fun decideFunction(name: String, hasKdoc: Boolean, isPublic: Boolean, isOverride: Boolean): String? =
    if (!hasKdoc && isPublic && !isOverride) "The function $name is missing documentation." else null

    fun decideProperty(name: String, hasKdoc: Boolean, isPublic: Boolean, isOverride: Boolean): String? =
    if (!hasKdoc && isPublic && !isOverride) "The property $name is missing documentation." else null
}
