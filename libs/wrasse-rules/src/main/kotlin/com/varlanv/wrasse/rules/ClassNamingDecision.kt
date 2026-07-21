package com.varlanv.wrasse.rules

/**
 * A class, interface, or object identifier is valid PascalCase, or a backtick-wrapped keyword,
 * or (only when [isJUnitJupiterImported]) any backtick-wrapped, non-empty name.
 */
object ClassNamingDecision {
    const val MESSAGE = "Class or object name should start with an uppercase letter and use camel case"

    fun decide(identifierText: CharSequence, isJUnitJupiterImported: Boolean): String? {
        if (IdentifierCasing.isBacktickKeyword(identifierText)) return null
        if (isJUnitJupiterImported && IdentifierCasing.isBacktickWrapped(identifierText)) return null
        val unquoted = IdentifierCasing.unquote(identifierText)
        return if (IdentifierCasing.isPascalCase(unquoted)) null else MESSAGE
    }
}
