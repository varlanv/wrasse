package com.varlanv.wrasse.rules

/**
 * An enum entry name is valid PascalCase or SCREAMING_SNAKE_CASE — the union of the two most
 * common enum-entry conventions, accepted side by side rather than picking one over the other.
 */
object EnumEntryNamingDecision {
    const val MESSAGE = "Enum entry name should be PascalCase ('EnumEntry') or SCREAMING_SNAKE_CASE ('ENUM_ENTRY')"

    fun decide(identifierText: CharSequence): String? {
        val unquoted = IdentifierCasing.unquote(identifierText)
        return if (IdentifierCasing.isPascalCase(unquoted) || IdentifierCasing.isScreamingSnakeCase(unquoted)) {
            null
        } else {
            MESSAGE
        }
    }
}
