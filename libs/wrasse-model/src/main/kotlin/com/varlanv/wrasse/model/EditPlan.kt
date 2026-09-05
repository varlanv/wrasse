package com.varlanv.wrasse.model

import com.varlanv.wrasse.lang.WEdit

/**
 * Per-file collector of attributed fix edits. Rules never touch this directly for
 * reporting — [WReporter.report] forwards each attached [WEdit] here, tagged with the
 * reporting rule's id and its collection sequence.
 *
 * Because the walk is post-order (children exit before parents), every edit inside a node's
 * span already sits in the plan by the time that node's own rule exits. A composing rule calls
 * [takeEditsIn] to pull those inner entries out, folds them into its own rewrite, and reports
 * one edit for the whole span — which flows back in through the same [add] call. No rule
 * ordering, no priorities: nesting order on the walk is the only protocol.
 *
 * Entries are kept ordered by span (start, then end), with same-span ties broken by
 * descending collection sequence — the order in which same-offset insertions must be handed
 * to [com.varlanv.wrasse.lang.WPatchWriter] for the applier to reproduce collection order in
 * the output (later-collected must be spliced first so earlier-collected ends up leftmost).
 * [finalEdits] additionally validates that whatever survives to end-of-walk is pairwise
 * disjoint under that same ordering.
 */
class EditPlan {
    /** One collected edit, attributed to the rule that reported it and its arrival order. */
    class Entry(
        val ruleId: String,
        val edit: WEdit,
        val sequence: Int,
    )

    private val entries = mutableListOf<Entry>()
    private var nextSequence = 0

    fun add(ruleId: String, edit: WEdit) {
        val entry = Entry(ruleId, edit, nextSequence++)
        var low = 0
        var high = entries.size
        while (low < high) {
            val mid = (low + high) ushr 1
            if (precedes(entry, entries[mid])) high = mid else low = mid + 1
        }
        var probe = low
        while (probe < entries.size && sameSpan(entries[probe].edit, edit)) {
            if (entries[probe].edit.replacement == edit.replacement) return
            probe++
        }
        entries.add(low, entry)
    }

    /**
     * Removes and returns every currently-collected entry, in the plan's own span order. Used
     * only by the printer (`DocBuilder.finish`), the single consumer that must observe the plan's
     * final, fully-collected state before deciding whether it can splice all of it.
     */
    fun takeAll(): List<Entry> {
        val all = entries.toList()
        entries.clear()
        return all
    }

    /** Puts back entries previously removed by [takeAll], verbatim, when a consumer declines them. */
    fun restore(taken: List<Entry>) {
        entries.addAll(taken)
    }

    fun takeEditsIn(startOffset: Int, endOffset: Int): List<Entry> {
        if (entries.isEmpty()) return emptyList()
        val taken = mutableListOf<Entry>()
        val iterator = entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (entry.edit.startOffset >= startOffset && entry.edit.endOffset <= endOffset) {
                taken.add(entry)
                iterator.remove()
            }
        }
        return taken
    }

    fun finalEdits(): List<WEdit> {
        for (i in 0 until entries.size - 1) {
            val current = entries[i]
            val next = entries[i + 1]
            check(next.edit.startOffset >= current.edit.endOffset) {
                "EditPlan: overlapping edits from rule '${current.ruleId}' " +
                    "(${current.edit.startOffset}..${current.edit.endOffset} -> \"${current.edit.replacement}\") " +
                    "and rule '${next.ruleId}' (${next.edit.startOffset}..${next.edit.endOffset} -> \"${next.edit.replacement}\")"
            }
        }
        return entries.map { it.edit }
    }

    private fun sameSpan(a: WEdit, b: WEdit): Boolean = a.startOffset == b.startOffset && a.endOffset == b.endOffset

    private fun precedes(a: Entry, b: Entry): Boolean {
        if (a.edit.startOffset != b.edit.startOffset) return a.edit.startOffset < b.edit.startOffset
        if (a.edit.endOffset != b.edit.endOffset) return a.edit.endOffset < b.edit.endOffset
        return a.sequence > b.sequence
    }
}
