package com.varlanv.wrasse.rules

import com.varlanv.wrasse.lang.WEdit

/**
 * One modifier-keyword token inside a `MODIFIER_LIST`, already classified as participating in the
 * canonical order comparison. [canonicalIndex] is its position in wrasse's canonical order (lower
 * sorts first); ties never occur since every comparable keyword is distinct within one list.
 */
class ModifierKeywordOccurrence(val canonicalIndex: Int, val startOffset: Int, val endOffset: Int)

/**
 * Verdict for one `MODIFIER_LIST`'s worth of comparable keywords: [expectedOrder] is the
 * space-joined canonical spelling for the message; [edits] is empty when the list has a comment
 * anywhere in it (report-only), otherwise one same-span replacement per keyword whose own text
 * must change, never touching anything else in the list (whitespace, annotations, `fun`/`value`,
 * a context-parameter list, or a comment).
 */
class ModifierOrderVerdict(val reportStart: Int, val reportEnd: Int, val expectedOrder: String, val edits: List<WEdit>)

/**
 * Pure verdict logic for `modifier-order`, compiler-free and unit-testable without kotlinc.
 *
 * A violation exists only when two or more [keywords] (already filtered by the caller to just the
 * canonically-comparable modifier keyword tokens — never annotations, `fun`/`value`, or a
 * context-parameter list) are out of their [ModifierKeywordOccurrence.canonicalIndex] order; fewer
 * than two is always trivially ordered, and [decide] returns `null`.
 *
 * When a violation exists, each keyword whose canonical position differs from where it currently
 * sits gets its own same-span replacement edit — the specific token's own `[startOffset, endOffset)`
 * swapped for the correct keyword's source text — rather than one edit spanning the whole list, so
 * anything physically between or around the compared keywords is never touched, wherever it sits.
 * [hasComment] (any `EOL_COMMENT`/`BLOCK_COMMENT`/`KDOC` anywhere in the modifier list, not just
 * between keywords) forces [ModifierOrderVerdict.edits] empty — report-only.
 */
object ModifierOrderDecision {
    fun decide(keywords: List<ModifierKeywordOccurrence>, sourceText: CharSequence, hasComment: Boolean): ModifierOrderVerdict? {
        if (keywords.size < 2) return null

        val sorted = keywords.sortedBy { it.canonicalIndex }
        if (isAlreadyOrdered(keywords, sorted)) return null

        val expectedOrder = sorted.joinToString(" ") { sourceText.subSequence(it.startOffset, it.endOffset) }
        val edits =
            if (hasComment) {
                emptyList()
            } else {
                buildList {
                    for (i in keywords.indices) {
                        if (keywords[i].canonicalIndex != sorted[i].canonicalIndex) {
                            val replacement = sourceText.subSequence(sorted[i].startOffset, sorted[i].endOffset).toString()
                            add(WEdit(keywords[i].startOffset, keywords[i].endOffset, replacement))
                        }
                    }
                }
            }

        return ModifierOrderVerdict(
            reportStart = keywords.first().startOffset,
            reportEnd = keywords.last().endOffset,
            expectedOrder = expectedOrder,
            edits = edits,
        )
    }

    private fun isAlreadyOrdered(keywords: List<ModifierKeywordOccurrence>, sorted: List<ModifierKeywordOccurrence>): Boolean {
        for (i in keywords.indices) {
            if (keywords[i].canonicalIndex != sorted[i].canonicalIndex) return false
        }
        return true
    }
}
