package com.varlanv.wrasse.rules

/** A constructor parameter name must be lowerCamelCase (sole upstream default pattern), unless it is `override`-fixed. */
object ConstructorParameterNamingDecision {
    const val MESSAGE = "Constructor parameter name should start with a lowercase letter and use camel case"

    fun decide(identifierText: CharSequence, hasOverride: Boolean): String? {
        if (hasOverride) return null
        if (IdentifierCasing.isBacktickKeyword(identifierText)) return null
        val unquoted = IdentifierCasing.unquote(identifierText)
        return if (IdentifierCasing.isLowerCamelCase(unquoted)) null else MESSAGE
    }
}
