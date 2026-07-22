package com.varlanv.wrasse.rules

/** A regular function's own value parameter name must be lowerCamelCase, unless it is `override`-fixed. */
object FunctionParameterNamingDecision {
    const val MESSAGE = "Function parameter name should start with a lowercase letter and use camel case"

    fun decide(identifierText: CharSequence, isOverride: Boolean): String? {
        if (isOverride) return null
        if (IdentifierCasing.isBacktickKeyword(identifierText)) return null
        val unquoted = IdentifierCasing.unquote(identifierText)
        return if (IdentifierCasing.isLowerCamelCase(unquoted)) null else MESSAGE
    }
}
