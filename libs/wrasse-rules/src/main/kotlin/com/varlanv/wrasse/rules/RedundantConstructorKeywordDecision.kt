package com.varlanv.wrasse.rules

import com.varlanv.wrasse.lang.WEdit

/**
 * Deletion-span policy for a primary constructor's own redundant `constructor` keyword,
 * compiler-free so it is unit-testable without a kotlinc dependency.
 *
 * [decide] deletes from [deletionStart] (the offset right after whatever real token precedes the
 * keyword, collapsing all of the intervening whitespace) through [keywordEnd], leaving everything
 * from there onward — typically `(`, directly adjacent — completely untouched.
 */
object RedundantConstructorKeywordDecision {
    const val MESSAGE = "Redundant constructor keyword"

    fun decide(deletionStart: Int, keywordEnd: Int): List<WEdit> = listOf(WEdit(deletionStart, keywordEnd, ""))
}
