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
 *
 * [finalEdits] resolves any surviving overlap rather than failing, via [resolveOverlaps]:
 * candidates are ranked by start ascending, then span length descending, then collection
 * sequence ascending, and kept greedily — an edit is kept unless it overlaps an already-kept
 * one, otherwise it is dropped. Touching (one edit's end equal to another's start) is not an
 * overlap. Ranking longer spans first at a shared start means an outer edit wins over one nested
 * inside it; when two edits share an identical span, the one collected earliest (lowest
 * sequence, i.e. the first rule to report it during the walk) is kept. Dropped entries are
 * exposed via [droppedEdits] so the caller can log them; the finding they came from stays
 * reported regardless, and — since the surviving edit already changed that text — a later pass
 * over the fixed file re-reports and this time fixes it. `DocBuilder.finish` runs the same
 * [resolveOverlaps] over its own [takeAll] before splicing, so the printer never sees a pair of
 * overlapping edits either.
 */
class EditPlan {
    /** One collected edit, attributed to the rule that reported it and its arrival order. */
    class Entry(
        val ruleId: String,
        val edit: WEdit,
        val sequence: Int,
    )

    /** One edit that lost overlap resolution, attributed to its reporting rule. */
    class Dropped(
        val ruleId: String,
        val startOffset: Int,
        val endOffset: Int,
    )

    companion object {
        /** See the class-level overlap-resolution rules. Kept entries are returned in [candidates]' own order. */
        fun resolveOverlaps(candidates: List<Entry>): Pair<List<Entry>, List<Dropped>> {
            val resolutionOrder = candidates.sortedWith(
                compareBy<Entry> { it.edit.startOffset }
                    .thenByDescending { it.edit.endOffset - it.edit.startOffset }
                    .thenBy { it.sequence },
            )
            val kept = mutableListOf<Entry>()
            val dropped = mutableListOf<Dropped>()
            for (candidate in resolutionOrder) {
                if (kept.any { overlaps(it.edit, candidate.edit) }) {
                    dropped.add(Dropped(candidate.ruleId, candidate.edit.startOffset, candidate.edit.endOffset))
                } else {
                    kept.add(candidate)
                }
            }
            val keptSet = kept.toHashSet()
            return candidates.filter { it in keptSet } to dropped
        }

        private fun overlaps(a: WEdit, b: WEdit): Boolean = a.startOffset < b.endOffset && b.startOffset < a.endOffset
    }

    private val entries = mutableListOf<Entry>()
    private var nextSequence = 0
    private var lastDropped: List<Dropped> = emptyList()

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

    /** Every edit dropped by the most recent [finalEdits] call, in the order they were dropped. */
    fun droppedEdits(): List<Dropped> = lastDropped

    fun finalEdits(): List<WEdit> {
        val (kept, dropped) = resolveOverlaps(entries)
        lastDropped = dropped
        return kept.map { it.edit }
    }

    private fun sameSpan(a: WEdit, b: WEdit): Boolean = a.startOffset == b.startOffset && a.endOffset == b.endOffset

    private fun precedes(a: Entry, b: Entry): Boolean {
        if (a.edit.startOffset != b.edit.startOffset) return a.edit.startOffset < b.edit.startOffset
        if (a.edit.endOffset != b.edit.endOffset) return a.edit.endOffset < b.edit.endOffset
        return a.sequence > b.sequence
    }
}
