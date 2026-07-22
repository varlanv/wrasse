package com.varlanv.wrasse.rules

/**
 * Parses an integer/float literal's own raw token text (underscores, `L`/`U`/`UL`/`F`/`D`
 * suffixes, `0x`/`0b` prefixes) into its numeric value, mirroring the upstream rule this derives
 * from exactly so the two agree on which literals are ignore-listed.
 */
object NumericLiteralValue {
    private const val HEX_RADIX = 16
    private const val BINARY_RADIX = 2

    fun parse(rawText: CharSequence): Double? {
        val normalized = rawText
            .toString()
            .trim()
            .lowercase()
            .replace("_", "")
            .removeSuffix("ul")
            .removeSuffix("l")
            .removeSuffix("d")
            .removeSuffix("f")
            .removeSuffix("u")
        return try {
            when {
                normalized.startsWith("0x") -> normalized.substring(2).toLong(HEX_RADIX).toDouble()
                normalized.startsWith("0b") -> normalized.substring(2).toLong(BINARY_RADIX).toDouble()
                else -> normalized.toDouble()
            }
        } catch (e: NumberFormatException) {
            null
        }
    }
}
