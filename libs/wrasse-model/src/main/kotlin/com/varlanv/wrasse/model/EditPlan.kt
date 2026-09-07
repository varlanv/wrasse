package com.varlanv.wrasse.model

import com.varlanv.wrasse.lang.WEdit
import java.util.TreeMap

/**
 * Per-file collector of attributed fix edits: [WReporter.report] forwards each attached [WEdit]
 * here as an [Entry], tagged with the reporting rule id, its collection sequence, and a group id
 * ([Entry.groupId]) shared by every edit of that same call — [resolveOverlaps] keeps or drops a
 * group as one atomic unit, never splitting it.
 *
 * Entries are kept ordered by span (start, then end; same-span ties by descending sequence).
 * [finalEdits] resolves overlaps via [resolveOverlaps]: groups are ranked by their earliest edit
 * (start ascending, then span length descending, then sequence ascending) and kept greedily in
 * that order — a group is kept only when none of its edits overlaps an edit already kept by an
 * earlier group, otherwise the whole group is dropped. Touching (one edit's end equal to
 * another's start) is not an overlap. [droppedEdits] exposes what lost this resolution, for the
 * `count:dropped-edits` perf counter only — the diagnostic that produced a dropped edit stays
 * reported regardless of whether its fix survived.
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
         * returned in [candidates]' own order. [keptIntervals] (kept spans by start, merged to
         * the widest end per start) turns each group's overlap check into a log-time lookup
         * instead of a scan of every already-kept edit.
         */
        fun resolveOverlaps(candidates: List<Entry>): Pair<List<Entry>, List<Dropped>> {
            if (candidates.isEmpty()) return candidates to emptyList()
            val groups = candidates
                .groupBy { it.groupId }
                .values
                .sortedWith(Comparator { a, b -> priority.compare(a.minWith(priority), b.minWith(priority)) })
            val kept = mutableListOf<Entry>()
            val dropped = mutableListOf<Dropped>()
            val keptIntervals = TreeMap<Int, Int>()
            for (group in groups) {
                val overlapsKept = group.any { candidate -> overlapsAny(candidate.edit, keptIntervals) }
                if (overlapsKept) {
                    dropped.addAll(group.map { Dropped(it.ruleId, it.edit.startOffset, it.edit.endOffset) })
                } else {
                    kept.addAll(group)
                    for (entry in group) {
                        keptIntervals.merge(entry.edit.startOffset, entry.edit.endOffset, ::maxOf)
                    }
                }
            }
            val keptSet = kept.toHashSet()
            return candidates.filter { it in keptSet } to dropped
        }

        private fun overlaps(a: WEdit, b: WEdit): Boolean = a.startOffset < b.endOffset && b.startOffset < a.endOffset

        private fun overlapsAny(edit: WEdit, keptIntervals: TreeMap<Int, Int>): Boolean {
            val floor = keptIntervals.floorEntry(edit.startOffset)
            if (floor != null && floor.key < edit.endOffset && edit.startOffset < floor.value) return true
            val ceiling = keptIntervals.ceilingEntry(edit.startOffset)
            return ceiling != null && ceiling.key < edit.endOffset && edit.startOffset < ceiling.value
        }
    }

    private val entries = mutableListOf<Entry>()
    private var nextSequence = 0
    private var nextGroupId = 0
    private var lastDropped: List<Dropped> = emptyList()
    private var lastSurvivingGroupIds: Set<Int> = emptySet()
    private val multiEditGroups = HashSet<Int>()

    /** A fresh id for grouping every edit of one [WReporter.report] call under [add]'s [groupId] parameter. */
    fun newGroupId(): Int = nextGroupId++

    /**
     * [groupSize] is the number of edits the report attaches under [groupId]; a group of more than
     * one edit is atomic, so an edit equal in span and replacement to an already-collected entry is
     * merged into it only when neither side's group is atomic.
     */
    fun add(
        ruleId: String,
        edit: WEdit,
        groupId: Int = newGroupId(),
        groupSize: Int = 1,
    ) {
        if (groupSize > 1) multiEditGroups.add(groupId)
        val entry = Entry(ruleId, edit, nextSequence++, groupId)
        var low = 0
        var high = entries.size
        while (low < high) {
            val mid = (low + high) ushr 1
            if (precedes(entry, entries[mid])) high = mid else low = mid + 1
        }
        var probe = low
        while (probe < entries.size && sameSpan(entries[probe].edit, edit)) {
            val existing = entries[probe]
            val safeToMerge = existing.groupId == groupId ||
                (groupId !in multiEditGroups && existing.groupId !in multiEditGroups)
            if (safeToMerge && existing.edit.replacement == edit.replacement) return
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
        lastSurvivingGroupIds = kept.mapTo(HashSet()) { it.groupId }
        return kept.map { it.edit }
    }

    /** Every [Entry.groupId] kept by the most recent [finalEdits] call. */
    fun survivingGroupIds(): Set<Int> = lastSurvivingGroupIds

    private fun sameSpan(a: WEdit, b: WEdit): Boolean = a.startOffset == b.startOffset && a.endOffset == b.endOffset

    private fun precedes(a: Entry, b: Entry): Boolean {
        if (a.edit.startOffset != b.edit.startOffset) return a.edit.startOffset < b.edit.startOffset
        if (a.edit.endOffset != b.edit.endOffset) return a.edit.endOffset < b.edit.endOffset
        return a.sequence > b.sequence
    }
}
