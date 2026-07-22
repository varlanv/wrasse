package com.varlanv.wrasse.rules

/**
 * Verdict logic for a file whose `PACKAGE_DIRECTIVE` carries no dotted name, compiler-free so it
 * is unit-testable without a kotlinc dependency.
 */
object MissingPackageDeclarationDecision {
    const val MESSAGE = "Kotlin source files should define a package"

    fun decide(hasPackageName: Boolean): String? = if (hasPackageName) null else MESSAGE
}
