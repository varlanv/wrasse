package com.varlanv.wrasse.rules

/**
 * Parses a `catch` clause's own `VALUE_PARAMETER_LIST` span text (e.g. `"(e: Exception)"`) into
 * its parameter name and declared type text, compiler-free so it is unit-testable without a
 * kotlinc dependency. Requires the exact `(name: Type)` shape with no annotation on the
 * parameter — an annotated catch parameter (`catch (@Suppress("x") e: Exception)`) never matches,
 * a narrower, false-negative-only simplification every catch-based rule in this batch shares.
 */
object CatchParameterText {
    private val PATTERN = Regex("""^\(\s*(`[^`]+`|[A-Za-z_$][\w$]*)\s*:\s*(.+?)\s*\)$""")

    fun parse(valueParameterListText: CharSequence): CatchParameterFacts? {
        val match = PATTERN.matchEntire(valueParameterListText.toString()) ?: return null
        val nameGroup = match.groups[1]!!
        val rawName = nameGroup.value
        val name =
            if (rawName.startsWith("`") && rawName.endsWith("`")) rawName.substring(1, rawName.length - 1) else rawName
        return CatchParameterFacts(
            name = name,
            typeText = match.groups[2]!!.value,
            nameStart = nameGroup.range.first,
            nameEnd = nameGroup.range.last + 1,
        )
    }
}

/** [nameStart]/[nameEnd] are relative to the parsed `VALUE_PARAMETER_LIST`'s own start offset. */
class CatchParameterFacts(
    val name: String,
    val typeText: String,
    val nameStart: Int,
    val nameEnd: Int,
)
