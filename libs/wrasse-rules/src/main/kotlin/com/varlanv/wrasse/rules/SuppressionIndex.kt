package com.varlanv.wrasse.rules

/**
 * Per-file accumulator of `@Suppress` regions, fed by [SuppressionCollectorRule] during the walk
 * and consulted by [isSuppressed] at report time.
 * Pure data plus containment matching — no kotlinc dependency, unit-testable standalone.
 *
 * Three region kinds, all uniform positional containment: file-wide (`@file:Suppress`, [markFile]),
 * and declaration/expression-scoped ([addRegion], a `[start, end)` span read off the annotated
 * element's own node span). [WILDCARD_ALL]/[WILDCARD_WRASSE] are matched case-insensitively against
 * every other id (`isRuleIdOrWildcard` callers pass the literal string arg; case-sensitivity for a
 * real rule id is exact).
 */
class SuppressionIndex {
    private var fileWildcard = false
    private val fileRuleIds = mutableSetOf<String>()
    private val wildcardRegions = mutableListOf<Region>()
    private val ruleRegions = mutableMapOf<String, MutableList<Region>>()

    fun markFile(ruleIdOrWildcard: String) {
        if (isWildcard(ruleIdOrWildcard)) {
            fileWildcard = true
        } else {
            fileRuleIds.add(ruleIdOrWildcard)
        }
    }

    fun addRegion(
        ruleIdOrWildcard: String,
        startOffset: Int,
        endOffset: Int,
    ) {
        val region = Region(startOffset, endOffset)
        if (isWildcard(ruleIdOrWildcard)) {
            wildcardRegions.add(region)
        } else {
            ruleRegions.getOrPut(ruleIdOrWildcard) { mutableListOf() }.add(region)
        }
    }

    fun isSuppressed(
        ruleId: String,
        startOffset: Int,
        endOffset: Int,
    ): Boolean {
        if (fileWildcard || ruleId in fileRuleIds) return true
        if (wildcardRegions.any { it.contains(startOffset, endOffset) }) return true
        return ruleRegions[ruleId]?.any { it.contains(startOffset, endOffset) } ?: false
    }

    private class Region(val startOffset: Int, val endOffset: Int) {
        fun contains(otherStart: Int, otherEnd: Int): Boolean =
            otherStart >= startOffset && otherEnd <= endOffset
    }

    companion object {
        private const val WILDCARD_ALL = "all"
        private const val WILDCARD_WRASSE = "wrasse"

        fun isWildcard(value: String): Boolean =
            value.equals(WILDCARD_ALL, ignoreCase = true) || value.equals(WILDCARD_WRASSE, ignoreCase = true)
    }
}
