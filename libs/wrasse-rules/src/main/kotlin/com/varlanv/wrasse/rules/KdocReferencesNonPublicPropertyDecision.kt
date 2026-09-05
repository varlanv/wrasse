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

    /**
     * True when some other member (function or property) of the same class body shares
     * [propertyName] and is not private — the KDoc link resolves to that member instead.
     */
    fun hasNonPrivateSameNameMember(
        propertyName: String,
        otherMembers: List<Pair<String, Boolean>>,
    ): Boolean = otherMembers.any { (name, isPrivate) -> name == propertyName && !isPrivate }
}
