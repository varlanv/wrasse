package com.varlanv.wrasse.rules

/**
 * A lambda's own parameter name must be lowerCamelCase or a lone `_` (the language's own
 * unused-destructuring-component marker) — the sole upstream default pattern. Lambdas cannot
 * carry `override`, so unlike [FunctionParameterNamingDecision] no such exemption applies.
 */
object LambdaParameterNamingDecision {
    const val MESSAGE = "Lambda parameter name should start with a lowercase letter and use camel case, or be '_'"

    fun decide(identifierText: CharSequence): String? {
        if (identifierText.contentEquals("_")) return null
        if (IdentifierCasing.isBacktickKeyword(identifierText)) return null
        val unquoted = IdentifierCasing.unquote(identifierText)
        if (unquoted == "_") return null
        return if (IdentifierCasing.isLowerCamelCase(unquoted)) null else MESSAGE
    }
}
