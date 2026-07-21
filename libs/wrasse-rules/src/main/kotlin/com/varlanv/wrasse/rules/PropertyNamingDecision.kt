package com.varlanv.wrasse.rules

/**
 * A `const val` must be SCREAMING_SNAKE_CASE (except the JVM-mandated `serialVersionUID`); any
 * other property must be lowerCamelCase, except: a backing property (leading underscore, owned
 * by [BackingPropertyNamingDecision] instead), a property with a custom getter, a top-level
 * `val`, or an object-member `val` — all three left unchecked because immutability, and thus
 * whether SCREAMING_SNAKE_CASE was actually intended, cannot be determined without resolution.
 */
object PropertyNamingDecision {
    const val CONST_MESSAGE = "Property name should use the SCREAMING_SNAKE_CASE notation when the value can not be changed"
    const val MESSAGE = "Property name should start with a lowercase letter and use camel case"

    private const val SERIAL_VERSION_UID = "serialVersionUID"

    fun decide(
        identifierText: CharSequence,
        hasConst: Boolean,
        hasCustomGetter: Boolean,
        isTopLevelVal: Boolean,
        isObjectMemberVal: Boolean,
    ): String? {
        if (IdentifierCasing.isBacktickKeyword(identifierText)) return null
        val unquoted = IdentifierCasing.unquote(identifierText)

        if (hasConst) {
            if (unquoted == SERIAL_VERSION_UID) return null
            return if (IdentifierCasing.isScreamingSnakeCase(unquoted)) null else CONST_MESSAGE
        }
        if (hasCustomGetter || isTopLevelVal || isObjectMemberVal) return null
        if (unquoted.startsWith("_")) return null
        return if (IdentifierCasing.isLowerCamelCase(unquoted)) null else MESSAGE
    }
}
