package com.varlanv.wrasse.rules

import com.varlanv.wrasse.lang.WEdit

/**
 * Verdict logic for a numeric literal's own underscore grouping, compiler-free so it is
 * unit-testable without a kotlinc dependency.
 *
 * A literal that already contains an underscore anywhere is left alone entirely — user-chosen
 * grouping is never second-guessed, and no partial regrouping is attempted. Otherwise a decimal,
 * hex (`0x`), or binary (`0b`) integer literal whose digit run is longer than [MAX_LENGTH] is
 * grouped into blocks of [MAX_LENGTH] counted from the right (thousands-style); a float literal is
 * grouped the same way on its real part and, independently, in blocks of [MAX_LENGTH] counted from
 * the left on its fractional part — each part only when that part's own digit run is longer than
 * [MAX_LENGTH]. A float literal in scientific notation (containing `e`/`E`) is out of scope
 * entirely: splitting on `.` alone cannot separate its mantissa from its exponent.
 */
object LongNumericalValuesDecision {
    const val MESSAGE = "Long numerical literal without underscore separators"
    private const val MAX_LENGTH = 3

    fun decideInteger(text: String, startOffset: Int): WEdit? {
        if (text.contains('_')) return null
        val prefix = if (text.startsWith("0x") || text.startsWith("0b")) text.substring(0, 2) else ""
        val suffix = if (text.endsWith("L")) "L" else ""
        val digits = text.substring(prefix.length, text.length - suffix.length)
        if (digits.length <= MAX_LENGTH) return null
        return WEdit(startOffset, startOffset + text.length, prefix + groupFromRight(digits) + suffix)
    }

    fun decideFloat(text: String, startOffset: Int): WEdit? {
        if (text.contains('_')) return null
        if (text.contains('e') || text.contains('E')) return null
        val dotIdx = text.indexOf('.')
        if (dotIdx < 0) return null
        val suffix = when {
            text.endsWith("f") -> "f"
            text.endsWith("F") -> "F"
            else -> ""
        }
        val realPart = text.substring(0, dotIdx)
        val fractionalPart = text.substring(dotIdx + 1, text.length - suffix.length)
        if (realPart.length <= MAX_LENGTH && fractionalPart.length <= MAX_LENGTH) return null
        val groupedReal = if (realPart.length > MAX_LENGTH) groupFromRight(realPart) else realPart
        val groupedFractional =
            if (fractionalPart.length > MAX_LENGTH) groupFromLeft(fractionalPart) else fractionalPart
        return WEdit(startOffset, startOffset + text.length, "$groupedReal.$groupedFractional$suffix")
    }

    private fun groupFromRight(
        digits: String,
    ): String = digits.reversed().chunked(MAX_LENGTH).reversed().joinToString("_") { it.reversed() }

    private fun groupFromLeft(digits: String): String = digits.chunked(MAX_LENGTH).joinToString("_")
}
