package com.varlanv.wrasse.rules

/**
 * A leading-underscore member property (other than the bare `_`, and never an override) must be
 * `_` followed by lowerCamelCase. When a same-class-body sibling property or single-expression
 * getter function correlates by name, that sibling must also be public; when no such sibling is
 * reachable in wrasse's per-class-body view (e.g. it lives in an outer class via a companion
 * object indirection), the visibility check is skipped rather than guessed.
 */
object BackingPropertyNamingDecision {
    const val SHAPE_MESSAGE = "Backing property should start with underscore followed by lower camel case"
    const val VISIBILITY_MESSAGE = "Backing property is only allowed when the matching property or function is public"

    fun decide(identifierText: CharSequence, hasOverride: Boolean, correlatedMemberIsPublic: Boolean?): String? {
        if (hasOverride) return null
        val unquoted = IdentifierCasing.unquote(identifierText)
        if (!unquoted.startsWith("_") || unquoted == "_") return null
        val rest = unquoted.removePrefix("_")
        if (!IdentifierCasing.isLowerCamelCase(rest)) return SHAPE_MESSAGE
        if (correlatedMemberIsPublic == null) return null
        return if (correlatedMemberIsPublic) null else VISIBILITY_MESSAGE
    }
}
