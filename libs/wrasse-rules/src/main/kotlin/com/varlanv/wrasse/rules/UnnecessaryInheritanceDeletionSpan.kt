package com.varlanv.wrasse.rules

import com.varlanv.wrasse.lang.WEdit

/**
 * Deletion-span policy for a redundant `Any()`/`Object()` supertype entry, compiler-free so it is
 * unit-testable without a kotlinc dependency.
 *
 * Three disjoint shapes, one per [UnnecessaryInheritanceRule] call site:
 * - [computeSoleEntry] — the entry is the supertype list's only member. Walks backward from
 *   [entryStart] over whitespace only; the character immediately before that point must be `:`,
 *   else the deletion isn't provably safe (a comment or anything else sits there) and this
 *   returns `null`. Once found, the colon and any whitespace before it are folded into the same
 *   deletion, so `class Foo : Any() {}` collapses to `class Foo {}` with no orphaned space.
 * - [computeLeadingEntry] — the entry is followed by a sibling: deletes the entry's own span up to
 *   (not including) the next entry's own start, taking the separating comma and whitespace with
 *   it, e.g. `Any(), Bar` → `Bar`.
 * - [computeTrailingEntry] — the entry is the list's last member with an earlier sibling: deletes
 *   from the previous entry's own end up to the entry's own end, taking the leading comma and
 *   whitespace with it, e.g. `Bar, Any()` → `Bar`.
 *
 * [computeLeadingEntry]/[computeTrailingEntry] return `null` (report-only) whenever
 * [hasAdjacentComment] is true — a comment sitting in the span that would otherwise be deleted
 * (between the entry and the comma it shares with its neighbor) is never provably safe to fold in,
 * the same uniform comment-bail precedent as every other T-bucket deletion rule.
 */
object UnnecessaryInheritanceDeletionSpan {

    fun computeSoleEntry(sourceText: CharSequence, entryStart: Int, entryEnd: Int): WEdit? {
        var start = entryStart
        while (start > 0 && sourceText[start - 1].isWhitespace()) start--
        if (start == 0 || sourceText[start - 1] != ':') return null
        start--
        while (start > 0 && sourceText[start - 1].isWhitespace()) start--
        return WEdit(start, entryEnd, "")
    }

    fun computeLeadingEntry(entryStart: Int, nextEntryStart: Int, hasAdjacentComment: Boolean): WEdit? =
        if (hasAdjacentComment) null else WEdit(entryStart, nextEntryStart, "")

    fun computeTrailingEntry(previousEntryEnd: Int, entryEnd: Int, hasAdjacentComment: Boolean): WEdit? =
        if (hasAdjacentComment) null else WEdit(previousEntryEnd, entryEnd, "")
}
