package com.varlanv.wrasse.rules

/**
 * Verdict logic for `throw NotImplementedError(...)` and `TODO(...)` stub markers,
 * compiler-free so it is unit-testable without a kotlinc dependency.
 */
object NotImplementedDeclarationDecision {
    const val MESSAGE = "The NotImplementedDeclaration should only be used when a method stub is necessary. " +
    "This defers the development of the functionality of this function. " +
        "Hence, the NotImplementedDeclaration should only serve as a temporary declaration. " +
        "Before releasing, this type of declaration should be removed."

    fun decideThrow(calleeName: String): String? = if (calleeName == "NotImplementedError") MESSAGE else null

    fun decideTodoCall(calleeName: String, argumentCount: Int): String? =
        if (calleeName == "TODO" && argumentCount <= 1) MESSAGE else null
}
