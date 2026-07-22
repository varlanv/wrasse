package com.varlanv.wrasse.rules

/**
 * Verdict logic for a class KDoc linking one of its own non-public member properties,
 * compiler-free so it is unit-testable without a kotlinc dependency. A link is `[name]`; a link
 * with custom display text (`[name][target]`) points somewhere else entirely and is never a
 * same-name reference.
 */
object KdocReferencesNonPublicPropertyDecision {
    fun isReferenced(kdocText: CharSequence, propertyName: String): Boolean {
        val text = kdocText.toString()
        return text.contains("[$propertyName]") && !text.contains("[$propertyName][")
    }

    fun message(propertyName: String): String =
    "The property '$propertyName' is non-public and should not be referenced from KDoc comments."
}
