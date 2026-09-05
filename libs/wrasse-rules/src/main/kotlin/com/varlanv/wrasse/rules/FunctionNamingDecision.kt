package com.varlanv.wrasse.rules

/**
 * A function identifier is valid lowerCamelCase, or a backtick-wrapped keyword, or exempt
 * because it is an override or a factory function (same name as its return type).
 *
 * In a file that imports a known test library ([isTestLibraryImported]), any backtick-wrapped
 * name is also allowed, and a plain name may additionally contain underscores
 * (`` `should do X` `` or `should_do_x`).
 */
object FunctionNamingDecision {
    const val MESSAGE = "Function name should start with a lowercase letter (except factory methods) and use camel case"

    fun decide(
        identifierText: CharSequence,
        isOverride: Boolean,
        isFactory: Boolean,
        isTestLibraryImported: Boolean,
    ): String? {
        if (isOverride || isFactory) return null
        if (IdentifierCasing.isBacktickKeyword(identifierText)) return null
        if (isTestLibraryImported) {
            if (IdentifierCasing.isBacktickWrapped(identifierText)) return null
            val unquoted = IdentifierCasing.unquote(identifierText)
            return if (isLowerCamelCaseWithUnderscores(unquoted)) null else MESSAGE
        }
        val unquoted = IdentifierCasing.unquote(identifierText)
        return if (IdentifierCasing.isLowerCamelCase(unquoted)) null else MESSAGE
    }

    private fun isLowerCamelCaseWithUnderscores(text: String): Boolean {
        if (text.isEmpty() || !text[0].isLowerCase()) return false
        return text.drop(1).all { it.isLetterOrDigit() || it == '_' }
    }
}
