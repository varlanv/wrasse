package com.varlanv.wrasse.model

import com.varlanv.wrasse.lang.WEdit

/**
 * Per-file collector of attributed fix edits. Rules never touch this directly for
 * reporting — [WReporter.report] forwards each attached [WEdit] here, tagged with the
 * reporting rule's id, its collection sequence, and a group id shared by every edit of that
 * same [WReporter.report] call ([Entry.groupId]): an inseparable multi-edit fix (an OPEN/CLOSE
 * brace-insertion pair, say) must survive or lose overlap resolution as one unit, never split.
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
 *
 * [finalEdits] resolves any surviving overlap rather than failing, via [resolveOverlaps]:
 * entries are grouped by [Entry.groupId], the groups are ranked by their earliest edit (start
 * ascending, then span length descending, then collection sequence ascending — an outer edit
 * wins over one nested inside it at a shared start, and of two identical spans the first
 * reported wins), and kept greedily in that order — a group is kept only when none of its own
 * edits overlaps an edit already kept by an earlier group, otherwise every edit in that group is
 * dropped together. Touching (one edit's end equal to another's start) is not an overlap.
 * Dropped entries are exposed via [droppedEdits] for the `count:dropped-edits` perf counter
 * only: the finding that produced a dropped edit stays reported regardless (it is a diagnostic,
 * never conditioned on its fix surviving overlap resolution), and since the surviving edit
 * already changed that text, a later pass over the fixed file re-reports and this time fixes it.
 * `DocBuilder.finish` runs the same [resolveOverlaps] over its own [takeAll] before splicing, so
 * the printer never sees a pair of overlapping edits either, and hands its own drops to
 * [recordDropped] since those entries never return to this plan for [finalEdits] to see them.
 */
class EditPlan {
    /**
     * One collected edit, attributed to the rule that reported it, its arrival order, and the
     * [WReporter.report] call it came from — every entry sharing [groupId] is kept or dropped
     * as one unit by [resolveOverlaps].
     */
    class Entry(
        val ruleId: String,
        val edit: WEdit,
        val sequence: Int,
        val groupId: Int,
    )

    /** One edit that lost overlap resolution, attributed to its reporting rule. */
    class Dropped(
        val ruleId: String,
        val startOffset: Int,
        val endOffset: Int,
    )

    companion object {
        private val priority = compareBy<Entry> { it.edit.startOffset }
            .thenByDescending { it.edit.endOffset - it.edit.startOffset }
            .thenBy { it.sequence }

        /**
         * See the class-level overlap-resolution rules: candidates are grouped by
         * [Entry.groupId] and whole groups are kept or dropped together. Kept entries are
         * returned in [candidates]' own order.
         */
        fun resolveOverlaps(candidates: List<Entry>): Pair<List<Entry>, List<Dropped>> {
            if (candidates.isEmpty()) return candidates to emptyList()
            val groups = candidates
                .groupBy { it.groupId }
                .values
                .sortedWith(Comparator { a, b -> priority.compare(a.minWith(priority), b.minWith(priority)) })
            val kept = mutableListOf<Entry>()
            val dropped = mutableListOf<Dropped>()
            var maxKeptEnd = Int.MIN_VALUE
            for (group in groups) {
                val mayOverlapKept = group.any { it.edit.startOffset < maxKeptEnd }
                val overlapsKept = mayOverlapKept &&
                    group.any { candidate -> kept.any { overlaps(it.edit, candidate.edit) } }
                if (overlapsKept) {
                    dropped.addAll(group.map { Dropped(it.ruleId, it.edit.startOffset, it.edit.endOffset) })
                } else {
                    kept.addAll(group)
                    for (entry in group) if (entry.edit.endOffset > maxKeptEnd) maxKeptEnd = entry.edit.endOffset
                }
            }
            val keptSet = kept.toHashSet()
            return candidates.filter { it in keptSet } to dropped
        }

        private fun overlaps(a: WEdit, b: WEdit): Boolean = a.startOffset < b.endOffset && b.startOffset < a.endOffset
    }

    private val entries = mutableListOf<Entry>()
    private var nextSequence = 0
    private var nextGroupId = 0
    private var lastDropped: List<Dropped> = emptyList()

    /** A fresh id for grouping every edit of one [WReporter.report] call under [add]'s [groupId] parameter. */
    fun newGroupId(): Int = nextGroupId++

    fun add(ruleId: String, edit: WEdit, groupId: Int = newGroupId()) {
        val entry = Entry(ruleId, edit, nextSequence++, groupId)
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

    /** Whether any collected edit lying within `[startOffset, endOffset]` inserts a line break. */
    fun hasMultilineEditIn(startOffset: Int, endOffset: Int): Boolean = entries.any { entry ->
        entry.edit.startOffset >= startOffset && entry.edit.endOffset <= endOffset && '\n' in entry.edit.replacement
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

    /**
     * Every edit dropped by the most recent [finalEdits] call, plus whatever a consumer that
     * removed entries via [takeAll] handed back through [recordDropped] — exposed for the
     * `count:dropped-edits` perf counter only. The finding a dropped edit came from stays
     * reported regardless of what this list contains.
     */
    fun droppedEdits(): List<Dropped> = lastDropped

    /**
     * Adds [dropped] to what [droppedEdits] reports, for a consumer (`DocBuilder.finish`) that
     * ran [resolveOverlaps] itself over entries it already removed via [takeAll] and is not
     * putting back.
     */
    fun recordDropped(dropped: List<Dropped>) {
        lastDropped = lastDropped + dropped
    }

    fun finalEdits(): List<WEdit> {
        val (kept, dropped) = resolveOverlaps(entries)
        lastDropped = lastDropped + dropped
        return kept.map { it.edit }
    }

    private fun sameSpan(a: WEdit, b: WEdit): Boolean = a.startOffset == b.startOffset && a.endOffset == b.endOffset

    private fun precedes(a: Entry, b: Entry): Boolean {
        if (a.edit.startOffset != b.edit.startOffset) return a.edit.startOffset < b.edit.startOffset
        if (a.edit.endOffset != b.edit.endOffset) return a.edit.endOffset < b.edit.endOffset
        return a.sequence > b.sequence
    }
}
