package com.varlanv.wrasse.rules

/** A property/variable name longer than the threshold ([DEFAULT_THRESHOLD] unless configured) is reported, unless `override`-fixed. */
object VariableNameMaxLengthDecision {
    const val DEFAULT_THRESHOLD = 64

    fun decide(
        identifierText: CharSequence,
        hasOverride: Boolean,
        threshold: Int = DEFAULT_THRESHOLD,
    ): String? {
        if (hasOverride) return null
        val unquoted = IdentifierCasing.unquote(identifierText)
        return if (unquoted.length > threshold) {
            "Variable name should be at most $threshold characters long"
        } else {
            null
        }
    }
}
