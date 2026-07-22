package com.varlanv.wrasse.rules

/**
 * Verdict logic for a KDoc attached to a private function/property, compiler-free so it is
 * unit-testable without a kotlinc dependency. A private declaration's own KDoc suggests it needs
 * explaining, which upstream reads as a signal the declaration itself should be renamed/split
 * instead of documented.
 */
object CommentOverPrivateDeclarationDecision {
    fun decideFunction(hasKdoc: Boolean, isPrivate: Boolean, name: String): String? =
    if (hasKdoc && isPrivate) {
        "The function $name has a comment. Prefer renaming the function giving it a more self-explanatory name."
    } else {
        null
    }

    fun decideProperty(hasKdoc: Boolean, isPrivate: Boolean): String? =
    if (hasKdoc && isPrivate) {
        "Private properties should be named in a self-explanatory manner without the need for a comment."
    } else {
        null
    }
}
