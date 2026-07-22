package com.varlanv.wrasse.rules

/** A property/variable name over 64 characters (sole upstream default) is reported, unless `override`-fixed. */
object VariableNameMaxLengthDecision {
    private const val MAXIMUM_LENGTH = 64

    fun decide(identifierText: CharSequence, hasOverride: Boolean): String? {
        if (hasOverride) return null
        val unquoted = IdentifierCasing.unquote(identifierText)
        return if (unquoted.length > MAXIMUM_LENGTH) {
            "Variable name should be at most $MAXIMUM_LENGTH characters long"
        } else {
            null
        }
    }
}
